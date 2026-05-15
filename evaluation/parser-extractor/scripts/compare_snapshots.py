#!/usr/bin/env python3
"""Print a compact before/after table for parser/extractor JSONL fixtures."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


def load_first_jsonl(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                value = json.loads(line)
                if not isinstance(value, dict):
                    raise ValueError(f"{path} must contain JSON objects")
                return value
    raise ValueError(f"{path} is empty")


def text_sample(value: str, limit: int = 54) -> str:
    normalized = " ".join(value.split())
    if len(normalized) <= limit:
        return normalized
    return normalized[: limit - 1] + "…"


def bounds(value: dict[str, Any]) -> str:
    return (
        f"{value.get('left', '')},"
        f"{value.get('top', '')},"
        f"{value.get('right', '')},"
        f"{value.get('bottom', '')}"
    )


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: compare_snapshots.py RAW.jsonl CLEANED.jsonl", file=sys.stderr)
        return 2

    raw = load_first_jsonl(Path(argv[1]))
    cleaned = load_first_jsonl(Path(argv[2]))

    print(f"# {raw.get('platform', 'unknown')} / {raw.get('screen', 'unknown')}")
    print()
    print("| State | Text | Reason / Source | Bounds |")
    print("| --- | --- | --- | --- |")

    for node in raw.get("raw_nodes", []):
        print(
            "| Raw | "
            f"{text_sample(str(node.get('text', '')))} | "
            f"{node.get('reason', '')} | "
            f"{bounds(node.get('boundsInScreen', {}))} |"
        )

    for item in cleaned.get("comments", []):
        print(
            "| Kept | "
            f"{text_sample(str(item.get('commentText', '')))} | "
            f"{item.get('source', item.get('author_id', ''))} | "
            f"{bounds(item.get('boundsInScreen', {}))} |"
        )

    for item in cleaned.get("removed", []):
        print(
            "| Removed | "
            f"{text_sample(str(item.get('text', '')))} | "
            f"{item.get('reason', '')} |  |"
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
