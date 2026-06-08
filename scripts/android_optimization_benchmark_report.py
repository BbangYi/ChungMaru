#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class OptimizationComparison:
    optimization_id: str
    label: str
    baseline_mode: str
    optimized_mode: str
    primary_metric: str
    baseline_work_metric: str
    optimized_work_metric: str
    quality_metric: str
    interpretation: str


COMPARISONS = [
    OptimizationComparison(
        optimization_id="candidate_pruning",
        label="Candidate pruning",
        baseline_mode="opt_base_all_nodes_backend",
        optimized_mode="s12_collect_backend",
        primary_metric="avg_observed_total_ms",
        baseline_work_metric="avg_screen_candidates",
        optimized_work_metric="avg_screen_candidates",
        quality_metric="avg_offensive",
        interpretation="All visible accessibility text is the baseline; optimized mode sends only selected analysis candidates.",
    ),
    OptimizationComparison(
        optimization_id="roi_ocr",
        label="ROI OCR",
        baseline_mode="opt_base_fullscreen_ocr",
        optimized_mode="s123_collect_backend_ocr",
        primary_metric="avg_ocr_ms",
        baseline_work_metric="avg_roi_selected",
        optimized_work_metric="avg_roi_selected",
        quality_metric="avg_ocr_selected",
        interpretation="Full-screen OCR is the baseline; optimized mode restricts OCR to planned ROIs.",
    ),
    OptimizationComparison(
        optimization_id="charbox_overlay",
        label="Char box / line coordinate planning",
        baseline_mode="opt_base_full_box_overlay",
        optimized_mode="s12345_full",
        primary_metric="avg_display_ms",
        baseline_work_metric="avg_overlay_candidates",
        optimized_work_metric="avg_overlay_candidates",
        quality_metric="avg_overlay_rendered",
        interpretation="Full node-box overlay is the baseline; optimized mode uses exact char ranges, visual OCR geometry, and overlay planning.",
    ),
    OptimizationComparison(
        optimization_id="full_pipeline",
        label="Optimized full pipeline",
        baseline_mode="opt_base_full_box_overlay",
        optimized_mode="s12345_full",
        primary_metric="avg_observed_total_ms",
        baseline_work_metric="avg_screen_candidates",
        optimized_work_metric="avg_screen_candidates",
        quality_metric="avg_overlay_rendered",
        interpretation="End-to-end comparison against the broad full-box overlay baseline. This mixes multiple optimizations.",
    ),
]


def parse_float(value: str | None) -> float | None:
    if value is None:
        return None
    stripped = value.strip()
    if not stripped:
        return None
    try:
        return float(stripped)
    except ValueError:
        return None


def fmt(value: float | None) -> str:
    if value is None:
        return ""
    return f"{value:.1f}"


def pct(value: float | None) -> str:
    if value is None:
        return ""
    return f"{value:.1f}"


def read_summary(path: Path) -> dict[str, dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        return {row["mode"]: row for row in reader}


def reduction(baseline: float | None, optimized: float | None) -> tuple[float | None, float | None]:
    if baseline is None or optimized is None:
        return None, None
    delta = baseline - optimized
    if baseline <= 0:
        return delta, None
    return delta, (delta / baseline) * 100.0


def build_rows(summary: dict[str, dict[str, str]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for comparison in COMPARISONS:
        baseline = summary.get(comparison.baseline_mode)
        optimized = summary.get(comparison.optimized_mode)
        baseline_ms = parse_float(baseline.get(comparison.primary_metric) if baseline else None)
        optimized_ms = parse_float(optimized.get(comparison.primary_metric) if optimized else None)
        reduction_ms, reduction_pct = reduction(baseline_ms, optimized_ms)

        baseline_work = parse_float(baseline.get(comparison.baseline_work_metric) if baseline else None)
        optimized_work = parse_float(optimized.get(comparison.optimized_work_metric) if optimized else None)
        work_reduction, work_reduction_pct = reduction(baseline_work, optimized_work)
        quality_value = parse_float(optimized.get(comparison.quality_metric) if optimized else None)

        if baseline is None:
            status = "missing_baseline"
        elif optimized is None:
            status = "missing_optimized"
        elif baseline_ms is None or optimized_ms is None:
            status = "missing_metric"
        elif reduction_ms is not None and reduction_ms >= 0:
            if work_reduction is not None and work_reduction > 0:
                status = "latency_and_work_reduced"
            elif work_reduction is not None and work_reduction <= 0:
                status = "latency_reduced_work_not_reduced"
            else:
                status = "latency_reduced"
        else:
            status = "latency_increased"

        rows.append(
            {
                "optimization_id": comparison.optimization_id,
                "label": comparison.label,
                "status": status,
                "baseline_mode": comparison.baseline_mode,
                "optimized_mode": comparison.optimized_mode,
                "primary_metric": comparison.primary_metric,
                "baseline_ms": fmt(baseline_ms),
                "optimized_ms": fmt(optimized_ms),
                "reduction_ms": fmt(reduction_ms),
                "reduction_pct": pct(reduction_pct),
                "baseline_work_metric": comparison.baseline_work_metric,
                "baseline_work": fmt(baseline_work),
                "optimized_work_metric": comparison.optimized_work_metric,
                "optimized_work": fmt(optimized_work),
                "work_reduction": fmt(work_reduction),
                "work_reduction_pct": pct(work_reduction_pct),
                "quality_metric": comparison.quality_metric,
                "quality_value": fmt(quality_value),
                "interpretation": comparison.interpretation,
            }
        )

    rows.append(
        {
            "optimization_id": "overlay_gate",
            "label": "Overlay gate",
            "status": "not_measured",
            "baseline_mode": "opt_base_no_overlay_gate",
            "optimized_mode": "s12345_full",
            "primary_metric": "stale_or_unstable_overlay_count",
            "baseline_ms": "",
            "optimized_ms": "",
            "reduction_ms": "",
            "reduction_pct": "",
            "baseline_work_metric": "stale_or_unstable_overlay_count",
            "baseline_work": "",
            "optimized_work_metric": "overlay_skipped_unstable_count",
            "optimized_work": "",
            "work_reduction": "",
            "work_reduction_pct": "",
            "quality_metric": "manual_stale_mask_rate",
            "quality_value": "",
            "interpretation": "Requires a dedicated no-gate baseline or manual video labels; current diagnostics do not expose a no-gate runtime mode.",
        }
    )
    return rows


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    fieldnames = list(rows[0].keys())
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def write_markdown(path: Path, rows: list[dict[str, str]], source_summary: Path) -> None:
    with path.open("w", encoding="utf-8") as handle:
        handle.write("# Android Optimization Benchmark\n\n")
        handle.write(f"Source summary: `{source_summary}`\n\n")
        handle.write(
            "This table answers: baseline 대비 각 최적화가 latency/work를 얼마나 줄였는가. "
            "`missing_*` rows must not be presented as measured improvement.\n\n"
        )
        handle.write("| Optimization | Status | Baseline | Optimized | Metric | Baseline ms | Optimized ms | Reduction ms | Reduction % | Work before -> after | Quality/result |\n")
        handle.write("| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |\n")
        for row in rows:
            if row["baseline_work"] and row["optimized_work"]:
                work = (
                    f"{row['baseline_work_metric']} {row['baseline_work']} -> "
                    f"{row['optimized_work_metric']} {row['optimized_work']}"
                )
            elif row["baseline_work"]:
                work = f"baseline {row['baseline_work_metric']} {row['baseline_work']}"
            elif row["optimized_work"]:
                work = f"optimized {row['optimized_work_metric']} {row['optimized_work']}"
            else:
                work = ""
            quality = row["quality_metric"]
            if row["quality_value"]:
                quality = f"{quality} {row['quality_value']}"
            handle.write(
                "| "
                f"{row['label']} | {row['status']} | `{row['baseline_mode']}` | `{row['optimized_mode']}` | "
                f"{row['primary_metric']} | {row['baseline_ms']} | {row['optimized_ms']} | "
                f"{row['reduction_ms']} | {row['reduction_pct']} | {work} | {quality} |\n"
            )
        handle.write("\n## Interpretation Notes\n\n")
        for row in rows:
            handle.write(f"- `{row['optimization_id']}`: {row['interpretation']}\n")


def write_findings(path: Path, rows: list[dict[str, str]], source_summary: Path) -> None:
    with path.open("w", encoding="utf-8") as handle:
        handle.write("# Android Optimization Findings\n\n")
        handle.write(f"Source summary: `{source_summary}`\n\n")
        handle.write("## Executive Read\n\n")
        for row in rows:
            status = row["status"]
            if status == "not_measured":
                handle.write(
                    f"- {row['label']}: not measured yet. {row['interpretation']}\n"
                )
                continue
            if status.startswith("missing_"):
                handle.write(
                    f"- {row['label']}: incomplete because `{status}`. Do not use this as an improvement claim.\n"
                )
                continue

            direction = "reduced" if row["reduction_ms"] and not row["reduction_ms"].startswith("-") else "increased"
            handle.write(
                f"- {row['label']}: {direction} `{row['primary_metric']}` "
                f"from {row['baseline_ms']}ms to {row['optimized_ms']}ms "
                f"({row['reduction_ms']}ms, {row['reduction_pct']}%). "
            )
            if row["work_reduction"]:
                handle.write(
                    f"Work delta: {row['baseline_work_metric']} {row['baseline_work']} -> "
                    f"{row['optimized_work_metric']} {row['optimized_work']} "
                    f"({row['work_reduction']} / {row['work_reduction_pct']}%). "
                )
            if status == "latency_reduced_work_not_reduced":
                handle.write(
                    "Treat as a latency observation, not proof that candidate count was pruned in this fixture. "
                )
            handle.write("\n")

        handle.write("\n## Presentation Cautions\n\n")
        handle.write(
            "- Use `latency_and_work_reduced` as the strongest optimization evidence.\n"
            "- Use `latency_reduced_work_not_reduced` only with the caveat that the work-count proxy did not improve.\n"
            "- Use `latency_increased` rows to explain quality/coverage cost or remaining bottlenecks, not as speed wins.\n"
            "- Manual video review is still required for missed, false, and stale mask quality.\n"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--out-dir", type=Path, required=True)
    args = parser.parse_args()

    summary = read_summary(args.summary)
    rows = build_rows(summary)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    write_csv(args.out_dir / "optimization_summary.csv", rows)
    write_markdown(args.out_dir / "optimization_ppt_table.md", rows, args.summary)
    write_findings(args.out_dir / "optimization_findings.md", rows, args.summary)
    print(f"[OK] optimization_summary={args.out_dir / 'optimization_summary.csv'}")
    print(f"[OK] optimization_ppt_table={args.out_dir / 'optimization_ppt_table.md'}")
    print(f"[OK] optimization_findings={args.out_dir / 'optimization_findings.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
