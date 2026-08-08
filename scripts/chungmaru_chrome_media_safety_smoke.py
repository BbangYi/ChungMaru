#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import csv
import html
import http.server
import json
import math
import os
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
DEFAULT_VISUAL_EVIDENCE_DIR = DEFAULT_OUTPUT_DIR / "visual"
FIXTURE_OUTPUT_PREFIX = "media-safety-smoke"
COMPOSITE_OUTPUT_PREFIX = "media-safety-composite"
LIVE_OUTPUT_PREFIX = "media-safety-live-smoke"
LIVE_SUMMARY_PREFIX = "media-safety-live-summary"
VISUAL_EVIDENCE_PREFIX = "media-safety-visual-evidence"
DEFAULT_SITE_SEED_FILE = Path("backend/data/site_intel_seed_massive.json")
DEFAULT_CHROME_FOR_TESTING_ROOT = Path("/private/tmp/chungmaru-chrome-for-testing/chrome")
CHROME_FOR_TESTING_EXECUTABLE = (
    "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
)
DEVELOPER_RUNTIME_LOG_ENABLED_STORAGE_KEY = "developerRuntimeLogEnabled"
ONE_SECOND_PROTECTION_BUDGET_MS = 1000


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

    raise RuntimeError(
        "Chrome for Testing was not found. Install it through the Chungmaru runner "
        "or pass an explicit Chromium-compatible --chrome-path. Standard Google Chrome "
        "does not provide a reliable --load-extension smoke environment."
    )


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
    background_cards = """
      <a class="css-banner css-risk-banner css-risk-banner-one" href="https://slot-casino.example/banner/777" data-chungmaru-media-harmful="true">
        <span>카지노 슬롯 가입코드 배너</span>
      </a>
      <a class="css-banner css-risk-banner css-risk-banner-two" href="https://toto-sportsbook.example/promo" data-chungmaru-media-harmful="true">
        <span>토토 스포츠북 페이백 배너</span>
      </a>
      <a class="css-banner css-safe-banner" href="https://school.example/festival" data-chungmaru-media-safe="true">
        <span>학교 축제 안내 배너</span>
      </a>
    """
    visual_explicit_cards = """
      <article class="media-card" data-chungmaru-media-harmful="true">
        <img alt="인물 사진 미리보기" src="/media.svg?tone=clean&amp;label=PHOTO%2001&amp;visual=explicit" />
        <h2>인물 사진 미리보기</h2>
        <p>시각 분류 경로를 확인하는 중립 문맥 fixture입니다.</p>
      </article>
    """
    visual_safe_cards = """
      <article class="media-card" data-chungmaru-media-safe="true">
        <img alt="풍경 사진 미리보기" src="/media.svg?tone=clean&amp;label=LANDSCAPE%2001&amp;visual=safe" />
        <h2>풍경 사진 미리보기</h2>
        <p>시각 분류기의 정상 이미지 유지 fixture입니다.</p>
      </article>
    """
    medical_adult_cards = """
      <article class="media-card" data-chungmaru-media-safe="true">
        <img alt="성인 ADHD 자가진단 테스트 안내" src="/media.svg?tone=clean&amp;label=ADULT%20ADHD%20TEST" />
        <h2>성인 ADHD 자가진단 무료 검사 테스트</h2>
        <p>성인 ADHD, 집중력, 심리 상담을 안내하는 의료 정보 카드입니다.</p>
      </article>
      <article class="media-card" data-chungmaru-media-safe="true">
        <img alt="성인 심리학 입문 도서 표지" src="/media.svg?tone=clean&amp;label=ADULT%20PSYCHOLOGY" />
        <h2>성인의 발달 심리학 도서</h2>
        <p>성인 대상 심리 검사와 건강 정보를 담은 정상 이미지입니다.</p>
      </article>
    """
    sidebar_frame_cards = """
      <main class="sidebar-layout">
        <section class="article-copy">
          <h2>일반 블로그 본문</h2>
          <p>사이드바와 iframe 광고 후보만 별도로 검증합니다.</p>
        </section>
        <aside aria-label="광고 영역" class="sidebar-slot">
          <iframe id="sidebar-ad-frame" title="광고" src="/frame?scenario=visual-explicit"></iframe>
        </aside>
      </main>
    """
    composite_cards = """
      <section class="google-fixture" role="search">
        <form action="/search" method="get">
          <input id="composite-search-input" name="q" value="카지노 가입코드 광고 차단 테스트" autocomplete="off" />
          <button type="submit">Search</button>
        </form>
        <div id="search" data-chungmaru-google-fixture="true">
          <div class="g" data-chungmaru-search-risk="harmful">
            <a href="https://jusoguide1.com/bbs/bannerhit.php?bn_id=91">카지노 토토 가입코드 첫충 페이백 주소</a>
            <span>바카라, 슬롯, 스포츠토토, 환전 광고 결과입니다.</span>
          </div>
          <div class="g" data-chungmaru-search-risk="safe">
            <a href="https://school.example/notice">학교 축제 안내</a>
            <span>정상 검색 결과입니다.</span>
          </div>
        </div>
      </section>
      <section class="article-copy">
        <h2>욕설과 유해 사이트가 섞인 본문</h2>
        <p>씨발 카지노 광고와 토토 가입코드 링크가 동적으로 추가되는 복합 fixture입니다.</p>
        <a href="https://adult-webtoon-plus.kr/landing" data-chungmaru-site-risk="adult">성인 웹툰 바로가기</a>
      </section>
      <section class="media-grid">
        <article class="media-card" data-chungmaru-media-harmful="true">
          <img alt="카지노 토토 가입코드 첫충 페이백 배너" src="/media.svg?tone=gambling&amp;label=COMPOSITE%20CASINO" />
          <h2>카지노 가입코드 배너</h2>
          <p>cheap filter가 처리할 명시적 도박 배너입니다.</p>
        </article>
        <article class="media-card">
          <img alt="인물 사진 미리보기" src="/media.svg?tone=clean&amp;label=PHOTO%2002&amp;visual=explicit" />
          <h2>인물 사진 미리보기</h2>
          <p>분류기 경로를 켜는 중립 문맥 이미지입니다.</p>
        </article>
        <article id="composite-late-card" class="media-card" data-chungmaru-media-harmful="true">
          <h2>지연 로드 광고 영역</h2>
          <p>스크롤 이후 이미지가 추가됩니다.</p>
        </article>
        <article class="media-card" data-chungmaru-media-safe="true">
          <img alt="도서관 안내 포스터" src="/media.svg?tone=clean&amp;label=LIBRARY" />
          <h2>도서관 안내</h2>
          <p>오탐 확인용 정상 카드입니다.</p>
        </article>
      </section>
      <aside class="sidebar-slot">
        <iframe id="composite-sidebar-ad-frame" title="광고" src="/frame?scenario=visual-explicit"></iframe>
      </aside>
      <script>
        window.__chungmaruLateMediaInsertedAt = 0;
        window.__chungmaruLateMediaHiddenAt = 0;
        const compositeMarkLateHidden = () => {
          if (window.__chungmaruLateMediaHiddenAt) return;
          const card = document.getElementById("composite-late-card");
          if (!card) return;
          const hidden = card.matches('[data-chungmaru-media-hidden="true"]') ||
            Boolean(card.closest('[data-chungmaru-media-hidden="true"]')) ||
            Boolean(card.querySelector('[data-chungmaru-media-hidden="true"]'));
          if (hidden) window.__chungmaruLateMediaHiddenAt = performance.now();
        };
        new MutationObserver(compositeMarkLateHidden).observe(document.documentElement, {
          attributes: true,
          childList: true,
          subtree: true,
          attributeFilter: ["data-chungmaru-media-hidden"]
        });
        window.setTimeout(() => {
          const card = document.getElementById("composite-late-card");
          if (!card) return;
          const image = document.createElement("img");
          image.alt = "지연 카지노 토토 가입코드 페이백 배너";
          image.src = "/media.svg?tone=gambling&label=LATE%20CASINO";
          window.__chungmaruLateMediaInsertedAt = performance.now();
          card.insertBefore(image, card.firstChild);
          compositeMarkLateHidden();
        }, 260);
      </script>
    """
    cards = clean_cards if scenario == "clean" else harmful_cards
    if scenario == "address-guide-video":
      cards = f"""<section class="jbanner-large-section">
      {address_guide_cards}
    </section>"""
    elif scenario == "late-load":
      cards = late_load_cards
    elif scenario == "background-banner":
      cards = background_cards
    elif scenario == "visual-explicit":
      cards = visual_explicit_cards
    elif scenario == "visual-safe":
      cards = visual_safe_cards
    elif scenario == "medical-adult":
      cards = medical_adult_cards
    elif scenario == "sidebar-frame":
      cards = sidebar_frame_cards
    elif scenario == "composite":
      cards = composite_cards
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
    .sidebar-layout {{
      display: grid;
      grid-template-columns: minmax(0, 1fr) 340px;
      gap: 24px;
    }}
    .article-copy {{
      min-height: 420px;
      padding: 24px;
      border: 1px solid #d9dde3;
      background: #ffffff;
      border-radius: 8px;
    }}
    .sidebar-slot {{
      display: block;
      min-height: 300px;
      border: 1px solid #d9dde3;
      background: #ffffff;
      border-radius: 8px;
      overflow: hidden;
    }}
    .sidebar-slot iframe {{
      display: block;
      width: 100%;
      height: 300px;
      border: 0;
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
    .css-banner {{
      display: flex;
      align-items: center;
      min-height: 118px;
      padding: 18px 22px;
      border-radius: 8px;
      color: #fff;
      font-size: 24px;
      font-weight: 800;
      text-decoration: none;
      background-size: cover;
      background-position: center;
      box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.18);
    }}
    .css-risk-banner-one {{
      background-image: url("/media.svg?tone=gambling&label=CSS%20CASINO%20BANNER");
    }}
    .css-risk-banner-two {{
      background-image: url("/media.svg?tone=gambling&label=CSS%20TOTO%20BANNER");
    }}
    .css-safe-banner {{
      background-image: url("/media.svg?tone=clean&label=SAFE%20SCHOOL%20BANNER");
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
        if scenario not in {
            "clean",
            "harmful",
            "address-guide-video",
            "late-load",
            "background-banner",
            "visual-explicit",
            "visual-safe",
            "medical-adult",
            "sidebar-frame",
            "composite",
        }:
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
        # Composite latency is measured against the active user-visible tab.
        # Without these flags headless Chrome can defer the tab's timer probe,
        # turning background scheduling into a false extension lag signal.
        "--disable-background-timer-throttling",
        "--disable-renderer-backgrounding",
        "--disable-backgrounding-occluded-windows",
        "--disable-component-extensions-with-background-pages",
        "--disable-features=DisableLoadExtensionCommandLineSwitch",
        "--disable-notifications",
        "--enable-unsafe-extension-debugging",
        "--enable-logging=stderr",
        "--host-resolver-rules=MAP www.google.com 127.0.0.1, MAP images.google.com 127.0.0.1",
        f"--load-extension={extension_dir}",
        "about:blank",
    ]
    if args.headless:
        command.insert(-1, "--headless=new")
        command.insert(-1, "--enable-webgl")
        command.insert(-1, "--use-angle=swiftshader")
        command.insert(-1, "--ignore-gpu-blocklist")
    else:
        command.insert(-1, "--start-minimized")
    args.chrome_log.parent.mkdir(parents=True, exist_ok=True)
    if args.chrome_log.exists():
        args.chrome_log.unlink()
    log_handle = args.chrome_log.open("ab")
    try:
        return subprocess.Popen(command, stdout=log_handle, stderr=log_handle)
    finally:
        log_handle.close()


def safe_artifact_slug(value: str, *, max_length: int = 96) -> str:
    slug = "".join(
        char.lower() if char.isalnum() else "-"
        for char in str(value or "")
    )
    slug = "-".join(part for part in slug.split("-") if part)
    return (slug or "artifact")[:max_length]


def prepare_visual_evidence_dir(path: Path, output_dir: Path) -> None:
    resolved_path = path.resolve()
    resolved_output_dir = output_dir.resolve()
    try:
      resolved_path.relative_to(resolved_output_dir)
    except ValueError as error:
      raise RuntimeError(
          f"visual evidence dir must be inside output dir: {resolved_path}"
      ) from error
    if path.exists():
      shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def should_capture_visual_evidence(case: dict[str, Any], capture_repeat: int) -> bool:
    if case.get("scenario") != "live":
      return False
    repeat_index = int_metric(case.get("repeat_index"))
    return capture_repeat <= 0 or repeat_index == capture_repeat


def capture_page_screenshot(page: CdpWebSocket, output_dir: Path, case: dict[str, Any]) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    case_id = safe_artifact_slug(str(case.get("case_id") or "live"))
    path = output_dir / f"{case_id}.png"
    page.call("Page.enable", timeout_s=5)
    result = page.call(
        "Page.captureScreenshot",
        {
            "format": "png",
            "fromSurface": True,
            "captureBeyondViewport": False,
        },
        timeout_s=10,
    )
    data = str(result.get("data") or "")
    if not data:
      raise RuntimeError("Page.captureScreenshot returned empty data")
    path.write_bytes(base64.b64decode(data))
    return path


def connect_service_worker(debugging_port: int, timeout_s: float = 10) -> CdpWebSocket:
    _extension_id, target = wait_for_service_worker(debugging_port, timeout_s=timeout_s)
    return CdpWebSocket(str(target["webSocketDebuggerUrl"]))


def build_extension_settings(
    media_safety_enabled: bool,
    media_intervention_mode: str = "auto",
    startup_gate_enabled: bool = False,
    *,
    global_enabled: bool = True,
    text_masking_enabled: bool = False,
    site_protection_enabled: bool = False,
    search_result_protection_enabled: bool = False,
    backend_enabled: bool = False,
) -> dict[str, Any]:
    return {
        "enabled": global_enabled,
        "sensitivity": 60,
        "categories": {
            "abuse": True,
            "hate": True,
            "insult": True,
            "spam": True,
        },
        "interventionMode": "mask",
        "textMaskingEnabled": text_masking_enabled,
        "siteProtectionEnabled": site_protection_enabled,
        "siteNavigationWarningEnabled": site_protection_enabled,
        "searchResultProtectionEnabled": search_result_protection_enabled,
        "mediaSafetyEnabled": media_safety_enabled,
        "mediaSafetyInterventionMode": media_intervention_mode,
        "mediaSafetyStartupGateEnabled": startup_gate_enabled,
        "showWellbeingWidget": False,
        "backendEnabled": backend_enabled,
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
    global_enabled: bool = True,
    text_masking_enabled: bool = False,
    site_protection_enabled: bool = False,
    search_result_protection_enabled: bool = False,
    backend_enabled: bool = False,
) -> dict[str, Any]:
    settings = build_extension_settings(
        media_safety_enabled,
        media_intervention_mode,
        startup_gate_enabled,
        global_enabled=global_enabled,
        text_masking_enabled=text_masking_enabled,
        site_protection_enabled=site_protection_enabled,
        search_result_protection_enabled=search_result_protection_enabled,
        backend_enabled=backend_enabled,
    )
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


def set_nsfw_classifier_test_override(worker: CdpWebSocket, mode: str) -> dict[str, Any]:
    normalized = mode if mode in {"normal", "off", "fixture", "cpu"} else "normal"
    expression = (
        "(async () => setNsfwClassifierTestOverride("
        f"{json.dumps({'type': 'SET_NSFW_CLASSIFIER_TEST_OVERRIDE', 'mode': normalized})},"
        "{ id: chrome.runtime.id }"
        "))()"
    )
    value = worker.evaluate(expression, timeout_s=10)
    if not isinstance(value, dict) or not value.get("ok"):
      raise RuntimeError(f"failed to set NSFW classifier test override: {value!r}")
    return value


def send_to_matched_tab(
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
        "const files = ['content-runtime-status.js', 'content-editable-overlay.js', 'content-self-test.js', 'content-wellbeing-widget.js', 'content-media-classifier.js', 'content-script.js'];"
        "async function send(tab, phase) {"
        "  try {"
        "    const response = await chrome.tabs.sendMessage(tab.id, message);"
        "    return { ok: true, tabId: tab.id, phase, response };"
        "  } catch (error) {"
        "    return { ok: false, tabId: tab.id, phase, reason: String(error && error.message ? error.message : error) };"
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
        "const matches = tabs.filter((item) => String(item.url || '').startsWith(pageUrl));"
        "const attempts = [];"
        "for (const tab of matches) {"
        "  const response = await send(tab, 'manifest');"
        "  if (response.ok) return { ...response, tabs: summaries, attempts };"
        "  attempts.push(response);"
        "}"
        "if (injectOnFailure) {"
        "  for (const tab of matches) {"
        "    try {"
        "      await inject(tab);"
        "    } catch (error) {"
        "      attempts.push({ tabId: tab.id, phase: 'inject', reason: String(error && error.message ? error.message : error) });"
        "      continue;"
        "    }"
        "    const response = await send(tab, 'programmatic-inject');"
        "    if (response.ok) return { ...response, tabs: summaries, attempts };"
        "    attempts.push(response);"
        "  }"
        "}"
        "return { ok: false, reason: 'NO_MATCHED_TAB_ACCEPTED_MESSAGE', tabs: summaries, attempts };"
        "})()"
    )
    value = worker.evaluate(expression, timeout_s=timeout_s)
    return value if isinstance(value, dict) else {"ok": False, "reason": f"unexpected trigger response: {value!r}"}


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
          const frames = Array.from(document.querySelectorAll('iframe'));
          const frameStats = frames.reduce((summary, frame) => {
            const frameDocument = frame.contentDocument;
            if (!frameDocument) return summary;
            const harmfulMarkers = Array.from(frameDocument.querySelectorAll('[data-chungmaru-media-harmful="true"]'));
            const protectedMarkers = harmfulMarkers.filter((marker) => Boolean(
              marker.closest('[data-chungmaru-frame-media-hidden="true"]') ||
              marker.querySelector('[data-chungmaru-frame-media-hidden="true"]')
            ));
            summary.total += harmfulMarkers.length;
            summary.hidden += protectedMarkers.length;
            return summary;
          }, { total: 0, hidden: 0 });
          return {
            bodyScenario: document.body ? (document.body.getAttribute('data-scenario') || '') : '',
            hiddenCount: hidden.length,
            compactSummaryCount: summaries.length,
            harmfulTotal: harmful.length,
            harmfulHiddenCount: harmful.filter(markerHidden).length,
            safeTotal: safe.length,
            safeHiddenCount: safe.filter(markerHidden).length,
            frameHarmfulTotal: frameStats.total,
            frameHarmfulHiddenCount: frameStats.hidden,
            hiddenReasons: hidden.map((node) => ({
              safety: node.getAttribute('data-shieldtext-media-safety') || '',
              reason: node.getAttribute('data-chungmaru-media-reason') || ''
            })),
            lateInsertedAt,
            lateHiddenAt,
            lateDecisionMs: lateInsertedAt > 0 && lateHiddenAt >= lateInsertedAt
              ? Math.round(lateHiddenAt - lateInsertedAt)
              : 0,
            layoutShiftScore: Number(window.__chungmaruLayoutShift?.score || 0),
            layoutShiftEntryCount: Number(window.__chungmaruLayoutShift?.entryCount || 0),
            layoutShiftMaxValue: Number(window.__chungmaruLayoutShift?.maxValue || 0),
            layoutShiftSupported: window.__chungmaruLayoutShift?.supported === true
          };
        })()""",
        timeout_s=5,
    )
    return value if isinstance(value, dict) else {}


def start_layout_shift_measurement(page: CdpWebSocket) -> None:
    page.evaluate(
        """(() => {
          const metric = { score: 0, entryCount: 0, maxValue: 0, supported: false };
          window.__chungmaruLayoutShift = metric;
          try {
            if (!('PerformanceObserver' in window)) return false;
            const observer = new PerformanceObserver((list) => {
              for (const entry of list.getEntries()) {
                if (entry.hadRecentInput) continue;
                const value = Number(entry.value || 0);
                metric.score += value;
                metric.entryCount += 1;
                metric.maxValue = Math.max(metric.maxValue, value);
              }
            });
            observer.observe({ type: 'layout-shift', buffered: true });
            metric.supported = true;
            return true;
          } catch (_) {
            return false;
          }
        })()""",
        timeout_s=5,
    )


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


def float_metric(value: Any) -> float:
    try:
      return max(0.0, float(value or 0))
    except (TypeError, ValueError):
      return 0.0


def bool_metric(value: Any, *, default: bool = False) -> bool:
    if isinstance(value, bool):
      return value
    if value is None:
      return default
    normalized = str(value).strip().lower()
    if not normalized:
      return default
    if normalized in {"1", "true", "yes", "y", "on"}:
      return True
    if normalized in {"0", "false", "no", "n", "off"}:
      return False
    return default


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


def sum_log_metric(logs: list[dict[str, Any]], key: str) -> int:
    return sum(int_metric(item.get(key)) for item in logs)


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
      "loggedMediaSafetyScanRequestCount": max_log_metric(logs, "mediaSafetyScanRequestCount"),
      "loggedMediaSafetyCoalescedScanRequestCount": max_log_metric(logs, "mediaSafetyCoalescedScanRequestCount"),
      "loggedMediaSafetyMediaLoadEventCount": max_log_metric(logs, "mediaSafetyMediaLoadEventCount"),
      "loggedMediaSafetyMutationBatchCount": max_log_metric(logs, "mediaSafetyMutationBatchCount"),
      "loggedMediaSafetyMutationAddedNodeCount": max_log_metric(logs, "mediaSafetyMutationAddedNodeCount"),
      "loggedMediaSafetyPotentialMutationBatchCount": max_log_metric(logs, "mediaSafetyPotentialMutationBatchCount"),
      "loggedMediaSafetyPageContextRefreshCount": max_log_metric(logs, "mediaSafetyPageContextRefreshCount"),
      "loggedMediaSafetyFastPathSeedCount": max_log_metric(logs, "mediaSafetyFastPathSeedCount"),
      "loggedMediaSafetyFastPathRequestCount": max_log_metric(logs, "mediaSafetyFastPathRequestCount"),
      "loggedMediaSafetyFastPathRunCount": max_log_metric(logs, "mediaSafetyFastPathRunCount"),
      "loggedMediaSafetyFastPathCandidateCount": max_log_metric(logs, "mediaSafetyFastPathCandidateCount"),
      "loggedMediaSafetyFastPathActionCount": max_log_metric(logs, "mediaSafetyFastPathActionCount"),
      "loggedClassifierDeadlineExceededCount": sum_log_metric(logs, "classifierDeadlineExceededCount"),
    }


def summarize_classifier_logs(logs: list[dict[str, Any]]) -> dict[str, Any]:
    classifier_logs = [
        item for item in logs
        if str(item.get("type") or "").startswith("media-safety-classifier-")
    ]
    batch_logs = [item for item in classifier_logs if item.get("type") == "media-safety-classifier-batch"]
    error_logs = [item for item in classifier_logs if item.get("type") == "media-safety-classifier-error"]
    ready_logs = [item for item in classifier_logs if item.get("type") == "media-safety-classifier-ready"]
    return {
        "classifier_log_count": len(classifier_logs),
        "classifier_ready_log_count": len(ready_logs),
        "classifier_batch_log_count": len(batch_logs),
        "classifier_error_log_count": len(error_logs),
        "classifier_candidate_count": sum_log_metric(batch_logs, "classifierCandidateCount"),
        "classifier_cache_hit_count": sum_log_metric(batch_logs, "cacheHitCount"),
        "classifier_blocked_count": sum_log_metric(batch_logs, "blockedCount"),
        "classifier_benign_count": sum_log_metric(batch_logs, "benignCount"),
        "classifier_ambiguous_count": sum_log_metric(batch_logs, "ambiguousCount"),
        "classifier_fetch_ms_max": max_log_metric(batch_logs, "fetchMs"),
        "classifier_decode_ms_max": max_log_metric(batch_logs, "decodeMs"),
        "classifier_inference_ms_max": max_log_metric(batch_logs, "inferenceMs"),
        "classifier_queue_wait_ms_max": max_log_metric(batch_logs, "queueWaitMs"),
        "classifier_decision_ms_max": max_log_metric(batch_logs, "classifierDecisionMs"),
        "classifier_dom_added_to_action_ms_max": max_log_metric(batch_logs, "domAddedToActionMs"),
        "classifier_model_load_count_max": max_log_metric(classifier_logs, "modelLoadCount"),
        "classifier_tensor_count_max": max_log_metric(classifier_logs, "tensorCount"),
        "classifier_backend": next((str(item.get("backend") or "") for item in reversed(classifier_logs) if item.get("backend")), ""),
        "classifier_model_version": next((str(item.get("modelVersion") or "") for item in reversed(classifier_logs) if item.get("modelVersion")), ""),
    }


def summarize_perf_logs(logs: list[dict[str, Any]]) -> dict[str, Any]:
    perf_logs = [item for item in logs if item.get("type") == "chrome-perf-summary"]
    return {
      "perf_runtime_log_count": len(perf_logs),
      "perf_pipeline_schedule_count": sum_log_metric(perf_logs, "pipelineScheduleCount"),
      "perf_pipeline_run_count": sum_log_metric(perf_logs, "pipelineRunCount"),
      "perf_pipeline_queued_count": sum_log_metric(perf_logs, "pipelineQueuedCount"),
      "perf_pipeline_suppressed_count": sum_log_metric(perf_logs, "pipelineSuppressedCount"),
      "perf_pipeline_duration_total_ms": sum_log_metric(perf_logs, "pipelineDurationTotalMs"),
      "perf_pipeline_duration_max_ms": max_log_metric(perf_logs, "pipelineDurationMaxMs"),
      "perf_search_result_schedule_count": sum_log_metric(perf_logs, "searchResultScheduleCount"),
      "perf_google_light_schedule_count": sum_log_metric(perf_logs, "googleLightScheduleCount"),
      "perf_media_safety_schedule_count": sum_log_metric(perf_logs, "mediaSafetyScheduleCount"),
      "perf_targeted_media_safety_schedule_count": sum_log_metric(
          perf_logs,
          "targetedMediaSafetyScheduleCount",
      ),
      "perf_runtime_message_count": sum_log_metric(perf_logs, "runtimeMessageCount"),
      "perf_backend_message_count": sum_log_metric(perf_logs, "backendMessageCount"),
      "perf_mutation_batch_count": sum_log_metric(perf_logs, "mutationBatchCount"),
      "perf_mutation_record_count": sum_log_metric(perf_logs, "mutationRecordCount"),
      "perf_mutation_added_node_count": sum_log_metric(perf_logs, "mutationAddedNodeCount"),
      "perf_mutation_potential_media_batch_count": sum_log_metric(
          perf_logs,
          "mutationPotentialMediaBatchCount",
      ),
      "perf_mutation_google_batch_count": sum_log_metric(perf_logs, "mutationGoogleBatchCount"),
      "perf_long_task_count": sum_log_metric(perf_logs, "longTaskCount"),
      "perf_long_task_max_ms": max_log_metric(perf_logs, "longTaskMaxMs"),
      "perf_event_loop_lag_count": sum_log_metric(perf_logs, "eventLoopLagCount"),
      "perf_event_loop_lag_max_ms": max_log_metric(perf_logs, "eventLoopLagMaxMs"),
      "perf_performance_guard_active": any(bool_metric(item.get("performanceGuardActive")) for item in perf_logs),
      "perf_performance_guard_remaining_ms_max": max_log_metric(perf_logs, "performanceGuardRemainingMs"),
    }


def apply_cpu_throttle(page: CdpWebSocket, rate: float) -> None:
    if rate <= 1:
      return
    page.call("Emulation.setCPUThrottlingRate", {"rate": rate}, timeout_s=5)


def perform_composite_interactions(page: CdpWebSocket) -> None:
    expression = """
(() => {
  const input = document.getElementById('composite-search-input');
  if (input) {
    input.focus();
    input.value = `${input.value} 스크롤 입력`;
    input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: '입력' }));
  }
  window.scrollTo(0, Math.max(0, document.body.scrollHeight - window.innerHeight));
  window.dispatchEvent(new Event('scroll'));
  window.setTimeout(() => {
    window.scrollTo(0, 0);
    window.dispatchEvent(new Event('scroll'));
  }, 120);
  return true;
})()
"""
    page.evaluate(expression, timeout_s=5)


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


def classifier_execution_mode(case: dict[str, Any]) -> str:
    override = str(case.get("nsfw_classifier_test_override") or "normal").strip().lower()
    if override in {"normal", "cpu"}:
      return "real-model"
    if override == "fixture":
      return "controlled-override"
    return "disabled"


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
    classifier_summary = summarize_classifier_logs(logs)
    classifier_mode = classifier_execution_mode(case)
    has_real_classifier_backend = (
        classifier_mode == "real-model" and
        bool(str(classifier_summary.get("classifier_backend") or "").strip())
    )
    perf_summary = summarize_perf_logs(logs)
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
    frame_harmful_total = int_metric(dom.get("frameHarmfulTotal"))
    frame_harmful_hidden_count = int_metric(dom.get("frameHarmfulHiddenCount"))
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
    is_live = case.get("scenario") == "live"
    is_chrome_error_page = final_origin == "chrome-error://chromewebdata"
    live_page_ok = (not is_live) or (bool(final_origin) and not is_chrome_error_page)
    return {
        "timestamp": now_iso(),
        "case_id": case["case_id"],
        "scenario": case["scenario"],
        "url_origin": origin,
        "url_path_prefix": path_prefix,
        "final_url_origin": final_origin,
        "final_url_path_prefix": final_path_prefix,
        "live_page_ok": live_page_ok,
        "live_page_status": "chrome-error" if is_chrome_error_page else ("loaded" if final_origin else "unknown"),
        "media_safety_enabled": bool(case["media_safety_enabled"]),
        "protection_profile": str(case.get("protection_profile") or ""),
        "global_enabled": bool(case.get("global_enabled", True)),
        "text_masking_enabled": bool(case.get("text_masking_enabled")),
        "site_protection_enabled": bool(case.get("site_protection_enabled")),
        "search_result_protection_enabled": bool(case.get("search_result_protection_enabled")),
        "cpu_backend": str(case.get("cpu_backend") or ""),
        "cpu_throttle_rate": float_metric(case.get("cpu_throttle_rate") or 1),
        "developer_log_enabled": bool(case["developer_log_enabled"]),
        "startup_gate_enabled": bool(case.get("media_safety_startup_gate_enabled")),
        "repeat_index": int_metric(case.get("repeat_index")),
        "seed_domain": str(case.get("seed_domain") or ""),
        "seed_category": str(case.get("seed_category") or ""),
        "seed_risk_level": str(case.get("seed_risk_level") or ""),
        "seed_title": str(case.get("seed_title") or "")[:120],
        "scan_ok": bool(summary.get("ok")),
        "scan_status": summary.get("status") or "",
        "error_code": summary.get("errorCode") or "",
        "reason": str(summary.get("reason") or "")[:220],
        "candidate_count": effective_candidate_count,
        "visible_tile_count": effective_visible_tile_count,
        "cheap_filter_hit_count": max(
            int_metric(summary.get("cheapFilterHitCount")),
            log_summary["loggedCheapFilterHitCount"],
        ),
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
        "media_safety_scan_request_count": max(
            int_metric(summary.get("mediaSafetyScanRequestCount")),
            log_summary["loggedMediaSafetyScanRequestCount"],
        ),
        "media_safety_coalesced_scan_request_count": max(
            int_metric(summary.get("mediaSafetyCoalescedScanRequestCount")),
            log_summary["loggedMediaSafetyCoalescedScanRequestCount"],
        ),
        "media_safety_media_load_event_count": max(
            int_metric(summary.get("mediaSafetyMediaLoadEventCount")),
            log_summary["loggedMediaSafetyMediaLoadEventCount"],
        ),
        "media_safety_mutation_batch_count": max(
            int_metric(summary.get("mediaSafetyMutationBatchCount")),
            log_summary["loggedMediaSafetyMutationBatchCount"],
        ),
        "media_safety_mutation_added_node_count": max(
            int_metric(summary.get("mediaSafetyMutationAddedNodeCount")),
            log_summary["loggedMediaSafetyMutationAddedNodeCount"],
        ),
        "media_safety_potential_mutation_batch_count": max(
            int_metric(summary.get("mediaSafetyPotentialMutationBatchCount")),
            log_summary["loggedMediaSafetyPotentialMutationBatchCount"],
        ),
        "media_safety_page_context_refresh_count": max(
            int_metric(summary.get("mediaSafetyPageContextRefreshCount")),
            log_summary["loggedMediaSafetyPageContextRefreshCount"],
        ),
        "media_safety_fast_path_seed_count": max(
            int_metric(summary.get("mediaSafetyFastPathSeedCount")),
            log_summary["loggedMediaSafetyFastPathSeedCount"],
        ),
        "media_safety_fast_path_request_count": max(
            int_metric(summary.get("mediaSafetyFastPathRequestCount")),
            log_summary["loggedMediaSafetyFastPathRequestCount"],
        ),
        "media_safety_fast_path_run_count": max(
            int_metric(summary.get("mediaSafetyFastPathRunCount")),
            log_summary["loggedMediaSafetyFastPathRunCount"],
        ),
        "media_safety_fast_path_candidate_count": max(
            int_metric(summary.get("mediaSafetyFastPathCandidateCount")),
            log_summary["loggedMediaSafetyFastPathCandidateCount"],
        ),
        "media_safety_fast_path_action_count": max(
            int_metric(summary.get("mediaSafetyFastPathActionCount")),
            log_summary["loggedMediaSafetyFastPathActionCount"],
        ),
        "classifier_deadline_exceeded_count": log_summary["loggedClassifierDeadlineExceededCount"],
        "late_decision_ms": max(
            int_metric(dom.get("lateDecisionMs")),
            int_metric(pre_manual_dom.get("lateDecisionMs")),
        ),
        "layout_shift_score": round(float_metric(dom.get("layoutShiftScore")), 4),
        "layout_shift_entry_count": int_metric(dom.get("layoutShiftEntryCount")),
        "layout_shift_max_value": round(float_metric(dom.get("layoutShiftMaxValue")), 4),
        "layout_shift_supported": bool_metric(dom.get("layoutShiftSupported")),
        "classifier_execution_mode": classifier_mode,
        "classifier_real_fetch_ms": (
            classifier_summary["classifier_fetch_ms_max"] if has_real_classifier_backend else ""
        ),
        "classifier_real_decode_ms": (
            classifier_summary["classifier_decode_ms_max"] if has_real_classifier_backend else ""
        ),
        "classifier_real_inference_ms": (
            classifier_summary["classifier_inference_ms_max"] if has_real_classifier_backend else ""
        ),
        **log_summary,
        **classifier_summary,
        **perf_summary,
        "runtime_log_count": len(logs),
        "media_runtime_log_count": len(media_logs),
        "pre_manual_hidden_count": int_metric(pre_manual_dom.get("hiddenCount")),
        "pre_manual_harmful_hidden_count": int_metric(pre_manual_dom.get("harmfulHiddenCount")),
        "pre_manual_safe_hidden_count": int_metric(pre_manual_dom.get("safeHiddenCount")),
        "hidden_count": hidden_count,
        "compact_summary_count": int_metric(dom.get("compactSummaryCount")),
        "harmful_total": harmful_total,
        "harmful_hidden_count": harmful_hidden_count,
        "frame_harmful_total": frame_harmful_total,
        "frame_harmful_hidden_count": frame_harmful_hidden_count,
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


def build_error_result_row(case: dict[str, Any], url: str, error: Exception) -> dict[str, Any]:
    return build_result_row(
        case=case,
        response={
            "response": {
                "ok": False,
                "status": "error",
                "errorCode": "LIVE_CASE_FAILED",
                "reason": str(error),
            },
            "phase": "harness",
        },
        dom={"locationHref": url},
        logs=[],
        url=url,
        pre_manual_dom={},
    )


def send_media_scan_message(
    worker: CdpWebSocket,
    case_url: str,
    message: dict[str, Any],
    *,
    strict_url_match: bool = False,
    timeout_s: float,
) -> dict[str, Any]:
    last_response: dict[str, Any] = {}
    for _attempt in range(6):
      if strict_url_match:
        last_response = send_to_matched_tab(
            worker,
            case_url,
            message,
            inject_on_failure=False,
            timeout_s=timeout_s,
        )
      else:
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

    if strict_url_match:
      return send_to_matched_tab(
          worker,
          case_url,
          message,
          inject_on_failure=True,
          timeout_s=timeout_s,
      )
    return send_to_fixture_tab(
      worker,
      case_url,
      message,
      inject_on_failure=True,
      timeout_s=timeout_s,
    )


def flush_runtime_perf_summary(
    worker: CdpWebSocket,
    case_url: str,
    *,
    strict_url_match: bool = False,
) -> None:
    send_media_scan_message(
        worker,
        case_url,
        {"type": "FLUSH_RUNTIME_PERF_SUMMARY"},
        strict_url_match=strict_url_match,
        timeout_s=3,
    )


def run_case(
    worker: CdpWebSocket,
    debugging_port: int,
    fixture_url: str,
    case: dict[str, Any],
) -> dict[str, Any]:
    set_nsfw_classifier_test_override(
        worker,
        str(case.get("nsfw_classifier_test_override") or "fixture"),
    )
    set_extension_state(
        worker,
        media_safety_enabled=bool(case["media_safety_enabled"]),
        developer_log_enabled=bool(case["developer_log_enabled"]),
        media_intervention_mode=str(case.get("media_intervention_mode") or "auto"),
        startup_gate_enabled=bool(case.get("media_safety_startup_gate_enabled")),
        global_enabled=bool(case.get("global_enabled", True)),
        text_masking_enabled=bool(case.get("text_masking_enabled")),
        site_protection_enabled=bool(case.get("site_protection_enabled")),
        search_result_protection_enabled=bool(case.get("search_result_protection_enabled")),
        backend_enabled=bool(case.get("backend_enabled")),
    )
    time.sleep(0.25)
    case_url = str(case.get("fixture_url") or f"{fixture_url}?scenario={case['scenario']}&case={case['case_id']}")
    target = create_tab(debugging_port, case_url)
    page = CdpWebSocket(str(target["webSocketDebuggerUrl"]))
    try:
      page.call("Page.bringToFront", timeout_s=5)
      apply_cpu_throttle(page, float(case.get("cpu_throttle_rate") or 1))
      wait_for_page_ready(page, timeout_s=10)
      start_layout_shift_measurement(page)
      time.sleep(0.45)
      if case["scenario"] == "composite":
        perform_composite_interactions(page)
        time.sleep(0.45)
      pre_manual_dom = inspect_media_dom(page)
      if case["scenario"] == "late-load" and bool(case["media_safety_enabled"]):
        # The full scan is intentionally settle-coalesced. Give its bounded
        # automatic path time to apply before issuing the manual smoke scan.
        deadline = time.monotonic() + 0.8
        while time.monotonic() < deadline:
          if int_metric(pre_manual_dom.get("harmfulHiddenCount")) >= 1:
            break
          time.sleep(0.05)
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
      if case["scenario"] in {"visual-explicit", "visual-safe", "sidebar-frame"}:
        deadline = time.monotonic() + 3.0
        while time.monotonic() < deadline:
          current_dom = inspect_media_dom(page)
          current_logs = get_runtime_logs(worker)
          classifier_batch_seen = any(
              item.get("type") == "media-safety-classifier-batch"
              for item in current_logs
          )
          if case["scenario"] == "visual-explicit" and bool(case["media_safety_enabled"]):
            if int_metric(current_dom.get("harmfulHiddenCount")) >= 1:
              break
          elif case["scenario"] == "sidebar-frame" and bool(case["media_safety_enabled"]):
            if int_metric(current_dom.get("frameHarmfulHiddenCount")) >= 1:
              break
          elif case["scenario"] == "visual-safe" and classifier_batch_seen:
            break
          else:
            break
          time.sleep(0.05)
      else:
        time.sleep(0.2)
      dom = inspect_media_dom(page)
      flush_runtime_perf_summary(worker, case_url)
      time.sleep(0.1)
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


def read_live_url_file(path: Path) -> list[dict[str, Any]]:
    targets: list[dict[str, Any]] = []
    lines = [
        line for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if not lines:
      return targets

    header = [item.strip().lower() for item in next(csv.reader([lines[0]]))]
    if "url" in header or "domain" in header:
      for row in csv.DictReader(lines):
        normalized = {str(key or "").strip().lower(): str(value or "").strip() for key, value in row.items()}
        raw_url = normalized.get("url") or normalized.get("domain") or normalized.get("seed_domain") or ""
        if not raw_url:
          continue
        url = normalize_live_url(raw_url)
        parsed = urllib.parse.urlparse(url)
        domain = normalized.get("seed_domain") or normalized.get("domain") or parsed.netloc
        targets.append({
            "url": url,
            "seed_domain": domain,
            "seed_category": normalized.get("seed_category") or normalized.get("category") or "",
            "seed_risk_level": normalized.get("seed_risk_level") or normalized.get("risk_level") or "",
            "seed_title": (normalized.get("seed_title") or normalized.get("title") or "")[:120],
        })
      return targets

    for line in lines:
      # Allow either plain URL/domain lines or a simple CSV first-column export.
      url = line.split(",", 1)[0].strip()
      if not url:
        continue
      targets.append({"url": normalize_live_url(url)})
    return targets


def load_seed_live_targets(
    seed_file: Path,
    *,
    categories: list[str],
    risk_levels: list[str],
    max_sites: int = 0,
) -> list[dict[str, Any]]:
    entries = json.loads(seed_file.read_text(encoding="utf-8"))
    if not isinstance(entries, list):
      raise ValueError(f"seed file must contain a list: {seed_file}")

    category_filter = {item.strip().lower() for item in categories if item.strip()}
    risk_filter = {item.strip().lower() for item in risk_levels if item.strip()}
    candidates: list[dict[str, Any]] = []
    seen_domains: set[str] = set()
    for entry in entries:
      if not isinstance(entry, dict):
        continue
      domain = str(entry.get("domain") or "").strip().lower()
      if not domain or domain in seen_domains:
        continue
      category = str(entry.get("category") or "").strip().lower()
      risk_level = str(entry.get("risk_level") or "").strip().lower()
      if category_filter and category not in category_filter:
        continue
      if risk_filter and risk_level not in risk_filter:
        continue
      raw_url = str(entry.get("url") or "").strip() or f"https://{domain}/"
      seen_domains.add(domain)
      candidates.append({
          "url": normalize_live_url(raw_url),
          "seed_domain": domain,
          "seed_category": category,
          "seed_risk_level": risk_level,
          "seed_title": str(entry.get("title") or "")[:120],
      })

    if max_sites <= 0:
      return candidates
    if len(category_filter) <= 1:
      return candidates[:max_sites]

    category_order = [item.strip().lower() for item in categories if item.strip()]
    buckets: dict[str, list[dict[str, Any]]] = {category: [] for category in category_order}
    for candidate in candidates:
      buckets.setdefault(str(candidate.get("seed_category") or ""), []).append(candidate)

    selected: list[dict[str, Any]] = []
    while len(selected) < max_sites:
      changed = False
      for category in category_order:
        bucket = buckets.get(category) or []
        if not bucket:
          continue
        selected.append(bucket.pop(0))
        changed = True
        if len(selected) >= max_sites:
          break
      if not changed:
        break
    return selected


def build_live_targets(args: argparse.Namespace) -> list[dict[str, Any]]:
    targets: list[dict[str, Any]] = []
    for url in args.live_url:
      targets.append({"url": normalize_live_url(url)})
    if args.live_url_file:
      targets.extend(read_live_url_file(args.live_url_file))
    if args.live_seed_file:
      categories = args.live_seed_category or ["adult", "gambling"]
      risk_levels = args.live_seed_risk_level or ["block"]
      targets.extend(load_seed_live_targets(
          args.live_seed_file,
          categories=categories,
          risk_levels=risk_levels,
          max_sites=args.live_max_sites,
      ))

    deduped: list[dict[str, Any]] = []
    seen_urls: set[str] = set()
    for target in targets:
      url = normalize_live_url(str(target.get("url") or ""))
      if url in seen_urls:
        continue
      seen_urls.add(url)
      deduped.append({**target, "url": url})
    if args.live_max_sites > 0 and not args.live_seed_file:
      deduped = deduped[:args.live_max_sites]
    return deduped


def startup_gate_values(mode: str) -> list[bool]:
    if mode == "decision-first":
      return [False]
    if mode == "startup-gate":
      return [True]
    return [False, True]


def run_live_case(
    worker: CdpWebSocket,
    debugging_port: int,
    live_url: str,
    case: dict[str, Any],
    *,
    settle_seconds: float,
    visual_evidence_dir: Path | None = None,
    visual_evidence_repeat: int = 1,
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
      pre_scan_dom = inspect_live_dom(page)
      current_page_url = str(pre_scan_dom.get("locationHref") or "")
      final_origin, _final_path_prefix = origin_and_path_prefix(current_page_url)
      if final_origin == "chrome-error://chromewebdata":
        return build_result_row(
            case=case,
            response={
                "response": {
                    "ok": True,
                    "status": "invalid_page",
                    "errorCode": "CHROME_ERROR_PAGE",
                    "reason": "Chrome error page; live media scan skipped.",
                },
                "phase": "harness",
            },
            dom=pre_scan_dom,
            logs=[],
            url=live_url,
        )
      parsed_current_page = urllib.parse.urlparse(current_page_url)
      if parsed_current_page.scheme not in {"http", "https"} or not parsed_current_page.netloc:
        return build_result_row(
            case=case,
            response={
                "response": {
                    "ok": True,
                    "status": "invalid_page",
                    "errorCode": "NON_HTTP_LIVE_PAGE",
                    "reason": f"Non-http live page; media scan skipped: {current_page_url[:80]}",
                },
                "phase": "harness",
            },
            dom=pre_scan_dom,
            logs=[],
            url=live_url,
        )
      settings = build_extension_settings(
          bool(case["media_safety_enabled"]),
          str(case.get("media_intervention_mode") or "auto"),
          bool(case.get("media_safety_startup_gate_enabled")),
      )
      response = send_media_scan_message(
          worker,
          current_page_url,
          {
              "type": "RUN_MEDIA_SAFETY_SCAN",
              "reason": f"live-smoke-{case['case_id']}",
              "settings": settings,
          },
          strict_url_match=True,
          timeout_s=15,
      )
      time.sleep(0.3)
      dom = inspect_live_dom(page)
      flush_runtime_perf_summary(worker, current_page_url, strict_url_match=True)
      time.sleep(0.1)
      logs = get_runtime_logs(worker)
      row = build_result_row(case=case, response=response, dom=dom, logs=logs, url=live_url)
      row["visual_artifact_path"] = ""
      row["visual_artifact_bytes"] = 0
      row["visual_capture_error"] = ""
      if visual_evidence_dir is not None and should_capture_visual_evidence(case, visual_evidence_repeat):
        try:
          screenshot_path = capture_page_screenshot(page, visual_evidence_dir, case)
          row["visual_artifact_path"] = str(screenshot_path)
          row["visual_artifact_bytes"] = screenshot_path.stat().st_size
        except Exception as error:  # noqa: BLE001 - visual evidence must not fail latency smoke
          row["visual_capture_error"] = str(error)[:220]
      return row
    finally:
      page.close()


def collect_fieldnames(rows: list[dict[str, Any]]) -> list[str]:
    fieldnames: list[str] = []
    seen: set[str] = set()
    for row in rows:
      for key in row.keys():
        if key in seen:
          continue
        seen.add(key)
        fieldnames.append(key)
    return fieldnames


def write_outputs(output_dir: Path, rows: list[dict[str, Any]], prefix: str) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    jsonl_path = output_dir / f"{prefix}.jsonl"
    csv_path = output_dir / f"{prefix}.csv"
    with jsonl_path.open("w", encoding="utf-8") as handle:
      for row in rows:
        handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    fieldnames = collect_fieldnames(rows)
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
      writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
      writer.writeheader()
      writer.writerows(rows)


def build_composite_cases(args: argparse.Namespace, fixture_port: int) -> list[dict[str, Any]]:
    classifier_backend = str(args.composite_classifier_backend or "cpu")
    repeat_count = max(1, int(args.composite_repeat or 1))
    local_fixture = f"http://127.0.0.1:{fixture_port}/?scenario=composite"
    google_search_fixture = f"http://www.google.com:{fixture_port}/search?scenario=composite&q=casino"
    google_images_fixture = f"http://www.google.com:{fixture_port}/search?scenario=composite&tbm=isch&q=casino"
    profiles = [
        {
            "profile": "off",
            "fixture_url": local_fixture,
            "global_enabled": False,
            "text_masking_enabled": False,
            "site_protection_enabled": False,
            "search_result_protection_enabled": False,
            "media_safety_enabled": False,
            "classifier_override": "off",
        },
        {
            "profile": "text_site",
            "fixture_url": google_search_fixture,
            "global_enabled": True,
            "text_masking_enabled": True,
            "site_protection_enabled": True,
            "search_result_protection_enabled": True,
            "media_safety_enabled": False,
            "classifier_override": "off",
        },
        {
            "profile": "media_cheap",
            "fixture_url": local_fixture,
            "global_enabled": True,
            "text_masking_enabled": False,
            "site_protection_enabled": False,
            "search_result_protection_enabled": False,
            "media_safety_enabled": True,
            "classifier_override": "off",
        },
        {
            "profile": "media_classifier",
            "fixture_url": local_fixture,
            "global_enabled": True,
            "text_masking_enabled": False,
            "site_protection_enabled": False,
            "search_result_protection_enabled": False,
            "media_safety_enabled": True,
            "classifier_override": classifier_backend,
        },
        {
            "profile": "all_features_on",
            "fixture_url": local_fixture,
            "global_enabled": True,
            "text_masking_enabled": True,
            "site_protection_enabled": True,
            "search_result_protection_enabled": True,
            "media_safety_enabled": True,
            "classifier_override": classifier_backend,
        },
    ]
    cases: list[dict[str, Any]] = []
    for repeat_index in range(1, repeat_count + 1):
      for profile in profiles:
        cases.append({
            "case_id": f"composite_{profile['profile']}_r{repeat_index}",
            "scenario": "composite",
            "developer_log_enabled": True,
            "media_intervention_mode": args.media_intervention_mode,
            "media_safety_startup_gate_enabled": False,
            "protection_profile": profile["profile"],
            "repeat_index": repeat_index,
            "cpu_backend": classifier_backend,
            "cpu_throttle_rate": float(args.cpu_throttle_rate or 1),
            "backend_enabled": False,
            "nsfw_classifier_test_override": profile["classifier_override"],
            **profile,
        })
    return cases


def write_composite_summary(output_dir: Path, rows: list[dict[str, Any]], args: argparse.Namespace) -> None:
    groups: dict[str, list[dict[str, Any]]] = {}
    for row in rows:
      groups.setdefault(str(row.get("protection_profile") or row.get("case_id") or ""), []).append(row)

    summary_rows: list[dict[str, Any]] = []
    for profile, group in sorted(groups.items()):
      real_inference_values = [
          int_metric(row.get("classifier_real_inference_ms"))
          for row in group
          if row.get("classifier_execution_mode") == "real-model"
          and str(row.get("classifier_real_inference_ms") or "").strip() != ""
      ]
      summary_rows.append({
          "protection_profile": profile,
          "run_count": len(group),
          "scan_ok_count": sum(bool(row.get("scan_ok")) for row in group),
          "action_count_max": max((int_metric(row.get("action_count")) for row in group), default=0),
          "false_hidden_count_max": max((int_metric(row.get("false_hidden_count")) for row in group), default=0),
          "collect_ms_p95": percentile((int_metric(row.get("collect_ms")) for row in group), 95),
          "apply_ms_p95": percentile((int_metric(row.get("apply_ms")) for row in group), 95),
          "dom_added_to_action_ms_p95": percentile((int_metric(row.get("dom_added_to_action_ms")) for row in group), 95),
          "classifier_queue_wait_ms_p95": percentile((int_metric(row.get("classifier_queue_wait_ms_max")) for row in group), 95),
          "classifier_real_run_count": len(real_inference_values),
          "classifier_inference_ms_p95": percentile(real_inference_values, 95) if real_inference_values else "",
          "cache_hit_count": sum(int_metric(row.get("classifier_cache_hit_count")) for row in group),
          "long_task_count": sum(int_metric(row.get("perf_long_task_count")) for row in group),
          "long_task_max_ms": max((int_metric(row.get("perf_long_task_max_ms")) for row in group), default=0),
          "event_loop_lag_count": sum(int_metric(row.get("perf_event_loop_lag_count")) for row in group),
          "event_loop_lag_max_ms": max((int_metric(row.get("perf_event_loop_lag_max_ms")) for row in group), default=0),
      })
    write_outputs(output_dir, summary_rows, "media-safety-composite-summary")

    report_lines = [
        "# Chungmaru CPU Composite Traversal Evidence",
        "",
        f"- Captured at: `{now_iso()}`",
        f"- Classifier backend requested: `{args.composite_classifier_backend}`",
        f"- CPU throttle rate: `{float(args.cpu_throttle_rate or 1)}` (supplementary only)",
        f"- Local logical cores: `{os.cpu_count() or 'unknown'}`",
        "- Reference profile: CPU backend, GPU disabled in headless Chrome, single classifier queue, 4 logical cores / 8GB RAM target; local machine constraints are recorded but not a low-spec proof.",
        "- Image bytes: fixture/generated local assets only for composite smoke; NSFW corpus bytes remain in Desktop/local scratch when classifier benchmark is run.",
        "",
        "## Profile Summary",
        "",
        "| Profile | Runs | OK | Actions max | False hides max | collect p95 | apply p95 | domAdded p95 | classifier queue p95 | real classifier runs | classifier inference p95 | long tasks | event-loop lag max |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for row in summary_rows:
      report_lines.append(
          "| {protection_profile} | {run_count} | {scan_ok_count} | {action_count_max} | "
          "{false_hidden_count_max} | {collect_ms_p95} | {apply_ms_p95} | "
          "{dom_added_to_action_ms_p95} | {classifier_queue_wait_ms_p95} | "
          "{classifier_real_run_count} | {classifier_inference_ms_p95} | {long_task_count} | {event_loop_lag_max_ms} |".format(**row)
      )
    report_lines.extend([
        "",
        "## Evidence Boundaries",
        "",
        "- Passed behavior is limited to controlled Chrome fixture traversal and developer runtime logs.",
        "- Google Search/Images rules are exercised through a local fixture served with a Google host mapping, not live Google.",
        "- Model quality and user-before-exposure claims remain Validation Needed until a reviewed CPU corpus run and live Desktop traversal complete.",
        "- No GPU or low-spec performance claim is made from this report.",
        "",
    ])
    (output_dir / "media-safety-composite-report.md").write_text("\n".join(report_lines), encoding="utf-8")


def percentile(values: list[int], pct: float) -> int:
    if not values:
      return 0
    sorted_values = sorted(values)
    index = max(0, min(len(sorted_values) - 1, math.ceil((pct / 100) * len(sorted_values)) - 1))
    return sorted_values[index]


def metric_values(rows: list[dict[str, Any]], key: str, *, successful_only: bool = True) -> list[int]:
    values: list[int] = []
    for row in rows:
      if successful_only and (
          not bool_metric(row.get("scan_ok"))
          or not bool_metric(row.get("live_page_ok"), default=True)
      ):
        continue
      values.append(int_metric(row.get(key)))
    return values


def float_metric_values(rows: list[dict[str, Any]], key: str, *, successful_only: bool = True) -> list[float]:
    values: list[float] = []
    for row in rows:
      if successful_only and (
          not bool_metric(row.get("scan_ok"))
          or not bool_metric(row.get("live_page_ok"), default=True)
      ):
        continue
      values.append(float_metric(row.get(key)))
    return values


def summarize_live_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[tuple[str, bool, str, str, str], list[dict[str, Any]]] = {}
    for row in rows:
      if row.get("scenario") != "live":
        continue
      key = (
          str(row.get("url_origin") or row.get("final_url_origin") or ""),
          bool_metric(row.get("startup_gate_enabled")),
          str(row.get("seed_domain") or ""),
          str(row.get("seed_category") or ""),
          str(row.get("seed_risk_level") or ""),
      )
      groups.setdefault(key, []).append(row)

    summary_rows: list[dict[str, Any]] = []
    for (url_origin, startup_gate_enabled, seed_domain, seed_category, seed_risk_level), group_rows in groups.items():
      ok_rows = [
          row for row in group_rows
          if bool_metric(row.get("scan_ok"))
          and bool_metric(row.get("live_page_ok"), default=True)
      ]
      error_rows = [row for row in group_rows if not bool_metric(row.get("scan_ok"))]
      invalid_page_rows = [
          row for row in group_rows
          if not bool_metric(row.get("live_page_ok"), default=True)
      ]
      # Use both pre-action scan metrics and post-action DOM metrics. A successful
      # remove/compact pass can leave zero visible candidates after action.
      visual_candidate_rows = [
          row for row in ok_rows
          if int_metric(row.get("candidate_sized_visible_media_element_count")) > 0
          or int_metric(row.get("visible_tile_count")) > 0
          or int_metric(row.get("candidate_count")) > 0
          or int_metric(row.get("action_count")) > 0
      ]
      action_rows = [row for row in ok_rows if int_metric(row.get("action_count")) > 0]
      dom_values = metric_values(group_rows, "dom_added_to_action_ms")
      apply_values = metric_values(group_rows, "apply_ms")
      collect_values = metric_values(group_rows, "collect_ms")
      action_values = metric_values(group_rows, "action_count")
      candidate_values = metric_values(group_rows, "candidate_count")
      visible_tile_values = metric_values(group_rows, "visible_tile_count")
      candidate_sized_values = metric_values(
          group_rows,
          "candidate_sized_visible_media_element_count",
          successful_only=False,
      )
      missed_values = metric_values(group_rows, "missed_visible_tile_count", successful_only=False)
      hidden_values = metric_values(group_rows, "hidden_count", successful_only=False)
      compact_values = metric_values(group_rows, "compact_summary_count", successful_only=False)
      remaining_values = metric_values(group_rows, "remaining_visible_tile_count", successful_only=False)
      false_hidden_values = metric_values(group_rows, "false_hidden_count", successful_only=False)
      fast_path_request_values = metric_values(group_rows, "media_safety_fast_path_request_count", successful_only=False)
      fast_path_run_values = metric_values(group_rows, "media_safety_fast_path_run_count", successful_only=False)
      fast_path_candidate_values = metric_values(group_rows, "media_safety_fast_path_candidate_count", successful_only=False)
      fast_path_action_values = metric_values(group_rows, "media_safety_fast_path_action_count", successful_only=False)
      classifier_deadline_values = metric_values(group_rows, "classifier_deadline_exceeded_count", successful_only=False)
      context_refresh_values = metric_values(group_rows, "media_safety_page_context_refresh_count", successful_only=False)
      coverage_values = float_metric_values(group_rows, "viewport_coverage_pct", successful_only=False)
      summary_rows.append({
          "timestamp": now_iso(),
          "url_origin": url_origin,
          "startup_gate_enabled": startup_gate_enabled,
          "seed_domain": seed_domain,
          "seed_category": seed_category,
          "seed_risk_level": seed_risk_level,
          "run_count": len(group_rows),
          "ok_count": len(ok_rows),
          "error_count": len(error_rows),
          "invalid_page_count": len(invalid_page_rows),
          "visual_candidate_run_count": len(visual_candidate_rows),
          "action_run_count": len(action_rows),
          "action_count_median": percentile(action_values, 50),
          "action_count_max": max(action_values, default=0),
          "candidate_count_max": max(candidate_values, default=0),
          "visible_tile_count_max": max(visible_tile_values, default=0),
          "candidate_sized_visible_media_element_count_max": max(candidate_sized_values, default=0),
          "remaining_visible_tile_count_max": max(remaining_values, default=0),
          "missed_visible_tile_count_max": max(missed_values, default=0),
          "false_hidden_count_max": max(false_hidden_values, default=0),
          "hidden_count_max": max(hidden_values, default=0),
          "compact_summary_count_max": max(compact_values, default=0),
          "media_safety_fast_path_request_count_max": max(fast_path_request_values, default=0),
          "media_safety_fast_path_run_count_max": max(fast_path_run_values, default=0),
          "media_safety_fast_path_candidate_count_max": max(fast_path_candidate_values, default=0),
          "media_safety_fast_path_action_count_max": max(fast_path_action_values, default=0),
          "classifier_deadline_exceeded_count_max": max(classifier_deadline_values, default=0),
          "media_safety_page_context_refresh_count_max": max(context_refresh_values, default=0),
          "viewport_coverage_pct_max": round(max(coverage_values, default=0.0), 1),
          "collect_ms_p50": percentile(collect_values, 50),
          "collect_ms_p95": percentile(collect_values, 95),
          "apply_ms_p50": percentile(apply_values, 50),
          "apply_ms_p95": percentile(apply_values, 95),
          "dom_added_to_action_ms_p50": percentile(dom_values, 50),
          "dom_added_to_action_ms_p95": percentile(dom_values, 95),
          "error_codes": ";".join(sorted({str(row.get("error_code") or "") for row in error_rows if row.get("error_code")})),
      })
    return summary_rows


def write_live_summary_outputs(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    summary_rows = summarize_live_rows(rows)
    if not summary_rows:
      return
    write_outputs(output_dir, summary_rows, LIVE_SUMMARY_PREFIX)


def write_visual_evidence_outputs(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    fieldnames = [
        "timestamp",
        "case_id",
        "url_origin",
        "seed_domain",
        "seed_category",
        "seed_risk_level",
        "repeat_index",
        "startup_gate_enabled",
        "live_page_ok",
        "scan_ok",
        "action_count",
        "removed_count",
        "placeholder_count",
        "remaining_visible_tile_count",
        "candidate_sized_visible_media_element_count",
        "false_hidden_count",
        "collect_ms",
        "apply_ms",
        "dom_added_to_action_ms",
        "visual_artifact_path",
        "visual_artifact_bytes",
        "visual_capture_error",
    ]
    manifest_rows = [
        {field: row.get(field, "") for field in fieldnames}
        for row in rows
        if row.get("visual_artifact_path") or row.get("visual_capture_error")
    ]
    output_dir.mkdir(parents=True, exist_ok=True)
    jsonl_path = output_dir / f"{VISUAL_EVIDENCE_PREFIX}.jsonl"
    csv_path = output_dir / f"{VISUAL_EVIDENCE_PREFIX}.csv"
    with jsonl_path.open("w", encoding="utf-8") as handle:
      for row in manifest_rows:
        handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
      writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
      writer.writeheader()
      writer.writerows(manifest_rows)


def assert_acceptance(rows: list[dict[str, Any]]) -> None:
    by_case = {row["case_id"]: row for row in rows}
    media_off = by_case["media_off_harmful"]
    log_off = by_case["log_off_harmful"]
    log_on = by_case["log_on_harmful"]
    clean = by_case["log_on_clean"]
    address_guide = by_case.get("log_on_address_guide_video")
    late_load = by_case.get("log_on_late_load")
    background_banner = by_case.get("log_on_background_banner")
    classifier_off = by_case.get("classifier_off_visual_explicit")
    classifier_log_off = by_case.get("classifier_log_off_visual_explicit")
    classifier_explicit = by_case.get("classifier_log_on_visual_explicit")
    classifier_safe = by_case.get("classifier_log_on_visual_safe")
    medical_adult = by_case.get("log_on_medical_adult")
    sidebar_frame = by_case.get("log_on_sidebar_frame")

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
    if medical_adult and (medical_adult["safe_hidden_count"] != 0 or medical_adult["false_hidden_count"] != 0):
      failures.append("log_on_medical_adult should not hide adult ADHD or medical information fixtures")
    if medical_adult and medical_adult["classifier_batch_log_count"] != 0:
      failures.append("log_on_medical_adult should not classify clear medical information fixtures")
    if sidebar_frame and sidebar_frame["frame_harmful_hidden_count"] < 1:
      failures.append("log_on_sidebar_frame should hide explicit visual media inside an iframe")
    if address_guide and address_guide["harmful_hidden_count"] < 6:
      failures.append("log_on_address_guide_video should hide source-backed video banners")
    if address_guide and address_guide["remaining_visible_tile_count"] != 0:
      failures.append("log_on_address_guide_video should not leave visible video banner tiles")
    if address_guide and float_metric(address_guide.get("layout_shift_score")) > 0.1:
      failures.append("log_on_address_guide_video should keep layout shift under 0.1")
    if late_load and late_load["pre_manual_harmful_hidden_count"] < 1:
      failures.append("log_on_late_load should auto-hide delayed media before manual smoke scan")
    if late_load and late_load["safe_hidden_count"] != 0:
      failures.append("log_on_late_load should not hide safe delayed fixture media")
    if late_load and late_load["late_decision_ms"] <= 0:
      failures.append("log_on_late_load should record delayed media decision latency")
    if background_banner and background_banner["harmful_hidden_count"] < 2:
      failures.append("log_on_background_banner should hide CSS background-image harmful banners")
    if background_banner and background_banner["safe_hidden_count"] != 0:
      failures.append("log_on_background_banner should not hide safe CSS background-image banner")
    if classifier_off and classifier_off["hidden_count"] != 0:
      failures.append("classifier_off_visual_explicit should not hide media")
    if classifier_off and classifier_off["classifier_batch_log_count"] != 0:
      failures.append("classifier_off_visual_explicit should not issue classifier batches")
    if classifier_log_off and classifier_log_off["harmful_hidden_count"] < 1:
      failures.append("classifier_log_off_visual_explicit should hide the visual-only harmful fixture")
    if classifier_log_off and (
        classifier_log_off["media_runtime_log_count"] != 0
        or classifier_log_off["classifier_batch_log_count"] != 0
    ):
      failures.append("classifier_log_off_visual_explicit should not write media/classifier developer logs")
    if classifier_explicit and classifier_explicit["harmful_hidden_count"] < 1:
      failures.append("classifier_log_on_visual_explicit should hide the visual-only harmful fixture")
    if classifier_explicit and classifier_explicit["classifier_batch_log_count"] < 1:
      failures.append("classifier_log_on_visual_explicit should write one aggregate classifier batch log")
    if classifier_explicit and classifier_explicit["classifier_blocked_count"] < 1:
      failures.append("classifier_log_on_visual_explicit should record a classifier block")
    if classifier_safe and (classifier_safe["safe_hidden_count"] != 0 or classifier_safe["false_hidden_count"] != 0):
      failures.append("classifier_log_on_visual_safe should keep safe media visible")
    if classifier_safe and classifier_safe["classifier_batch_log_count"] < 1:
      failures.append("classifier_log_on_visual_safe should write one aggregate classifier batch log")
    if classifier_safe and classifier_safe["classifier_benign_count"] < 1:
      failures.append("classifier_log_on_visual_safe should record a benign classifier decision")
    for row in rows:
      case_id = str(row.get("case_id") or "unknown")
      action_count = int_metric(row.get("action_count"))
      dom_added_to_action_ms = int_metric(row.get("dom_added_to_action_ms"))
      if action_count > 0 and dom_added_to_action_ms > ONE_SECOND_PROTECTION_BUDGET_MS:
        failures.append(
            f"{case_id} exceeded the {ONE_SECOND_PROTECTION_BUDGET_MS}ms DOM-to-action budget "
            f"({dom_added_to_action_ms}ms)"
        )
      classifier_decision_ms = int_metric(row.get("classifier_decision_ms_max"))
      if classifier_decision_ms > ONE_SECOND_PROTECTION_BUDGET_MS:
        failures.append(
            f"{case_id} exceeded the {ONE_SECOND_PROTECTION_BUDGET_MS}ms classifier decision budget "
            f"({classifier_decision_ms}ms)"
        )
    if late_load and int_metric(late_load.get("late_decision_ms")) > ONE_SECOND_PROTECTION_BUDGET_MS:
      failures.append(
          "log_on_late_load exceeded the "
          f"{ONE_SECOND_PROTECTION_BUDGET_MS}ms delayed-media decision budget "
          f"({int_metric(late_load.get('late_decision_ms'))}ms)"
      )
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
    parser.add_argument("--live-url-file", type=Path, default=None)
    parser.add_argument("--live-seed-file", nargs="?", const=DEFAULT_SITE_SEED_FILE, type=Path, default=None)
    parser.add_argument("--live-seed-category", action="append", default=[])
    parser.add_argument("--live-seed-risk-level", action="append", default=[])
    parser.add_argument("--live-max-sites", type=int, default=0)
    parser.add_argument("--live-repeat", type=int, default=1)
    parser.add_argument("--live-settle-seconds", type=float, default=2.0)
    parser.add_argument("--live-startup-mode", choices=["both", "decision-first", "startup-gate"], default="both")
    parser.add_argument("--capture-visual-evidence", action="store_true")
    parser.add_argument("--visual-evidence-dir", type=Path, default=DEFAULT_VISUAL_EVIDENCE_DIR)
    parser.add_argument(
        "--visual-evidence-repeat",
        type=int,
        default=1,
        help="Capture only this live repeat index. Use 0 to capture every repeat.",
    )
    parser.add_argument("--media-intervention-mode", choices=["auto", "placeholder", "remove"], default="auto")
    parser.add_argument(
        "--composite-profile",
        action="store_true",
        help="Run the CPU-oriented OFF/text-site/cheap/classifier/all-features composite traversal matrix.",
    )
    parser.add_argument("--composite-repeat", type=int, default=1)
    parser.add_argument(
        "--composite-classifier-backend",
        choices=["cpu", "fixture", "off"],
        default="cpu",
        help="Classifier backend used for composite media_classifier/all_features_on profiles.",
    )
    parser.add_argument(
        "--cpu-throttle-rate",
        type=float,
        default=1.0,
        help="Optional Chrome CPU throttling rate for supplementary stress only.",
    )
    display_group = parser.add_mutually_exclusive_group()
    display_group.add_argument(
        "--headless",
        dest="headless",
        action="store_true",
        default=True,
        help="Run Chrome in headless mode so smoke tests do not steal focus. This is the default.",
    )
    display_group.add_argument(
        "--headed",
        dest="headless",
        action="store_false",
        help="Show the Chrome test window for manual visual debugging.",
    )
    parser.add_argument("--clean-profile", action="store_true", default=True)
    parser.add_argument("--keep-chrome", action="store_true")
    parser.add_argument(
        "--summary-only",
        action="store_true",
        help="Write full CSV/JSONL artifacts but print only a compact completion summary.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.chrome_path = detect_chrome_path(args.chrome_path)
    if args.output_dir != DEFAULT_OUTPUT_DIR and args.visual_evidence_dir == DEFAULT_VISUAL_EVIDENCE_DIR:
      args.visual_evidence_dir = args.output_dir / "visual"
    if args.capture_visual_evidence:
      prepare_visual_evidence_dir(args.visual_evidence_dir, args.output_dir)
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
              "case_id": "log_on_medical_adult",
              "scenario": "medical-adult",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
              "nsfw_classifier_test_override": "fixture",
          },
          {
              "case_id": "log_on_sidebar_frame",
              "scenario": "sidebar-frame",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
              "nsfw_classifier_test_override": "fixture",
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
          {
              "case_id": "log_on_background_banner",
              "scenario": "background-banner",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
          },
          {
              "case_id": "classifier_off_visual_explicit",
              "scenario": "visual-explicit",
              "media_safety_enabled": False,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
              "nsfw_classifier_test_override": "off",
          },
          {
              "case_id": "classifier_log_off_visual_explicit",
              "scenario": "visual-explicit",
              "media_safety_enabled": True,
              "developer_log_enabled": False,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
              "nsfw_classifier_test_override": "fixture",
          },
          {
              "case_id": "classifier_log_on_visual_explicit",
              "scenario": "visual-explicit",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
              "nsfw_classifier_test_override": "fixture",
          },
          {
              "case_id": "classifier_log_on_visual_safe",
              "scenario": "visual-safe",
              "media_safety_enabled": True,
              "developer_log_enabled": True,
              "media_intervention_mode": args.media_intervention_mode,
              "media_safety_startup_gate_enabled": False,
              "nsfw_classifier_test_override": "fixture",
          },
      ]
      live_requested = bool(args.live_url or args.live_url_file or args.live_seed_file)
      live_targets = build_live_targets(args)
      if live_requested and not live_targets:
        raise RuntimeError("live smoke requested but no live targets were selected")
      if args.composite_profile:
        for case in build_composite_cases(args, fixture_port):
          worker = connect_service_worker(args.debugging_port)
          try:
            rows.append(run_case(worker, args.debugging_port, fixture_url, case))
          except Exception as error:  # noqa: BLE001 - composite matrix should keep later profiles
            rows.append(build_error_result_row(case, str(case.get("fixture_url") or fixture_url), error))
          finally:
            worker.close()
        write_outputs(args.output_dir, rows, COMPOSITE_OUTPUT_PREFIX)
        write_composite_summary(args.output_dir, rows, args)
      elif live_targets:
        repeat_count = max(1, int(args.live_repeat or 1))
        startup_modes = startup_gate_values(args.live_startup_mode)
        for index, target in enumerate(live_targets, start=1):
          live_url = str(target.get("url") or "")
          parsed = urllib.parse.urlparse(live_url)
          host_slug = parsed.netloc.replace(".", "_")
          for repeat_index in range(1, repeat_count + 1):
            for startup_gate_enabled in startup_modes:
              mode_label = "startup_gate" if startup_gate_enabled else "decision_first"
              repeat_suffix = f"_r{repeat_index}" if repeat_count > 1 else ""
              case = {
                  "case_id": f"live_{index}_{host_slug}_{mode_label}{repeat_suffix}",
                  "scenario": "live",
                  "media_safety_enabled": True,
                  "developer_log_enabled": True,
                  "media_intervention_mode": args.media_intervention_mode,
                  "media_safety_startup_gate_enabled": startup_gate_enabled,
                  "repeat_index": repeat_index,
                  "seed_domain": target.get("seed_domain") or "",
                  "seed_category": target.get("seed_category") or "",
                  "seed_risk_level": target.get("seed_risk_level") or "",
                  "seed_title": target.get("seed_title") or "",
              }
              worker = None
              try:
                worker = connect_service_worker(args.debugging_port)
                rows.append(run_live_case(
                    worker,
                    args.debugging_port,
                    live_url,
                    case,
                    settle_seconds=args.live_settle_seconds,
                    visual_evidence_dir=args.visual_evidence_dir if args.capture_visual_evidence else None,
                    visual_evidence_repeat=max(0, int(args.visual_evidence_repeat or 0)),
                ))
              except Exception as error:  # noqa: BLE001 - bulk live smoke should keep later URLs running
                rows.append(build_error_result_row(case, live_url, error))
              finally:
                if worker is not None:
                  worker.close()
        write_outputs(args.output_dir, rows, LIVE_OUTPUT_PREFIX)
        write_live_summary_outputs(args.output_dir, rows)
        if args.capture_visual_evidence:
          write_visual_evidence_outputs(args.output_dir, rows)
      else:
        for case in cases:
          worker = connect_service_worker(args.debugging_port)
          try:
            rows.append(run_case(worker, args.debugging_port, fixture_url, case))
          finally:
            worker.close()
        write_outputs(args.output_dir, rows, FIXTURE_OUTPUT_PREFIX)
        assert_acceptance(rows)
      payload = {"ok": True, "case_count": len(rows), "output_dir": str(args.output_dir)}
      if not args.summary_only:
        payload["rows"] = rows
      print(json.dumps(payload, ensure_ascii=False, indent=2))
      return 0
    except Exception as error:  # noqa: BLE001 - smoke output should preserve failure reason
      if rows:
        is_live_run = bool(args.live_url or args.live_url_file or args.live_seed_file)
        prefix = LIVE_OUTPUT_PREFIX if is_live_run else FIXTURE_OUTPUT_PREFIX
        write_outputs(args.output_dir, rows, prefix)
        if is_live_run:
          write_live_summary_outputs(args.output_dir, rows)
          if args.capture_visual_evidence:
            write_visual_evidence_outputs(args.output_dir, rows)
      payload = {"ok": False, "error": str(error), "rows": rows}
      if args.summary_only:
        payload = {"ok": False, "error": str(error), "case_count": len(rows)}
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
