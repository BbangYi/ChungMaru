#!/usr/bin/env python3
"""Run Chungmaru pipeline regression cases through the local backend.

This script intentionally uses only the public `/analyze_batch` contract.
It is a reporting/evaluation helper, not a second implementation of the model.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_CASE_FILE = Path(__file__).with_name("cases.jsonl")


def load_cases(path: Path) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"{path}:{line_number}: invalid JSONL row: {exc}") from exc
            if not isinstance(item.get("text"), str):
                raise SystemExit(f"{path}:{line_number}: missing text")
            if "expected_offensive" not in item:
                raise SystemExit(f"{path}:{line_number}: missing expected_offensive")
            cases.append(item)
    return cases


def post_analyze_batch(
    backend_url: str,
    texts: list[str],
    sensitivity: int,
    timeout: float,
) -> tuple[list[dict[str, Any]], float]:
    endpoint = backend_url.rstrip("/") + "/analyze_batch"
    payload = json.dumps(
        {"texts": texts, "sensitivity": sensitivity},
        ensure_ascii=False,
    ).encode("utf-8")
    request = Request(
        endpoint,
        data=payload,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    started = time.perf_counter()
    try:
        with urlopen(request, timeout=timeout) as response:
            body = json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"backend returned HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise SystemExit(f"backend request failed: {exc.reason}") from exc
    except TimeoutError as exc:
        raise SystemExit(f"backend request timed out after {timeout}s") from exc

    elapsed_ms = (time.perf_counter() - started) * 1000
    results = body.get("results")
    if not isinstance(results, list):
        raise SystemExit(f"invalid backend response: missing results list: {body!r}")
    if len(results) != len(texts):
        raise SystemExit(f"result count mismatch: {len(results)} != {len(texts)}")
    return results, elapsed_ms


def span_texts(result: dict[str, Any]) -> list[str]:
    spans = result.get("evidence_spans") or []
    return [
        str(span.get("text", ""))
        for span in spans
        if isinstance(span, dict) and str(span.get("text", "")).strip()
    ]


def span_matches(result: dict[str, Any], expected_spans: list[str]) -> bool:
    if not expected_spans:
        return True
    actual = span_texts(result)
    normalized_actual = [value.lower() for value in actual]
    for expected in expected_spans:
        value = str(expected).lower()
        if not any(value == item or value in item for item in normalized_actual):
            return False
    return True


def as_float(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number >= 0 else None


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    sorted_values = sorted(values)
    index = min(len(sorted_values) - 1, max(0, round((len(sorted_values) - 1) * percent)))
    return sorted_values[index]


def summarize_metric(values: list[float]) -> dict[str, float | None]:
    if not values:
        return {"min": None, "avg": None, "p95": None, "max": None}
    return {
        "min": round(min(values), 3),
        "avg": round(sum(values) / len(values), 3),
        "p95": round(percentile(values, 0.95) or 0, 3),
        "max": round(max(values), 3),
    }


def summarize_latency(rows: list[dict[str, Any]]) -> dict[str, Any]:
    timing_values = [
        value
        for value in (as_float(row.get("timing_ms")) for row in rows)
        if value is not None
    ]
    model_values = [
        value
        for value in (as_float(row.get("model_timing_ms")) for row in rows)
        if value is not None
    ]
    slowest_rows = sorted(
        (row for row in rows if as_float(row.get("timing_ms")) is not None),
        key=lambda row: as_float(row.get("timing_ms")) or 0,
        reverse=True,
    )[:5]
    return {
        "timing_ms": summarize_metric(timing_values),
        "model_timing_ms": summarize_metric(model_values),
        "slowest": [
            {
                "id": row.get("id"),
                "group": row.get("group"),
                "pass": row.get("pass"),
                "timing_ms": row.get("timing_ms"),
                "model_timing_ms": row.get("model_timing_ms"),
                "actual": row.get("actual"),
                "spans": row.get("spans"),
            }
            for row in slowest_rows
        ],
    }


def evaluate(
    cases: list[dict[str, Any]],
    results: list[dict[str, Any]],
    sensitivity: int | None = None,
) -> dict[str, Any]:
    rows = []
    totals = {
        "count": len(cases),
        "pass": 0,
        "fail": 0,
        "false_positive": 0,
        "false_negative": 0,
        "span_fail": 0,
    }
    groups: dict[str, dict[str, int]] = {}

    for case, result in zip(cases, results, strict=True):
        canonical_expected_offensive = bool(case["expected_offensive"])
        # In product settings, sensitivity 0 means "do not filter", not "run
        # the model with an ultra-high threshold". Treat it as a UX contract.
        expected_offensive = False if sensitivity is not None and sensitivity <= 0 else canonical_expected_offensive
        actual_offensive = bool(result.get("is_offensive"))
        expected_spans = list(case.get("expected_spans") or [])
        spans_ok = span_matches(result, expected_spans) if expected_offensive and actual_offensive else True
        passed = expected_offensive == actual_offensive and spans_ok
        group = str(case.get("group") or "uncategorized")
        groups.setdefault(group, {"count": 0, "pass": 0, "fail": 0})
        groups[group]["count"] += 1
        groups[group]["pass" if passed else "fail"] += 1

        totals["pass" if passed else "fail"] += 1
        if not expected_offensive and actual_offensive:
            totals["false_positive"] += 1
        if expected_offensive and not actual_offensive:
            totals["false_negative"] += 1
        if expected_offensive and actual_offensive and not spans_ok:
            totals["span_fail"] += 1

        rows.append(
            {
                "id": case.get("id"),
                "group": group,
                "text": case.get("text"),
                "backend_original": result.get("original"),
                "expected": expected_offensive,
                "canonical_expected": canonical_expected_offensive,
                "actual": actual_offensive,
                "is_profane": bool(result.get("is_profane")),
                "is_toxic": bool(result.get("is_toxic")),
                "is_hate": bool(result.get("is_hate")),
                "spans": span_texts(result),
                "expected_spans": expected_spans,
                "pass": passed,
                "timing_ms": result.get("timing_ms"),
                "model_timing_ms": result.get("model_timing_ms"),
                "llm_timing_ms": result.get("llm_timing_ms"),
                "scores": result.get("scores"),
                "notes": case.get("notes", ""),
            }
        )

    metrics = summarize_classification_metrics(rows)
    return {
        "totals": totals,
        "metrics": metrics,
        "groups": groups,
        "latency": summarize_latency(rows),
        "rows": rows,
    }


def summarize_classification_metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    tp = sum(1 for row in rows if row["expected"] and row["actual"])
    tn = sum(1 for row in rows if not row["expected"] and not row["actual"])
    fp = sum(1 for row in rows if not row["expected"] and row["actual"])
    fn = sum(1 for row in rows if row["expected"] and not row["actual"])
    count = len(rows)
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = (2 * precision * recall / (precision + recall)) if precision + recall else 0.0
    accuracy = (tp + tn) / count if count else 0.0
    return {
        "true_positive": tp,
        "true_negative": tn,
        "false_positive": fp,
        "false_negative": fn,
        "accuracy": round(accuracy, 4),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
    }


def print_human_report(report: dict[str, Any], backend_ms: float, sensitivity: int) -> None:
    totals = report["totals"]
    print(f"Chungmaru pipeline evaluation")
    print(f"- sensitivity: {sensitivity}")
    print(f"- backend round-trip: {backend_ms:.1f}ms")
    print(
        "- totals: "
        f"{totals['pass']}/{totals['count']} pass, "
        f"{totals['false_positive']} FP, "
        f"{totals['false_negative']} FN, "
        f"{totals['span_fail']} span fail"
    )
    metrics = report.get("metrics") or {}
    if metrics:
        print(
            "- metrics: "
            f"accuracy {metrics['accuracy']:.4f}, "
            f"precision {metrics['precision']:.4f}, "
            f"recall {metrics['recall']:.4f}, "
            f"f1 {metrics['f1']:.4f}, "
            f"TP {metrics['true_positive']}, TN {metrics['true_negative']}"
        )
    print()
    print("Group summary")
    for group, stats in sorted(report["groups"].items()):
        print(f"- {group}: {stats['pass']}/{stats['count']} pass")
    print()

    latency = report.get("latency") or {}
    timing = latency.get("timing_ms") or {}
    model_timing = latency.get("model_timing_ms") or {}
    if timing.get("avg") is not None or model_timing.get("avg") is not None:
        print("Latency summary")
        if timing.get("avg") is not None:
            print(
                "- per-result pipeline: "
                f"avg {timing['avg']}ms, p95 {timing['p95']}ms, max {timing['max']}ms"
            )
        if model_timing.get("avg") is not None:
            print(
                "- per-result model: "
                f"avg {model_timing['avg']}ms, p95 {model_timing['p95']}ms, max {model_timing['max']}ms"
            )
        slowest = list(latency.get("slowest") or [])
        if slowest:
            print("- slowest cases:")
            for row in slowest:
                print(
                    f"  - {row['id']} [{row['group']}]: "
                    f"pipeline={row['timing_ms']}ms model={row['model_timing_ms']}ms "
                    f"actual={row['actual']} spans={row['spans']}"
                )
        print()

    failed = [row for row in report["rows"] if not row["pass"]]
    if not failed:
        print("All cases passed.")
        return

    print("Failed cases")
    for row in failed:
        print(
            f"- {row['id']} [{row['group']}]: "
            f"expected={row['expected']} actual={row['actual']} "
            f"expected_spans={row['expected_spans']} spans={row['spans']} "
            f"notes={row['notes']}"
        )


def parse_sensitivity_list(raw_value: str) -> list[int]:
    values: list[int] = []
    for raw_part in str(raw_value or "").split(","):
        part = raw_part.strip()
        if not part:
            continue
        try:
            value = int(part)
        except ValueError as exc:
            raise argparse.ArgumentTypeError(f"invalid sensitivity value: {part!r}") from exc
        if value < 0 or value > 100:
            raise argparse.ArgumentTypeError("sensitivity values must be between 0 and 100")
        values.append(value)
    if not values:
        raise argparse.ArgumentTypeError("at least one sensitivity value is required")
    return values


def run_evaluation_once(
    cases: list[dict[str, Any]],
    backend: str,
    sensitivity: int,
    timeout: float,
) -> dict[str, Any]:
    results, backend_ms = post_analyze_batch(
        backend,
        [case["text"] for case in cases],
        sensitivity=sensitivity,
        timeout=timeout,
    )
    report = evaluate(cases, results, sensitivity=sensitivity)
    report["backend_ms"] = round(backend_ms, 3)
    report["backend"] = backend
    report["sensitivity"] = sensitivity
    return report


def summarize_repeated_runs(runs: list[dict[str, Any]]) -> dict[str, Any]:
    if not runs:
        return {}

    backend_samples = [float(run.get("backend_ms") or 0) for run in runs]
    return {
        "repeat": len(runs),
        "backend_ms_samples": [round(value, 3) for value in backend_samples],
        "backend_ms_min": round(min(backend_samples), 3),
        "backend_ms_avg": round(sum(backend_samples) / len(backend_samples), 3),
        "backend_ms_max": round(max(backend_samples), 3),
        "all_passed": all(run.get("totals", {}).get("fail") == 0 for run in runs),
        "runs": runs,
    }


def print_repeated_report(summary: dict[str, Any]) -> None:
    runs = list(summary.get("runs") or [])
    if not runs:
        return

    first = runs[0]
    print_human_report(first, float(first.get("backend_ms") or 0), int(first["sensitivity"]))
    if len(runs) <= 1:
        return

    print()
    print("Repeated latency")
    print(f"- repeat: {summary['repeat']}")
    print(f"- min/avg/max: {summary['backend_ms_min']}ms / {summary['backend_ms_avg']}ms / {summary['backend_ms_max']}ms")
    print(f"- samples: {', '.join(f'{value}ms' for value in summary['backend_ms_samples'])}")


def backend_decision(row: dict[str, Any]) -> str:
    if not row.get("actual"):
        return "allow"
    if row.get("spans"):
        return "span_mask"
    return "review_no_span"


def score_value(row: dict[str, Any], label: str) -> float | None:
    scores = row.get("scores") or {}
    value = scores.get(label) if isinstance(scores, dict) else None
    return as_float(value)


def write_artifacts(output_dir: Path, summaries: list[dict[str, Any]]) -> dict[str, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "summaries": summaries,
    }

    json_path = output_dir / "backend-e2e-summary.json"
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    csv_path = output_dir / "backend-e2e-rows.csv"
    fieldnames = [
        "sensitivity",
        "run_index",
        "case_id",
        "group",
        "text",
        "expected_offensive",
        "actual_offensive",
        "pass",
        "backend_decision",
        "is_profane",
        "is_toxic",
        "is_hate",
        "score_profanity",
        "score_toxicity",
        "score_hate",
        "expected_spans",
        "evidence_spans",
        "timing_ms",
        "model_timing_ms",
        "llm_timing_ms",
        "notes",
    ]
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for summary in summaries:
            for run_index, run in enumerate(summary.get("runs") or [], start=1):
                for row in run.get("rows") or []:
                    writer.writerow(
                        {
                            "sensitivity": run.get("sensitivity"),
                            "run_index": run_index,
                            "case_id": row.get("id"),
                            "group": row.get("group"),
                            "text": row.get("text"),
                            "expected_offensive": row.get("expected"),
                            "actual_offensive": row.get("actual"),
                            "pass": row.get("pass"),
                            "backend_decision": backend_decision(row),
                            "is_profane": row.get("is_profane"),
                            "is_toxic": row.get("is_toxic"),
                            "is_hate": row.get("is_hate"),
                            "score_profanity": score_value(row, "profanity"),
                            "score_toxicity": score_value(row, "toxicity"),
                            "score_hate": score_value(row, "hate"),
                            "expected_spans": " | ".join(str(value) for value in row.get("expected_spans") or []),
                            "evidence_spans": " | ".join(str(value) for value in row.get("spans") or []),
                            "timing_ms": row.get("timing_ms"),
                            "model_timing_ms": row.get("model_timing_ms"),
                            "llm_timing_ms": row.get("llm_timing_ms"),
                            "notes": row.get("notes"),
                        }
                    )

    md_path = output_dir / "backend-e2e-report.md"
    md_path.write_text(render_markdown_report(payload), encoding="utf-8")
    return {"json": json_path, "csv": csv_path, "markdown": md_path}


def md_cell(value: Any) -> str:
    text = str(value if value is not None else "-")
    return text.replace("\n", " ").replace("|", "\\|")


def render_markdown_report(payload: dict[str, Any]) -> str:
    lines = [
        "# Backend E2E Test Report",
        "",
        f"- generated_at: `{payload['generated_at']}`",
        "- contract: public `POST /analyze_batch`",
        "",
        "## Summary",
        "",
        "| Sensitivity | Repeat | Pass | Fail | Accuracy | Precision | Recall | F1 | FP | FN | Span Fail | Backend ms avg |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for summary in payload.get("summaries") or []:
        runs = list(summary.get("runs") or [])
        if not runs:
            continue
        first = runs[0]
        totals = first.get("totals") or {}
        metrics = first.get("metrics") or {}
        lines.append(
            "| "
            f"{first.get('sensitivity')} | "
            f"{summary.get('repeat')} | "
            f"{totals.get('pass')} | "
            f"{totals.get('fail')} | "
            f"{metrics.get('accuracy')} | "
            f"{metrics.get('precision')} | "
            f"{metrics.get('recall')} | "
            f"{metrics.get('f1')} | "
            f"{totals.get('false_positive')} | "
            f"{totals.get('false_negative')} | "
            f"{totals.get('span_fail')} | "
            f"{summary.get('backend_ms_avg')} |"
        )

    lines.extend([
        "",
        "## Group Summary",
        "",
        "| Sensitivity | Group | Pass | Count |",
        "| --- | --- | ---: | ---: |",
    ])
    for summary in payload.get("summaries") or []:
        runs = list(summary.get("runs") or [])
        if not runs:
            continue
        first = runs[0]
        for group, stats in sorted((first.get("groups") or {}).items()):
            lines.append(
                f"| {first.get('sensitivity')} | {md_cell(group)} | {stats.get('pass')} | {stats.get('count')} |"
            )

    lines.extend([
        "",
        "## Latency",
        "",
        "| Sensitivity | Backend round-trip samples | Pipeline avg | Pipeline p95 | Pipeline max | Model avg | Model p95 | Model max |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ])
    for summary in payload.get("summaries") or []:
        runs = list(summary.get("runs") or [])
        if not runs:
            continue
        first = runs[0]
        latency = first.get("latency") or {}
        pipeline = latency.get("timing_ms") or {}
        model = latency.get("model_timing_ms") or {}
        samples = md_cell(", ".join(f"{value}ms" for value in summary.get("backend_ms_samples") or []))
        lines.append(
            "| "
            f"{first.get('sensitivity')} | "
            f"{samples} | "
            f"{pipeline.get('avg')} | "
            f"{pipeline.get('p95')} | "
            f"{pipeline.get('max')} | "
            f"{model.get('avg')} | "
            f"{model.get('p95')} | "
            f"{model.get('max')} |"
        )

    lines.extend([
        "",
        "## Slowest Cases",
        "",
        "| Sensitivity | Case | Group | Pipeline ms | Model ms | Decision | Evidence spans |",
        "| --- | --- | --- | ---: | ---: | --- | --- |",
    ])
    for summary in payload.get("summaries") or []:
        runs = list(summary.get("runs") or [])
        if not runs:
            continue
        first = runs[0]
        rows_by_id = {row.get("id"): row for row in first.get("rows") or []}
        for row in (first.get("latency") or {}).get("slowest") or []:
            full_row = rows_by_id.get(row.get("id"), row)
            spans = md_cell(", ".join(str(value) for value in full_row.get("spans") or []))
            lines.append(
                "| "
                f"{first.get('sensitivity')} | "
                f"{md_cell(row.get('id'))} | "
                f"{md_cell(row.get('group'))} | "
                f"{row.get('timing_ms')} | "
                f"{row.get('model_timing_ms')} | "
                f"{md_cell(backend_decision(full_row))} | "
                f"{spans or '-'} |"
            )

    failed_rows = []
    for summary in payload.get("summaries") or []:
        for run in summary.get("runs") or []:
            failed_rows.extend((run.get("sensitivity"), row) for row in run.get("rows") or [] if not row.get("pass"))
    lines.extend([
        "",
        "## Failed Cases",
        "",
    ])
    if failed_rows:
        lines.extend([
            "| Sensitivity | Case | Group | Expected | Actual | Expected spans | Evidence spans |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ])
        for sensitivity, row in failed_rows:
            expected_spans = md_cell(", ".join(str(value) for value in row.get("expected_spans") or []))
            spans = md_cell(", ".join(str(value) for value in row.get("spans") or []))
            lines.append(
                f"| {sensitivity} | {md_cell(row.get('id'))} | {md_cell(row.get('group'))} | {row.get('expected')} | "
                f"{row.get('actual')} | {expected_spans or '-'} | {spans or '-'} |"
            )
    else:
        lines.append("No failed cases.")

    lines.append("")
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", default="http://127.0.0.1:8000", help="Backend base URL")
    parser.add_argument("--case-file", type=Path, default=DEFAULT_CASE_FILE, help="JSONL case file")
    parser.add_argument("--sensitivity", type=int, default=60, help="Sensitivity passed to /analyze_batch")
    parser.add_argument(
        "--sensitivities",
        type=parse_sensitivity_list,
        help="Comma-separated sensitivity values for a sweep, for example 0,20,60,100",
    )
    parser.add_argument("--repeat", type=int, default=1, help="Repeat each sensitivity to capture cold/warm latency")
    parser.add_argument("--timeout", type=float, default=20.0, help="HTTP timeout in seconds")
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Write backend E2E summary JSON, row CSV, and Markdown report to this directory",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    cases = load_cases(args.case_file)
    repeat = max(1, int(args.repeat or 1))
    sensitivities = args.sensitivities or [args.sensitivity]

    summaries = []
    for sensitivity in sensitivities:
        runs = [
            run_evaluation_once(cases, args.backend, sensitivity, args.timeout)
            for _ in range(repeat)
        ]
        summaries.append(summarize_repeated_runs(runs))

    if args.json:
        if len(summaries) == 1 and repeat == 1:
            print(json.dumps(summaries[0]["runs"][0], ensure_ascii=False, indent=2))
        else:
            print(json.dumps({"summaries": summaries}, ensure_ascii=False, indent=2))
    else:
        for index, summary in enumerate(summaries):
            if index > 0:
                print()
                print("=" * 72)
                print()
            print_repeated_report(summary)

    if args.output_dir:
        paths = write_artifacts(args.output_dir, summaries)
        print()
        print("Artifacts")
        for label, path in paths.items():
            print(f"- {label}: {path}")

    return 0 if all(summary.get("all_passed") for summary in summaries) else 1


if __name__ == "__main__":
    sys.exit(main())
