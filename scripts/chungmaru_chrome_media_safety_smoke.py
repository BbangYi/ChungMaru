#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import html
import http.server
import json
import shutil
import socket
import sys
import subprocess
import threading
import time
import urllib.parse
from datetime import datetime
from pathlib import Path
from typing import Any

from chungmaru_chrome_latency_smoke import (
    CHROME_APP,
    CdpWebSocket,
    ThreadedTCPServer,
    create_tab,
    send_to_fixture_tab,
    wait_for_page_ready,
    wait_for_service_worker,
    wait_for_targets,
)


DEFAULT_EXTENSION_DIR = Path("extension/chrome")
DEFAULT_PROFILE_DIR = Path("/tmp/chungmaru-chrome-media-safety-profile")
DEFAULT_CHROME_LOG = Path("/tmp/chungmaru-chrome-media-safety.log")
DEFAULT_OUTPUT_DIR = Path("evaluation/media-safety/results/current")
FIXTURE_OUTPUT_PREFIX = "media-safety-smoke"
LIVE_OUTPUT_PREFIX = "media-safety-live-smoke"
DEFAULT_CHROME_FOR_TESTING_ROOT = Path("/private/tmp/chungmaru-chrome-for-testing/chrome")
CHROME_FOR_TESTING_EXECUTABLE = (
    "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
)
DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY = "developerRuntimeLogEnabled"


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as handle:
        handle.bind(("127.0.0.1", 0))
        return int(handle.getsockname()[1])


def version_sort_key(path: Path) -> tuple[int, ...]:
    parts = []
    for item in path.name.split("."):
      try:
        parts.append(int(item))
      except ValueError:
        parts.append(0)
    return tuple(parts)


def detect_chrome_path(explicit_path: Path | None) -> Path:
    if explicit_path:
      return explicit_path

    if DEFAULT_CHROME_FOR_TESTING_ROOT.exists():
      version_dirs = [
          item for item in DEFAULT_CHROME_FOR_TESTING_ROOT.iterdir()
          if item.is_dir()
      ]
      for version_dir in sorted(version_dirs, key=version_sort_key, reverse=True):
        candidate = version_dir / CHROME_FOR_TESTING_EXECUTABLE
        if candidate.exists():
          return candidate

    return CHROME_APP


def svg_response(label: str, tone: str) -> str:
    fill = {
        "gambling": "#111827",
        "adult": "#7f1d1d",
        "clean": "#0f766e",
    }.get(tone, "#1f2937")
    accent = {
        "gambling": "#facc15",
        "adult": "#fecdd3",
        "clean": "#a7f3d0",
    }.get(tone, "#f9fafb")
    safe_label = html.escape(label)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="640" height="220" viewBox="0 0 640 220">
  <rect width="640" height="220" rx="16" fill="{fill}"/>
  <rect x="18" y="18" width="604" height="184" rx="14" fill="none" stroke="{accent}" stroke-width="6"/>
  <text x="36" y="92" fill="{accent}" font-size="36" font-weight="800" font-family="Arial, sans-serif">{safe_label}</text>
  <text x="38" y="148" fill="#ffffff" font-size="24" font-weight="700" font-family="Arial, sans-serif">Chungmaru media fixture</text>
</svg>"""


def build_media_page(scenario: str) -> str:
    address_guide_cards = "\n".join(
        f"""<div class="jbanner-large-item" data-chungmaru-media-harmful="true">
        <a href="https://jusoguide1.com/bbs/bannerhit.php?bn_id={index}" target="_blank">
          <video autoplay loop muted playsinline class="jbanner-media">
            <source src="https://jusoguide1.com/data/banner_my/{index}.webm?v=20260630214255" type="video/webm">
          </video>
        </a>
      </div>"""
        for index in [23, 58, 15, 61, 46, 55, 60, 59]
    )
    harmful_cards = """
      <article class="media-card" data-chungmaru-media-harmful="true">
        <img alt="카지노 스포츠토토 가입코드 첫충 페이백 배너" src="/media.svg?tone=gambling&label=%EC%B9%B4%EC%A7%80%EB%85%B8%20%EA%B0%80%EC%9E%85%EC%BD%94%EB%93%9C" />
        <h2>카지노 가입코드 첫충 페이백 배너</h2>
        <p>스포츠토토, 바카라, 슬롯, 고액 환전 광고 카드입니다.</p>
      </article>
      <article class="media-card" data-chungmaru-media-harmful="true">
        <img alt="19금 성인 영상 노출 썸네일" src="/media.svg?tone=adult&label=19%EA%B8%88%20%EC%84%B1%EC%9D%B8%20%EC%8D%B8%EB%84%A4%EC%9D%BC" />
        <h2>19금 성인 영상 썸네일</h2>
        <p>성인, 노출, 무삭제 영상 문맥을 포함한 카드입니다.</p>
      </article>
      <article class="media-card" data-chungmaru-media-safe="true">
        <img alt="학교 축제 안내 포스터" src="/media.svg?tone=clean&label=%ED%95%99%EA%B5%90%20%EC%B6%95%EC%A0%9C" />
        <h2>학교 축제 안내</h2>
        <p>오탐 방지를 위한 정상 이미지 카드입니다.</p>
      </article>
    """
    clean_cards = """
      <article class="media-card" data-chungmaru-media-safe="true">
        <img alt="도서관 열람실 안내 포스터" src="/media.svg?tone=clean&label=%EB%8F%84%EC%84%9C%EA%B4%80%20%EC%95%88%EB%82%B4" />
        <h2>도서관 열람실 안내</h2>
        <p>학습 공간 운영 시간 안내 이미지입니다.</p>
      </article>
      <article class="media-card" data-chungmaru-media-safe="true">
        <img alt="산책로 풍경 사진" src="/media.svg?tone=clean&label=%EC%82%B0%EC%B1%85%EB%A1%9C%20%ED%92%8D%EA%B2%BD" />
        <h2>산책로 풍경 사진</h2>
        <p>일반 풍경 썸네일입니다.</p>
      </article>
    """
    late_load_cards = """
      <article id="late-harmful-card" class="media-card" data-chungmaru-media-harmful="true">
        <h2>카지노 주소 모음</h2>
        <p>카지노, 토토, 바카라, 첫충, 페이백 배너가 지연 로드되는 fixture입니다.</p>
      </article>
      <article class="media-card" data-chungmaru-media-safe="true">
        <img alt="학교 공지 안내 이미지" src="/media.svg?tone=clean&label=%ED%95%99%EA%B5%90%20%EA%B3%B5%EC%A7%80" />
        <h2>학교 공지</h2>
        <p>지연 로드 케이스의 정상 카드입니다.</p>
      </article>
      <script>
        window.__chungmaruLateMediaInsertedAt = 0;
        window.__chungmaruLateMediaHiddenAt = 0;
        const markLateMediaHidden = () => {
          if (window.__chungmaruLateMediaHiddenAt) return;
          const card = document.getElementById("late-harmful-card");
          if (!card) return;
          const hidden = card.matches('[data-chungmaru-media-hidden="true"]') ||
            Boolean(card.closest('[data-chungmaru-media-hidden="true"]')) ||
            Boolean(card.querySelector('[data-chungmaru-media-hidden="true"]'));
          if (hidden) {
            window.__chungmaruLateMediaHiddenAt = performance.now();
          }
        };
        const lateMediaObserver = new MutationObserver(markLateMediaHidden);
        lateMediaObserver.observe(document.documentElement, {
          attributes: true,
          childList: true,
          subtree: true,
          attributeFilter: ["data-chungmaru-media-hidden"]
        });
        window.setTimeout(() => {
          const card = document.getElementById("late-harmful-card");
          if (!card) return;
          const link = document.createElement("a");
          link.href = "https://jusoguide1.com/bbs/bannerhit.php?bn_id=777";
          const image = document.createElement("img");
          image.alt = "카지노 토토 가입코드 첫충 페이백 지연 로드 배너";
          image.src = "/media.svg?tone=gambling&label=%EC%A7%80%EC%97%B0%20%EC%B9%B4%EC%A7%80%EB%85%B8%20%EB%B0%B0%EB%84%88";
          link.appendChild(image);
          window.__chungmaruLateMediaInsertedAt = performance.now();
          card.insertBefore(link, card.firstChild);
          markLateMediaHidden();
        }, 240);
      </script>
    """
    cards = clean_cards if scenario == "clean" else harmful_cards
    if scenario == "address-guide-video":
      cards = f"""<section class="jbanner-large-section">
      {address_guide_cards}
    </section>"""
    elif scenario == "late-load":
      cards = late_load_cards
    return f"""<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <title>Chungmaru Media Safety Fixture - {html.escape(scenario)}</title>
  <style>
    body {{
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f6f7f8;
      color: #111827;
    }}
    main {{
      width: min(1120px, calc(100vw - 48px));
      margin: 28px auto;
    }}
    .media-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 16px;
    }}
    .media-card {{
      border: 1px solid #d9dde3;
      background: #ffffff;
      padding: 12px;
      border-radius: 8px;
    }}
    .media-card img {{
      display: block;
      width: 100%;
      aspect-ratio: 16 / 5.5;
      object-fit: cover;
      border-radius: 6px;
    }}
    .media-card h2 {{
      margin: 10px 0 4px;
      font-size: 18px;
    }}
    .media-card p {{
      margin: 0;
      color: #4b5563;
      font-size: 14px;
    }}
    .jbanner-large-section {{
      display: grid;
      grid-template-columns: repeat(2, minmax(280px, 1fr));
      gap: 8px;
      background: #333;
      padding: 8px;
    }}
    .jbanner-large-item {{
      min-height: 92px;
      background: #111;
    }}
    .jbanner-media {{
      display: block;
      width: 100%;
      aspect-ratio: 16 / 2.4;
      min-height: 92px;
      object-fit: cover;
      background: linear-gradient(90deg, #111827, #7f1d1d 48%, #facc15);
    }}
  </style>
</head>
<body data-scenario="{html.escape(scenario)}">
  <main>
    <h1>Chungmaru Media Safety Fixture</h1>
    {cards if scenario == "address-guide-video" else f'<section class="media-grid">{cards}</section>'}
  </main>
</body>
</html>"""


class MediaFixtureHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed.query)
        if parsed.path == "/media.svg":
            label = params.get("label", ["media"])[0]
            tone = params.get("tone", ["clean"])[0]
            body = svg_response(label, tone).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "image/svg+xml; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        scenario = params.get("scenario", ["harmful"])[0]
        if scenario not in {"clean", "harmful", "address-guide-video", "late-load"}:
          scenario = "harmful"
        body = build_media_page(scenario).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: Any) -> None:
        return


def start_fixture_server(port: int) -> ThreadedTCPServer:
    server = ThreadedTCPServer(("127.0.0.1", port), MediaFixtureHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def launch_media_smoke_chrome(args: argparse.Namespace) -> subprocess.Popen[bytes]:
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
        "--window-size=1440,900",
        "--window-position=-4000,0",
        "--force-device-scale-factor=1",
        "--disable-background-networking",
        "--disable-component-extensions-with-background-pages",
        "--disable-features=DisableLoadExtensionCommandLineSwitch",
        "--disable-notifications",
        "--enable-unsafe-extension-debugging",
        "--enable-logging=stderr",
        f"--load-extension={extension_dir}",
        "about:blank",
    ]
    args.chrome_log.parent.mkdir(parents=True, exist_ok=True)
    if args.chrome_log.exists():
        args.chrome_log.unlink()
    log_handle = args.chrome_log.open("ab")
    try:
        return subprocess.Popen(command, stdout=log_handle, stderr=log_handle)
    finally:
        log_handle.close()


def connect_service_worker(debugging_port: int, timeout_s: float = 10) -> CdpWebSocket:
    _extension_id, target = wait_for_service_worker(debugging_port, timeout_s=timeout_s)
    return CdpWebSocket(str(target["webSocketDebuggerUrl"]))


def build_extension_settings(
    media_safety_enabled: bool,
    media_intervention_mode: str = "auto",
    startup_gate_enabled: bool = False,
) -> dict[str, Any]:
    return {
        "enabled": True,
        "sensitivity": 60,
        "categories": {
            "abuse": True,
            "hate": True,
            "insult": True,
            "spam": True,
        },
        "interventionMode": "mask",
        "textMaskingEnabled": False,
        "siteProtectionEnabled": False,
        "siteNavigationWarningEnabled": False,
        "searchResultProtectionEnabled": False,
        "mediaSafetyEnabled": media_safety_enabled,
        "mediaSafetyInterventionMode": media_intervention_mode,
        "mediaSafetyStartupGateEnabled": startup_gate_enabled,
        "showWellbeingWidget": False,
        "backendEnabled": False,
        "backendApiBaseUrl": "http://127.0.0.1:8000",
        "requestTimeoutMs": 10000,
    }


def set_extension_state(
    worker: CdpWebSocket,
    *,
    media_safety_enabled: bool,
    developer_log_enabled: bool,
    media_intervention_mode: str = "auto",
    startup_gate_enabled: bool = False,
) -> dict[str, Any]:
    settings = build_extension_settings(media_safety_enabled, media_intervention_mode, startup_gate_enabled)
    expression = (
        "(async () => {"
        "  await chrome.storage.local.remove(['runtimeEventLog', 'lastStats', 'lastPayload', 'lastDecision']);"
        f"  await chrome.storage.local.set({json.dumps({DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY: developer_log_enabled})});"
        f"  await chrome.storage.sync.set({json.dumps({'settings': settings}, ensure_ascii=False)});"
        "  return {"
        "    local: await chrome.storage.local.get(['runtimeEventLog', 'developerRuntimeLogEnabled']),"
        "    sync: await chrome.storage.sync.get('settings')"
        "  };"
        "})()"
    )
    value = worker.evaluate(expression, timeout_s=10)
    return value if isinstance(value, dict) else {}


def get_runtime_logs(worker: CdpWebSocket) -> list[dict[str, Any]]:
    value = worker.evaluate(
        "(async () => (await chrome.storage.local.get('runtimeEventLog')).runtimeEventLog || [])()",
        timeout_s=5,
    )
    return value if isinstance(value, list) else []


def inspect_media_dom(page: CdpWebSocket) -> dict[str, Any]:
    value = page.evaluate(
        """(() => {
          const hidden = Array.from(document.querySelectorAll('[data-chungmaru-media-hidden="true"]'));
          const summaries = Array.from(document.querySelectorAll('[data-chungmaru-media-summary="true"]'));
          const harmful = Array.from(document.querySelectorAll('[data-chungmaru-media-harmful="true"]'));
          const safe = Array.from(document.querySelectorAll('[data-chungmaru-media-safe="true"]'));
          const lateInsertedAt = Number(window.__chungmaruLateMediaInsertedAt || 0);
          const lateHiddenAt = Number(window.__chungmaruLateMediaHiddenAt || 0);
          const markerHidden = (marker) => Boolean(
            marker.closest('[data-chungmaru-media-hidden="true"]') ||
            marker.querySelector('[data-chungmaru-media-hidden="true"]')
          );
          return {
            bodyScenario: document.body ? (document.body.getAttribute('data-scenario') || '') : '',
            hiddenCount: hidden.length,
            compactSummaryCount: summaries.length,
            harmfulTotal: harmful.length,
            harmfulHiddenCount: harmful.filter(markerHidden).length,
            safeTotal: safe.length,
            safeHiddenCount: safe.filter(markerHidden).length,
            hiddenReasons: hidden.map((node) => ({
              safety: node.getAttribute('data-shieldtext-media-safety') || '',
              reason: node.getAttribute('data-chungmaru-media-reason') || ''
            })),
            lateInsertedAt,
            lateHiddenAt,
            lateDecisionMs: lateInsertedAt > 0 && lateHiddenAt >= lateInsertedAt
              ? Math.round(lateHiddenAt - lateInsertedAt)
              : 0
          };
        })()""",
        timeout_s=5,
    )
    return value if isinstance(value, dict) else {}


def inspect_live_dom(page: CdpWebSocket) -> dict[str, Any]:
    value = page.evaluate(
        """(() => {
          const isVisible = (node) => {
            if (!node || !(node instanceof Element)) return false;
            const style = window.getComputedStyle(node);
            if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity || '1') === 0) return false;
            const rect = node.getBoundingClientRect();
            return rect.width >= 24 && rect.height >= 24 && rect.bottom >= 0 && rect.right >= 0 && rect.top <= window.innerHeight && rect.left <= window.innerWidth;
          };
          const compactUrl = (value) => {
            try {
              const parsed = new URL(String(value || ''), location.href);
              return `${parsed.origin}${parsed.pathname}`.slice(0, 140);
            } catch (error) {
              return String(value || '').slice(0, 100);
            }
          };
          const hidden = Array.from(document.querySelectorAll('[data-chungmaru-media-hidden="true"]'));
          const summaries = Array.from(document.querySelectorAll('[data-chungmaru-media-summary="true"]'));
          const media = Array.from(document.querySelectorAll('img, video, [role="img"], picture, source'));
          const visibleMedia = media.filter(isVisible);
          const candidateSizedVisibleMedia = visibleMedia.filter((node) => {
            const rect = node.getBoundingClientRect();
            return !(rect.width < 56 && rect.height < 56) && rect.width * rect.height >= 3600;
          });
          const backgrounds = Array.from(document.querySelectorAll('a, article, div, section')).filter((node) => {
            const image = window.getComputedStyle(node).backgroundImage || '';
            return image.includes('url(');
          });
          return {
            bodyScenario: document.body ? (document.body.getAttribute('data-scenario') || '') : '',
            locationHref: window.location.href,
            readyState: document.readyState,
            hiddenCount: hidden.length,
            compactSummaryCount: summaries.length,
            harmfulTotal: 0,
            harmfulHiddenCount: 0,
            safeTotal: 0,
            safeHiddenCount: 0,
            domElementCount: document.querySelectorAll('*').length,
            bodyTextLength: (document.body && document.body.innerText ? document.body.innerText.length : 0),
            mediaElementCount: media.length,
            visibleMediaElementCount: visibleMedia.length,
            candidateSizedVisibleMediaElementCount: candidateSizedVisibleMedia.length,
            visibleMediaSamples: visibleMedia.slice(0, 12).map((node) => {
              const rect = node.getBoundingClientRect();
              const source = node instanceof HTMLVideoElement
                ? (node.currentSrc || node.src || node.querySelector('source[src]')?.getAttribute('src') || '')
                : (node.currentSrc || node.src || node.getAttribute('src') || '');
              return {
                tag: node.tagName.toLowerCase(),
                cls: String(node.className || '').slice(0, 80),
                src: compactUrl(source),
                href: compactUrl(node.closest('a[href]')?.href || ''),
                hiddenAncestor: Boolean(node.closest('[data-chungmaru-media-hidden="true"]')),
                rect: `${Math.round(rect.width)}x${Math.round(rect.height)}`
              };
            }),
            backgroundImageElementCount: backgrounds.length,
            visibleBackgroundImageElementCount: backgrounds.filter(isVisible).length
          };
        })()""",
        timeout_s=5,
    )
    return value if isinstance(value, dict) else {}


def int_metric(value: Any) -> int:
    try:
      return max(0, int(value or 0))
    except (TypeError, ValueError):
      return 0


def max_log_metric(logs: list[dict[str, Any]], key: str) -> int:
    return max((int_metric(item.get(key)) for item in logs), default=0)


def latest_log_metric(logs: list[dict[str, Any]], key: str) -> int:
    for item in reversed(logs):
      if key in item:
        return int_metric(item.get(key))
    return 0


def sum_action_log_metric(logs: list[dict[str, Any]], key: str) -> int:
    action_logs = [item for item in logs if item.get("type") == "media-safety-action"]
    return sum(int_metric(item.get(key)) for item in action_logs)


def summarize_media_logs(logs: list[dict[str, Any]]) -> dict[str, int]:
    return {
      "loggedCandidateCount": max_log_metric(logs, "candidateCount"),
      "loggedVisibleTileCount": max_log_metric(logs, "visibleTileCount"),
      "loggedCheapFilterHitCount": max_log_metric(logs, "cheapFilterHitCount"),
      "loggedActionCount": sum_action_log_metric(logs, "actionCount"),
      "loggedRemovedCount": sum_action_log_metric(logs, "removedCount"),
      "loggedPlaceholderCount": sum_action_log_metric(logs, "placeholderCount"),
      "loggedMergedTargetCount": max_log_metric(logs, "mergedTargetCount"),
      "loggedCollapsedGroupCount": max_log_metric(logs, "collapsedGroupCount"),
      "loggedHiddenAreaPx": max_log_metric(logs, "hiddenAreaPx"),
      "loggedViewportCoveragePct10": max_log_metric(
          [{"viewportCoveragePct10": float(item.get("viewportCoveragePct") or 0) * 10} for item in logs],
          "viewportCoveragePct10",
      ),
      "loggedRemainingVisibleTileCount": max_log_metric(logs, "remainingVisibleTileCount"),
      "loggedLatestRemainingVisibleTileCount": latest_log_metric(logs, "remainingVisibleTileCount"),
      "loggedMissedVisibleTileCount": max_log_metric(logs, "missedVisibleTileCount"),
      "loggedFalseHiddenCount": max_log_metric(logs, "falseHiddenCount"),
      "loggedCollectMs": max_log_metric(logs, "collectMs"),
      "loggedCheapFilterMs": max_log_metric(logs, "cheapFilterMs"),
      "loggedApplyMs": max_log_metric(logs, "applyMs"),
      "loggedDomAddedToActionMs": max_log_metric(logs, "domAddedToActionMs"),
    }


def describe_action_mode(removed_count: int, placeholder_count: int, action_count: int) -> str:
    if removed_count > 0 and placeholder_count > 0:
      return "mixed"
    if removed_count > 0:
      return "remove"
    if placeholder_count > 0:
      return "placeholder"
    if action_count > 0:
      return "unknown"
    return "none"


def origin_and_path_prefix(url: str) -> tuple[str, str]:
    parsed = urllib.parse.urlparse(url)
    origin = f"{parsed.scheme}://{parsed.netloc}" if parsed.scheme and parsed.netloc else ""
    path = parsed.path or "/"
    if len(path) > 80:
      path = path[:80]
    return origin, path


def build_result_row(
    *,
    case: dict[str, Any],
    response: dict[str, Any],
    dom: dict[str, Any],
    logs: list[dict[str, Any]],
    url: str = "",
    pre_manual_dom: dict[str, Any] | None = None,
) -> dict[str, Any]:
    media_logs = [
        item for item in logs
        if str(item.get("type") or "").startswith("media-safety")
    ]
    summary = response.get("response") if isinstance(response, dict) else {}
    if not isinstance(summary, dict):
      summary = {}
    log_summary = summarize_media_logs(media_logs)
    media_enabled = bool(case["media_safety_enabled"])
    hidden_count = int_metric(dom.get("hiddenCount"))
    removed_count = max(int_metric(summary.get("removedCount")), log_summary["loggedRemovedCount"])
    placeholder_count = max(int_metric(summary.get("placeholderCount")), log_summary["loggedPlaceholderCount"])
    merged_target_count = max(int_metric(summary.get("mergedTargetCount")), log_summary["loggedMergedTargetCount"])
    collapsed_group_count = max(int_metric(summary.get("collapsedGroupCount")), log_summary["loggedCollapsedGroupCount"])
    hidden_area_px = max(int_metric(summary.get("hiddenAreaPx")), log_summary["loggedHiddenAreaPx"])
    viewport_coverage_pct = max(
        float(summary.get("viewportCoveragePct") or 0),
        float(log_summary["loggedViewportCoveragePct10"]) / 10,
    )
    remaining_visible_tile_count = max(
        int_metric(summary.get("remainingVisibleTileCount")),
        log_summary["loggedLatestRemainingVisibleTileCount"],
    )
    harmful_total = int_metric(dom.get("harmfulTotal"))
    harmful_hidden_count = int_metric(dom.get("harmfulHiddenCount"))
    safe_total = int_metric(dom.get("safeTotal"))
    safe_hidden_count = int_metric(dom.get("safeHiddenCount"))
    pre_manual_dom = pre_manual_dom if isinstance(pre_manual_dom, dict) else {}
    dom_candidate_count = harmful_total + safe_total if media_enabled else 0
    effective_action_count = max(
        int_metric(summary.get("actionCount")),
        log_summary["loggedActionCount"],
        hidden_count if media_enabled else 0,
    )
    effective_candidate_count = max(
        int_metric(summary.get("candidateCount")),
        log_summary["loggedCandidateCount"],
        dom_candidate_count,
        effective_action_count,
    )
    effective_visible_tile_count = max(
        int_metric(summary.get("visibleTileCount")),
        log_summary["loggedVisibleTileCount"],
        dom_candidate_count,
        effective_action_count,
    )
    origin, path_prefix = origin_and_path_prefix(url)
    final_origin, final_path_prefix = origin_and_path_prefix(str(dom.get("locationHref") or ""))
    return {
        "timestamp": now_iso(),
        "case_id": case["case_id"],
        "scenario": case["scenario"],
        "url_origin": origin,
        "url_path_prefix": path_prefix,
        "final_url_origin": final_origin,
        "final_url_path_prefix": final_path_prefix,
        "media_safety_enabled": bool(case["media_safety_enabled"]),
        "developer_log_enabled": bool(case["developer_log_enabled"]),
        "startup_gate_enabled": bool(case.get("media_safety_startup_gate_enabled")),
        "scan_ok": bool(summary.get("ok")),
        "scan_status": summary.get("status") or "",
        "candidate_count": effective_candidate_count,
        "visible_tile_count": effective_visible_tile_count,
        "cheap_filter_hit_count": max(int_metric(summary.get("cheapFilterHitCount")), log_summary["loggedCheapFilterHitCount"], effective_action_count),
        "action_count": effective_action_count,
        "action_mode": describe_action_mode(removed_count, placeholder_count, effective_action_count),
        "removed_count": removed_count,
        "placeholder_count": placeholder_count,
        "merged_target_count": merged_target_count,
        "collapsed_group_count": collapsed_group_count,
        "hidden_area_px": hidden_area_px,
        "viewport_coverage_pct": round(viewport_coverage_pct, 1),
        "remaining_visible_tile_count": remaining_visible_tile_count,
        "missed_visible_tile_count": max(int_metric(summary.get("missedVisibleTileCount")), log_summary["loggedMissedVisibleTileCount"]),
        "false_hidden_count": max(int_metric(summary.get("falseHiddenCount")), log_summary["loggedFalseHiddenCount"], safe_hidden_count),
        "collect_ms": max(int_metric(summary.get("collectMs")), log_summary["loggedCollectMs"]),
        "cheap_filter_ms": max(int_metric(summary.get("cheapFilterMs")), log_summary["loggedCheapFilterMs"]),
        "apply_ms": max(int_metric(summary.get("applyMs")), log_summary["loggedApplyMs"]),
        "dom_added_to_action_ms": max(int_metric(summary.get("domAddedToActionMs")), log_summary["loggedDomAddedToActionMs"]),
        "late_decision_ms": max(
            int_metric(dom.get("lateDecisionMs")),
            int_metric(pre_manual_dom.get("lateDecisionMs")),
        ),
        **log_summary,
        "runtime_log_count": len(logs),
        "media_runtime_log_count": len(media_logs),
        "pre_manual_hidden_count": int_metric(pre_manual_dom.get("hiddenCount")),
        "pre_manual_harmful_hidden_count": int_metric(pre_manual_dom.get("harmfulHiddenCount")),
        "pre_manual_safe_hidden_count": int_metric(pre_manual_dom.get("safeHiddenCount")),
        "hidden_count": hidden_count,
        "compact_summary_count": int_metric(dom.get("compactSummaryCount")),
        "harmful_total": harmful_total,
        "harmful_hidden_count": harmful_hidden_count,
        "safe_total": safe_total,
        "safe_hidden_count": safe_hidden_count,
        "dom_element_count": int_metric(dom.get("domElementCount")),
        "body_text_length": int_metric(dom.get("bodyTextLength")),
        "media_element_count": int_metric(dom.get("mediaElementCount")),
        "visible_media_element_count": int_metric(dom.get("visibleMediaElementCount")),
        "candidate_sized_visible_media_element_count": int_metric(dom.get("candidateSizedVisibleMediaElementCount")),
        "visible_media_samples": json.dumps(dom.get("visibleMediaSamples") or [], ensure_ascii=False),
        "background_image_element_count": int_metric(dom.get("backgroundImageElementCount")),
        "visible_background_image_element_count": int_metric(dom.get("visibleBackgroundImageElementCount")),
        "document_ready_state": str(dom.get("readyState") or ""),
        "body_scenario": str(dom.get("bodyScenario") or ""),
        "message_phase": response.get("phase") if isinstance(response, dict) else "",
    }


def send_media_scan_message(
    worker: CdpWebSocket,
    case_url: str,
    message: dict[str, Any],
    *,
    timeout_s: float,
) -> dict[str, Any]:
    last_response: dict[str, Any] = {}
    for _attempt in range(6):
      last_response = send_to_fixture_tab(
          worker,
          case_url,
          message,
          inject_on_failure=False,
          timeout_s=timeout_s,
      )
      if last_response.get("ok"):
        return last_response
      time.sleep(0.25)

    return send_to_fixture_tab(
        worker,
        case_url,
        message,
        inject_on_failure=True,
        timeout_s=timeout_s,
    )


def run_case(
    worker: CdpWebSocket,
    debugging_port: int,
    fixture_url: str,
    case: dict[str, Any],
) -> dict[str, Any]:
    set_extension_state(
        worker,
        media_safety_enabled=bool(case["media_safety_enabled"]),
        developer_log_enabled=bool(case["developer_log_enabled"]),
        media_intervention_mode=str(case.get("media_intervention_mode") or "auto"),
        startup_gate_enabled=bool(case.get("media_safety_startup_gate_enabled")),
    )
    time.sleep(0.25)
    case_url = f"{fixture_url}?scenario={case['scenario']}&case={case['case_id']}"
    target = create_tab(debugging_port, case_url)
    page = CdpWebSocket(str(target["webSocketDebuggerUrl"]))
    try:
      wait_for_page_ready(page, timeout_s=10)
      time.sleep(0.45)
      pre_manual_dom = inspect_media_dom(page)
      settings = build_extension_settings(
          bool(case["media_safety_enabled"]),
          str(case.get("media_intervention_mode") or "auto"),
          bool(case.get("media_safety_startup_gate_enabled")),
      )
      response = send_media_scan_message(
          worker,
          case_url,
          {
              "type": "RUN_MEDIA_SAFETY_SCAN",
              "reason": f"smoke-{case['case_id']}",
              "settings": settings,
          },
          timeout_s=10,
      )
      time.sleep(0.2)
      dom = inspect_media_dom(page)
      logs = get_runtime_logs(worker)
      return build_result_row(case=case, response=response, dom=dom, logs=logs, pre_manual_dom=pre_manual_dom)
    finally:
      page.close()


def normalize_live_url(value: str) -> str:
    raw = str(value or "").strip()
    if not raw:
      raise ValueError("live URL cannot be empty")
    if "://" not in raw:
      raw = f"https://{raw}"
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
      raise ValueError(f"unsupported live URL: {value}")
    return raw


def run_live_case(
    worker: CdpWebSocket,
    debugging_port: int,
    live_url: str,
    case: dict[str, Any],
    *,
    settle_seconds: float,
) -> dict[str, Any]:
    set_extension_state(
        worker,
        media_safety_enabled=bool(case["media_safety_enabled"]),
        developer_log_enabled=bool(case["developer_log_enabled"]),
        media_intervention_mode=str(case.get("media_intervention_mode") or "auto"),
        startup_gate_enabled=bool(case.get("media_safety_startup_gate_enabled")),
    )
    time.sleep(0.25)
    target = create_tab(debugging_port, live_url)
    page = CdpWebSocket(str(target["webSocketDebuggerUrl"]))
    try:
      wait_for_page_ready(page, timeout_s=20)
      time.sleep(settle_seconds)
      settings = build_extension_settings(
          bool(case["media_safety_enabled"]),
          str(case.get("media_intervention_mode") or "auto"),
          bool(case.get("media_safety_startup_gate_enabled")),
      )
      response = send_media_scan_message(
          worker,
          live_url,
          {
              "type": "RUN_MEDIA_SAFETY_SCAN",
              "reason": f"live-smoke-{case['case_id']}",
              "settings": settings,
          },
          timeout_s=15,
      )
      time.sleep(0.3)
      dom = inspect_live_dom(page)
      logs = get_runtime_logs(worker)
      return build_result_row(case=case, response=response, dom=dom, logs=logs, url=live_url)
    finally:
      page.close()


def write_outputs(output_dir: Path, rows: list[dict[str, Any]], prefix: str) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    jsonl_path = output_dir / f"{prefix}.jsonl"
    csv_path = output_dir / f"{prefix}.csv"
    with jsonl_path.open("w", encoding="utf-8") as handle:
      for row in rows:
        handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    fieldnames = list(rows[0].keys()) if rows else []
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
      writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
      writer.writeheader()
      writer.writerows(rows)


def assert_acceptance(rows: list[dict[str, Any]]) -> None:
    by_case = {row["case_id"]: row for row in rows}
    media_off = by_case["media_off_harmful"]
    log_off = by_case["log_off_harmful"]
    log_on = by_case["log_on_harmful"]
    clean = by_case["log_on_clean"]
    address_guide = by_case.get("log_on_address_guide_video")
    late_load = by_case.get("log_on_late_load")

    failures = []
    if media_off["hidden_count"] != 0 or media_off["action_count"] != 0:
      failures.append("media_off_harmful should not hide media")
    if log_off["harmful_hidden_count"] < 2:
      failures.append("log_off_harmful should hide both harmful fixtures")
    if log_off["runtime_log_count"] != 0:
      failures.append("log_off_harmful should not write runtime logs")
    if log_on["harmful_hidden_count"] < 2:
      failures.append("log_on_harmful should hide both harmful fixtures")
    if log_on["media_runtime_log_count"] < 1:
      failures.append("log_on_harmful should write aggregate media logs")
    if clean["safe_hidden_count"] != 0 or clean["false_hidden_count"] != 0:
      failures.append("log_on_clean should not hide clean fixtures")
    if address_guide and address_guide["harmful_hidden_count"] < 6:
      failures.append("log_on_address_guide_video should hide source-backed video banners")
    if address_guide and address_guide["remaining_visible_tile_count"] != 0:
      failures.append("log_on_address_guide_video should not leave visible video banner tiles")
    if late_load and late_load["pre_manual_harmful_hidden_count"] < 1:
      failures.append("log_on_late_load should auto-hide delayed media before manual smoke scan")
    if late_load and late_load["safe_hidden_count"] != 0:
      failures.append("log_on_late_load should not hide safe delayed fixture media")
    if late_load and late_load["late_decision_ms"] <= 0:
      failures.append("log_on_late_load should record delayed media decision latency")
    if failures:
      raise RuntimeError("; ".join(failures))


def read_chrome_log_hint(path: Path) -> str:
    try:
      text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
      return ""
    if "--load-extension is not allowed" in text:
      return "Chrome blocked --load-extension; run with Chrome for Testing/Chromium via --chrome-path."
    if "--disable-extensions-except is not allowed" in text:
      return "Chrome ignored extension isolation flags; use Chrome for Testing/Chromium for extension smoke."
    return ""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Chungmaru Chrome media-safety smoke against fixture pages.")
    parser.add_argument("--extension-dir", type=Path, default=DEFAULT_EXTENSION_DIR)
    parser.add_argument("--profile-dir", type=Path, default=DEFAULT_PROFILE_DIR)
    parser.add_argument("--chrome-path", type=Path, default=None)
    parser.add_argument("--chrome-log", type=Path, default=DEFAULT_CHROME_LOG)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--debugging-port", type=int, default=9337)
    parser.add_argument("--fixture-port", type=int, default=0)
    parser.add_argument("--live-url", action="append", default=[])
    parser.add_argument("--live-settle-seconds", type=float, default=2.0)
    parser.add_argument("--media-intervention-mode", choices=["auto", "placeholder", "remove"], default="auto")
    parser.add_argument("--clean-profile", action="store_true", default=True)
    parser.add_argument("--keep-chrome", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.chrome_path = detect_chrome_path(args.chrome_path)
    fixture_port = args.fixture_port or find_free_port()
    fixture_server = start_fixture_server(fixture_port)
    chrome = launch_media_smoke_chrome(args)
    rows: list[dict[str, Any]] = []
    try:
      wait_for_targets(args.debugging_port, timeout_s=20)
      extension_id, _target = wait_for_service_worker(args.debugging_port, timeout_s=20)
      fixture_url = f"http://127.0.0.1:{fixture_port}/"
      cases = [
          {
              "case_id": "media_off_harmful",
              "scenario": "harmful",
              "media_safety_enabled": False,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
          },
          {
              "case_id": "log_off_harmful",
              "scenario": "harmful",
              "media_safety_enabled": True,
              "developer_log_enabled": False,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
          },
          {
              "case_id": "log_on_harmful",
              "scenario": "harmful",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
          },
          {
              "case_id": "log_on_clean",
              "scenario": "clean",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
          },
          {
              "case_id": "log_on_address_guide_video",
              "scenario": "address-guide-video",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
          },
          {
              "case_id": "log_on_late_load",
              "scenario": "late-load",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
          },
      ]
      if args.live_url:
        live_urls = [normalize_live_url(item) for item in args.live_url]
        for index, live_url in enumerate(live_urls, start=1):
          parsed = urllib.parse.urlparse(live_url)
          host_slug = parsed.netloc.replace(".", "_")
          for startup_gate_enabled in [False, True]:
            case = {
                "case_id": (
                    f"live_{index}_{host_slug}_"
                    f"{'startup_gate' if startup_gate_enabled else 'decision_first'}"
                ),
                "scenario": "live",
                "media_safety_enabled": True,
                "developer_log_enabled": True,
                "media_intervention_mode": args.media_intervention_mode,
                "media_safety_startup_gate_enabled": startup_gate_enabled,
            }
            worker = connect_service_worker(args.debugging_port)
            try:
              rows.append(run_live_case(
                  worker,
                  args.debugging_port,
                  live_url,
                  case,
                  settle_seconds=args.live_settle_seconds,
              ))
            finally:
              worker.close()
        write_outputs(args.output_dir, rows, LIVE_OUTPUT_PREFIX)
      else:
        for case in cases:
          worker = connect_service_worker(args.debugging_port)
          try:
            rows.append(run_case(worker, args.debugging_port, fixture_url, case))
          finally:
            worker.close()
        write_outputs(args.output_dir, rows, FIXTURE_OUTPUT_PREFIX)
        assert_acceptance(rows)
      print(json.dumps({"ok": True, "rows": rows, "output_dir": str(args.output_dir)}, ensure_ascii=False, indent=2))
      return 0
    except Exception as error:  # noqa: BLE001 - smoke output should preserve failure reason
      if rows:
        prefix = LIVE_OUTPUT_PREFIX if args.live_url else FIXTURE_OUTPUT_PREFIX
        write_outputs(args.output_dir, rows, prefix)
      payload = {"ok": False, "error": str(error), "rows": rows}
      hint = read_chrome_log_hint(args.chrome_log)
      if hint:
        payload["hint"] = hint
      print(json.dumps(payload, ensure_ascii=False, indent=2), file=sys.stderr)
      return 1
    finally:
      fixture_server.shutdown()
      fixture_server.server_close()
      if not args.keep_chrome:
        chrome.terminate()
        try:
          chrome.wait(timeout=5)
        except Exception:
          chrome.kill()


if __name__ == "__main__":
    raise SystemExit(main())
