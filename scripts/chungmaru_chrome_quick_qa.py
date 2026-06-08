#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import csv
import io
import json
import shutil
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from chungmaru_chrome_google_demo import (  # noqa: E402
    CdpWebSocket,
    DEFAULT_QUERY_SET,
    collect_render_diagnostics,
    create_tab,
    demo_settings,
    detect_chrome_path,
    dismiss_google_consent,
    effective_masked_span_count,
    launch_chrome,
    navigate_to_search,
    queries_for_set,
    query_metadata,
    run_control_surface_checks,
    run_pipeline_for_google_scene,
    send_extension_message,
    set_demo_settings,
    warmup_backend,
    wait_for_page_ready,
    wait_for_service_worker,
)


DEFAULT_OUTPUT_DIR = Path("evaluation/latency/results/current")
DEFAULT_PROFILE_ROOT = Path("/private/tmp/chungmaru-chrome-quick-qa-profiles")
DEFAULT_LOG_ROOT = Path("/private/tmp/chungmaru-chrome-quick-qa-logs")
DEFAULT_APPEND_JSONL = DEFAULT_OUTPUT_DIR / "chrome-quick-qa.jsonl"
DEFAULT_APPEND_CSV = DEFAULT_OUTPUT_DIR / "chrome-quick-qa.csv"
UTF8_BOM = b"\xef\xbb\xbf"
SITE_WARNING_FORCE_BACK_RISK_SCORE = 0.9
SITE_WARNING_FORCE_BACK_SENSITIVITY = 50

CSV_FIELDS = [
    "run_id",
    "captured_at",
    "scenario",
    "kind",
    "sensitivity",
    "query_or_url",
    "query_display",
    "query_scenario_id",
    "query_category",
    "expected_result",
    "query_echo_ok",
    "diagnostic_query_values",
    "ok",
    "verdict",
    "can_continue",
    "backend_ok",
    "effective_masked_span_count",
    "render_box_count",
    "inline_mask_count",
    "duplicate_rendered_original_count",
    "ai_overview_candidate_count",
    "requested_analysis_count",
    "settings_load_ms",
    "first_mask_latency_ms",
    "visible_first_mask_ms",
    "preexisting_mask_count",
    "first_paint_mask_ms",
    "local_preflight_masked_span_count",
    "preconceal_count",
    "candidate_collect_ms",
    "dirty_select_ms",
    "prioritize_ms",
    "foreground_select_ms",
    "parser_ms",
    "pre_backend_ms",
    "local_preflight_ms",
    "backend_round_trip_ms",
    "backend_reported_ms",
    "decision_build_ms",
    "mask_apply_ms",
    "post_backend_to_mask_ms",
    "total_to_mask_ms",
    "total_candidate_count",
    "foreground_candidate_count",
    "returned_span_count",
    "latency_note",
    "hover_or_widget",
    "note",
]


def now_id() -> str:
    return datetime.now().strftime("%Y%m%dT%H%M%S")


def csv_value(value: Any) -> str | int | float:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (str, int, float)):
        return value
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def append_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    exists = ensure_csv_file(path)
    with path.open("a", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        if not exists:
            writer.writeheader()
        for row in rows:
            writer.writerow({field: csv_value(row.get(field, "")) for field in CSV_FIELDS})


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: csv_value(row.get(field, "")) for field in CSV_FIELDS})


def append_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    if not rows:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def nested_get(value: dict[str, Any], path: list[str], default: Any = None) -> Any:
    current: Any = value
    for key in path:
        if not isinstance(current, dict):
            return default
        current = current.get(key)
    return current if current is not None else default


def number_like(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def ensure_csv_file(path: Path) -> bool:
    if not path.exists() or path.stat().st_size == 0:
        return False
    raw = path.read_bytes()
    text = raw.decode("utf-8-sig")
    reader = csv.DictReader(io.StringIO(text))
    existing_fields = reader.fieldnames or []
    if raw.startswith(UTF8_BOM) and existing_fields == CSV_FIELDS:
        return True

    existing_rows = list(reader)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for row in existing_rows:
            writer.writerow({field: row.get(field, "") for field in CSV_FIELDS})
    return True


def latest_attempt_stats(pipeline: dict[str, Any]) -> dict[str, Any]:
    attempts = pipeline.get("attempts")
    if isinstance(attempts, list) and attempts:
        stats = nested_get(attempts[-1], ["trigger_response", "response", "stats"], None)
        if isinstance(stats, dict):
            return stats
    stats = pipeline.get("last_stats")
    return stats if isinstance(stats, dict) else {}


def backend_warmup_row(args: argparse.Namespace, result: dict[str, Any]) -> dict[str, Any]:
    timings = result.get("timings") if isinstance(result, dict) else {}
    return {
        "run_id": args.run_id,
        "captured_at": datetime.now().isoformat(timespec="seconds"),
        "scenario": "preflight",
        "kind": "backend-warmup",
        "sensitivity": "",
        "query_or_url": f"{args.backend.rstrip('/')}/warmup",
        "query_display": "backend /warmup",
        "query_scenario_id": "backend-warmup",
        "query_category": "preflight",
        "expected_result": "classifier+span-ready",
        "query_echo_ok": "",
        "diagnostic_query_values": "",
        "ok": bool(result.get("ok")) if isinstance(result, dict) else False,
        "verdict": "",
        "can_continue": "",
        "backend_ok": bool(result.get("ok")) if isinstance(result, dict) else False,
        "effective_masked_span_count": "",
        "render_box_count": "",
        "inline_mask_count": "",
        "duplicate_rendered_original_count": "",
        "ai_overview_candidate_count": "",
        "requested_analysis_count": "",
        "first_mask_latency_ms": "",
        "visible_first_mask_ms": "",
        "preexisting_mask_count": "",
        "first_paint_mask_ms": "",
        "local_preflight_masked_span_count": "",
        "preconceal_count": "",
        "candidate_collect_ms": "",
        "parser_ms": "",
        "pre_backend_ms": "",
        "backend_round_trip_ms": result.get("client_wall_ms", "") if isinstance(result, dict) else "",
        "backend_reported_ms": result.get("endpoint_total_ms", result.get("total_ms", "")) if isinstance(result, dict) else "",
        "decision_build_ms": "",
        "mask_apply_ms": "",
        "post_backend_to_mask_ms": "",
        "total_to_mask_ms": "",
        "total_candidate_count": "",
        "foreground_candidate_count": "",
        "returned_span_count": "",
        "latency_note": "backend-warmup",
        "hover_or_widget": "",
        "note": {
            "timings": timings if isinstance(timings, dict) else {},
            "models_after": result.get("after", {}) if isinstance(result, dict) else {},
            "error": result.get("error", "") if isinstance(result, dict) else "",
        },
    }


def phase_ms(stats: dict[str, Any], key: str) -> Any:
    phase = stats.get("phaseTimings")
    if not isinstance(phase, dict):
        return ""
    return phase.get(key, "")


def search_input_values(diagnostics: dict[str, Any]) -> list[str]:
    values = diagnostics.get("searchInputs") if isinstance(diagnostics, dict) else []
    if not isinstance(values, list):
        return []
    result: list[str] = []
    for item in values:
        if not isinstance(item, dict):
            continue
        value = item.get("value")
        if isinstance(value, str) and value:
            result.append(value)
    return result


def summarize_render_diagnostics(
    diagnostics: dict[str, Any],
    expected_query: str | None = None,
) -> dict[str, Any]:
    values = search_input_values(diagnostics)
    query_echo_ok = expected_query in values if expected_query else ""
    if expected_query and values and not query_echo_ok:
        display_values = ["<redacted-non-query-value>" for _ in values[:3]]
        value_shapes = [f"len={len(value)}" for value in values[:3]]
    else:
        display_values = values[:3]
        value_shapes = []
    return {
        "renderedCount": diagnostics.get("renderedCount", ""),
        "renderBoxCount": diagnostics.get("renderBoxCount", ""),
        "inlineMaskCount": diagnostics.get("inlineMaskCount", ""),
        "editableOverlayCount": diagnostics.get("editableOverlayCount", ""),
        "concealedEditableSourceCount": diagnostics.get("concealedEditableSourceCount", ""),
        "duplicateRenderedOriginalCount": diagnostics.get("duplicateRenderedOriginalCount", ""),
        "aiOverviewCandidateCount": diagnostics.get("aiOverviewCandidateCount", ""),
        "searchInputCount": len(values),
        "queryEchoOk": query_echo_ok,
        "searchInputValues": display_values,
        "searchInputValueShapes": value_shapes,
        "aiOverviewTextSamples": diagnostics.get("aiOverviewTextSamples", []),
        "maskedOriginalTextSamples": diagnostics.get("maskedOriginalTextSamples", []),
        "inlineMaskTextSamples": diagnostics.get("inlineMaskTextSamples", []),
        "editableOverlayTextSamples": diagnostics.get("editableOverlayTextSamples", []),
    }


def latency_note(
    *,
    stats: dict[str, Any],
    before_summary: dict[str, Any],
    after_summary: dict[str, Any],
    total_ms: Any,
    backend_ms: Any,
    preexisting_mask_count: int = 0,
) -> str:
    notes: list[str] = []
    if preexisting_mask_count > 0:
        notes.append(f"already-masked-before-trigger={preexisting_mask_count}")
    if before_summary.get("queryEchoOk") is False:
        notes.append("query-input-mismatch")
    requested = number_like(stats.get("requestedAnalysisCount"))
    if requested > 1:
        notes.append(f"multi-candidate-analysis={int(requested)}")
    ai_count = max(
        number_like(before_summary.get("aiOverviewCandidateCount")),
        number_like(after_summary.get("aiOverviewCandidateCount")),
    )
    if ai_count:
        notes.append(f"ai-overview-candidates={int(ai_count)}")
    background_prefix = "background-" if preexisting_mask_count > 0 else ""
    if number_like(backend_ms) >= 300:
        notes.append(f"{background_prefix}backend-roundtrip-spike")
    elif number_like(backend_ms) >= 150:
        notes.append(f"{background_prefix}backend-roundtrip-moderate")
    if number_like(total_ms) >= 500:
        notes.append(f"{background_prefix}total-latency-spike")
    return "; ".join(notes) if notes else "normal"


def write_settings_with_retry(
    worker: CdpWebSocket,
    settings: dict[str, Any],
    attempts: int = 8,
) -> tuple[dict[str, Any], bool]:
    last_result: dict[str, Any] = {}
    for _ in range(attempts):
        result = set_demo_settings(worker, settings)
        last_result = result if isinstance(result, dict) else {"result": result}
        stored = last_result.get("settings")
        if (
            isinstance(stored, dict)
            and stored.get("backendEnabled") is True
            and stored.get("backendApiBaseUrl") == settings.get("backendApiBaseUrl")
            and int(stored.get("sensitivity", -1)) == int(settings.get("sensitivity", -2))
        ):
            return last_result, True
        time.sleep(0.2)
    return last_result, False


def expected_site_warning_can_continue(policy: dict[str, Any], sensitivity: int) -> bool | str:
    verdict = str(policy.get("verdict", ""))
    try:
        risk_score = float(policy.get("risk_score", 0))
    except (TypeError, ValueError):
        return ""
    return not (
        verdict == "block"
        and risk_score >= SITE_WARNING_FORCE_BACK_RISK_SCORE
        and sensitivity >= SITE_WARNING_FORCE_BACK_SENSITIVITY
    )


def run_google_query(
    *,
    worker: CdpWebSocket,
    page: CdpWebSocket,
    settings: dict[str, Any],
    settings_write_ok: bool,
    run_id: str,
    scenario: str,
    query: str,
    attempts: int,
) -> dict[str, Any]:
    navigate_to_search(page, query, timeout_s=18)
    time.sleep(0.7)
    dismiss_google_consent(page)
    before = collect_render_diagnostics(page)
    pipeline = run_pipeline_for_google_scene(
        worker,
        settings,
        settings_write_ok,
        reason=f"quick-qa-{scenario}",
        force_settings_snapshot=False,
        attempts=attempts,
    )
    after = collect_render_diagnostics(page)
    stats = latest_attempt_stats(pipeline)
    effective_masks = effective_masked_span_count(pipeline, after)
    before_summary = summarize_render_diagnostics(before, expected_query=query)
    after_summary = summarize_render_diagnostics(after, expected_query=query)
    raw_total_ms = phase_ms(stats, "totalToMaskMs") or stats.get("durationMs", "")
    backend_ms = phase_ms(stats, "backendRoundTripMs")
    preexisting_mask_count = int(
        max(
            number_like(before_summary.get("renderBoxCount")),
            number_like(before_summary.get("inlineMaskCount")),
        )
    )
    visible_first_mask_ms = 0 if preexisting_mask_count > 0 else stats.get("firstMaskLatencyMs", "")
    total_ms = 0 if preexisting_mask_count > 0 else raw_total_ms
    stage_values = [
        phase_ms(stats, key)
        for key in [
            "settingsLoadMs",
            "candidateCollectMs",
            "dirtySelectMs",
            "prioritizeMs",
            "foregroundSelectMs",
            "parserMs",
            "localPreflightMs",
            "backendRoundTripMs",
            "decisionBuildMs",
            "maskApplyMs",
        ]
    ]
    has_current_pipeline_work = (
        number_like(stats.get("requestedAnalysisCount")) > 0 or
        any(str(value) not in {"", "0"} for value in stage_values)
    )
    meta = query_metadata(query)
    return {
        "run_id": run_id,
        "captured_at": datetime.now().isoformat(timespec="seconds"),
        "scenario": scenario,
        "kind": "google-search",
        "sensitivity": settings.get("sensitivity"),
        "query_or_url": query,
        "query_display": query,
        "query_scenario_id": meta.get("query_scenario_id", ""),
        "query_category": meta.get("query_category", ""),
        "expected_result": meta.get("expected_result", ""),
        "query_echo_ok": before_summary.get("queryEchoOk", ""),
        "diagnostic_query_values": before_summary.get("searchInputValues", []),
        "ok": True,
        "verdict": "",
        "can_continue": "",
        "backend_ok": stats.get("backendStatus") == "ready",
        "effective_masked_span_count": effective_masks,
        "render_box_count": after.get("renderBoxCount", ""),
        "inline_mask_count": after.get("inlineMaskCount", ""),
        "duplicate_rendered_original_count": after.get("duplicateRenderedOriginalCount", ""),
        "ai_overview_candidate_count": after.get("aiOverviewCandidateCount", ""),
        "requested_analysis_count": stats.get("requestedAnalysisCount", ""),
        "settings_load_ms": phase_ms(stats, "settingsLoadMs"),
        "first_mask_latency_ms": stats.get("firstMaskLatencyMs", ""),
        "visible_first_mask_ms": visible_first_mask_ms,
        "preexisting_mask_count": preexisting_mask_count,
        "first_paint_mask_ms": stats.get("firstPaintMaskMs", ""),
        "local_preflight_masked_span_count": stats.get("localPreflightMaskedSpanCount", ""),
        "preconceal_count": stats.get("preconcealCount", ""),
        "candidate_collect_ms": phase_ms(stats, "candidateCollectMs"),
        "dirty_select_ms": phase_ms(stats, "dirtySelectMs"),
        "prioritize_ms": phase_ms(stats, "prioritizeMs"),
        "foreground_select_ms": phase_ms(stats, "foregroundSelectMs"),
        "parser_ms": phase_ms(stats, "parserMs"),
        "pre_backend_ms": phase_ms(stats, "preBackendMs"),
        "local_preflight_ms": phase_ms(stats, "localPreflightMs"),
        "backend_round_trip_ms": backend_ms,
        "backend_reported_ms": phase_ms(stats, "backendReportedMs"),
        "decision_build_ms": phase_ms(stats, "decisionBuildMs"),
        "mask_apply_ms": phase_ms(stats, "maskApplyMs"),
        "post_backend_to_mask_ms": phase_ms(stats, "postBackendToMaskMs"),
        "total_to_mask_ms": total_ms,
        "total_candidate_count": stats.get("totalCandidateCount", ""),
        "foreground_candidate_count": stats.get("foregroundCandidateCount", ""),
        "returned_span_count": stats.get("returnedSpanCount", ""),
        "effective_masked_span_count": effective_masks,
        "latency_note": latency_note(
            stats=stats,
            before_summary=before_summary,
            after_summary=after_summary,
            total_ms=raw_total_ms,
            backend_ms=backend_ms,
            preexisting_mask_count=preexisting_mask_count,
        ),
        "hover_or_widget": "",
        "note": {
            "before": before_summary,
            "after": after_summary,
            "decision_source": stats.get("lastDecisionSource", ""),
            "backend_endpoint": stats.get("backendEndpoint", ""),
            "pipeline_total_to_mask_ms": (
                raw_total_ms if preexisting_mask_count > 0 and has_current_pipeline_work else ""
            ),
        },
    }


def run_scenario(args: argparse.Namespace, sensitivity: int, scenario: str) -> list[dict[str, Any]]:
    args.sensitivity = sensitivity
    args.profile_dir = args.profile_root / args.run_id / f"profile-{scenario}"
    args.chrome_log = args.log_root / f"{args.run_id}.{scenario}.chrome.log"
    if args.profile_dir.exists():
        shutil.rmtree(args.profile_dir)
    rows: list[dict[str, Any]] = []
    chrome_process = None
    worker: CdpWebSocket | None = None
    page: CdpWebSocket | None = None
    try:
        chrome_process = launch_chrome(args)
        extension_id, worker_target = wait_for_service_worker(args.debugging_port, args.startup_timeout)
        worker = CdpWebSocket(str(worker_target["webSocketDebuggerUrl"]))
        settings = demo_settings(args)
        settings["sensitivity"] = sensitivity
        settings_write, settings_write_ok = write_settings_with_retry(worker, settings)

        control = run_control_surface_checks(args, extension_id, settings)
        site_policy = nested_get(control, ["site_policy", "policy"], {})
        can_continue = nested_get(site_policy, ["can_continue"], "")
        if can_continue == "":
            can_continue = expected_site_warning_can_continue(
                site_policy if isinstance(site_policy, dict) else {},
                sensitivity,
            )
        rows.append(
            {
                "run_id": args.run_id,
                "captured_at": datetime.now().isoformat(timespec="seconds"),
                "scenario": scenario,
                "kind": "settings-control",
                "sensitivity": sensitivity,
                "query_or_url": args.warning_url,
                "query_display": args.warning_url,
                "query_scenario_id": "site-warning-direct",
                "query_category": "site-risk",
                "expected_result": "block-or-warning",
                "query_echo_ok": "",
                "diagnostic_query_values": "",
                "ok": settings_write_ok and nested_get(control, ["backend_health", "ok"], False),
                "verdict": nested_get(site_policy, ["verdict"], ""),
                "can_continue": can_continue,
                "backend_ok": nested_get(control, ["backend_health", "ok"], ""),
                "effective_masked_span_count": "",
                "render_box_count": "",
                "inline_mask_count": "",
                "duplicate_rendered_original_count": "",
                "ai_overview_candidate_count": "",
                "requested_analysis_count": "",
                "candidate_collect_ms": "",
                "visible_first_mask_ms": "",
                "preexisting_mask_count": "",
                "parser_ms": "",
                "pre_backend_ms": "",
                "backend_round_trip_ms": nested_get(control, ["backend_health", "durationMs"], ""),
                "backend_reported_ms": "",
                "decision_build_ms": "",
                "mask_apply_ms": "",
                "post_backend_to_mask_ms": "",
                "total_to_mask_ms": "",
                "total_candidate_count": "",
                "foreground_candidate_count": "",
                "returned_span_count": "",
                "latency_note": "site-warning-control",
                "hover_or_widget": {
                    "set_override_ok": nested_get(control, ["wellbeing_set_override", "ok"], ""),
                    "clear_override_ok": nested_get(control, ["wellbeing_clear_override", "ok"], ""),
                    "view": nested_get(control, ["wellbeing_state_after_override", "view"], {}),
                },
                "note": {
                    "settings_write_ok": settings_write_ok,
                    "settings_write": settings_write,
                    "control": control,
                },
            }
        )

        target = create_tab(args.debugging_port, "https://www.google.com/?hl=ko")
        page = CdpWebSocket(str(target["webSocketDebuggerUrl"]))
        page.call("Page.enable")
        page.call("Runtime.enable")
        width, height = args.viewport
        page.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": width,
                "height": height,
                "deviceScaleFactor": 1,
                "mobile": False,
            },
        )
        wait_for_page_ready(page, timeout_s=12)
        dismiss_google_consent(page)

        for query in args.queries:
            rows.append(
                run_google_query(
                    worker=worker,
                    page=page,
                    settings=settings,
                    settings_write_ok=settings_write_ok,
                    run_id=args.run_id,
                    scenario=scenario,
                    query=query,
                    attempts=args.google_pipeline_attempts,
                )
            )
    finally:
        if page is not None:
            page.close()
        if worker is not None:
            worker.close()
        if chrome_process is not None:
            chrome_process.terminate()
            try:
                chrome_process.wait(timeout=5)
            except Exception:
                chrome_process.kill()
    return rows


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run fast Chungmaru Chrome QA without video rendering.")
    parser.add_argument("--run-id", default=f"chrome-quick-qa-{now_id()}")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--append-jsonl", type=Path, default=DEFAULT_APPEND_JSONL)
    parser.add_argument("--append-csv", type=Path, default=DEFAULT_APPEND_CSV)
    parser.add_argument("--profile-root", type=Path, default=DEFAULT_PROFILE_ROOT)
    parser.add_argument("--log-root", type=Path, default=DEFAULT_LOG_ROOT)
    parser.add_argument("--backend", default="http://127.0.0.1:8000")
    parser.add_argument("--no-backend-warmup", action="store_true")
    parser.add_argument("--backend-warmup-timeout", type=float, default=60.0)
    parser.add_argument("--extension-dir", type=Path, default=Path("extension/chrome"))
    parser.add_argument("--chrome-path", type=Path, default=None)
    parser.add_argument("--profile-dir", type=Path, default=DEFAULT_PROFILE_ROOT / "profile")
    parser.add_argument("--chrome-log", type=Path, default=DEFAULT_LOG_ROOT / "chrome.log")
    parser.add_argument("--debugging-port", type=int, default=9342)
    parser.add_argument("--startup-timeout", type=float, default=20)
    parser.add_argument("--headless", action="store_true", help="Use Chrome headless mode. Google live SERP may degrade.")
    parser.add_argument("--visible", action="store_true", help="Show Chrome at 0,0 for manual debugging.")
    parser.add_argument("--headed", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--viewport", type=lambda value: tuple(map(int, value.lower().split("x"))), default=(1440, 900))
    parser.add_argument("--warning-url", default="https://adult-webtoon-plus.kr/")
    parser.add_argument("--sensitivity", type=int, nargs="+", default=[60, 20])
    parser.add_argument("--google-pipeline-attempts", type=int, default=1)
    parser.add_argument("--query-set", default=DEFAULT_QUERY_SET)
    parser.add_argument("--queries", nargs="+", default=None)
    parser.add_argument("--clean-profile", action="store_true", default=True)
    parser.add_argument("--write-run-files", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    args.chrome_path = detect_chrome_path(str(args.chrome_path) if args.chrome_path else None)
    if args.queries is None:
        args.queries = queries_for_set(args.query_set)
    if args.headless and (args.visible or args.headed):
        raise SystemExit("--headless cannot be combined with --visible/--headed")
    args.window_position = "0,0" if args.visible or args.headed else "-4000,0"
    args.start_minimized = False
    args.output_dir.mkdir(parents=True, exist_ok=True)

    all_rows: list[dict[str, Any]] = []
    warmup_result = (
        {"ok": True, "skipped": True}
        if args.no_backend_warmup
        else warmup_backend(args.backend, timeout_s=args.backend_warmup_timeout)
    )
    print(f"backend_warmup={json.dumps(warmup_result, ensure_ascii=False)}")
    all_rows.append(backend_warmup_row(args, warmup_result))

    base_debugging_port = int(args.debugging_port)
    for index, sensitivity in enumerate(args.sensitivity):
        scenario = f"sensitivity-{sensitivity}"
        scenario_args = copy.copy(args)
        scenario_args.debugging_port = base_debugging_port + index
        rows = run_scenario(scenario_args, sensitivity, scenario)
        all_rows.extend(rows)

    append_jsonl(args.append_jsonl, all_rows)
    append_csv(args.append_csv, all_rows)
    if args.write_run_files:
        report = {
            "run_id": args.run_id,
            "captured_at": datetime.now().isoformat(timespec="seconds"),
            "row_count": len(all_rows),
            "rows": all_rows,
        }
        report_path = args.output_dir / "latest-quick-qa.json"
        csv_path = args.output_dir / "latest-quick-qa.csv"
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        write_csv(csv_path, all_rows)
        print(f"report={report_path}")
        print(f"csv={csv_path}")

    print(f"append_jsonl={args.append_jsonl}")
    print(f"append_csv={args.append_csv}")
    print(f"rows={len(all_rows)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
