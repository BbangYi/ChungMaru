"""
간단한 사이트 인텔 수집 스크립트

사용 예:
  python backend/scripts/crawl_site_intel.py --url https://www.naver.com
  python backend/scripts/crawl_site_intel.py --input backend/data/site_intel_seed.json
  python backend/scripts/crawl_site_intel.py --rebuild-seed

주의:
- 현재 버전은 경량 수집기다.
- title, meta description, URL 패턴을 기반으로 로컬 site_intel DB를 채운다.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "api"))

from site_intel_store import SiteIntelStore  # noqa: E402


TITLE_PATTERN = re.compile(r"<title[^>]*>(.*?)</title>", re.IGNORECASE | re.DOTALL)
META_DESC_PATTERN = re.compile(
    r'<meta[^>]+name=["\']description["\'][^>]+content=["\'](.*?)["\']',
    re.IGNORECASE | re.DOTALL,
)


def fetch_html(url: str, timeout: int = 8) -> str:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (compatible; ChungMaruSiteCrawler/1.0)"
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def infer_category(url: str, title: str, summary: str) -> tuple[str, str, bool, bool]:
    text = " ".join([url, title, summary]).lower()
    if any(keyword in text for keyword in ["casino", "bet", "slot", "gamble", "poker"]):
        return "gambling", "block", False, True
    if any(keyword in text for keyword in ["adult", "porn", "sex", "cam", "escort"]):
        return "adult", "block", False, True
    if any(keyword in text for keyword in ["wallet", "airdrop", "verify-account", "password-reset", "gift-card"]):
        return "phishing", "block", True, False
    if any(keyword in text for keyword in ["crack", "keygen", "trojan", "malware", "exploit"]):
        return "malware", "block", True, False
    if any(keyword in text for keyword in ["torrent", "magnet", "watchfree", "pirate"]):
        return "piracy", "warning", False, True
    return "unknown", "allow", False, False


def crawl_single_url(url: str) -> dict:
    html = fetch_html(url)
    title_match = TITLE_PATTERN.search(html)
    meta_match = META_DESC_PATTERN.search(html)
    title = (title_match.group(1).strip() if title_match else "")[:200]
    summary = (meta_match.group(1).strip() if meta_match else "")[:500]
    category, risk_level, security_threat, harmful_content = infer_category(url, title, summary)
    domain = (urlparse(url).hostname or "").lower()
    if domain.startswith("www."):
        domain = domain[4:]
    return {
        "domain": domain,
        "title": title or domain,
        "summary": summary,
        "category": category,
        "risk_level": risk_level,
        "security_threat": security_threat,
        "harmful_content": harmful_content,
        "source": "crawler",
        "tags": [category] if category != "unknown" else [],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", action="append", default=[], help="수집할 URL")
    parser.add_argument("--input", help="site intel JSON 파일 경로")
    parser.add_argument("--rebuild-seed", action="store_true", help="기본 seed 파일로 DB를 초기화하고 재적재")
    args = parser.parse_args()

    store = SiteIntelStore()

    if args.rebuild_seed:
        result = store.rebuild_from_seed()
        print(json.dumps({"ok": True, "mode": "rebuild-seed", **result}, ensure_ascii=False, indent=2))
        return 0

    entries = []

    if args.input:
        payload = json.loads(Path(args.input).read_text(encoding="utf-8"))
        if isinstance(payload, list):
            entries.extend(payload)

    for url in args.url:
        entries.append(crawl_single_url(url))

    if not entries:
        print("입력 데이터가 없습니다. --url 또는 --input을 사용하세요.")
        return 1

    count = store.bulk_upsert(entries)
    print(json.dumps({"ok": True, "stored": count, "stats": store.stats()}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
