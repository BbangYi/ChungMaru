#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import urllib.parse
from pathlib import Path
from typing import Any


DEFAULT_SEED_FILE = Path("backend/data/site_intel_seed_massive.json")
DEFAULT_OUTPUT_FILE = Path("evaluation/media-safety/fixtures/live-media-risk-priority-urls.csv")
DEFAULT_PREPEND_FILE = Path("evaluation/media-safety/fixtures/live-visual-rich-urls.csv")
DEFAULT_CATEGORIES = ["gambling", "adult"]
DEFAULT_RISK_LEVELS = ["block"]
CSV_FIELDS = [
    "url",
    "seed_domain",
    "seed_category",
    "seed_risk_level",
    "seed_title",
    "priority_group",
    "source_note",
]


def normalize_url(value: str) -> str:
    raw = str(value or "").strip()
    if not raw:
        raise ValueError("URL/domain cannot be empty")
    if "://" not in raw:
        raw = f"https://{raw}"
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError(f"unsupported URL/domain: {value}")
    return raw


def domain_from_url(value: str) -> str:
    try:
        return urllib.parse.urlparse(normalize_url(value)).netloc.lower()
    except ValueError:
        return str(value or "").strip().lower()


def read_prepend_file(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []

    lines = [
        line for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if not lines:
        return []

    rows: list[dict[str, str]] = []
    reader = csv.DictReader(lines)
    for raw in reader:
        normalized = {str(key or "").strip(): str(value or "").strip() for key, value in raw.items()}
        url = normalized.get("url") or normalized.get("domain") or normalized.get("seed_domain") or ""
        if not url:
            continue
        domain = normalized.get("seed_domain") or normalized.get("domain") or domain_from_url(url)
        rows.append({
            "url": normalize_url(url),
            "seed_domain": domain,
            "seed_category": normalized.get("seed_category") or normalized.get("category") or "",
            "seed_risk_level": normalized.get("seed_risk_level") or normalized.get("risk_level") or "",
            "seed_title": normalized.get("seed_title") or normalized.get("title") or "",
            "priority_group": "known-live-visual-rich",
            "source_note": f"prepended from {path}",
        })
    return rows


def load_seed_entries(path: Path) -> list[dict[str, Any]]:
    entries = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(entries, list):
        raise ValueError(f"seed file must contain a list: {path}")
    return [entry for entry in entries if isinstance(entry, dict)]


def text_blob(entry: dict[str, Any]) -> str:
    values: list[str] = []
    for key in ["domain", "title", "summary", "category", "region", "language"]:
        values.append(str(entry.get(key) or ""))
    for key in ["tags", "aliases", "indicators", "risk_types"]:
        value = entry.get(key) or []
        if isinstance(value, list):
            values.extend(str(item) for item in value)
    return " ".join(values).lower()


def score_entry(entry: dict[str, Any]) -> tuple[int, str]:
    domain = str(entry.get("domain") or "").lower()
    category = str(entry.get("category") or "").lower()
    region = str(entry.get("region") or "").lower()
    blob = text_blob(entry)

    score = 0
    reasons: list[str] = []
    if category == "gambling":
        score += 80
        reasons.append("gambling")
    if category == "adult":
        score += 60
        reasons.append("adult")
    if region == "kr" or domain.endswith(".kr") or domain.endswith(".co.kr"):
        score += 35
        reasons.append("kr")
    if entry.get("harmful_content") is True:
        score += 15
        reasons.append("harmful")
    if any(token in blob for token in ["toto", "토토", "casino", "카지노", "bet", "베팅", "바카라"]):
        score += 18
        reasons.append("banner-keyword")
    if any(token in blob for token in ["19", "성인", "adult", "explicit", "streaming"]):
        score += 12
        reasons.append("adult-keyword")
    if domain.endswith((".bet", ".casino", ".live", ".cam", ".video")):
        score += 8
        reasons.append("risk-tld")
    return score, "+".join(reasons) or "seed"


def seed_row(entry: dict[str, Any]) -> dict[str, str]:
    domain = str(entry.get("domain") or "").strip().lower()
    score, reason = score_entry(entry)
    return {
        "url": normalize_url(str(entry.get("url") or domain)),
        "seed_domain": domain,
        "seed_category": str(entry.get("category") or "").strip().lower(),
        "seed_risk_level": str(entry.get("risk_level") or "").strip().lower(),
        "seed_title": str(entry.get("title") or "")[:120],
        "priority_group": f"seed-score-{score:03d}",
        "source_note": reason,
    }


def filter_seed_entries(
    entries: list[dict[str, Any]],
    *,
    categories: list[str],
    risk_levels: list[str],
) -> list[dict[str, Any]]:
    category_filter = {item.strip().lower() for item in categories if item.strip()}
    risk_filter = {item.strip().lower() for item in risk_levels if item.strip()}
    filtered: list[dict[str, Any]] = []
    seen_domains: set[str] = set()
    for entry in entries:
        domain = str(entry.get("domain") or "").strip().lower()
        if not domain or domain in seen_domains:
            continue
        category = str(entry.get("category") or "").strip().lower()
        risk_level = str(entry.get("risk_level") or "").strip().lower()
        if category_filter and category not in category_filter:
            continue
        if risk_filter and risk_level not in risk_filter:
            continue
        seen_domains.add(domain)
        filtered.append(entry)
    return filtered


def select_rows(
    rows: list[dict[str, str]],
    *,
    categories: list[str],
    max_sites: int,
    per_category: int,
) -> list[dict[str, str]]:
    if max_sites <= 0 and per_category <= 0:
        return rows

    selected: list[dict[str, str]] = []
    category_order = [item.strip().lower() for item in categories if item.strip()]
    buckets: dict[str, list[dict[str, str]]] = {category: [] for category in category_order}
    for row in rows:
        buckets.setdefault(row["seed_category"], []).append(row)

    if per_category > 0:
        for category in category_order:
            selected.extend((buckets.get(category) or [])[:per_category])
    else:
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

    if max_sites > 0:
        selected = selected[:max_sites]
    return selected


def dedupe_rows(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    deduped: list[dict[str, str]] = []
    seen: set[str] = set()
    for row in rows:
        url = normalize_url(row["url"])
        if url in seen:
            continue
        seen.add(url)
        deduped.append({field: str(row.get(field) or "") for field in CSV_FIELDS})
    return deduped


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build prioritized Chungmaru media safety live-smoke candidate CSVs from site intel seed data."
    )
    parser.add_argument("--seed-file", type=Path, default=DEFAULT_SEED_FILE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_FILE)
    parser.add_argument("--category", action="append", default=[])
    parser.add_argument("--risk-level", action="append", default=[])
    parser.add_argument("--max-sites", type=int, default=48)
    parser.add_argument("--per-category", type=int, default=20)
    parser.add_argument("--prepend-url-file", type=Path, action="append", default=[DEFAULT_PREPEND_FILE])
    parser.add_argument("--no-prepend", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    categories = args.category or DEFAULT_CATEGORIES
    risk_levels = args.risk_level or DEFAULT_RISK_LEVELS

    entries = filter_seed_entries(
        load_seed_entries(args.seed_file),
        categories=categories,
        risk_levels=risk_levels,
    )
    rows = [seed_row(entry) for entry in entries]
    rows.sort(key=lambda row: (-int(row["priority_group"].rsplit("-", 1)[-1]), row["seed_category"], row["seed_domain"]))
    selected = select_rows(rows, categories=categories, max_sites=args.max_sites, per_category=args.per_category)

    prepended: list[dict[str, str]] = []
    if not args.no_prepend:
        for path in args.prepend_url_file:
            prepended.extend(read_prepend_file(path))

    output_rows = dedupe_rows(prepended + selected)
    write_csv(args.output, output_rows)
    print(json.dumps({
        "ok": True,
        "output": str(args.output),
        "rows": len(output_rows),
        "seedRows": len(selected),
        "prependedRows": len(prepended),
        "categories": categories,
        "riskLevels": risk_levels,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
