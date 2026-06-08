#!/usr/bin/env python3
"""Render lightweight latency charts from Chungmaru CSV evidence."""

from __future__ import annotations

import argparse
import csv
import html
import math
import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


DEFAULT_RESULTS_DIR = Path("evaluation/latency/results/current")
DEFAULT_OUTPUT_DIRNAME = "charts"
DEFAULT_INPUTS = [
    "chungmaru-latency-samples.csv",
    "chrome-demo-latency.csv",
    "chrome-demo-attempt-latency.csv",
    "android-e2e-samples.csv",
]

TOTAL_KEYS = [
    "total_to_mask_ms",
    "total_to_mask",
    "duration_ms",
    "first_mask_ms",
    "backend_roundtrip_ms",
    "backend_round_trip_ms",
]

DISPLAY_TOTAL_KEYS = [
    "total_to_mask_ms",
    "total_to_mask",
    "duration_ms",
    "first_mask_ms",
]

STAGE_KEYS = {
    "settings": ["settings_load_ms"],
    "candidate": ["candidate_collect_ms"],
    "parser": ["parser_ms"],
    "pre_backend": ["pre_backend_ms"],
    "local_preflight": ["local_preflight_ms"],
    "backend": ["backend_roundtrip_ms", "backend_round_trip_ms"],
    "backend_reported": ["backend_reported_ms"],
    "decision": ["decision_build_ms"],
    "mask_apply": ["mask_apply_ms"],
    "post_backend": ["post_backend_to_mask_ms"],
}

BUCKETS = [
    ("<=100ms", 0, 100),
    ("100-250ms", 100, 250),
    ("250-500ms", 250, 500),
    ("500-1000ms", 500, 1000),
    (">1000ms", 1000, math.inf),
]


@dataclass(frozen=True)
class LatencyRow:
    source_file: str
    source_group: str
    row_number: int
    run_id: str
    scenario: str
    scene_type: str
    query: str
    total_ms: float
    total_basis: str
    display_relevant: bool
    stages: dict[str, float]
    visible_first_mask_ms: float | None
    raw: dict[str, str]


def number(value: object) -> float | None:
    if value is None:
        return None
    text = str(value).strip().replace(",", "")
    if not text:
        return None
    try:
        parsed = float(text)
    except ValueError:
        return None
    if not math.isfinite(parsed):
        return None
    return parsed


def first_number(row: dict[str, str], keys: Iterable[str]) -> tuple[str, float] | tuple[None, None]:
    for key in keys:
        value = number(row.get(key))
        if value is not None:
            return key, value
    return None, None


def compact_text(value: object, limit: int = 96) -> str:
    text = " ".join(str(value or "").split())
    if len(text) <= limit:
        return text
    return text[: max(0, limit - 1)] + "…"


def source_group_for(source_file: str, row: dict[str, str]) -> str:
    source = str(row.get("source") or "").strip()
    mode = str(row.get("mode") or "").strip()
    scene_type = str(row.get("scene_type") or "").strip()
    if mode == "chrome-google-demo":
        if "attempt" in source_file:
            return f"chrome-demo-attempt:{scene_type or 'unknown'}"
        return f"chrome-demo:{scene_type or 'unknown'}"
    if source:
        return source
    if mode:
        return mode
    return Path(source_file).stem


def is_display_relevant(source_group: str, basis: str | None) -> bool:
    if source_group == "backend-direct":
        return False
    return bool(basis in DISPLAY_TOTAL_KEYS)


def read_latency_rows(results_dir: Path, input_names: list[str]) -> list[LatencyRow]:
    rows: list[LatencyRow] = []
    input_name_set = set(input_names)
    for input_name in input_names:
        path = results_dir / input_name
        if not path.exists():
            continue
        with path.open(newline="", encoding="utf-8-sig") as handle:
            reader = csv.DictReader(handle)
            for row_number, row in enumerate(reader, start=2):
                notes = str(row.get("notes") or "")
                if input_name == "chungmaru-latency-samples.csv":
                    imported_source = ""
                    for part in notes.split(";"):
                        part = part.strip()
                        if part.startswith("imported_from="):
                            imported_source = part.split("=", 1)[1].strip()
                            break
                    if imported_source and imported_source in input_name_set:
                        continue
                basis, total = first_number(row, TOTAL_KEYS)
                if total is None or basis is None:
                    continue
                source_group = source_group_for(input_name, row)
                stages: dict[str, float] = {}
                for stage, keys in STAGE_KEYS.items():
                    _, value = first_number(row, keys)
                    if value is not None:
                        stages[stage] = value
                query = (
                    row.get("query")
                    or row.get("query_or_url")
                    or row.get("url")
                    or row.get("scenario")
                    or ""
                )
                rows.append(
                    LatencyRow(
                        source_file=input_name,
                        source_group=source_group,
                        row_number=row_number,
                        run_id=row.get("run_id", ""),
                        scenario=row.get("scenario") or row.get("query_category") or "",
                        scene_type=row.get("scene_type", ""),
                        query=query,
                        total_ms=total,
                        total_basis=basis,
                        display_relevant=is_display_relevant(source_group, basis),
                        stages=stages,
                        visible_first_mask_ms=number(row.get("visible_first_mask_ms")),
                        raw=row,
                    )
                )
    return rows


def percentile(values: list[float], quantile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    pos = (len(ordered) - 1) * quantile
    lower = math.floor(pos)
    upper = math.ceil(pos)
    if lower == upper:
        return ordered[int(pos)]
    weight = pos - lower
    return ordered[lower] * (1 - weight) + ordered[upper] * weight


def stats(values: list[float]) -> dict[str, float]:
    if not values:
        return {"count": 0, "avg": 0, "median": 0, "p90": 0, "p95": 0, "max": 0, "stddev": 0}
    avg = statistics.fmean(values)
    stddev = statistics.pstdev(values) if len(values) > 1 else 0.0
    return {
        "count": len(values),
        "avg": avg,
        "median": statistics.median(values),
        "p90": percentile(values, 0.90),
        "p95": percentile(values, 0.95),
        "max": max(values),
        "stddev": stddev,
    }


def fmt_ms(value: float) -> str:
    return f"{value:.1f}"


def write_csv(path: Path, rows: list[dict[str, object]], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def build_source_summary(rows: list[LatencyRow]) -> list[dict[str, object]]:
    groups: dict[tuple[str, str], list[LatencyRow]] = defaultdict(list)
    for row in rows:
        groups[(row.source_file, row.source_group)].append(row)
    output: list[dict[str, object]] = []
    for (source_file, source_group), group_rows in sorted(groups.items()):
        values = [row.total_ms for row in group_rows]
        display_values = [row.total_ms for row in group_rows if row.display_relevant]
        group_stats = stats(values)
        display_stats = stats(display_values)
        output.append(
            {
                "source_file": source_file,
                "source_group": source_group,
                "rows": int(group_stats["count"]),
                "display_rows": int(display_stats["count"]),
                "avg_ms": fmt_ms(group_stats["avg"]),
                "median_ms": fmt_ms(group_stats["median"]),
                "p90_ms": fmt_ms(group_stats["p90"]),
                "p95_ms": fmt_ms(group_stats["p95"]),
                "max_ms": fmt_ms(group_stats["max"]),
                "stddev_ms": fmt_ms(group_stats["stddev"]),
                "display_avg_ms": fmt_ms(display_stats["avg"]),
                "display_p95_ms": fmt_ms(display_stats["p95"]),
                "display_max_ms": fmt_ms(display_stats["max"]),
            }
        )
    return output


def build_scenario_summary(rows: list[LatencyRow]) -> list[dict[str, object]]:
    groups: dict[tuple[str, str, str], list[LatencyRow]] = defaultdict(list)
    for row in rows:
        if not row.display_relevant:
            continue
        key = (row.source_group, row.scenario or "unknown", row.scene_type or "")
        groups[key].append(row)
    output: list[dict[str, object]] = []
    for (source_group, scenario, scene_type), group_rows in sorted(groups.items()):
        values = [row.total_ms for row in group_rows]
        group_stats = stats(values)
        output.append(
            {
                "source_group": source_group,
                "scenario": scenario,
                "scene_type": scene_type,
                "rows": int(group_stats["count"]),
                "avg_ms": fmt_ms(group_stats["avg"]),
                "median_ms": fmt_ms(group_stats["median"]),
                "p90_ms": fmt_ms(group_stats["p90"]),
                "p95_ms": fmt_ms(group_stats["p95"]),
                "max_ms": fmt_ms(group_stats["max"]),
                "over_250ms": sum(1 for value in values if value > 250),
                "over_1000ms": sum(1 for value in values if value > 1000),
            }
        )
    return output


def build_worst_cases(rows: list[LatencyRow], limit: int) -> list[dict[str, object]]:
    display_rows = [row for row in rows if row.display_relevant]
    worst = sorted(display_rows, key=lambda row: row.total_ms, reverse=True)[:limit]
    output: list[dict[str, object]] = []
    for rank, row in enumerate(worst, start=1):
        output.append(
            {
                "rank": rank,
                "source_file": row.source_file,
                "source_group": row.source_group,
                "row_number": row.row_number,
                "run_id": row.run_id,
                "scenario": row.scenario,
                "scene_type": row.scene_type,
                "query": compact_text(row.query, 140),
                "total_ms": fmt_ms(row.total_ms),
                "total_basis": row.total_basis,
                "visible_first_mask_ms": "" if row.visible_first_mask_ms is None else fmt_ms(row.visible_first_mask_ms),
                "candidate_collect_ms": fmt_ms(row.stages.get("candidate", 0.0)) if "candidate" in row.stages else "",
                "parser_ms": fmt_ms(row.stages.get("parser", 0.0)) if "parser" in row.stages else "",
                "backend_ms": fmt_ms(row.stages.get("backend", 0.0)) if "backend" in row.stages else "",
                "mask_apply_ms": fmt_ms(row.stages.get("mask_apply", 0.0)) if "mask_apply" in row.stages else "",
                "post_backend_ms": fmt_ms(row.stages.get("post_backend", 0.0)) if "post_backend" in row.stages else "",
            }
        )
    return output


def svg_header(width: int, height: int) -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#fafaf7"/>',
        '<style>text{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;fill:#242424}.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}.muted{fill:#6f7478}.axis{stroke:#d4d1c8;stroke-width:1}.barbg{fill:#ece9df}.accent{fill:#2f7667}.warn{fill:#d58c55}.bad{fill:#c94f45}.line{fill:none;stroke:#2f7667;stroke-width:2}.dot{fill:#2f7667}.dot-warn{fill:#d58c55}.dot-bad{fill:#c94f45}</style>',
    ]


def write_total_timeseries(path: Path, rows: list[LatencyRow]) -> None:
    display_rows = [row for row in rows if row.display_relevant]
    width, height = 1200, 520
    margin_left, margin_top, margin_right, margin_bottom = 72, 64, 32, 64
    plot_w = width - margin_left - margin_right
    plot_h = height - margin_top - margin_bottom
    values = [row.total_ms for row in display_rows]
    max_value = max([250.0, *values], default=250.0)
    y_max = max(300.0, min(max_value, percentile(values, 0.99) * 1.25 if values else 300.0))
    y_max = max(y_max, 300.0)
    lines = svg_header(width, height)
    lines.extend(
        [
            '<text x="72" y="36" font-size="24" font-weight="750">Display latency time series</text>',
            '<text x="72" y="58" font-size="13" class="muted">display-relevant rows only; orange/red points exceed 250/1000ms</text>',
            f'<line x1="{margin_left}" y1="{margin_top + plot_h}" x2="{margin_left + plot_w}" y2="{margin_top + plot_h}" class="axis"/>',
            f'<line x1="{margin_left}" y1="{margin_top}" x2="{margin_left}" y2="{margin_top + plot_h}" class="axis"/>',
        ]
    )
    if not display_rows:
        lines.append('<text x="72" y="120" font-size="18" class="muted">No display latency rows found.</text>')
    else:
        denom = max(1, len(display_rows) - 1)

        def point(index: int, value: float) -> tuple[float, float]:
            x = margin_left + (index / denom) * plot_w
            y = margin_top + plot_h - (min(value, y_max) / y_max) * plot_h
            return x, y

        path_points = []
        for index, row in enumerate(display_rows):
            x, y = point(index, row.total_ms)
            path_points.append(("M" if index == 0 else "L") + f"{x:.1f},{y:.1f}")
        lines.append(f'<path d="{" ".join(path_points)}" class="line" opacity="0.55"/>')
        threshold_y = margin_top + plot_h - (250 / y_max) * plot_h
        lines.append(f'<line x1="{margin_left}" y1="{threshold_y:.1f}" x2="{margin_left + plot_w}" y2="{threshold_y:.1f}" stroke="#d58c55" stroke-dasharray="6 6"/>')
        lines.append(f'<text x="{margin_left + plot_w - 72}" y="{threshold_y - 8:.1f}" font-size="12" class="muted">250ms</text>')
        stride = max(1, len(display_rows) // 700)
        for index, row in enumerate(display_rows):
            if index % stride != 0 and row.total_ms <= 250:
                continue
            x, y = point(index, row.total_ms)
            klass = "dot-bad" if row.total_ms > 1000 else "dot-warn" if row.total_ms > 250 else "dot"
            radius = 3 if row.total_ms > 250 else 2
            label = html.escape(f"{row.source_group} {row.total_ms:.1f}ms {compact_text(row.query, 60)}")
            lines.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{radius}" class="{klass}"><title>{label}</title></circle>')
        x_labels = [
            (0, "start"),
            (len(display_rows) // 2, "middle"),
            (len(display_rows) - 1, "end"),
        ]
        for index, label in x_labels:
            x, _ = point(index, 0)
            lines.append(f'<text x="{x:.1f}" y="{height - 26}" text-anchor="middle" font-size="12" class="muted">{label}</text>')
        for tick in [0, 100, 250, 500, 1000]:
            if tick > y_max:
                continue
            y = margin_top + plot_h - (tick / y_max) * plot_h
            lines.append(f'<text x="{margin_left - 12}" y="{y + 4:.1f}" text-anchor="end" font-size="12" class="muted">{tick}</text>')
            lines.append(f'<line x1="{margin_left}" y1="{y:.1f}" x2="{margin_left + plot_w}" y2="{y:.1f}" class="axis" opacity="0.35"/>')
    lines.append("</svg>")
    path.write_text("\n".join(lines), encoding="utf-8")


def write_source_average_bars(path: Path, rows: list[LatencyRow]) -> None:
    summary = build_source_summary(rows)
    display_summary = [row for row in summary if int(row["display_rows"]) > 0]
    display_summary.sort(key=lambda row: float(row["display_avg_ms"]), reverse=True)
    width = 1200
    row_h = 42
    height = max(220, 100 + len(display_summary) * row_h)
    max_value = max([float(row["display_p95_ms"]) for row in display_summary] + [250.0])
    max_value = max(max_value, 300.0)
    lines = svg_header(width, height)
    lines.extend(
        [
            '<text x="72" y="36" font-size="24" font-weight="750">Average and p95 by source</text>',
            '<text x="72" y="58" font-size="13" class="muted">bar = average, marker = p95</text>',
        ]
    )
    label_w = 330
    bar_x = 390
    bar_w = 680
    for index, row in enumerate(display_summary):
        y = 92 + index * row_h
        avg = float(row["display_avg_ms"])
        p95 = float(row["display_p95_ms"])
        avg_w = max(2, min(bar_w, avg / max_value * bar_w))
        p95_x = bar_x + min(bar_w, p95 / max_value * bar_w)
        lines.append(f'<text x="72" y="{y + 21}" font-size="14" class="mono">{html.escape(str(row["source_group"]))}</text>')
        lines.append(f'<rect x="{bar_x}" y="{y}" width="{bar_w}" height="24" class="barbg"/>')
        lines.append(f'<rect x="{bar_x}" y="{y}" width="{avg_w:.1f}" height="24" class="accent"/>')
        lines.append(f'<line x1="{p95_x:.1f}" y1="{y - 4}" x2="{p95_x:.1f}" y2="{y + 28}" stroke="#c94f45" stroke-width="3"/>')
        lines.append(f'<text x="{bar_x + bar_w + 18}" y="{y + 19}" font-size="13">{avg:.1f} / {p95:.1f}ms</text>')
    lines.append("</svg>")
    path.write_text("\n".join(lines), encoding="utf-8")


def write_stage_breakdown(path: Path, rows: list[LatencyRow]) -> None:
    display_rows = [row for row in rows if row.display_relevant]
    groups: dict[str, list[LatencyRow]] = defaultdict(list)
    for row in display_rows:
        groups[row.source_group].append(row)
    stage_order = ["candidate", "parser", "pre_backend", "backend", "decision", "mask_apply", "post_backend"]
    colors = {
        "candidate": "#89a6a0",
        "parser": "#afc4d6",
        "pre_backend": "#c9b99b",
        "backend": "#d58c55",
        "decision": "#7e8b6b",
        "mask_apply": "#2f7667",
        "post_backend": "#c94f45",
    }
    rows_out = []
    for group, group_rows in groups.items():
        averages = {}
        for stage in stage_order:
            values = [row.stages[stage] for row in group_rows if stage in row.stages]
            averages[stage] = statistics.fmean(values) if values else 0.0
        total = sum(averages.values())
        rows_out.append((group, averages, total))
    rows_out.sort(key=lambda item: item[2], reverse=True)
    width = 1200
    row_h = 44
    height = max(260, 132 + len(rows_out) * row_h)
    max_value = max([item[2] for item in rows_out] + [250.0])
    lines = svg_header(width, height)
    lines.extend(
        [
            '<text x="72" y="36" font-size="24" font-weight="750">Average stage breakdown</text>',
            '<text x="72" y="58" font-size="13" class="muted">stacked from measured stage columns; missing stages are left blank</text>',
        ]
    )
    legend_x = 72
    for stage in stage_order:
        lines.append(f'<rect x="{legend_x}" y="78" width="14" height="14" fill="{colors[stage]}"/>')
        lines.append(f'<text x="{legend_x + 20}" y="90" font-size="12" class="muted">{stage}</text>')
        legend_x += 118
    label_w = 330
    bar_x = 390
    bar_w = 680
    for index, (group, averages, total) in enumerate(rows_out):
        y = 118 + index * row_h
        lines.append(f'<text x="72" y="{y + 21}" font-size="14" class="mono">{html.escape(group)}</text>')
        lines.append(f'<rect x="{bar_x}" y="{y}" width="{bar_w}" height="24" class="barbg"/>')
        x = bar_x
        for stage in stage_order:
            value = averages[stage]
            if value <= 0:
                continue
            w = max(1, value / max_value * bar_w)
            lines.append(f'<rect x="{x:.1f}" y="{y}" width="{w:.1f}" height="24" fill="{colors[stage]}"><title>{stage}: {value:.1f}ms</title></rect>')
            x += w
        lines.append(f'<text x="{bar_x + bar_w + 18}" y="{y + 19}" font-size="13">{total:.1f}ms</text>')
    lines.append("</svg>")
    path.write_text("\n".join(lines), encoding="utf-8")


def write_bucket_chart(path: Path, rows: list[LatencyRow]) -> None:
    display_rows = [row for row in rows if row.display_relevant]
    counts = Counter()
    for row in display_rows:
        for label, low, high in BUCKETS:
            if row.total_ms > low and row.total_ms <= high:
                counts[label] += 1
                break
    width, height = 920, 420
    max_count = max(counts.values(), default=1)
    lines = svg_header(width, height)
    lines.extend(
        [
            '<text x="60" y="36" font-size="24" font-weight="750">Latency buckets</text>',
            '<text x="60" y="58" font-size="13" class="muted">display-relevant total latency distribution</text>',
        ]
    )
    bar_w = 120
    gap = 40
    base_y = 340
    max_h = 230
    for index, (label, _, _) in enumerate(BUCKETS):
        count = counts[label]
        x = 70 + index * (bar_w + gap)
        h = 0 if max_count == 0 else count / max_count * max_h
        klass = "bad" if label == ">1000ms" else "warn" if "250" in label or "500" in label or "1000" in label else "accent"
        lines.append(f'<rect x="{x}" y="{base_y - h:.1f}" width="{bar_w}" height="{h:.1f}" class="{klass}"/>')
        lines.append(f'<text x="{x + bar_w / 2:.1f}" y="{base_y + 28}" text-anchor="middle" font-size="13" class="mono">{label}</text>')
        lines.append(f'<text x="{x + bar_w / 2:.1f}" y="{base_y - h - 10:.1f}" text-anchor="middle" font-size="18" font-weight="750">{count}</text>')
    lines.append("</svg>")
    path.write_text("\n".join(lines), encoding="utf-8")


def write_report(path: Path, rows: list[LatencyRow], source_summary: list[dict[str, object]], worst: list[dict[str, object]]) -> None:
    display_rows = [row for row in rows if row.display_relevant]
    all_stats = stats([row.total_ms for row in rows])
    display_stats = stats([row.total_ms for row in display_rows])
    lines = [
        "# Chungmaru Latency Chart Report",
        "",
        "Generated from CSV files in `evaluation/latency/results/current`.",
        "",
        "## Scope",
        "",
        f"- Total parsed rows: {len(rows)}",
        f"- Display-relevant rows: {len(display_rows)}",
        "- Backend-direct rows are separated from display latency because they do not measure parsing, DOM rendering, or mask application.",
        "",
        "## Overall",
        "",
        "| Scope | Rows | Avg ms | Median ms | P90 ms | P95 ms | Max ms | Stddev ms |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        f"| All rows | {int(all_stats['count'])} | {fmt_ms(all_stats['avg'])} | {fmt_ms(all_stats['median'])} | {fmt_ms(all_stats['p90'])} | {fmt_ms(all_stats['p95'])} | {fmt_ms(all_stats['max'])} | {fmt_ms(all_stats['stddev'])} |",
        f"| Display rows | {int(display_stats['count'])} | {fmt_ms(display_stats['avg'])} | {fmt_ms(display_stats['median'])} | {fmt_ms(display_stats['p90'])} | {fmt_ms(display_stats['p95'])} | {fmt_ms(display_stats['max'])} | {fmt_ms(display_stats['stddev'])} |",
        "",
        "## Charts",
        "",
        "- `total-latency-timeseries.svg`: every display-relevant row in input order.",
        "- `source-average-bars.svg`: source-level average and p95.",
        "- `stage-breakdown-bars.svg`: average stage timing when stage columns exist.",
        "- `latency-buckets.svg`: distribution against 100/250/500/1000ms buckets.",
        "",
        "## Worst Cases",
        "",
        "| Rank | Source | Query / Scenario | Total ms | Basis | Backend ms | Candidate ms | Parser ms | Mask ms |",
        "| ---: | --- | --- | ---: | --- | ---: | ---: | ---: | ---: |",
    ]
    for row in worst[:12]:
        query = html.escape(str(row["query"]))
        lines.append(
            f"| {row['rank']} | {html.escape(str(row['source_group']))} | {query} | {row['total_ms']} | "
            f"{row['total_basis']} | {row['backend_ms']} | {row['candidate_collect_ms']} | {row['parser_ms']} | {row['mask_apply_ms']} |"
        )
    lines.extend(
        [
            "",
            "## Source Summary",
            "",
            "| Source | Rows | Display rows | Avg ms | P95 ms | Max ms | Display avg | Display p95 |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for row in source_summary:
        lines.append(
            f"| {html.escape(str(row['source_group']))} | {row['rows']} | {row['display_rows']} | {row['avg_ms']} | "
            f"{row['p95_ms']} | {row['max_ms']} | {row['display_avg_ms']} | {row['display_p95_ms']} |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def render(args: argparse.Namespace) -> int:
    results_dir = args.results_dir
    output_dir = args.output_dir or results_dir / DEFAULT_OUTPUT_DIRNAME
    output_dir.mkdir(parents=True, exist_ok=True)
    rows = read_latency_rows(results_dir, args.inputs)
    source_summary = build_source_summary(rows)
    scenario_summary = build_scenario_summary(rows)
    worst_cases = build_worst_cases(rows, args.worst_limit)

    write_csv(
        output_dir / "latency-source-summary.csv",
        source_summary,
        [
            "source_file",
            "source_group",
            "rows",
            "display_rows",
            "avg_ms",
            "median_ms",
            "p90_ms",
            "p95_ms",
            "max_ms",
            "stddev_ms",
            "display_avg_ms",
            "display_p95_ms",
            "display_max_ms",
        ],
    )
    write_csv(
        output_dir / "latency-scenario-summary.csv",
        scenario_summary,
        [
            "source_group",
            "scenario",
            "scene_type",
            "rows",
            "avg_ms",
            "median_ms",
            "p90_ms",
            "p95_ms",
            "max_ms",
            "over_250ms",
            "over_1000ms",
        ],
    )
    write_csv(
        output_dir / "latency-worst-cases.csv",
        worst_cases,
        [
            "rank",
            "source_file",
            "source_group",
            "row_number",
            "run_id",
            "scenario",
            "scene_type",
            "query",
            "total_ms",
            "total_basis",
            "visible_first_mask_ms",
            "candidate_collect_ms",
            "parser_ms",
            "backend_ms",
            "mask_apply_ms",
            "post_backend_ms",
        ],
    )
    write_total_timeseries(output_dir / "total-latency-timeseries.svg", rows)
    write_source_average_bars(output_dir / "source-average-bars.svg", rows)
    write_stage_breakdown(output_dir / "stage-breakdown-bars.svg", rows)
    write_bucket_chart(output_dir / "latency-buckets.svg", rows)
    write_report(output_dir / "latency-chart-report.md", rows, source_summary, worst_cases)

    display_count = sum(1 for row in rows if row.display_relevant)
    print(f"rows={len(rows)} display_rows={display_count} output={output_dir}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Render Chungmaru latency charts from accumulated CSV logs.")
    parser.add_argument("--results-dir", type=Path, default=DEFAULT_RESULTS_DIR)
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--inputs", nargs="+", default=DEFAULT_INPUTS)
    parser.add_argument("--worst-limit", type=int, default=40)
    return parser


def main() -> int:
    return render(build_parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
