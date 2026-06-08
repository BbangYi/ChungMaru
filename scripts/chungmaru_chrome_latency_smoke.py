#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import hashlib
import http.server
import json
import os
import shutil
import socket
import socketserver
import struct
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any

from chungmaru_latency_csv import (
    DEFAULT_MATRIX_BATCH_SIZES,
    DEFAULT_MATRIX_SCENARIOS,
    parse_csv_list,
    parse_int_list,
    select_texts,
)


DEFAULT_OUTPUT = Path("evaluation/latency/results/chrome-last-stats.jsonl")
DEFAULT_EXTENSION_DIR = Path("extension/chrome")
DEFAULT_PROFILE_DIR = Path("/tmp/chungmaru-chrome-latency-profile")
DEFAULT_CHROME_LOG = Path("/tmp/chungmaru-chrome-latency.log")
CHROME_APP = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
DEFAULT_PIPELINE_RUN_REASON = "manual-request"


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def html_escape(value: str) -> str:
    return (
        str(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def build_latency_page() -> str:
    return """<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <title>Chungmaru Latency Fixture</title>
  <style>
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f7f7f4;
      color: #111;
    }
    main {
      width: min(980px, calc(100vw - 48px));
      margin: 32px auto;
    }
    .result-card {
      border: 1px solid #d8d5cc;
      background: #fffdfa;
      padding: 18px 20px;
      margin: 12px 0;
      border-radius: 10px;
    }
    .meta {
      color: #777;
      font-size: 13px;
      margin-bottom: 6px;
    }
    .comment {
      font-size: 18px;
      line-height: 1.5;
      font-weight: 650;
    }
  </style>
</head>
<body>
  <main>
    <h1>Chungmaru Latency Fixture</h1>
    <p id="fixture-meta">waiting</p>
    <section id="fixture-root"></section>
  </main>
  <script>
    window.__renderChungmaruLatencyScenario = function(payload) {
      const root = document.getElementById("fixture-root");
      const meta = document.getElementById("fixture-meta");
      const texts = Array.isArray(payload.texts) ? payload.texts : [];
      meta.textContent = `${payload.scenario} / batch ${payload.batchSize} / sample ${payload.sampleId}`;
      root.innerHTML = texts.map((text, index) => `
        <article class="result-card" data-index="${index}">
          <div class="meta">좋아요 ${index + 1}개 · 답글달기 · ${index + 1}분 전</div>
          <h2 class="comment">${text}</h2>
          <p>검색 결과 스니펫과 댓글 후보를 함께 둔 테스트 영역입니다.</p>
        </article>
      `).join("");
      document.body.dataset.renderedSample = String(payload.sampleId || "");
      return { ok: true, count: texts.length };
    };
  </script>
</body>
</html>
"""


class FixtureHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(build_latency_page().encode("utf-8"))

    def log_message(self, format: str, *args: Any) -> None:
        return


class ThreadedTCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


def start_fixture_server(port: int) -> ThreadedTCPServer:
    server = ThreadedTCPServer(("127.0.0.1", port), FixtureHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


class CdpWebSocket:
    def __init__(self, ws_url: str) -> None:
        parsed = urllib.parse.urlparse(ws_url)
        if parsed.scheme != "ws":
            raise ValueError(f"Only ws:// URLs are supported: {ws_url}")
        self.host = parsed.hostname or "127.0.0.1"
        self.port = parsed.port or 80
        self.path = parsed.path
        if parsed.query:
            self.path += f"?{parsed.query}"
        self.socket = socket.create_connection((self.host, self.port), timeout=10)
        self._next_id = 0
        self._handshake()

    def close(self) -> None:
        try:
            self.socket.close()
        except OSError:
            pass

    def _handshake(self) -> None:
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        request = (
            f"GET {self.path} HTTP/1.1\r\n"
            f"Host: {self.host}:{self.port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        self.socket.sendall(request.encode("ascii"))
        response = b""
        while b"\r\n\r\n" not in response:
            chunk = self.socket.recv(4096)
            if not chunk:
                break
            response += chunk
        if b" 101 " not in response.split(b"\r\n", 1)[0]:
            raise RuntimeError(f"WebSocket handshake failed: {response[:200]!r}")

    def _send_frame(self, payload: bytes) -> None:
        header = bytearray([0x81])
        length = len(payload)
        if length < 126:
            header.append(0x80 | length)
        elif length < 65536:
            header.append(0x80 | 126)
            header.extend(struct.pack("!H", length))
        else:
            header.append(0x80 | 127)
            header.extend(struct.pack("!Q", length))
        mask = os.urandom(4)
        header.extend(mask)
        masked = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
        self.socket.sendall(bytes(header) + masked)

    def _recv_exact(self, length: int) -> bytes:
        chunks = []
        remaining = length
        while remaining > 0:
            chunk = self.socket.recv(remaining)
            if not chunk:
                raise RuntimeError("WebSocket closed")
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)

    def _recv_frame(self) -> dict[str, Any]:
        first_two = self._recv_exact(2)
        opcode = first_two[0] & 0x0F
        masked = bool(first_two[1] & 0x80)
        length = first_two[1] & 0x7F
        if length == 126:
            length = struct.unpack("!H", self._recv_exact(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", self._recv_exact(8))[0]
        mask = self._recv_exact(4) if masked else b""
        payload = self._recv_exact(length)
        if masked:
            payload = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
        if opcode == 8:
            raise RuntimeError("WebSocket closed by remote")
        if opcode != 1:
            return {}
        return json.loads(payload.decode("utf-8"))

    def call(self, method: str, params: dict[str, Any] | None = None, timeout_s: float = 10) -> dict[str, Any]:
        self._next_id += 1
        message_id = self._next_id
        self._send_frame(json.dumps({"id": message_id, "method": method, "params": params or {}}).encode("utf-8"))
        deadline = time.time() + timeout_s
        last_timeout: TimeoutError | None = None
        while time.time() < deadline:
            try:
                message = self._recv_frame()
            except TimeoutError as error:
                last_timeout = error
                continue
            if message.get("id") == message_id:
                if "error" in message:
                    raise RuntimeError(f"CDP {method} failed: {message['error']}")
                return message.get("result", {})
        if last_timeout is not None:
            raise TimeoutError(f"Timed out waiting for {method}: {last_timeout}")
        raise TimeoutError(f"Timed out waiting for {method}")

    def evaluate(self, expression: str, await_promise: bool = True, timeout_s: float = 10) -> Any:
        result = self.call(
            "Runtime.evaluate",
            {
                "expression": expression,
                "awaitPromise": await_promise,
                "returnByValue": True,
            },
            timeout_s=timeout_s,
        )
        remote = result.get("result", {})
        if "value" in remote:
            return remote["value"]
        return None


def http_json(url: str, timeout_s: float = 10, method: str = "GET") -> Any:
    request = urllib.request.Request(url, method=method)
    with urllib.request.urlopen(request, timeout=timeout_s) as response:
        return json.loads(response.read())


def wait_for_targets(debugging_port: int, timeout_s: float = 15) -> list[dict[str, Any]]:
    deadline = time.time() + timeout_s
    last_error: Exception | None = None
    while time.time() < deadline:
        try:
            return http_json(f"http://127.0.0.1:{debugging_port}/json/list", timeout_s=2)
        except Exception as error:  # noqa: BLE001 - diagnostics for local Chrome startup
            last_error = error
            time.sleep(0.2)
    raise RuntimeError(f"Chrome debugging endpoint did not become ready: {last_error}")


def wait_for_service_worker(debugging_port: int, timeout_s: float = 15) -> tuple[str, dict[str, Any]]:
    deadline = time.time() + timeout_s
    last_targets: list[dict[str, Any]] = []
    while time.time() < deadline:
        targets = wait_for_targets(debugging_port, timeout_s=2)
        last_targets = targets
        extension_workers = []
        for target in targets:
            url = str(target.get("url") or "")
            if target.get("type") != "service_worker" or not url.startswith("chrome-extension://"):
                continue
            extension_workers.append(target)
            probe: CdpWebSocket | None = None
            try:
                probe = CdpWebSocket(str(target["webSocketDebuggerUrl"]))
                manifest_name = probe.evaluate("chrome.runtime.getManifest().name", timeout_s=2)
                if "청마루" in str(manifest_name):
                    extension_id = urllib.parse.urlparse(url).netloc
                    return extension_id, target
            except Exception:
                pass
            finally:
                if probe:
                    probe.close()
        time.sleep(0.25)
    summary = [
        {
            "type": target.get("type"),
            "title": target.get("title"),
            "url": str(target.get("url") or "")[:180],
        }
        for target in last_targets
    ]
    raise RuntimeError(f"Chungmaru extension service worker target was not found; targets={summary}")


def create_tab(debugging_port: int, url: str) -> dict[str, Any]:
    encoded = urllib.parse.quote(url, safe=":/?&=%")
    endpoint = f"http://127.0.0.1:{debugging_port}/json/new?{encoded}"
    try:
        return http_json(endpoint, method="PUT")
    except urllib.error.HTTPError:
        return http_json(endpoint)


def wait_for_page_ready(page: CdpWebSocket, timeout_s: float = 10) -> None:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        ready_state = page.evaluate("document.readyState", timeout_s=2)
        if ready_state in {"interactive", "complete"}:
            return
        time.sleep(0.1)
    raise TimeoutError("Timed out waiting for fixture page document.readyState")


def launch_chrome(args: argparse.Namespace) -> subprocess.Popen[bytes]:
    extension_dir = args.extension_dir.resolve()
    profile_dir = args.profile_dir.resolve()
    if args.clean_profile and profile_dir.exists():
        shutil.rmtree(profile_dir)
    profile_dir.mkdir(parents=True, exist_ok=True)

    command = [
        str(args.chrome_path),
        f"--user-data-dir={profile_dir}",
        f"--remote-debugging-port={args.debugging_port}",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-background-networking",
        "--disable-features=DisableLoadExtensionCommandLineSwitch",
        "--enable-logging=stderr",
        f"--disable-extensions-except={extension_dir}",
        f"--load-extension={extension_dir}",
        "about:blank",
    ]
    if sys.platform.startswith("win"):
        # Chrome for Testing can fail early under Windows SSH from user-writable paths.
        command.extend([
            "--no-sandbox",
            "--disable-gpu",
        ])
    args.chrome_log.parent.mkdir(parents=True, exist_ok=True)
    if args.chrome_log.exists():
        args.chrome_log.unlink()
    log_handle = args.chrome_log.open("ab")
    try:
        return subprocess.Popen(command, stdout=log_handle, stderr=log_handle)
    finally:
        log_handle.close()


def build_extension_settings(args: argparse.Namespace) -> dict[str, Any]:
    return {
        "enabled": True,
        "sensitivity": args.sensitivity,
        "categories": {
            "profanity": True,
            "toxicity": True,
            "hate": True,
            "abuse": True,
            "insult": True,
            "spam": True,
        },
        "interventionMode": "mask",
        "showReason": True,
        "siteProtectionEnabled": False,
        "searchResultProtectionEnabled": False,
        "showWellbeingWidget": False,
        "backendEnabled": True,
        "backendApiBaseUrl": args.backend,
        "requestTimeoutMs": 10000,
    }


def set_extension_settings(worker: CdpWebSocket, args: argparse.Namespace) -> dict[str, Any]:
    settings = build_extension_settings(args)
    write_expression = (
        "(async () => {"
        f"await chrome.storage.sync.set({json.dumps({'settings': settings}, ensure_ascii=False)});"
        "await chrome.storage.local.clear();"
        "return await chrome.storage.sync.get('settings');"
        "})()"
    )
    read_expression = "(async () => await chrome.storage.sync.get('settings'))()"
    last_result: Any = None
    for _ in range(12):
        try:
            worker.evaluate(write_expression, timeout_s=20)
        except Exception as error:  # noqa: BLE001 - retry diagnostics for flaky CDP/service-worker startup
            last_result = {"writeError": str(error)}
            time.sleep(0.5)
            continue
        time.sleep(0.35)
        try:
            last_result = worker.evaluate(read_expression, timeout_s=10)
        except Exception as error:  # noqa: BLE001 - retry diagnostics for flaky CDP/service-worker startup
            last_result = {"readError": str(error)}
            time.sleep(0.5)
            continue
        stored_settings = last_result.get("settings") if isinstance(last_result, dict) else None
        if isinstance(stored_settings, dict) and stored_settings.get("backendEnabled") is True:
            return stored_settings
    raise RuntimeError(f"Failed to enable backend in extension settings: {last_result}")


def get_last_stats(worker: CdpWebSocket) -> dict[str, Any] | None:
    value = worker.evaluate(
        "(async () => await chrome.storage.local.get(['lastStats']))()",
        timeout_s=5,
    )
    if isinstance(value, dict) and isinstance(value.get("lastStats"), dict):
        return value["lastStats"]
    return None


def get_requested_analysis_count(stats: dict[str, Any] | None) -> int:
    if not isinstance(stats, dict):
        return 0
    value = stats.get("requestedAnalysisCount")
    if not value:
        diagnostics = stats.get("lastForegroundDiagnostics")
        if isinstance(diagnostics, dict):
            value = diagnostics.get("requestedTextCount")
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0


def read_existing_progress(output: Path) -> dict[str, int]:
    if not output.exists():
        return {"rows": 0, "detections": 0, "max_sample_id": 0}

    rows = 0
    detections = 0
    max_sample_id = 0
    with output.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                continue
            rows += 1
            try:
                max_sample_id = max(max_sample_id, int(record.get("sample_id") or 0))
            except (TypeError, ValueError):
                pass
            detections += get_requested_analysis_count(record.get("lastStats"))
    return {"rows": rows, "detections": detections, "max_sample_id": max_sample_id}


def clear_last_stats(worker: CdpWebSocket) -> None:
    worker.evaluate(
        "(async () => { await chrome.storage.local.remove(['lastStats', 'lastPayload', 'lastDecision']); return true; })()",
        timeout_s=5,
    )


def clear_extension_runtime_state(worker: CdpWebSocket) -> None:
    worker.evaluate(
        "("
        "async () => {"
        "  await chrome.storage.local.remove(["
        "    'lastStats',"
        "    'lastPayload',"
        "    'lastDecision',"
        "    'lastForegroundDiagnostics',"
        "    'analysisCache',"
        "    'backendAnalysisCache'"
        "  ]);"
        "  return true;"
        "}"
        ")()",
        timeout_s=5,
    )


def render_sample(page: CdpWebSocket, scenario: str, batch_size: int, sample_id: int) -> None:
    texts = [
        f"{text} [latency sample {sample_id}-{index}]"
        for index, text in enumerate(select_texts(batch_size, sample_id, scenario))
    ]
    payload = {
        "scenario": scenario,
        "batchSize": batch_size,
        "sampleId": sample_id,
        "texts": texts,
    }
    expression = f"window.__renderChungmaruLatencyScenario({json.dumps(payload, ensure_ascii=False)})"
    page.evaluate(expression, timeout_s=5)


def send_to_fixture_tab(
    worker: CdpWebSocket,
    page_url: str,
    message: dict[str, Any],
    *,
    inject_on_failure: bool,
    timeout_s: float = 5,
) -> dict[str, Any]:
    expression = (
        "(async () => {"
        f"const pageUrl = {json.dumps(page_url)};"
        f"const message = {json.dumps(message, ensure_ascii=False)};"
        f"const injectOnFailure = {json.dumps(inject_on_failure)};"
        "const manifest = chrome.runtime.getManifest();"
        "const apiState = {"
        "  name: manifest.name,"
        "  permissions: manifest.permissions || [],"
        "  hasTabs: !!chrome.tabs,"
        "  hasScripting: !!chrome.scripting,"
        "  hasRuntime: !!chrome.runtime"
        "};"
        "const files = ['content-runtime-status.js', 'content-editable-overlay.js', 'content-self-test.js', 'content-wellbeing-widget.js', 'content-script.js'];"
        "async function send(tab, phase) {"
        "  try {"
        "    const response = await chrome.tabs.sendMessage(tab.id, message);"
        "    return { ok: true, tabId: tab.id, phase, response, apiState };"
        "  } catch (error) {"
        "    return { ok: false, tabId: tab.id, phase, reason: String(error && error.message ? error.message : error), apiState };"
        "  }"
        "}"
        "async function inject(tab) {"
        "  try {"
        "    await chrome.scripting.insertCSS({ target: { tabId: tab.id }, files: ['content-style.css'] });"
        "  } catch (error) {}"
        "  await chrome.scripting.executeScript({ target: { tabId: tab.id }, files });"
        "  await new Promise((resolve) => setTimeout(resolve, 250));"
        "}"
        "const tabs = await chrome.tabs.query({});"
        "const summaries = tabs.slice(0, 8).map((item) => ({ id: item.id, url: item.url || '', title: item.title || '', active: !!item.active }));"
        "const ordered = [];"
        "const byUrl = tabs.find((item) => String(item.url || '').startsWith(pageUrl));"
        "if (byUrl) ordered.push(byUrl);"
        "const active = tabs.find((item) => item.active);"
        "if (active && !ordered.some((item) => item.id === active.id)) ordered.push(active);"
        "for (let index = tabs.length - 1; index >= 0; index -= 1) {"
        "  const item = tabs[index];"
        "  if (item && item.id && !ordered.some((candidate) => candidate.id === item.id)) ordered.push(item);"
        "}"
        "const attempts = [];"
        "for (const tab of ordered) {"
        "  if (!tab || !tab.id) continue;"
        "  const response = await send(tab, 'manifest');"
        "  if (response.ok) return { ...response, tabs: summaries, attempts };"
        "  attempts.push(response);"
        "}"
        "if (injectOnFailure) {"
        "  for (const tab of ordered) {"
        "    if (!tab || !tab.id) continue;"
        "    try {"
        "      await inject(tab);"
        "    } catch (error) {"
        "      attempts.push({ tabId: tab.id, phase: 'inject', reason: String(error && error.message ? error.message : error), apiState });"
        "      continue;"
        "    }"
        "    const response = await send(tab, 'programmatic-inject');"
        "    if (response.ok) return { ...response, tabs: summaries, attempts };"
        "    attempts.push(response);"
        "  }"
        "}"
        "return { ok: false, reason: 'NO_TAB_ACCEPTED_MESSAGE', tabs: summaries, attempts, apiState };"
        "})()"
    )
    value = worker.evaluate(expression, timeout_s=timeout_s)
    return value if isinstance(value, dict) else {"ok": False, "reason": f"unexpected trigger response: {value!r}"}


def apply_settings_to_page(
    worker: CdpWebSocket,
    page_url: str,
    settings: dict[str, Any],
    timeout_s: float = 5,
) -> dict[str, Any]:
    return send_to_fixture_tab(
        worker,
        page_url,
        {"type": "APPLY_SETTINGS_SNAPSHOT", "settings": settings},
        inject_on_failure=True,
        timeout_s=timeout_s,
    )


def trigger_pipeline(
    worker: CdpWebSocket,
    page_url: str,
    reason: str,
    timeout_s: float = 5,
) -> dict[str, Any]:
    return send_to_fixture_tab(
        worker,
        page_url,
        {"type": "RUN_PIPELINE", "reason": reason},
        inject_on_failure=False,
        timeout_s=timeout_s,
    )


def trigger_pipeline_with_retry(
    worker: CdpWebSocket,
    page_url: str,
    reason: str,
    timeout_s: float = 4,
) -> dict[str, Any]:
    deadline = time.time() + timeout_s
    last_response: dict[str, Any] | None = None
    while time.time() < deadline:
        response = trigger_pipeline(worker, page_url, reason, timeout_s=3)
        last_response = response
        if response.get("ok"):
            return response
        time.sleep(0.2)
    return last_response or {"ok": False, "reason": "trigger timed out before first attempt"}


def is_usable_backend_stats(stats: dict[str, Any], expected_run_reason: str) -> bool:
    phase = stats.get("phaseTimings")
    if not isinstance(phase, dict) or phase.get("totalToMaskMs") is None:
        return False
    if stats.get("runReason") == expected_run_reason:
        return True
    return False


def number_like(value: Any) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0.0


def wait_for_sample_stats(worker: CdpWebSocket, expected_run_reason: str, timeout_s: float) -> dict[str, Any]:
    deadline = time.time() + timeout_s
    last_stats: dict[str, Any] | None = None
    while time.time() < deadline:
        stats = get_last_stats(worker)
        if stats:
            last_stats = stats
            if is_usable_backend_stats(stats, expected_run_reason):
                return stats
        time.sleep(0.08)
    raise TimeoutError(
        f"Timed out waiting for extension phaseTimings with runReason={expected_run_reason}; "
        f"lastStats={last_stats}"
    )


def extract_trigger_stats(trigger_response: dict[str, Any], expected_run_reason: str) -> dict[str, Any] | None:
    response = trigger_response.get("response") if isinstance(trigger_response, dict) else None
    stats = response.get("stats") if isinstance(response, dict) else None
    if not isinstance(stats, dict) or stats.get("runReason") != expected_run_reason:
        return None
    phase = stats.get("phaseTimings")
    if not isinstance(phase, dict) or phase.get("totalToMaskMs") is None:
        return None
    return stats


def sample_plan(args: argparse.Namespace) -> list[tuple[str, int]]:
    scenarios = parse_csv_list(args.scenarios, DEFAULT_MATRIX_SCENARIOS)
    batch_sizes = parse_int_list(args.batch_sizes, DEFAULT_MATRIX_BATCH_SIZES)
    return [(scenario, batch_size) for scenario in scenarios for batch_size in batch_sizes]


def run_smoke(args: argparse.Namespace) -> None:
    server = start_fixture_server(args.page_port)
    chrome_process: subprocess.Popen[bytes] | None = None
    worker: CdpWebSocket | None = None
    page: CdpWebSocket | None = None
    rows_written = 0
    pipeline_run_reason = str(args.pipeline_reason or DEFAULT_PIPELINE_RUN_REASON)

    try:
        chrome_process = launch_chrome(args)
        extension_id, worker_target = wait_for_service_worker(args.debugging_port, args.startup_timeout)
        worker = CdpWebSocket(str(worker_target["webSocketDebuggerUrl"]))
        settings = build_extension_settings(args)
        try:
            settings = set_extension_settings(worker, args)
        except RuntimeError as error:
            # Some local Chrome profiles can hang on chrome.storage.sync writes while
            # content-script message delivery still works. For E2E smoke, keep testing
            # the real masking path by applying the same settings snapshot to the tab.
            print(f"settings_storage_fallback={error}", flush=True)

        page_url = f"http://127.0.0.1:{args.page_port}/"
        page_target = create_tab(args.debugging_port, page_url)
        page = CdpWebSocket(str(page_target["webSocketDebuggerUrl"]))
        page.call("Page.enable")
        page.call("Runtime.enable")
        page.call("Page.navigate", {"url": page_url})
        wait_for_page_ready(page, timeout_s=10)
        settings_response = apply_settings_to_page(worker, page_url, settings, timeout_s=8)
        if not settings_response.get("ok"):
            raise RuntimeError(f"APPLY_SETTINGS_SNAPSHOT failed: {settings_response}")
        time.sleep(1.0)
        clear_extension_runtime_state(worker)

        args.output.parent.mkdir(parents=True, exist_ok=True)
        existing_progress = read_existing_progress(args.output) if args.append else {
            "rows": 0,
            "detections": 0,
            "max_sample_id": 0
        }
        rows_written = int(existing_progress["rows"])
        detections_written = int(existing_progress["detections"])
        run_rows_written = 0
        run_detections_written = 0
        started_monotonic = time.monotonic()

        if args.append and args.output.exists():
            print(
                f"append=true existing_rows={rows_written} "
                f"existing_detections={detections_written} output={args.output}"
            )

        def should_stop() -> bool:
            if args.target_detections and run_detections_written >= args.target_detections:
                return True
            if args.max_samples and run_rows_written >= args.max_samples:
                return True
            if args.max_runtime_minutes:
                return (time.monotonic() - started_monotonic) >= args.max_runtime_minutes * 60
            return False

        combinations = sample_plan(args)
        sample_id = int(existing_progress["max_sample_id"])
        mode = "a" if args.append else "w"
        with args.output.open(mode, encoding="utf-8") as handle:
            if args.target_detections:
                combo_index = 0
                while not should_stop():
                    scenario, batch_size = combinations[combo_index % len(combinations)]
                    combo_index += 1
                    sample_id += 1
                    clear_last_stats(worker)
                    render_sample(page, scenario, batch_size, sample_id)
                    time.sleep(0.25)
                    trigger_response = trigger_pipeline_with_retry(
                        worker,
                        page_url,
                        pipeline_run_reason,
                    )
                    if not trigger_response.get("ok"):
                        raise RuntimeError(f"RUN_PIPELINE trigger failed: {trigger_response}")
                    stats = extract_trigger_stats(trigger_response, pipeline_run_reason)
                    if stats is None:
                        stats = wait_for_sample_stats(worker, pipeline_run_reason, args.sample_timeout)
                    record = {
                        "run_id": args.run_id,
                        "sample_id": sample_id,
                        "trigger_reason": pipeline_run_reason,
                        "measured_at": now_iso(),
                        "url": page_url,
                        "scenario": scenario,
                        "batch_size": batch_size,
                        "sensitivity": args.sensitivity,
                        "extension_id": extension_id,
                        "trigger": trigger_response,
                        "lastStats": stats,
                        "notes": "real Chrome unpacked extension content-script pipeline",
                    }
                    handle.write(json.dumps(record, ensure_ascii=False) + "\n")
                    handle.flush()
                    rows_written += 1
                    run_rows_written += 1
                    sample_detections = get_requested_analysis_count(stats)
                    detections_written += sample_detections
                    run_detections_written += sample_detections
                    print(
                        f"[{rows_written}] {scenario} batch={batch_size} "
                        f"run_detections={run_detections_written}"
                        f"{('/' + str(args.target_detections)) if args.target_detections else ''} "
                        f"total_detections={detections_written} "
                        f"totalToMask={stats.get('phaseTimings', {}).get('totalToMaskMs')}ms "
                        f"backend={stats.get('phaseTimings', {}).get('backendRoundTripMs')}ms"
                    )
            else:
                for scenario, batch_size in combinations:
                    for _ in range(args.samples_per_combo):
                        if should_stop():
                            break
                        sample_id += 1
                        clear_last_stats(worker)
                        render_sample(page, scenario, batch_size, sample_id)
                        time.sleep(0.25)
                        trigger_response = trigger_pipeline_with_retry(
                            worker,
                            page_url,
                            pipeline_run_reason,
                        )
                        if not trigger_response.get("ok"):
                            raise RuntimeError(f"RUN_PIPELINE trigger failed: {trigger_response}")
                        stats = extract_trigger_stats(trigger_response, pipeline_run_reason)
                        if stats is None:
                            stats = wait_for_sample_stats(worker, pipeline_run_reason, args.sample_timeout)
                        record = {
                            "run_id": args.run_id,
                            "sample_id": sample_id,
                            "trigger_reason": pipeline_run_reason,
                            "measured_at": now_iso(),
                            "url": page_url,
                            "scenario": scenario,
                            "batch_size": batch_size,
                            "sensitivity": args.sensitivity,
                            "extension_id": extension_id,
                            "trigger": trigger_response,
                            "lastStats": stats,
                            "notes": "real Chrome unpacked extension content-script pipeline",
                        }
                        handle.write(json.dumps(record, ensure_ascii=False) + "\n")
                        handle.flush()
                        rows_written += 1
                        run_rows_written += 1
                        sample_detections = get_requested_analysis_count(stats)
                        detections_written += sample_detections
                        run_detections_written += sample_detections
                        print(
                            f"[{rows_written}] {scenario} batch={batch_size} "
                            f"run_detections={run_detections_written} "
                            f"total_detections={detections_written} "
                            f"totalToMask={stats.get('phaseTimings', {}).get('totalToMaskMs')}ms "
                            f"backend={stats.get('phaseTimings', {}).get('backendRoundTripMs')}ms"
                        )
                    if should_stop():
                        break

        print(f"wrote={args.output}")
        print(f"rows={rows_written}")
        print(f"detections={detections_written}")
        print(f"run_rows={run_rows_written}")
        print(f"run_detections={run_detections_written}")
    finally:
        if page:
            page.close()
        if worker:
            worker.close()
        if chrome_process:
            chrome_process.terminate()
            try:
                chrome_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                chrome_process.kill()
        server.shutdown()
        server.server_close()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run a real Chrome extension latency smoke and export lastStats JSONL.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--backend", default="http://127.0.0.1:8000")
    parser.add_argument("--extension-dir", type=Path, default=DEFAULT_EXTENSION_DIR)
    parser.add_argument("--profile-dir", type=Path, default=DEFAULT_PROFILE_DIR)
    parser.add_argument("--chrome-path", type=Path, default=CHROME_APP)
    parser.add_argument("--chrome-log", type=Path, default=DEFAULT_CHROME_LOG)
    parser.add_argument("--debugging-port", type=int, default=9233)
    parser.add_argument("--page-port", type=int, default=8765)
    parser.add_argument("--scenarios", default="mixed,search-result,profanity,bypass")
    parser.add_argument("--batch-sizes", default="4,8,16")
    parser.add_argument("--samples-per-combo", type=int, default=3)
    parser.add_argument("--sensitivity", type=int, default=60)
    parser.add_argument("--startup-timeout", type=float, default=20)
    parser.add_argument("--sample-timeout", type=float, default=8)
    parser.add_argument("--run-id", default=f"chrome-smoke-{datetime.now().strftime('%Y%m%d-%H%M%S')}")
    parser.add_argument("--pipeline-reason", default=DEFAULT_PIPELINE_RUN_REASON)
    parser.add_argument("--clean-profile", action="store_true")
    parser.add_argument("--append", action="store_true", help="Append to an existing JSONL output and resume counts.")
    parser.add_argument(
        "--target-detections",
        type=int,
        default=0,
        help="Run until this execution adds this many requestedAnalysisCount detections.",
    )
    parser.add_argument(
        "--max-samples",
        type=int,
        default=0,
        help="Stop after this execution adds this many JSONL rows.",
    )
    parser.add_argument(
        "--max-runtime-minutes",
        type=float,
        default=0,
        help="Stop gracefully after this many minutes.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if not args.chrome_path.exists():
        raise SystemExit(f"Chrome executable not found: {args.chrome_path}")
    if not args.extension_dir.exists():
        raise SystemExit(f"Extension directory not found: {args.extension_dir}")
    run_smoke(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
