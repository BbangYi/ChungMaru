#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


DEFAULT_RESULTS_DIR = Path("evaluation/media-safety/results/current")
DEFAULT_SITE_SEED_FILE = Path("backend/data/site_intel_seed_massive.json")
LATENCY_SUMMARY_NAME = "media-safety-latency-summary.csv"
STAGE_LATENCY_NAME = "media-safety-stage-latency.csv"
COVERAGE_AUDIT_NAME = "media-safety-coverage-audit.csv"
REPORT_JSON_NAME = "media-safety-report.json"
REPORT_MD_NAME = "media-safety-report.md"

INPUT_FILENAMES = {
    "media-safety-smoke.csv",
    "media-safety-live-smoke.csv",
}

STAGE_SPECS = [
    {
        "stage_id": "01_collect",
        "stage_name": "DOM 후보 수집",
        "field": "collect_ms",
        "budget_p95_ms": 50,
        "meaning": "visible media/image/video 후보를 DOM에서 수집하는 시간",
    },
    {
        "stage_id": "02_cheap_filter",
        "stage_name": "cheap filter",
        "field": "cheap_filter_ms",
        "budget_p95_ms": 120,
        "meaning": "alt/title/aria/link/card text/url/domain 신호 판정 시간",
    },
    {
        "stage_id": "03_apply",
        "stage_name": "hide/remove 적용",
        "field": "apply_ms",
        "budget_p95_ms": 80,
        "meaning": "class/data attribute, placeholder, compact group 적용 시간",
    },
    {
        "stage_id": "04_dom_to_action",
        "stage_name": "DOM 추가 후 action",
        "field": "dom_added_to_action_ms",
        "budget_p95_ms": 120,
        "meaning": "후보가 화면/DOM에 들어온 뒤 실제 숨김 처리까지 걸린 시간",
    },
    {
        "stage_id": "05_late_load_decision",
        "stage_name": "late-load decision",
        "field": "late_decision_ms",
        "budget_p95_ms": 120,
        "meaning": "이미지/영상이 늦게 삽입된 뒤 숨김 처리까지 걸린 시간",
    },
    {
        "stage_id": "06_image_fetch_future",
        "stage_name": "image fetch",
        "field": "image_fetch_ms",
        "budget_p95_ms": 80,
        "meaning": "향후 classifier/OCR 후보 이미지 fetch 시간",
    },
    {
        "stage_id": "07_bitmap_decode_future",
        "stage_name": "bitmap decode",
        "field": "bitmap_decode_ms",
        "budget_p95_ms": 80,
        "meaning": "향후 classifier/OCR 입력 bitmap decode 시간",
    },
    {
        "stage_id": "08_classifier_future",
        "stage_name": "NSFW/banner classifier",
        "field": "classifier_ms",
        "budget_p95_ms": 120,
        "meaning": "향후 이미지 classifier 추론 시간",
    },
    {
        "stage_id": "09_ocr_future",
        "stage_name": "image OCR",
        "field": "ocr_ms",
        "budget_p95_ms": 200,
        "meaning": "향후 이미지 내 유해 단어 OCR 시간",
    },
]

SUMMARY_FIELDNAMES = [
    "dataset",
    "source_file",
    "case_group",
    "scenario",
    "seed_domain",
    "seed_category",
    "seed_risk_level",
    "media_safety_enabled",
    "developer_log_enabled",
    "startup_gate_enabled",
    "run_count",
    "enabled_run_count",
    "loaded_count",
    "ok_count",
    "error_count",
    "invalid_page_count",
    "visual_candidate_run_count",
    "action_run_count",
    "action_count_sum",
    "action_count_median",
    "action_count_max",
    "candidate_count_max",
    "visible_tile_count_max",
    "cheap_filter_hit_count_max",
    "candidate_sized_visible_media_element_count_max",
    "remaining_visible_tile_count_max",
    "missed_visible_tile_count_max",
    "false_hidden_count_max",
    "hidden_count_max",
    "compact_summary_count_max",
    "viewport_coverage_pct_max",
    "runtime_log_count_max",
    "media_runtime_log_count_max",
    "visual_artifact_count",
    "visual_artifact_paths",
    "collect_ms_p50",
    "collect_ms_p95",
    "collect_ms_max",
    "cheap_filter_ms_p50",
    "cheap_filter_ms_p95",
    "cheap_filter_ms_max",
    "apply_ms_p50",
    "apply_ms_p95",
    "apply_ms_max",
    "dom_added_to_action_ms_p50",
    "dom_added_to_action_ms_p95",
    "dom_added_to_action_ms_max",
    "late_decision_ms_p50",
    "late_decision_ms_p95",
    "late_decision_ms_max",
    "measured_stage_count",
    "report_status",
    "evidence_tier",
    "coverage_warning",
    "report_note",
]

STAGE_FIELDNAMES = [
    "dataset",
    "source_file",
    "case_group",
    "scenario",
    "seed_domain",
    "seed_category",
    "seed_risk_level",
    "media_safety_enabled",
    "developer_log_enabled",
    "startup_gate_enabled",
    "stage_id",
    "stage_name",
    "field",
    "unit",
    "meaning",
    "run_count",
    "measured_run_count",
    "p50_ms",
    "p95_ms",
    "max_ms",
    "budget_p95_ms",
    "budget_status",
    "report_status",
]

COVERAGE_FIELDNAMES = [
    "scope",
    "category",
    "seed_block_domain_count",
    "live_smoke_domain_count",
    "live_harmful_visual_evidence_domain_count",
    "controlled_harmful_regression_count",
    "live_benign_negative_domain_count",
    "coverage_ratio_pct",
    "readiness_status",
    "report_note",
]

HARMFUL_VISUAL_CATEGORIES = {"adult", "gambling"}
BROAD_SMOKE_MIN_HARMFUL_DOMAINS_PER_CATEGORY = 10
BROAD_SMOKE_MIN_TOTAL_HARMFUL_DOMAINS = 25
BROAD_SMOKE_MIN_BENIGN_DOMAINS = 30


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def read_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    normalized = str(value or "").strip().lower()
    return normalized in {"1", "true", "yes", "y", "on"}


def read_int(value: Any) -> int:
    try:
        return max(0, int(float(str(value or "0").strip() or "0")))
    except (TypeError, ValueError):
        return 0


def read_float(value: Any) -> float:
    try:
        return max(0.0, float(str(value or "0").strip() or "0"))
    except (TypeError, ValueError):
        return 0.0


def clean_cell(value: Any, max_length: int = 220) -> str:
    if value is None:
        return ""
    normalized = " ".join(str(value).split())
    return normalized[:max_length]


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    sorted_values = sorted(values)
    index = max(
        0,
        min(len(sorted_values) - 1, math.ceil((pct / 100.0) * len(sorted_values)) - 1),
    )
    return sorted_values[index]


def metric(values: list[float], name: str) -> str:
    if not values:
        return ""
    if name == "p50":
        value = statistics.median(values)
    elif name == "p95":
        value = percentile(values, 95)
    elif name == "max":
        value = max(values)
    else:
        raise ValueError(f"unknown metric: {name}")
    if float(value).is_integer():
        return str(int(value))
    return f"{value:.1f}".rstrip("0").rstrip(".")


def host_from_row(row: dict[str, Any]) -> str:
    seed_domain = clean_cell(row.get("seed_domain"), 120)
    if seed_domain:
        return seed_domain
    for key in ("final_url_origin", "url_origin"):
        value = clean_cell(row.get(key), 160)
        if not value:
            continue
        parsed = urlparse(value)
        if parsed.netloc:
            return parsed.netloc
        return value.replace("https://", "").replace("http://", "").strip("/")
    return clean_cell(row.get("case_id"), 120) or "unknown"


def infer_category(row: dict[str, Any]) -> str:
    category = clean_cell(row.get("seed_category"), 80)
    if category:
        return category
    scenario = clean_cell(row.get("scenario"), 80)
    if scenario:
        return scenario
    return "unknown"


def infer_risk_level(row: dict[str, Any]) -> str:
    risk_level = clean_cell(row.get("seed_risk_level"), 80)
    if risk_level:
        return risk_level
    case_id = clean_cell(row.get("case_id"), 160).lower()
    scenario = clean_cell(row.get("scenario"), 80).lower()
    if not read_bool(row.get("media_safety_enabled")):
        return "disabled-control"
    if "clean" in case_id or scenario == "clean":
        return "allow"
    if any(item in case_id for item in ("harmful", "address-guide", "late-load")):
        return "block"
    if scenario in {"harmful", "address-guide-video", "late-load"}:
        return "block"
    return "unknown"


def dataset_for_path(path: Path, results_dir: Path) -> str:
    relative = path.relative_to(results_dir)
    if relative.parent == Path("."):
        return "root"
    return relative.parent.as_posix()


def source_file_for_path(path: Path, results_dir: Path) -> str:
    return path.relative_to(results_dir).as_posix()


def find_input_csvs(results_dir: Path) -> list[Path]:
    if not results_dir.exists():
        return []
    return sorted(
        path
        for path in results_dir.rglob("*.csv")
        if path.name in INPUT_FILENAMES
    )


def read_input_rows(results_dir: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for path in find_input_csvs(results_dir):
        dataset = dataset_for_path(path, results_dir)
        source_file = source_file_for_path(path, results_dir)
        with path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            for index, row in enumerate(reader, start=1):
                normalized = dict(row)
                normalized["_dataset"] = dataset
                normalized["_source_file"] = source_file
                normalized["_row_index"] = str(index)
                rows.append(normalized)
    return rows


def read_seed_rows(seed_file: Path) -> list[dict[str, Any]]:
    if not seed_file.exists():
        return []
    try:
        payload = json.loads(seed_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return []
    if not isinstance(payload, list):
        return []
    return [item for item in payload if isinstance(item, dict)]


def group_key(row: dict[str, Any]) -> tuple[str, ...]:
    scenario = clean_cell(row.get("scenario"), 80) or "unknown"
    case_group = host_from_row(row) if scenario == "live" else clean_cell(row.get("case_id"), 120)
    return (
        clean_cell(row.get("_dataset"), 120) or "root",
        clean_cell(row.get("_source_file"), 220),
        case_group or "unknown",
        scenario,
        host_from_row(row),
        infer_category(row),
        infer_risk_level(row),
        str(read_bool(row.get("media_safety_enabled"))),
        str(read_bool(row.get("developer_log_enabled"))),
        str(read_bool(row.get("startup_gate_enabled"))),
    )


def values_for(rows: list[dict[str, Any]], field: str, *, successful_only: bool = True) -> list[float]:
    values: list[float] = []
    for row in rows:
        if successful_only and not (read_bool(row.get("scan_ok")) and read_bool(row.get("live_page_ok", True))):
            continue
        if field not in row:
            continue
        raw = str(row.get(field) or "").strip()
        if raw == "":
            continue
        values.append(read_float(raw))
    return values


def max_int(rows: list[dict[str, Any]], field: str) -> int:
    return max((read_int(row.get(field)) for row in rows), default=0)


def max_float(rows: list[dict[str, Any]], field: str) -> float:
    return max((read_float(row.get(field)) for row in rows), default=0.0)


def count_visual_artifacts(rows: list[dict[str, Any]]) -> tuple[int, str]:
    paths = [
        clean_cell(row.get("visual_artifact_path"), 220)
        for row in rows
        if clean_cell(row.get("visual_artifact_path"), 220)
    ]
    unique_paths = list(dict.fromkeys(paths))
    return len(unique_paths), "; ".join(unique_paths[:4])


def is_block_like(category: str, risk_level: str, scenario: str) -> bool:
    category_norm = category.lower()
    risk_norm = risk_level.lower()
    scenario_norm = scenario.lower()
    return (
        risk_norm == "block"
        or category_norm in {"adult", "gambling", "harmful", "address-guide-video", "late-load"}
        or scenario_norm in {"harmful", "address-guide-video", "late-load"}
    )


def classify_summary(summary: dict[str, Any]) -> tuple[str, str]:
    risk_level = str(summary["seed_risk_level"]).lower()
    category = str(summary["seed_category"]).lower()
    scenario = str(summary["scenario"]).lower()
    enabled_count = int(summary["enabled_run_count"])
    loaded_count = int(summary["loaded_count"])
    ok_count = int(summary["ok_count"])
    action_count_sum = int(summary["action_count_sum"])
    action_count_max = int(summary["action_count_max"])
    missed_max = int(summary["missed_visible_tile_count_max"])
    false_hidden_max = int(summary["false_hidden_count_max"])
    visual_candidate_run_count = int(summary["visual_candidate_run_count"])
    candidate_sized_max = int(summary["candidate_sized_visible_media_element_count_max"])

    if enabled_count == 0:
        return "disabled_control", "media safety off control; 차단 동작이 없어야 하는 기준 row"
    if loaded_count == 0:
        return "invalid_or_unloaded", "페이지 로드 실패 또는 비 HTTP/chrome-error 페이지라 품질 evidence에서 제외"
    if ok_count == 0:
        return "scan_failed", "scan_ok가 없어 원인 로그 확인 필요"
    if risk_level == "allow" or category in {"benign", "benign-thumbnail"} or scenario == "clean":
        if action_count_max == 0 and false_hidden_max == 0:
            return "benign_negative", "정상/인접 이미지 후보를 수집했지만 숨김 0으로 오탐 억제 evidence"
        return "false_positive_review", "allow/benign row에서 숨김 또는 falseHidden 발생"
    if is_block_like(category, risk_level, scenario):
        if action_count_sum > 0 and missed_max == 0:
            return "strong_visual_block", "유해 visual 후보가 빠르게 숨김/삭제되어 발표 evidence로 승격 가능"
        if action_count_sum > 0:
            return "partial_visual_block", "일부 유해 visual을 처리했지만 remaining/missed 지표 검토 필요"
        if visual_candidate_run_count > 0 or candidate_sized_max > 0:
            return "missed_or_policy_gap", "가시 visual 후보가 있었지만 action이 없어 rule/evidence 보강 필요"
        return "no_visual_candidate", "live page에서 보고서용 visual 후보를 확인하지 못함"
    return "needs_review", "risk label이 불명확해 수동 해석 필요"


def classify_evidence_tier(summary: dict[str, Any]) -> tuple[str, str]:
    status = str(summary.get("report_status") or "")
    scenario = str(summary.get("scenario") or "")
    source_file = str(summary.get("source_file") or "")
    visual_artifact_count = int(summary.get("visual_artifact_count") or 0)
    action_count_max = int(summary.get("action_count_max") or 0)

    if status == "disabled_control":
        return "control_row", ""
    if scenario == "live" and status == "strong_visual_block" and visual_artifact_count > 0:
        return "live_harmful_visual_evidence", ""
    if scenario == "live" and status == "benign_negative" and visual_artifact_count > 0:
        return "live_benign_negative_evidence", ""
    if scenario == "live" and status == "strong_visual_block":
        return "live_harmful_without_screenshot", "live harmful row has action but no screenshot artifact"
    if scenario == "live" and status == "benign_negative":
        return "live_benign_without_screenshot", "live benign row has no screenshot artifact"
    if source_file.endswith("media-safety-smoke.csv"):
        if action_count_max > 0:
            return "controlled_harmful_regression", ""
        return "controlled_negative_or_control", ""
    return "review_or_gap", "not enough visual/report evidence for promotion"


def unique_live_domains(
    rows: list[dict[str, Any]],
    *,
    category: str | None = None,
    tier: str | None = None,
) -> set[str]:
    domains: set[str] = set()
    for row in rows:
        if str(row.get("scenario") or "") != "live":
            continue
        if category is not None and str(row.get("seed_category") or "") != category:
            continue
        if tier is not None and str(row.get("evidence_tier") or "") != tier:
            continue
        domain = str(row.get("seed_domain") or row.get("case_group") or "").strip().lower()
        if domain:
            domains.add(domain)
    return domains


def seed_block_domain_count(seed_rows: list[dict[str, Any]], category: str) -> int:
    domains = {
        str(row.get("domain") or "").strip().lower()
        for row in seed_rows
        if str(row.get("category") or "").strip().lower() == category
        and str(row.get("risk_level") or "").strip().lower() == "block"
        and str(row.get("domain") or "").strip()
    }
    return len(domains)


def coverage_status_for_category(category: str, evidence_count: int, seed_count: int) -> tuple[str, str]:
    if category not in HARMFUL_VISUAL_CATEGORIES:
        return (
            "not_media_safety_scope",
            "이미지 차단 품질보다 유해사이트 차단/접속 정책 검증에 가까운 카테고리다.",
        )
    if evidence_count == 0:
        return (
            "no_live_visual_evidence",
            "해당 카테고리 live screenshot evidence가 아직 없다. 완성도 주장 금지.",
        )
    if evidence_count < BROAD_SMOKE_MIN_HARMFUL_DOMAINS_PER_CATEGORY:
        return (
            "mechanism_proof_only",
            f"동작 예시는 있으나 {BROAD_SMOKE_MIN_HARMFUL_DOMAINS_PER_CATEGORY}개 도메인 미만이라 coverage proof가 아니다.",
        )
    ratio = (evidence_count / seed_count * 100.0) if seed_count else 0.0
    return (
        "limited_broad_smoke",
        f"카테고리별 최소 도메인 수는 넘었지만 seed 대비 coverage는 {ratio:.1f}%라 추가 확장이 필요하다.",
    )


def build_coverage_audit_rows(
    summary_rows: list[dict[str, Any]],
    seed_rows: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    seed_categories = {
        str(row.get("category") or "").strip().lower()
        for row in seed_rows
        if str(row.get("risk_level") or "").strip().lower() == "block"
    }
    categories = sorted(HARMFUL_VISUAL_CATEGORIES | {"phishing", "malware"} | seed_categories)
    live_benign_domains = unique_live_domains(summary_rows, tier="live_benign_negative_evidence")
    rows: list[dict[str, Any]] = []
    total_seed = 0
    total_live_smoke = 0
    total_visual_evidence = 0
    total_controlled = 0

    for category in categories:
        seed_count = seed_block_domain_count(seed_rows, category)
        live_domains = unique_live_domains(summary_rows, category=category)
        evidence_domains = unique_live_domains(
            summary_rows,
            category=category,
            tier="live_harmful_visual_evidence",
        )
        controlled_count = sum(
            1 for row in summary_rows
            if str(row.get("seed_category") or "") == category
            and str(row.get("evidence_tier") or "") == "controlled_harmful_regression"
        )
        status, note = coverage_status_for_category(category, len(evidence_domains), seed_count)
        ratio = (len(evidence_domains) / seed_count * 100.0) if seed_count else 0.0
        rows.append({
            "scope": "category",
            "category": category,
            "seed_block_domain_count": seed_count,
            "live_smoke_domain_count": len(live_domains),
            "live_harmful_visual_evidence_domain_count": len(evidence_domains),
            "controlled_harmful_regression_count": controlled_count,
            "live_benign_negative_domain_count": "",
            "coverage_ratio_pct": f"{ratio:.1f}",
            "readiness_status": status,
            "report_note": note,
        })
        if category in HARMFUL_VISUAL_CATEGORIES:
            total_seed += seed_count
            total_live_smoke += len(live_domains)
            total_visual_evidence += len(evidence_domains)
            total_controlled += controlled_count

    total_ratio = (total_visual_evidence / total_seed * 100.0) if total_seed else 0.0
    if total_visual_evidence == 0:
        total_status = "no_live_visual_evidence"
        total_note = "adult/gambling live visual evidence가 없다."
    elif (
        total_visual_evidence < BROAD_SMOKE_MIN_TOTAL_HARMFUL_DOMAINS
        or len(live_benign_domains) < BROAD_SMOKE_MIN_BENIGN_DOMAINS
    ):
        total_status = "mechanism_proof_only"
        total_note = (
            f"현재 유해 visual live evidence {total_visual_evidence}개, benign negative {len(live_benign_domains)}개다. "
            f"최소 목표는 유해 {BROAD_SMOKE_MIN_TOTAL_HARMFUL_DOMAINS}개 이상, benign {BROAD_SMOKE_MIN_BENIGN_DOMAINS}개 이상이다."
        )
    else:
        total_status = "broad_smoke_candidate"
        total_note = "최소 broad smoke 수량은 충족했지만 화면 녹화와 미탐/오탐 검토가 추가로 필요하다."

    rows.insert(0, {
        "scope": "harmful_visual_total",
        "category": "adult+gambling",
        "seed_block_domain_count": total_seed,
        "live_smoke_domain_count": total_live_smoke,
        "live_harmful_visual_evidence_domain_count": total_visual_evidence,
        "controlled_harmful_regression_count": total_controlled,
        "live_benign_negative_domain_count": len(live_benign_domains),
        "coverage_ratio_pct": f"{total_ratio:.1f}",
        "readiness_status": total_status,
        "report_note": total_note,
    })
    return rows


def build_summary_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, ...], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[group_key(row)].append(row)

    summaries: list[dict[str, Any]] = []
    for key, group_rows in sorted(grouped.items()):
        (
            dataset,
            source_file,
            case_group,
            scenario,
            seed_domain,
            seed_category,
            seed_risk_level,
            media_enabled,
            developer_log_enabled,
            startup_gate_enabled,
        ) = key
        action_values = [read_int(row.get("action_count")) for row in group_rows]
        artifact_count, artifact_paths = count_visual_artifacts(group_rows)
        summary: dict[str, Any] = {
            "dataset": dataset,
            "source_file": source_file,
            "case_group": case_group,
            "scenario": scenario,
            "seed_domain": seed_domain,
            "seed_category": seed_category,
            "seed_risk_level": seed_risk_level,
            "media_safety_enabled": media_enabled,
            "developer_log_enabled": developer_log_enabled,
            "startup_gate_enabled": startup_gate_enabled,
            "run_count": len(group_rows),
            "enabled_run_count": sum(1 for row in group_rows if read_bool(row.get("media_safety_enabled"))),
            "loaded_count": sum(1 for row in group_rows if read_bool(row.get("live_page_ok", True))),
            "ok_count": sum(1 for row in group_rows if read_bool(row.get("scan_ok"))),
            "error_count": sum(1 for row in group_rows if clean_cell(row.get("error_code"))),
            "invalid_page_count": sum(1 for row in group_rows if clean_cell(row.get("live_page_status")) in {"chrome-error", "invalid_page"}),
            "visual_candidate_run_count": sum(
                1 for row in group_rows
                if read_int(row.get("candidate_sized_visible_media_element_count")) > 0
                or read_int(row.get("visible_tile_count")) > 0
                or read_int(row.get("candidate_count")) > 0
                or read_int(row.get("action_count")) > 0
            ),
            "action_run_count": sum(1 for value in action_values if value > 0),
            "action_count_sum": sum(action_values),
            "action_count_median": metric([float(value) for value in action_values], "p50"),
            "action_count_max": max(action_values, default=0),
            "candidate_count_max": max_int(group_rows, "candidate_count"),
            "visible_tile_count_max": max_int(group_rows, "visible_tile_count"),
            "cheap_filter_hit_count_max": max_int(group_rows, "cheap_filter_hit_count"),
            "candidate_sized_visible_media_element_count_max": max_int(
                group_rows,
                "candidate_sized_visible_media_element_count",
            ),
            "remaining_visible_tile_count_max": max_int(group_rows, "remaining_visible_tile_count"),
            "missed_visible_tile_count_max": max_int(group_rows, "missed_visible_tile_count"),
            "false_hidden_count_max": max_int(group_rows, "false_hidden_count"),
            "hidden_count_max": max_int(group_rows, "hidden_count"),
            "compact_summary_count_max": max_int(group_rows, "compact_summary_count"),
            "viewport_coverage_pct_max": metric([max_float(group_rows, "viewport_coverage_pct")], "max"),
            "runtime_log_count_max": max_int(group_rows, "runtime_log_count"),
            "media_runtime_log_count_max": max_int(group_rows, "media_runtime_log_count"),
            "visual_artifact_count": artifact_count,
            "visual_artifact_paths": artifact_paths,
        }
        measured_stage_count = 0
        for spec in STAGE_SPECS:
            values = values_for(group_rows, str(spec["field"]))
            if values:
                measured_stage_count += 1
            field = str(spec["field"])
            summary[f"{field}_p50"] = metric(values, "p50")
            summary[f"{field}_p95"] = metric(values, "p95")
            summary[f"{field}_max"] = metric(values, "max")
        summary["measured_stage_count"] = measured_stage_count
        report_status, report_note = classify_summary(summary)
        summary["report_status"] = report_status
        evidence_tier, coverage_warning = classify_evidence_tier(summary)
        summary["evidence_tier"] = evidence_tier
        summary["coverage_warning"] = coverage_warning
        summary["report_note"] = report_note
        summaries.append({field: summary.get(field, "") for field in SUMMARY_FIELDNAMES})
    return summaries


def budget_status(values: list[float], budget_p95_ms: int) -> str:
    if not values:
        return "not_instrumented"
    p95 = percentile(values, 95)
    if p95 <= budget_p95_ms:
        return "within_budget"
    return "over_budget"


def build_stage_rows(
    input_rows: list[dict[str, Any]],
    summary_rows: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, ...], list[dict[str, Any]]] = defaultdict(list)
    for row in input_rows:
        grouped[group_key(row)].append(row)

    summary_by_key = {
        (
            str(row["dataset"]),
            str(row["source_file"]),
            str(row["case_group"]),
            str(row["scenario"]),
            str(row["seed_domain"]),
            str(row["seed_category"]),
            str(row["seed_risk_level"]),
            str(row["media_safety_enabled"]),
            str(row["developer_log_enabled"]),
            str(row["startup_gate_enabled"]),
        ): row
        for row in summary_rows
    }
    stage_rows: list[dict[str, Any]] = []
    for key, group_rows in sorted(grouped.items()):
        summary = summary_by_key.get(key, {})
        (
            dataset,
            source_file,
            case_group,
            scenario,
            seed_domain,
            seed_category,
            seed_risk_level,
            media_enabled,
            developer_log_enabled,
            startup_gate_enabled,
        ) = key
        for spec in STAGE_SPECS:
            values = values_for(group_rows, str(spec["field"]))
            stage_rows.append({
                "dataset": dataset,
                "source_file": source_file,
                "case_group": case_group,
                "scenario": scenario,
                "seed_domain": seed_domain,
                "seed_category": seed_category,
                "seed_risk_level": seed_risk_level,
                "media_safety_enabled": media_enabled,
                "developer_log_enabled": developer_log_enabled,
                "startup_gate_enabled": startup_gate_enabled,
                "stage_id": spec["stage_id"],
                "stage_name": spec["stage_name"],
                "field": spec["field"],
                "unit": "ms",
                "meaning": spec["meaning"],
                "run_count": len(group_rows),
                "measured_run_count": len(values),
                "p50_ms": metric(values, "p50"),
                "p95_ms": metric(values, "p95"),
                "max_ms": metric(values, "max"),
                "budget_p95_ms": spec["budget_p95_ms"],
                "budget_status": budget_status(values, int(spec["budget_p95_ms"])),
                "report_status": summary.get("report_status", ""),
            })
    return stage_rows


def write_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def global_stage_summary(input_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for spec in STAGE_SPECS:
        values = values_for(input_rows, str(spec["field"]))
        rows.append({
            "stage_id": spec["stage_id"],
            "stage_name": spec["stage_name"],
            "field": spec["field"],
            "measured_run_count": len(values),
            "p50_ms": metric(values, "p50"),
            "p95_ms": metric(values, "p95"),
            "max_ms": metric(values, "max"),
            "budget_p95_ms": spec["budget_p95_ms"],
            "budget_status": budget_status(values, int(spec["budget_p95_ms"])),
        })
    return rows


def overall_coverage_verdict(coverage_rows: list[dict[str, Any]]) -> str:
    for row in coverage_rows:
        if row.get("scope") == "harmful_visual_total":
            return str(row.get("readiness_status") or "unknown")
    return "unknown"


def markdown_table(rows: list[dict[str, Any]], columns: list[tuple[str, str]], *, empty: str = "_없음_") -> list[str]:
    if not rows:
        return [empty]
    lines = [
        "| " + " | ".join(header for _field, header in columns) + " |",
        "| " + " | ".join("---" for _field, _header in columns) + " |",
    ]
    for row in rows:
        cells = [clean_cell(row.get(field), 180).replace("|", "/") for field, _header in columns]
        lines.append("| " + " | ".join(cells) + " |")
    return lines


def build_markdown_report(
    *,
    results_dir: Path,
    input_files: list[Path],
    input_rows: list[dict[str, Any]],
    summary_rows: list[dict[str, Any]],
    stage_rows: list[dict[str, Any]],
    coverage_rows: list[dict[str, Any]],
    output_paths: dict[str, Path],
    max_rows: int,
) -> str:
    status_counts = Counter(str(row["report_status"]) for row in summary_rows)
    stage_summary = global_stage_summary(input_rows)
    live_evidence = [
        row for row in summary_rows
        if row["evidence_tier"] in {"live_harmful_visual_evidence", "live_benign_negative_evidence"}
    ][:max_rows]
    controlled = [
        row for row in summary_rows
        if row["evidence_tier"] in {"controlled_harmful_regression", "controlled_negative_or_control", "control_row"}
    ][:max_rows]
    warning_rows = [
        row for row in summary_rows
        if row.get("coverage_warning")
    ][:max_rows]
    review = [
        row for row in summary_rows
        if row["report_status"] not in {"strong_visual_block", "benign_negative", "disabled_control"}
        or row["evidence_tier"] == "review_or_gap"
    ][:max_rows]
    generated_at = now_iso()
    coverage_verdict = overall_coverage_verdict(coverage_rows)

    lines: list[str] = [
        "# Chungmaru Media Safety Report",
        "",
        f"- generated_at: `{generated_at}`",
        f"- results_dir: `{results_dir}`",
        f"- input_files: `{len(input_files)}`",
        f"- raw_rows: `{len(input_rows)}`",
        f"- summary_groups: `{len(summary_rows)}`",
        "",
        "## Report-Ready Summary",
        "",
        "이 파일은 smoke raw CSV를 보고서 작성용으로 줄인 산출물이다. 발표에는 screenshot이 있는 `live_harmful_visual_evidence`와 `live_benign_negative_evidence`를 우선 쓰고, controlled fixture는 regression evidence로 분리한다.",
        "",
        f"- coverage_verdict: `{coverage_verdict}`",
        "- 현재 수치는 기능 메커니즘과 속도 검증용이다. 다양한 실제 유해 사이트를 넓게 커버했다는 주장에는 아직 부족하다.",
        "",
    ]
    lines.extend(markdown_table(
        [
            {"status": status, "count": count}
            for status, count in sorted(status_counts.items())
        ],
        [("status", "status"), ("count", "count")],
    ))
    lines.extend([
        "",
        "## Coverage Audit",
        "",
        "seed 목록 대비 실제 live screenshot evidence가 얼마나 있는지 보는 표다. 이 표의 목적은 과장 방지다.",
        "",
    ])
    lines.extend(markdown_table(
        coverage_rows,
        [
            ("scope", "scope"),
            ("category", "category"),
            ("seed_block_domain_count", "seed block domains"),
            ("live_smoke_domain_count", "live smoke domains"),
            ("live_harmful_visual_evidence_domain_count", "live harmful evidence"),
            ("live_benign_negative_domain_count", "benign negatives"),
            ("coverage_ratio_pct", "coverage %"),
            ("readiness_status", "status"),
            ("report_note", "note"),
        ],
    ))
    lines.extend([
        "",
        "## Latency Budget Table",
        "",
        "현재 v1에서 실제 계측된 stage와 향후 classifier/OCR 계측 예정 stage를 한 표에 둔다. `not_instrumented`는 아직 기능을 붙이지 않았다는 뜻이지 통과가 아니다.",
        "",
    ])
    lines.extend(markdown_table(
        stage_summary,
        [
            ("stage_id", "stage"),
            ("stage_name", "name"),
            ("measured_run_count", "n"),
            ("p50_ms", "p50 ms"),
            ("p95_ms", "p95 ms"),
            ("max_ms", "max ms"),
            ("budget_p95_ms", "p95 budget"),
            ("budget_status", "status"),
        ],
    ))
    lines.extend([
        "",
        "## Live Screenshot Evidence",
        "",
    ])
    lines.extend(markdown_table(
        live_evidence,
        [
            ("evidence_tier", "tier"),
            ("report_status", "status"),
            ("case_group", "case/domain"),
            ("seed_category", "category"),
            ("seed_risk_level", "risk"),
            ("run_count", "runs"),
            ("action_run_count", "action runs"),
            ("action_count_max", "action max"),
            ("false_hidden_count_max", "false hidden max"),
            ("collect_ms_p95", "collect p95"),
            ("cheap_filter_ms_p95", "filter p95"),
            ("apply_ms_p95", "apply p95"),
            ("dom_added_to_action_ms_p95", "dom->action p95"),
            ("visual_artifact_count", "screens"),
        ],
    ))
    lines.extend([
        "",
        "## Controlled Regression Evidence",
        "",
        "controlled fixture는 실제 사이트 screenshot evidence가 아니라 기능 토글, 로그 on/off, late-load, clean negative 회귀 검증으로 해석한다.",
        "",
    ])
    lines.extend(markdown_table(
        controlled,
        [
            ("evidence_tier", "tier"),
            ("report_status", "status"),
            ("case_group", "case"),
            ("seed_category", "category"),
            ("seed_risk_level", "risk"),
            ("run_count", "runs"),
            ("action_run_count", "action runs"),
            ("action_count_max", "action max"),
            ("false_hidden_count_max", "false hidden max"),
            ("late_decision_ms_max", "late max"),
        ],
    ))
    lines.extend([
        "",
        "## Coverage Warnings",
        "",
    ])
    lines.extend(markdown_table(
        warning_rows,
        [
            ("evidence_tier", "tier"),
            ("case_group", "case/domain"),
            ("report_status", "status"),
            ("visual_artifact_count", "screens"),
            ("coverage_warning", "warning"),
        ],
    ))
    lines.extend([
        "",
        "## Rows Needing Review",
        "",
    ])
    lines.extend(markdown_table(
        review,
        [
            ("report_status", "status"),
            ("case_group", "case/domain"),
            ("seed_category", "category"),
            ("seed_risk_level", "risk"),
            ("run_count", "runs"),
            ("action_count_max", "action max"),
            ("candidate_sized_visible_media_element_count_max", "visible candidates"),
            ("missed_visible_tile_count_max", "missed max"),
            ("false_hidden_count_max", "false hidden max"),
            ("report_note", "note"),
        ],
    ))
    lines.extend([
        "",
        "## Evidence Interpretation",
        "",
        "- `collect_ms`, `cheap_filter_ms`, `apply_ms`는 한 scan cycle 내부 stage latency다.",
        "- `dom_added_to_action_ms`는 DOM/viewport에 후보가 들어온 뒤 action까지의 지연이다. 사용자가 보기 전에 가리는 목표와 가장 직접적으로 연결된다.",
        "- `late_decision_ms`는 늦게 로드된 이미지 fixture에서 삽입 후 숨김까지 걸린 시간이다.",
        "- `candidate_sized_visible_media_element_count`는 30px 아이콘을 제외한 보고서용 잔여 visual 후보 지표다.",
        "- `false_hidden_count`는 controlled/benign fixture에서만 오탐 지표로 해석한다. live 위험 사이트 row의 truth label로 과해석하지 않는다.",
        "",
        "## Known Gaps",
        "",
        "- v1은 YOLO/NSFW classifier/OCR을 붙이지 않았다. 따라서 classifier/OCR 속도는 아직 `not_instrumented`로 보고한다.",
        "- live harmful visual evidence는 현재 주소가이드 계열 2개 도메인에 머문다. Chrome 이미지 차단을 성숙하다고 말하기에는 부족하다.",
        "- live URL seed는 reachable 여부와 visual banner 존재 여부가 섞여 있으므로, `live_page_ok`, visible candidate, screenshot을 통과한 row만 evidence로 승격한다.",
        "- Google Images/YouTube harmful query와 화면 녹화 evidence는 다음 반복에서 추가해야 한다.",
        "",
        "## Generated Files",
        "",
    ])
    for label, path in output_paths.items():
        lines.append(f"- {label}: `{path}`")
    lines.extend([
        "",
        "## Input Files",
        "",
    ])
    for path in input_files:
        lines.append(f"- `{path}`")
    lines.append("")
    _ = stage_rows
    return "\n".join(lines)


def write_json_report(
    path: Path,
    *,
    results_dir: Path,
    input_files: list[Path],
    input_rows: list[dict[str, Any]],
    summary_rows: list[dict[str, Any]],
    stage_rows: list[dict[str, Any]],
    coverage_rows: list[dict[str, Any]],
    output_paths: dict[str, Path],
) -> None:
    status_counts = Counter(str(row["report_status"]) for row in summary_rows)
    payload = {
        "generatedAt": now_iso(),
        "resultsDir": str(results_dir),
        "inputFiles": [str(path) for path in input_files],
        "rawRowCount": len(input_rows),
        "summaryGroupCount": len(summary_rows),
        "stageRowCount": len(stage_rows),
        "statusCounts": dict(sorted(status_counts.items())),
        "evidenceTierCounts": dict(sorted(Counter(str(row["evidence_tier"]) for row in summary_rows).items())),
        "coverageVerdict": overall_coverage_verdict(coverage_rows),
        "coverageAudit": coverage_rows,
        "stageBudgetSummary": global_stage_summary(input_rows),
        "generatedFiles": {label: str(path) for label, path in output_paths.items()},
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build report-ready media safety latency/evidence artifacts from current smoke CSVs.",
    )
    parser.add_argument("--results-dir", type=Path, default=DEFAULT_RESULTS_DIR)
    parser.add_argument("--seed-file", type=Path, default=DEFAULT_SITE_SEED_FILE)
    parser.add_argument("--max-md-rows", type=int, default=20)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    results_dir = args.results_dir
    input_files = find_input_csvs(results_dir)
    input_rows = read_input_rows(results_dir)
    if not input_rows:
        raise SystemExit(f"no media safety smoke CSV rows found under {results_dir}")

    summary_rows = build_summary_rows(input_rows)
    stage_rows = build_stage_rows(input_rows, summary_rows)
    seed_rows = read_seed_rows(args.seed_file)
    coverage_rows = build_coverage_audit_rows(summary_rows, seed_rows)
    output_paths = {
        "latency_summary_csv": results_dir / LATENCY_SUMMARY_NAME,
        "stage_latency_csv": results_dir / STAGE_LATENCY_NAME,
        "coverage_audit_csv": results_dir / COVERAGE_AUDIT_NAME,
        "report_json": results_dir / REPORT_JSON_NAME,
        "report_md": results_dir / REPORT_MD_NAME,
    }

    write_csv(output_paths["latency_summary_csv"], summary_rows, SUMMARY_FIELDNAMES)
    write_csv(output_paths["stage_latency_csv"], stage_rows, STAGE_FIELDNAMES)
    write_csv(output_paths["coverage_audit_csv"], coverage_rows, COVERAGE_FIELDNAMES)
    write_json_report(
        output_paths["report_json"],
        results_dir=results_dir,
        input_files=input_files,
        input_rows=input_rows,
        summary_rows=summary_rows,
        stage_rows=stage_rows,
        coverage_rows=coverage_rows,
        output_paths=output_paths,
    )
    output_paths["report_md"].write_text(
        build_markdown_report(
            results_dir=results_dir,
            input_files=input_files,
            input_rows=input_rows,
            summary_rows=summary_rows,
            stage_rows=stage_rows,
            coverage_rows=coverage_rows,
            output_paths=output_paths,
            max_rows=max(1, int(args.max_md_rows)),
        ),
        encoding="utf-8",
    )

    print(json.dumps({
        "resultsDir": str(results_dir),
        "inputFileCount": len(input_files),
        "rawRowCount": len(input_rows),
        "summaryGroupCount": len(summary_rows),
        "stageRowCount": len(stage_rows),
        "coverageRowCount": len(coverage_rows),
        "coverageVerdict": overall_coverage_verdict(coverage_rows),
        "outputs": {label: str(path) for label, path in output_paths.items()},
    }, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
