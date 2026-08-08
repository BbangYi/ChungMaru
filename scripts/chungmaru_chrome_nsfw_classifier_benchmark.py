#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import http.server
import json
import math
import os
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import urllib.error
from datetime import datetime
from functools import partial
from pathlib import Path
from typing import Any, Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from chungmaru_chrome_latency_smoke import CdpWebSocket, wait_for_service_worker, wait_for_targets


MODEL_VERSION = "nsfwjs-mobilenet-v2@v4.2.1"
RUNTIME_MODE = "headless-swiftshader-webgl"
DEFAULT_EXTENSION_DIR = Path("extension/chrome")
DEFAULT_OUTPUT_DIR = Path("evaluation/media-safety/results/current")
DEFAULT_CORPUS_DIR = Path(os.environ.get("CHUNGMARU_NSFW_CORPUS_DIR", "/private/tmp/chungmaru-nsfw-corpus"))
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "ChungmaruMediaSafetyBot/1.0 (https://github.com/Gimminu/Chungmaru)"
ALLOWED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
MIN_IMAGE_DIMENSION = 160
MAX_IMAGE_BYTES = 5 * 1024 * 1024
EXCLUDED_TITLE_WORDS = ("child", "children", "girl", "boy", "teen", "minor", "schoolgirl", "underage")
COMMONS_REQUEST_INTERVAL_SECONDS = 0.75
_LAST_COMMONS_REQUEST_AT = 0.0

CORPUS_BUCKETS = (
    {
        "name": "harmful_photo",
        "label": "harmful",
        "target": 25,
        "categories": [
            "Nude women in 2015",
            "Nude women in 2016",
            "Nude women in 2017",
            "Nude women in 2018",
            "Nude women in 2019",
            "Nude women in 2020",
        ],
    },
    {
        "name": "harmful_illustration",
        "label": "harmful",
        "target": 15,
        "categories": [
            "Alphabet pornographique (Joseph Apoux)",
            "Sex drawings",
            "Human sexual activity drawings",
            "Drawings of couples having sex",
            "Shunga drawings",
            "Erotic illustrations in French books",
        ],
    },
    {
        "name": "benign_general",
        "label": "benign",
        "target": 30,
        "categories": ["Landscapes", "Dogs", "Cats"],
    },
    {
        "name": "benign_hard_negative",
        "label": "benign",
        "target": 30,
        "categories": ["Women wearing bikinis", "Cosplay"],
    },
)


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as handle:
        handle.bind(("127.0.0.1", 0))
        return int(handle.getsockname()[1])


def percentile(values: Iterable[float], percentile_value: float) -> float:
    ordered = sorted(float(value) for value in values)
    if not ordered:
        return 0.0
    position = (len(ordered) - 1) * percentile_value / 100
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 2)
    fraction = position - lower
    return round(ordered[lower] * (1 - fraction) + ordered[upper] * fraction, 2)


def commons_headers() -> dict[str, str]:
    return {
        "User-Agent": USER_AGENT,
        "Api-User-Agent": USER_AGENT,
        "Accept": "application/json,image/avif,image/webp,image/png,image/jpeg,*/*;q=0.8",
    }


def throttle_commons_request() -> None:
    global _LAST_COMMONS_REQUEST_AT
    remaining = COMMONS_REQUEST_INTERVAL_SECONDS - (time.monotonic() - _LAST_COMMONS_REQUEST_AT)
    if remaining > 0:
        time.sleep(remaining)
    _LAST_COMMONS_REQUEST_AT = time.monotonic()


def retry_delay_seconds(error: Exception, attempt: int) -> float:
    if isinstance(error, urllib.error.HTTPError) and error.code in {429, 503}:
        retry_after = str(error.headers.get("Retry-After") or "").strip()
        if retry_after.isdigit():
            return min(120.0, max(2.0, float(retry_after)))
        return min(120.0, 10.0 * (2 ** attempt))
    return min(8.0, 1.0 * (2 ** attempt))


def request_json(params: dict[str, str], attempts: int = 5) -> dict[str, Any]:
    request_params = {"maxlag": "2", **params}
    url = f"{COMMONS_API}?{urllib.parse.urlencode(request_params)}"
    last_error: Exception | None = None
    for attempt in range(attempts):
        try:
            throttle_commons_request()
            request = urllib.request.Request(url, headers=commons_headers())
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
            if isinstance(payload, dict):
                return payload
        except Exception as error:  # noqa: BLE001 - network retries are bounded
            last_error = error
        time.sleep(retry_delay_seconds(last_error, attempt))
    raise RuntimeError(f"Wikimedia API request failed: {last_error}")


def category_file_titles(category: str, limit: int = 160) -> list[str]:
    titles: list[str] = []
    continuation = ""
    while len(titles) < limit:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": f"Category:{category}",
            "cmtype": "file",
            "cmlimit": "100",
            "format": "json",
            "formatversion": "2",
        }
        if continuation:
            params["cmcontinue"] = continuation
        payload = request_json(params)
        members = payload.get("query", {}).get("categorymembers", [])
        titles.extend(str(item.get("title") or "") for item in members if item.get("title"))
        continuation = str(payload.get("continue", {}).get("cmcontinue") or "")
        if not continuation or not members:
            break
        time.sleep(0.25)
    return titles[:limit]


def image_info_for_titles(titles: list[str]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for start in range(0, len(titles), 40):
        payload = request_json({
            "action": "query",
            "titles": "|".join(titles[start:start + 40]),
            "prop": "imageinfo",
            "iiprop": "url|size|mime|extmetadata",
            "iiurlwidth": "640",
            "format": "json",
            "formatversion": "2",
        })
        for page in payload.get("query", {}).get("pages", []):
            info = (page.get("imageinfo") or [{}])[0]
            metadata = info.get("extmetadata") or {}
            rows.append({
                "pageId": int(page.get("pageid") or 0),
                "title": str(page.get("title") or ""),
                "mime": str(info.get("mime") or ""),
                "width": int(info.get("thumbwidth") or info.get("width") or 0),
                "height": int(info.get("thumbheight") or info.get("height") or 0),
                "downloadUrl": str(info.get("thumburl") or info.get("url") or ""),
                "sourcePage": str(info.get("descriptionurl") or ""),
                "license": str((metadata.get("LicenseShortName") or {}).get("value") or ""),
                "licenseUrl": str((metadata.get("LicenseUrl") or {}).get("value") or ""),
            })
        time.sleep(0.25)
    return rows


def license_is_allowed(value: str) -> bool:
    normalized = str(value or "").lower()
    return any(token in normalized for token in ("public domain", "cc0", "cc by", "pdm"))


def title_is_allowed(value: str) -> bool:
    normalized = str(value or "").lower()
    return not any(token in normalized for token in EXCLUDED_TITLE_WORDS)


def download_image(url: str, output: Path, attempts: int = 4) -> tuple[str, int]:
    if output.exists() and 0 < output.stat().st_size <= MAX_IMAGE_BYTES:
        data = output.read_bytes()
        return hashlib.sha256(data).hexdigest(), len(data)
    last_error: Exception | None = None
    for attempt in range(attempts):
        try:
            throttle_commons_request()
            request = urllib.request.Request(url, headers=commons_headers())
            with urllib.request.urlopen(request, timeout=45) as response:
                declared = int(response.headers.get("Content-Length") or 0)
                if declared > MAX_IMAGE_BYTES:
                    raise RuntimeError("image exceeds byte limit")
                data = response.read(MAX_IMAGE_BYTES + 1)
            if len(data) > MAX_IMAGE_BYTES:
                raise RuntimeError("image exceeds byte limit")
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
            return hashlib.sha256(data).hexdigest(), len(data)
        except Exception as error:  # noqa: BLE001 - network retries are bounded
            last_error = error
            time.sleep(retry_delay_seconds(error, attempt))
    raise RuntimeError(f"image download failed: {last_error}")


def build_corpus_manifest(corpus_dir: Path) -> dict[str, Any]:
    images_dir = corpus_dir / "images"
    images_dir.mkdir(parents=True, exist_ok=True)
    samples: list[dict[str, Any]] = []

    for bucket in CORPUS_BUCKETS:
        bucket_candidates: list[dict[str, Any]] = []
        seen_titles: set[str] = set()
        for category in bucket["categories"]:
            titles = [title for title in category_file_titles(str(category)) if title not in seen_titles]
            seen_titles.update(titles)
            for info in image_info_for_titles(titles):
                if (
                    info["mime"] not in ALLOWED_MIME_TYPES
                    or min(info["width"], info["height"]) < MIN_IMAGE_DIMENSION
                    or not info["downloadUrl"]
                    or not license_is_allowed(info["license"])
                    or not title_is_allowed(info["title"])
                ):
                    continue
                info["sourceCategory"] = category
                bucket_candidates.append(info)
            if len(bucket_candidates) >= int(bucket["target"]) * 2:
                break

        bucket_candidates.sort(key=lambda item: (int(item["pageId"]), str(item["title"])))
        selected = bucket_candidates[:int(bucket["target"])]
        if len(selected) < int(bucket["target"]):
            raise RuntimeError(
                f"insufficient Commons candidates for {bucket['name']}: "
                f"required={bucket['target']} found={len(selected)}"
            )

        calibration_count = round(int(bucket["target"]) * 0.6)
        for index, info in enumerate(selected, start=1):
            sample_id = f"{bucket['name']}-{index:03d}"
            extension = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}[info["mime"]]
            local_name = f"{sample_id}{extension}"
            sha256, byte_count = download_image(info["downloadUrl"], images_dir / local_name)
            samples.append({
                "sampleId": sample_id,
                "label": bucket["label"],
                "bucket": bucket["name"],
                "split": "calibration" if index <= calibration_count else "holdout",
                "title": info["title"],
                "sourceCategory": info["sourceCategory"],
                "sourcePage": info["sourcePage"],
                "downloadUrl": info["downloadUrl"],
                "license": info["license"],
                "licenseUrl": info["licenseUrl"],
                "sha256": sha256,
                "bytes": byte_count,
                "mime": info["mime"],
                "localName": local_name,
            })

    manifest = {
        "schemaVersion": 1,
        "createdAt": now_iso(),
        "source": "Wikimedia Commons category-derived candidate corpus",
        "reviewStatus": "category_seed_needs_human_review",
        "labelPolicy": "adult-only category seed with minor-related title exclusions; human review still required",
        "sampleCount": len(samples),
        "samples": samples,
    }
    (corpus_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


def load_or_prepare_corpus(corpus_dir: Path, refresh: bool) -> dict[str, Any]:
    manifest_path = corpus_dir / "manifest.json"
    if refresh or not manifest_path.exists():
        return build_corpus_manifest(corpus_dir)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for sample in manifest.get("samples", []):
        image_path = corpus_dir / "images" / str(sample["localName"])
        if not image_path.exists():
            raise RuntimeError(f"corpus image missing: {image_path}")
        digest = hashlib.sha256(image_path.read_bytes()).hexdigest()
        if digest != sample.get("sha256"):
            raise RuntimeError(f"corpus checksum mismatch: {sample.get('sampleId')}")
    return manifest


class QuietCorpusHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format: str, *args: Any) -> None:
        return


def start_corpus_server(images_dir: Path, port: int) -> http.server.ThreadingHTTPServer:
    handler = partial(QuietCorpusHandler, directory=str(images_dir))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def detect_chrome_path(explicit: Path | None) -> Path:
    candidates: list[Path] = []
    if explicit:
        candidates.append(explicit)
    configured = os.environ.get("CHUNGMARU_CHROME_PATH")
    if configured:
        candidates.append(Path(configured))
    if os.name == "nt":
        for root in filter(None, [os.environ.get("LOCALAPPDATA"), os.environ.get("PROGRAMFILES"), os.environ.get("PROGRAMFILES(X86)")]):
            candidates.extend([
                Path(root) / "Google" / "Chrome for Testing" / "Application" / "chrome.exe",
                Path(root) / "Google" / "Chrome" / "Application" / "chrome.exe",
            ])
    else:
        cft_root = Path("/private/tmp/chungmaru-chrome-for-testing/chrome")
        if cft_root.exists():
            candidates.extend(sorted(cft_root.glob("*/chrome-*/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"), reverse=True))
        candidates.extend([
            Path("/Applications/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"),
            Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
        ])
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise RuntimeError("Chrome for Testing/Chrome executable was not found")


def launch_chrome(chrome_path: Path, extension_dir: Path, profile_dir: Path, port: int, log_path: Path) -> subprocess.Popen[bytes]:
    if profile_dir.exists():
        shutil.rmtree(profile_dir)
    profile_dir.mkdir(parents=True, exist_ok=True)
    command = [
        str(chrome_path),
        f"--user-data-dir={profile_dir}",
        f"--remote-debugging-port={port}",
        "--headless=new",
        "--enable-webgl",
        "--use-angle=swiftshader",
        "--ignore-gpu-blocklist",
        "--no-first-run",
        "--no-default-browser-check",
        "--window-size=1440,900",
        "--window-position=-4000,0",
        "--disable-background-networking",
        "--disable-component-extensions-with-background-pages",
        "--disable-features=DisableLoadExtensionCommandLineSwitch",
        "--enable-unsafe-extension-debugging",
        f"--load-extension={extension_dir.resolve()}",
        "about:blank",
    ]
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_handle = log_path.open("wb")
    try:
        return subprocess.Popen(command, stdout=log_handle, stderr=log_handle)
    finally:
        log_handle.close()


def stop_process(process: subprocess.Popen[bytes]) -> None:
    process.terminate()
    try:
        process.wait(timeout=5)
    except Exception:
        process.kill()


def worker_call(worker: CdpWebSocket, function_name: str, *args: Any, timeout_s: float = 30) -> dict[str, Any]:
    serialized = ",".join(json.dumps(item, ensure_ascii=False) for item in args)
    value = worker.evaluate(f"(async () => {function_name}({serialized}))()", timeout_s=timeout_s)
    if not isinstance(value, dict):
        raise RuntimeError(f"unexpected {function_name} response: {value!r}")
    return value


def configure_worker(worker: CdpWebSocket, backend: str) -> None:
    settings = {
        "enabled": True,
        "sensitivity": 60,
        "textMaskingEnabled": False,
        "siteProtectionEnabled": False,
        "siteNavigationWarningEnabled": False,
        "searchResultProtectionEnabled": False,
        "mediaSafetyEnabled": True,
        "mediaSafetyInterventionMode": "auto",
        "mediaSafetyStartupGateEnabled": False,
        "showWellbeingWidget": False,
        "backendEnabled": False,
    }
    worker.evaluate(
        "(async () => {"
        "await chrome.storage.local.set({developerRuntimeLogEnabled:false});"
        f"await chrome.storage.sync.set({json.dumps({'settings': settings})});"
        "return true;})()",
        timeout_s=10,
    )
    deadline = time.monotonic() + 12
    last_response: dict[str, Any] = {}
    while time.monotonic() < deadline:
        response = worker.evaluate(
            "(async () => setNsfwClassifierTestOverride("
            f"{{type:'SET_NSFW_CLASSIFIER_TEST_OVERRIDE',mode:{json.dumps(backend)}}},"
            "{id:chrome.runtime.id}))()",
            timeout_s=10,
        )
        if response.get("ok"):
            return
        last_response = response
        time.sleep(0.25)
    raise RuntimeError(f"failed to enable classifier after startup warm-up: {last_response}")


class ResilientExtensionWorker:
    """Reconnects to the MV3 service worker when Chrome retires its CDP target."""

    def __init__(self, port: int, backend: str):
        self.port = port
        self.backend = backend
        self._worker: CdpWebSocket | None = None
        self.reconnect_count = 0

    def attach(self, *, configure: bool) -> None:
        _extension_id, target = wait_for_service_worker(self.port, timeout_s=25)
        self._worker = CdpWebSocket(str(target["webSocketDebuggerUrl"]))
        if configure:
            configure_worker(self._worker, self.backend)

    def evaluate(self, expression: str, timeout_s: float = 30) -> Any:
        if self._worker is None:
            self.attach(configure=True)
        try:
            return self._worker.evaluate(expression, timeout_s=timeout_s)
        except RuntimeError as error:
            if "WebSocket closed" not in str(error):
                raise
            self._worker.close()
            self._worker = None
            self.reconnect_count += 1
            self.attach(configure=True)
            return self._worker.evaluate(expression, timeout_s=timeout_s)

    def close(self) -> None:
        if self._worker is not None:
            self._worker.close()
            self._worker = None


def open_worker(
    chrome_path: Path,
    extension_dir: Path,
    profile_dir: Path,
    port: int,
    log_path: Path,
    backend: str,
) -> tuple[subprocess.Popen[bytes], ResilientExtensionWorker]:
    process = launch_chrome(chrome_path, extension_dir, profile_dir, port, log_path)
    try:
        wait_for_targets(port, timeout_s=25)
        worker = ResilientExtensionWorker(port, backend)
        worker.attach(configure=True)
        return process, worker
    except Exception:
        stop_process(process)
        raise


def classify_batch(
    worker: CdpWebSocket,
    samples: list[dict[str, Any]],
    source_urls: dict[str, str],
    phase: str,
    run_index: int,
    batch_index: int,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    items = [
        {"candidateKey": f"{phase}-{run_index}-{batch_index}-{sample['sampleId']}", "sourceUrl": source_urls[sample["sampleId"]]}
        for sample in samples
    ]
    message = {
        "type": "CLASSIFY_NSFW_IMAGE_BATCH",
        "requestId": f"{phase}-{run_index}-{batch_index}",
        "contextKey": phase,
        "items": items,
    }
    started = time.perf_counter()
    response = worker_call(
        worker,
        "classifyNsfwImageBatch",
        message,
        {"id": "__benchmark__", "tab": {"url": "http://127.0.0.1/benchmark"}},
        timeout_s=45,
    )
    wall_ms = round((time.perf_counter() - started) * 1000, 2)
    results_by_key = {str(item.get("candidateKey") or ""): item for item in response.get("results", [])}
    raw_rows: list[dict[str, Any]] = []
    for sample, request_item in zip(samples, items):
        result = results_by_key.get(request_item["candidateKey"], {})
        scores = result.get("scores") or {}
        raw_rows.append({
            "captured_at": now_iso(),
            "model_version": response.get("modelVersion") or MODEL_VERSION,
            "runtime_mode": RUNTIME_MODE,
            "backend": response.get("backend") or "",
            "phase": phase,
            "run_index": run_index,
            "batch_size": len(samples),
            "batch_index": batch_index,
            "sample_id": sample["sampleId"],
            "label": sample["label"],
            "bucket": sample["bucket"],
            "split": sample["split"],
            "adult_context": False,
            "drawing": round(float(scores.get("Drawing") or 0), 8),
            "hentai": round(float(scores.get("Hentai") or 0), 8),
            "neutral": round(float(scores.get("Neutral") or 0), 8),
            "porn": round(float(scores.get("Porn") or 0), 8),
            "sexy": round(float(scores.get("Sexy") or 0), 8),
            "explicit_score": round(float(scores.get("Porn") or 0) + float(scores.get("Hentai") or 0), 8),
            "cache_hit": bool(result.get("cacheHit")),
            "fetch_ms": int(result.get("fetchMs") or 0),
            "decode_ms": int(result.get("decodeMs") or 0),
            "inference_ms": int(response.get("inferenceMs") or 0),
            "queue_wait_ms": int(response.get("queueWaitMs") or 0),
            "offscreen_total_ms": int(response.get("totalMs") or 0),
            "wall_ms": wall_ms,
            "ok": bool(result.get("ok")),
            "error_code": str(result.get("errorCode") or response.get("errorCode") or ""),
        })
    batch_row = {
        "runtime_mode": RUNTIME_MODE,
        "phase": phase,
        "run_index": run_index,
        "batch_size": len(samples),
        "batch_index": batch_index,
        "candidate_count": len(samples),
        "cache_hit_count": int(response.get("cacheHitCount") or 0),
        "fetch_ms": int(response.get("fetchMs") or 0),
        "decode_ms": int(response.get("decodeMs") or 0),
        "inference_ms": int(response.get("inferenceMs") or 0),
        "queue_wait_ms": int(response.get("queueWaitMs") or 0),
        "offscreen_total_ms": int(response.get("totalMs") or 0),
        "wall_ms": wall_ms,
        "backend": str(response.get("backend") or ""),
        "model_load_count": int(response.get("modelLoadCount") or 0),
        "tensor_count": int(response.get("tensorCount") or 0),
        "ok": bool(response.get("ok")),
        "error_code": str(response.get("errorCode") or ""),
    }
    return raw_rows, batch_row


def run_batches(
    worker: CdpWebSocket,
    samples: list[dict[str, Any]],
    source_urls: dict[str, str],
    *,
    phase: str,
    run_index: int,
    batch_size: int,
    completed_batches: set[tuple[str, int, int, int]] | None = None,
    on_batch: Any | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    raw_rows: list[dict[str, Any]] = []
    batch_rows: list[dict[str, Any]] = []
    for batch_index, start in enumerate(range(0, len(samples), batch_size), start=1):
        key = (phase, run_index, batch_size, batch_index)
        if completed_batches and key in completed_batches:
            continue
        current = samples[start:start + batch_size]
        raw, batch = classify_batch(worker, current, source_urls, phase, run_index, batch_index)
        raw_rows.extend(raw)
        batch_rows.append(batch)
        if on_batch:
            on_batch(raw, batch)
    return raw_rows, batch_rows


def choose_calibrated_threshold(rows: list[dict[str, Any]]) -> tuple[float, dict[str, float]]:
    candidates = [round(value / 100, 2) for value in range(45, 86, 5)]
    feasible: list[tuple[float, float, float]] = []
    for threshold in candidates:
        harmful = [row for row in rows if row["label"] == "harmful"]
        benign = [row for row in rows if row["label"] == "benign"]
        recall = sum(float(row["explicit_score"]) >= threshold for row in harmful) / max(1, len(harmful))
        false_hidden = sum(float(row["explicit_score"]) >= threshold for row in benign) / max(1, len(benign))
        if false_hidden <= 0.05:
            feasible.append((recall, threshold, false_hidden))
    if not feasible:
        return 0.65, {"recall": 0.0, "falseHiddenRate": 1.0}
    recall, threshold, false_hidden = max(feasible, key=lambda item: (item[0], item[1]))
    return threshold, {"recall": recall, "falseHiddenRate": false_hidden}


def quality_metrics(rows: list[dict[str, Any]], threshold: float) -> dict[str, float | int]:
    harmful = [row for row in rows if row["label"] == "harmful"]
    benign = [row for row in rows if row["label"] == "benign"]
    harmful_blocked = sum(float(row["explicit_score"]) >= threshold for row in harmful)
    benign_blocked = sum(float(row["explicit_score"]) >= threshold for row in benign)
    return {
        "harmfulCount": len(harmful),
        "benignCount": len(benign),
        "harmfulBlocked": harmful_blocked,
        "benignBlocked": benign_blocked,
        "harmfulRecall": round(harmful_blocked / max(1, len(harmful)), 4),
        "benignFalseHiddenRate": round(benign_blocked / max(1, len(benign)), 4),
    }


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = sorted({key for row in rows for key in row})
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def read_checkpoint(path: Path, *, resume: bool) -> dict[str, Any]:
    if not resume or not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return payload if isinstance(payload, dict) else {}


def write_checkpoint(
    path: Path,
    *,
    status: str,
    raw_rows: list[dict[str, Any]],
    batch_rows: list[dict[str, Any]],
    cold_rows: list[dict[str, Any]],
    final_status: dict[str, Any] | None = None,
    error: str = "",
) -> None:
    payload = {
        "updatedAt": now_iso(),
        "status": status,
        "modelVersion": MODEL_VERSION,
        "rawRows": raw_rows,
        "batchRows": batch_rows,
        "coldRows": cold_rows,
        "finalStatus": final_status or {},
        "error": error,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temp_path.replace(path)


def batch_key(row: dict[str, Any]) -> tuple[str, int, int, int]:
    return (
        str(row.get("phase") or ""),
        int(row.get("run_index") or 0),
        int(row.get("batch_size") or 0),
        int(row.get("batch_index") or 0),
    )


def build_summary_rows(batch_rows: list[dict[str, Any]], quality: dict[str, Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    groups: dict[tuple[str, int], list[dict[str, Any]]] = {}
    for row in batch_rows:
        groups.setdefault((str(row["phase"]), int(row["batch_size"])), []).append(row)
    for (phase, batch_size), group in sorted(groups.items()):
        rows.append({
            "record_type": "performance",
            "phase": phase,
            "batch_size": batch_size,
            "batch_count": len(group),
            "candidate_count": sum(int(item["candidate_count"]) for item in group),
            "cache_hit_rate": round(sum(int(item["cache_hit_count"]) for item in group) / max(1, sum(int(item["candidate_count"]) for item in group)), 4),
            "inference_ms_p50": percentile((item["inference_ms"] for item in group), 50),
            "inference_ms_p95": percentile((item["inference_ms"] for item in group), 95),
            "offscreen_total_ms_p50": percentile((item["offscreen_total_ms"] for item in group), 50),
            "offscreen_total_ms_p95": percentile((item["offscreen_total_ms"] for item in group), 95),
            "wall_ms_p50": percentile((item["wall_ms"] for item in group), 50),
            "wall_ms_p95": percentile((item["wall_ms"] for item in group), 95),
            "error_count": sum(not bool(item["ok"]) for item in group),
        })
    rows.append({"record_type": "quality", **quality})
    return rows


def write_report(
    output_dir: Path,
    manifest: dict[str, Any],
    cold_rows: list[dict[str, Any]],
    summary_rows: list[dict[str, Any]],
    quality: dict[str, Any],
    final_status: dict[str, Any],
) -> str:
    warm_rows = [row for row in summary_rows if row.get("record_type") == "performance" and row.get("phase") == "full-corpus"]
    warm_inference_p95 = max((float(row.get("inference_ms_p95") or 0) for row in warm_rows), default=0)
    warm_total_p95 = max((float(row.get("offscreen_total_ms_p95") or 0) for row in warm_rows), default=0)
    cached_rows = [row for row in summary_rows if row.get("record_type") == "performance" and row.get("phase") == "full-corpus-cache"]
    cache_hit_rate = max((float(row.get("cache_hit_rate") or 0) for row in cached_rows), default=0)
    reviewed = manifest.get("reviewStatus") == "human_reviewed"
    criteria = {
        "corpus_count_100": int(manifest.get("sampleCount") or 0) == 100,
        "labels_human_reviewed": reviewed,
        "holdout_recall_ge_85pct": float(quality["holdoutHarmfulRecall"]) >= 0.85,
        "holdout_false_hidden_le_5pct": float(quality["holdoutBenignFalseHiddenRate"]) <= 0.05,
        "warm_classifier_total_p95_lt_1000ms": warm_total_p95 < 1000,
        "cache_hit_rate_ge_90pct": cache_hit_rate >= 0.9,
        "model_load_once": int(final_status.get("modelLoadCount") or 0) == 1,
        "tensor_delta_zero": int(quality.get("tensorDelta") or 0) == 0,
    }
    verdict = "controlled_classifier_proof" if all(criteria.values()) else "Validation Needed"
    lines = [
        "# Chungmaru NSFW Classifier Report",
        "",
        f"- Verdict: **{verdict}**",
        f"- Model: `{MODEL_VERSION}`",
        f"- Runtime: `{RUNTIME_MODE}` (controlled benchmark only; not a user-device GPU measurement)",
        f"- Backend: `{final_status.get('backend') or 'unknown'}`",
        f"- Corpus: {manifest.get('sampleCount', 0)} candidates, review status `{manifest.get('reviewStatus')}`",
        f"- Calibrated explicit threshold: `{quality['calibratedThreshold']}`",
        f"- Holdout harmful recall: {float(quality['holdoutHarmfulRecall']) * 100:.1f}%",
        f"- Holdout benign false-hidden rate: {float(quality['holdoutBenignFalseHiddenRate']) * 100:.1f}%",
        f"- Warm inference p95: {warm_inference_p95:.1f} ms",
        f"- Warm classifier total p95: {warm_total_p95:.1f} ms",
        f"- Repeated-pass cache hit rate: {cache_hit_rate * 100:.1f}%",
        f"- Cold model load runs: {len(cold_rows)}",
        "",
        "## Acceptance",
        "",
    ]
    lines.extend(f"- [{'x' if passed else ' '}] `{name}`" for name, passed in criteria.items())
    lines.extend([
        "",
        "## Scope",
        "",
        "The image bytes stayed in Desktop scratch and are not copied into repository or runner artifacts. ",
        "The manifest contains source, license, SHA-256, label bucket, and split only. Category-derived labels require human review before quality claims. ",
        "Passing this report permits only controlled classifier proof; it does not claim broad live-site maturity.",
        "",
    ])
    (output_dir / "nsfw-classifier-report.md").write_text("\n".join(lines), encoding="utf-8")
    return verdict


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Benchmark Chungmaru's bundled Chrome NSFW classifier.")
    parser.add_argument("--extension-dir", type=Path, default=DEFAULT_EXTENSION_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--corpus-dir", type=Path, default=DEFAULT_CORPUS_DIR)
    parser.add_argument("--chrome-path", type=Path, default=None)
    parser.add_argument("--profile-root", type=Path, default=None)
    parser.add_argument("--refresh-corpus", action="store_true")
    parser.add_argument("--cold-runs", type=int, default=3)
    parser.add_argument("--warm-runs", type=int, default=3)
    parser.add_argument(
        "--checkpoint",
        type=Path,
        default=None,
        help="Checkpoint JSON path. Defaults to output-dir/nsfw-classifier-checkpoint.json.",
    )
    parser.add_argument("--no-resume", action="store_true", help="Ignore any existing checkpoint.")
    parser.add_argument("--warmup-only", action="store_true")
    parser.add_argument(
        "--backend",
        choices=["normal", "cpu"],
        default="normal",
        help="Benchmark-only TFJS backend selection. normal uses WebGL with headless SwiftShader; cpu is a reference run.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.output_dir = args.output_dir.resolve()
    args.corpus_dir = args.corpus_dir.resolve()
    args.extension_dir = args.extension_dir.resolve()
    args.profile_root = (args.profile_root or args.corpus_dir.parent / "chrome-profiles").resolve()
    args.checkpoint = (args.checkpoint or args.output_dir / "nsfw-classifier-checkpoint.json").resolve()
    chrome_path = detect_chrome_path(args.chrome_path)
    args.output_dir.mkdir(parents=True, exist_ok=True)

    if args.warmup_only:
        port = find_free_port()
        process, worker = open_worker(
            chrome_path,
            args.extension_dir,
            args.profile_root / "warmup-only",
            port,
            args.output_dir / "chrome-warmup.log",
            args.backend,
        )
        try:
            warmup = worker_call(worker, "warmNsfwClassifier", {"source": "benchmark-warmup"}, timeout_s=45)
            print(json.dumps({"ok": bool(warmup.get("ok")), "warmup": warmup}, ensure_ascii=False, indent=2))
            return 0 if warmup.get("ok") else 1
        finally:
            worker.close()
            stop_process(process)

    manifest = load_or_prepare_corpus(args.corpus_dir, args.refresh_corpus)
    samples = list(manifest.get("samples") or [])
    if len(samples) != 100:
        raise RuntimeError(f"expected 100 corpus samples, found {len(samples)}")
    corpus_port = find_free_port()
    server = start_corpus_server(args.corpus_dir / "images", corpus_port)
    source_urls = {
        sample["sampleId"]: f"http://127.0.0.1:{corpus_port}/{urllib.parse.quote(sample['localName'])}"
        for sample in samples
    }

    checkpoint = read_checkpoint(args.checkpoint, resume=not args.no_resume)
    cold_rows: list[dict[str, Any]] = list(checkpoint.get("coldRows") or [])
    raw_rows: list[dict[str, Any]] = list(checkpoint.get("rawRows") or [])
    batch_rows: list[dict[str, Any]] = list(checkpoint.get("batchRows") or [])
    final_status: dict[str, Any] = dict(checkpoint.get("finalStatus") or {})
    completed_cold_runs = {int(row.get("run_index") or 0) for row in cold_rows}
    completed_batches = {batch_key(row) for row in batch_rows}

    def checkpoint_progress(status: str = "running", error: str = "") -> None:
        write_checkpoint(
            args.checkpoint,
            status=status,
            raw_rows=raw_rows,
            batch_rows=batch_rows,
            cold_rows=cold_rows,
            final_status=final_status,
            error=error,
        )

    checkpoint_progress("running")
    try:
        for cold_index in range(1, max(1, args.cold_runs) + 1):
            if cold_index in completed_cold_runs:
                continue
            port = find_free_port()
            process, worker = open_worker(
                chrome_path,
                args.extension_dir,
                args.profile_root / f"cold-{cold_index}",
                port,
                args.output_dir / f"chrome-cold-{cold_index}.log",
                args.backend,
            )
            try:
                started = time.perf_counter()
                warmup = worker_call(worker, "warmNsfwClassifier", {"source": "benchmark-cold"}, timeout_s=45)
                cold_rows.append({
                    "run_index": cold_index,
                    "runtime_mode": RUNTIME_MODE,
                    "backend": warmup.get("backend") or "",
                    "model_load_ms": int(warmup.get("modelLoadMs") or 0),
                    "warmup_ms": int(warmup.get("warmupMs") or 0),
                    "wall_ms": round((time.perf_counter() - started) * 1000, 2),
                    "model_load_count": int(warmup.get("modelLoadCount") or 0),
                    "tensor_count": int(warmup.get("tensorCount") or 0),
                    "ok": bool(warmup.get("ok")),
                    "error_code": str(warmup.get("errorCode") or ""),
                })
                completed_cold_runs.add(cold_index)
                checkpoint_progress("running")
            finally:
                worker.close()
                stop_process(process)

        port = find_free_port()
        process, worker = open_worker(
            chrome_path,
            args.extension_dir,
            args.profile_root / "warm-main",
            port,
            args.output_dir / "chrome-warm-main.log",
            args.backend,
        )
        try:
            warmup = worker_call(worker, "warmNsfwClassifier", {"source": "benchmark-warm"}, timeout_s=45)
            tensor_baseline = int(warmup.get("tensorCount") or 0)
            representative = [samples[index] for index in range(0, len(samples), max(1, len(samples) // 12))][:12]
            for batch_size in (1, 2):
                variant_urls = {
                    sample["sampleId"]: f"{source_urls[sample['sampleId']]}?batch={batch_size}"
                    for sample in representative
                }
                run_batches(
                    worker,
                    representative,
                    variant_urls,
                    phase="batch-comparison",
                    run_index=batch_size,
                    batch_size=batch_size,
                    completed_batches=completed_batches,
                    on_batch=lambda raw_batch, batch: (
                        raw_rows.extend(raw_batch),
                        batch_rows.append(batch),
                        completed_batches.add(batch_key(batch)),
                        checkpoint_progress("running"),
                    ),
                )

            for run_index in range(1, max(1, args.warm_runs) + 1):
                phase = "full-corpus" if run_index == 1 else "full-corpus-cache"
                run_batches(
                    worker,
                    samples,
                    source_urls,
                    phase=phase,
                    run_index=run_index,
                    batch_size=2,
                    completed_batches=completed_batches,
                    on_batch=lambda raw_batch, batch: (
                        raw_rows.extend(raw_batch),
                        batch_rows.append(batch),
                        completed_batches.add(batch_key(batch)),
                        checkpoint_progress("running"),
                    ),
                )

            final_status = worker_call(worker, "getNsfwClassifierStatus", timeout_s=15)
            tensor_delta = int(final_status.get("tensorCount") or 0) - tensor_baseline
            checkpoint_progress("finalizing")
        finally:
            worker.close()
            stop_process(process)
    except Exception as error:
        checkpoint_progress("failed", str(error))
        raise
    finally:
        server.shutdown()
        server.server_close()

    calibration_rows = [row for row in raw_rows if row["phase"] == "full-corpus" and row["split"] == "calibration"]
    holdout_rows = [row for row in raw_rows if row["phase"] == "full-corpus" and row["split"] == "holdout"]
    calibrated_threshold, calibration_quality = choose_calibrated_threshold(calibration_rows)
    holdout_quality = quality_metrics(holdout_rows, calibrated_threshold)
    fixed_holdout_quality = quality_metrics(holdout_rows, 0.65)
    quality = {
        "calibratedThreshold": calibrated_threshold,
        "calibrationHarmfulRecall": calibration_quality["recall"],
        "calibrationBenignFalseHiddenRate": calibration_quality["falseHiddenRate"],
        "holdoutHarmfulRecall": holdout_quality["harmfulRecall"],
        "holdoutBenignFalseHiddenRate": holdout_quality["benignFalseHiddenRate"],
        "fixedThresholdHoldoutRecall": fixed_holdout_quality["harmfulRecall"],
        "fixedThresholdHoldoutFalseHiddenRate": fixed_holdout_quality["benignFalseHiddenRate"],
        "tensorDelta": tensor_delta,
    }
    summary_rows = build_summary_rows(batch_rows, quality)
    comparison_rows = [
        {"metric": "cold_model_load_ms", "baseline": 0, "optimized": percentile((row["model_load_ms"] for row in cold_rows), 50), "target": "record_only"},
        {"metric": "warm_classifier_total_p95_ms", "baseline": 0, "optimized": max((row.get("offscreen_total_ms_p95", 0) for row in summary_rows if row.get("phase") == "full-corpus"), default=0), "target": "<1000"},
        {"metric": "cached_wall_p95_ms", "baseline": 0, "optimized": max((row.get("wall_ms_p95", 0) for row in summary_rows if row.get("phase") == "full-corpus-cache"), default=0), "target": "<=150"},
        {"metric": "cache_hit_rate", "baseline": 0, "optimized": max((row.get("cache_hit_rate", 0) for row in summary_rows if row.get("phase") == "full-corpus-cache"), default=0), "target": ">=0.90"},
        {"metric": "google_general_search_classifier_requests", "baseline": 0, "optimized": "pending_quick_qa", "target": "0"},
    ]

    write_csv(args.output_dir / "nsfw-classifier-raw.csv", raw_rows)
    write_csv(args.output_dir / "nsfw-classifier-summary.csv", summary_rows)
    write_csv(args.output_dir / "nsfw-performance-comparison.csv", comparison_rows)
    write_csv(args.output_dir / "nsfw-cold-start.csv", cold_rows)
    artifact_fields = ("sampleId", "sourcePage", "license", "licenseUrl", "sha256", "label", "split")
    manifest_for_artifact = {
        key: value
        for key, value in manifest.items()
        if key != "samples"
    }
    manifest_for_artifact["samples"] = [
        {key: sample.get(key) for key in artifact_fields}
        for sample in samples
    ]
    (args.output_dir / "nsfw-corpus-manifest.json").write_text(
        json.dumps(manifest_for_artifact, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    verdict = write_report(args.output_dir, manifest, cold_rows, summary_rows, quality, final_status)
    checkpoint_progress("complete")
    print(json.dumps({
        "ok": True,
        "verdict": verdict,
        "outputDir": str(args.output_dir),
        "corpusCount": len(samples),
        "quality": quality,
        "status": final_status,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
