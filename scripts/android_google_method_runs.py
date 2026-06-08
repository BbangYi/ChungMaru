#!/usr/bin/env python3
"""Aggregate Android Google mobile evidence into one method-labeled CSV.

This keeps the original per-run raw_runs.csv files untouched. The output is a
row-level table for comparison/reporting across method attempts and large
future runs.
"""

from __future__ import annotations

import argparse
import csv
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


DEFAULT_METHOD_MAP = Path("evaluation/latency/android-google-method-map.tsv")
DEFAULT_OUTPUT = Path("evaluation/latency/results/current/android-google-method-runs.csv")

RAW_NUMERIC_FIELDS = [
    "collect_ms",
    "node_collection_ms",
    "visual_roi_planning_ms",
    "screen_candidate_extraction_ms",
    "candidate_post_processing_ms",
    "candidate_parallel_wait_ms",
    "backend_api_ms",
    "backend_e2e_ms",
    "ocr_ms",
    "coord_ms",
    "display_ms",
    "node_count",
    "screen_candidates",
    "char_nodes",
    "char_range_candidates",
    "overlay_candidates",
    "overlay_rendered",
    "offensive",
    "filtered",
    "visual_roi_candidates",
    "visual_roi_selected",
    "visual_ocr_raw",
    "visual_ocr_selected",
    "observed_total_ms",
    "risk_gate_mask_ms",
    "risk_gate_event_age_ms",
    "risk_gate_receive_to_mask_ms",
    "fast_provisional_mask_ms",
    "fast_provisional_event_age_ms",
    "fast_provisional_build_ms",
    "fast_provisional_overlay_ms",
    "fast_provisional_receive_to_mask_ms",
]

OUTPUT_FIELDS = [
    "sample_key",
    "method_row_index",
    "method_order",
    "method_id",
    "method_label",
    "method_notes",
    "evidence_batch_id",
    "run_id",
    "row_index",
    "valid_latency_row",
    "mode",
    "stages",
    "scenario",
    "scenario_id",
    "repeat_index",
    "query",
    "category",
    "expected",
    "video_recorded",
    "device",
    *RAW_NUMERIC_FIELDS,
    "first_mask_ms",
    "first_mask_source",
    "demo_mp4",
    "screen_png",
    "summary_md",
    "artifact_dir",
    "evidence_dir",
    "raw_runs_csv",
    "logcat",
    "log_fast_provisional_count",
    "log_risk_gate_count",
    "log_hide_browser_scroll_count",
    "log_translate_mask_overlay_count",
]

SLIM_OUTPUT_FIELDS = [
    "sample_key",
    "method_order",
    "method_id",
    "method_label",
    "method_row_index",
    "evidence_batch_id",
    "run_id",
    "valid_latency_row",
    "scenario_id",
    "repeat_index",
    "query",
    "category",
    "expected",
    "video_recorded",
    "mode",
    "first_mask_ms",
    "first_mask_source",
    "fast_provisional_mask_ms",
    "fast_provisional_event_age_ms",
    "fast_provisional_receive_to_mask_ms",
    "risk_gate_mask_ms",
    "risk_gate_event_age_ms",
    "risk_gate_receive_to_mask_ms",
    "collect_ms",
    "node_collection_ms",
    "backend_api_ms",
    "display_ms",
    "observed_total_ms",
    "screen_candidates",
    "overlay_rendered",
    "demo_mp4",
    "screen_png",
    "summary_md",
]


@dataclass(frozen=True)
class MethodInfo:
    order: str
    method_id: str
    label: str
    notes: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--evidence-root",
        type=Path,
        required=True,
        help="Root containing desktop-android-google-* evidence directories.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="Combined CSV output path.",
    )
    parser.add_argument(
        "--method-map",
        type=Path,
        default=DEFAULT_METHOD_MAP,
        help="TSV with run_id_prefix, method_order, method_id, method_label, method_notes.",
    )
    parser.add_argument(
        "--include-invalid",
        action="store_true",
        help="Deprecated compatibility flag. Invalid rows are included by default.",
    )
    parser.add_argument(
        "--valid-only",
        action="store_true",
        help="Only include rows that have at least one numeric latency value.",
    )
    parser.add_argument(
        "--slim-output",
        type=Path,
        default=None,
        help="Optional analyst-friendly CSV output path with only key comparison columns.",
    )
    parser.add_argument(
        "--no-slim-output",
        action="store_true",
        help="Do not write the default sibling *-slim.csv output.",
    )
    return parser.parse_args()


def read_method_map(path: Path) -> dict[str, MethodInfo]:
    if not path.exists():
        return {}

    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        mapping: dict[str, MethodInfo] = {}
        for row in reader:
            prefix = (row.get("run_id_prefix") or "").strip()
            if not prefix:
                continue
            mapping[prefix] = MethodInfo(
                order=(row.get("method_order") or "").strip(),
                method_id=(row.get("method_id") or prefix).strip(),
                label=(row.get("method_label") or prefix).strip(),
                notes=(row.get("method_notes") or "").strip(),
            )
        return mapping


def method_for_run(run_id: str, mapping: dict[str, MethodInfo]) -> MethodInfo:
    for prefix, info in sorted(mapping.items(), key=lambda item: len(item[0]), reverse=True):
        if run_id.startswith(prefix):
            return info

    return MethodInfo(
        order="",
        method_id=infer_method_id(run_id),
        label=infer_method_id(run_id),
        notes="method inferred from run id; update method map for a human label",
    )


def infer_method_id(run_id: str) -> str:
    value = run_id.strip()
    if not value:
        return "unknown"
    value = re.sub(r"[^A-Za-z0-9]+", "_", value).strip("_").lower()
    return value or "unknown"


def read_csv_rows(path: Path) -> list[dict[str, str]]:
    try:
        if not path.exists():
            return []
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            return list(csv.DictReader(handle))
    except OSError:
        return []


def read_scenes(path: Path) -> dict[str, dict[str, str]]:
    scenes: dict[str, dict[str, str]] = {}
    for row in read_csv_rows(path):
        run_id = row.get("run_id", "").strip()
        if run_id:
            scenes[run_id] = row
    return scenes


def read_scene_index(evidence_dir: Path) -> dict[str, dict[str, str]]:
    scenes: dict[str, dict[str, str]] = {}
    candidates = [
        evidence_dir / "artifact-index.csv",
        evidence_dir / "google-mobile-scenes.csv",
        evidence_dir / "results" / "google-mobile" / "google-mobile-scenes.csv",
        evidence_dir / "results" / "google-mobile-scenes.csv",
    ]
    for path in candidates:
        for run_id, row in read_scenes(path).items():
            current = scenes.setdefault(run_id, {})
            for key, value in row.items():
                if value not in (None, ""):
                    current[key] = value
    return scenes


def is_numeric(value: str | None) -> bool:
    if value is None:
        return False
    return bool(re.fullmatch(r"-?\d+(?:\.\d+)?", value.strip()))


def numeric_or_blank(row: dict[str, str], field: str) -> str:
    value = (row.get(field) or "").strip()
    return value if is_numeric(value) and not value.startswith("-") else ""


def valid_latency_row(row: dict[str, str]) -> bool:
    return any(numeric_or_blank(row, field) for field in RAW_NUMERIC_FIELDS)


def best_first_mask(row: dict[str, str]) -> tuple[str, str]:
    # For reporting, "first mask" should preserve the runtime's first visible
    # provisional marker when it exists. Do not choose observed_total just
    # because it is numerically smaller; that value is a pipeline total proxy.
    for source, value in [
        ("fast_provisional", numeric_or_blank(row, "fast_provisional_mask_ms")),
        ("risk_gate", numeric_or_blank(row, "risk_gate_mask_ms")),
        ("observed_total", numeric_or_blank(row, "observed_total_ms")),
    ]:
        if value:
            return value, source
    return "", ""


def count_matches(path: Path, pattern: str) -> int:
    if not path.exists():
        return 0
    regex = re.compile(pattern)
    count = 0
    with path.open("r", encoding="utf-8", errors="ignore") as handle:
        for line in handle:
            if regex.search(line):
                count += 1
    return count


def find_artifact_dir(evidence_dir: Path, row: dict[str, str]) -> Path | None:
    artifact = (row.get("artifact_dir") or "").strip()
    if artifact:
        suffix = Path(artifact).name
        candidate = evidence_dir / "artifacts" / suffix
        if candidate.exists():
            return candidate

    artifacts = sorted((evidence_dir / "artifacts").glob("*"))
    return artifacts[0] if artifacts else None


def evidence_dirs(root: Path) -> Iterable[Path]:
    return sorted(
        (path for path in root.glob("desktop-android-google-*") if path.is_dir()),
        key=lambda path: path.name,
    )


def first_existing(paths: Iterable[Path]) -> Path | None:
    for path in paths:
        try:
            if path.exists():
                return path
        except OSError:
            continue
    return None


def aggregate(evidence_root: Path, method_map: dict[str, MethodInfo], include_invalid: bool) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    method_counts: dict[str, int] = {}

    for evidence_dir in evidence_dirs(evidence_root):
        batch_id = evidence_dir.name
        raw_csv = first_existing(
            [
                evidence_dir / "results" / "google-mobile" / "raw_runs.csv",
                evidence_dir / "google-mobile" / "raw_runs.csv",
                evidence_dir / "raw_runs.csv",
            ]
        )
        if raw_csv is None:
            continue
        raw_rows = read_csv_rows(raw_csv)
        scenes = read_scene_index(evidence_dir)

        for index, raw in enumerate(raw_rows, start=1):
            run_id = (raw.get("run_id") or batch_id).strip()
            method = method_for_run(batch_id, method_map)
            scene = scenes.get(run_id, {})
            valid = valid_latency_row(raw)
            if not valid and not include_invalid:
                continue

            artifact_dir = find_artifact_dir(evidence_dir, raw)
            logcat = artifact_dir / "logs" / "mask-logcat.txt" if artifact_dir else None
            first_mask_ms, first_mask_source = best_first_mask(raw)

            out = {
                "sample_key": "",
                "method_row_index": "",
                "method_order": method.order,
                "method_id": (scene.get("method_id") or method.method_id).strip(),
                "method_label": (scene.get("method_label") or method.label).strip(),
                "method_notes": method.notes,
                "evidence_batch_id": batch_id,
                "run_id": run_id,
                "row_index": str(index),
                "valid_latency_row": "1" if valid else "0",
                "mode": raw.get("mode", ""),
                "stages": raw.get("stages", ""),
                "scenario": raw.get("scenario", ""),
                "scenario_id": scene.get("scenario_id", ""),
                "repeat_index": scene.get("repeat_index", ""),
                "query": scene.get("query", ""),
                "category": scene.get("category", ""),
                "expected": scene.get("expected", ""),
                "video_recorded": scene.get("video_recorded", ""),
                "device": raw.get("device", ""),
                "first_mask_ms": first_mask_ms,
                "first_mask_source": first_mask_source,
                "demo_mp4": scene.get("demo_mp4", ""),
                "screen_png": scene.get("screen_png", ""),
                "summary_md": scene.get("summary_md", ""),
                "artifact_dir": raw.get("artifact_dir", ""),
                "evidence_dir": str(evidence_dir),
                "raw_runs_csv": str(raw_csv),
                "logcat": str(logcat) if logcat else "",
                "log_fast_provisional_count": str(count_matches(logcat, r"fast provisional mask") if logcat else 0),
                "log_risk_gate_count": str(count_matches(logcat, r"risk gate mask") if logcat else 0),
                "log_hide_browser_scroll_count": str(
                    count_matches(logcat, r"hide browser mask overlay during scroll") if logcat else 0
                ),
                "log_translate_mask_overlay_count": str(count_matches(logcat, r"translate mask overlay") if logcat else 0),
            }
            for field in RAW_NUMERIC_FIELDS:
                out[field] = numeric_or_blank(raw, field)

            method_id = out["method_id"] or "unknown"
            method_counts[method_id] = method_counts.get(method_id, 0) + 1
            out["method_row_index"] = str(method_counts[method_id])
            scenario_key = out["scenario_id"] or out["scenario"] or "scenario"
            repeat_key = out["repeat_index"] or out["row_index"]
            out["sample_key"] = "__".join(
                [
                    method_id,
                    scenario_key,
                    f"r{repeat_key}",
                    out["evidence_batch_id"],
                    out["row_index"],
                ]
            )
            rows.append(out)

    return rows


def write_rows(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def default_slim_output(path: Path) -> Path:
    return path.with_name(f"{path.stem}-slim{path.suffix}")


def write_slim_rows(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=SLIM_OUTPUT_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in SLIM_OUTPUT_FIELDS})


def main() -> int:
    args = parse_args()
    method_map = read_method_map(args.method_map)
    rows = aggregate(args.evidence_root, method_map, include_invalid=not args.valid_only)
    write_rows(args.output, rows)
    print(f"output={args.output}")
    print(f"rows={len(rows)}")
    if not args.no_slim_output:
        slim_output = args.slim_output or default_slim_output(args.output)
        write_slim_rows(slim_output, rows)
        print(f"slim_output={slim_output}")
        print(f"slim_columns={len(SLIM_OUTPUT_FIELDS)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
