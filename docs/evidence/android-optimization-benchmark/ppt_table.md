# Android Pipeline Benchmark

Generated: 2026-06-03T01:24:50Z

This report is generated from real Android runtime stage toggles via `pipeline_experiment_mode`.

## Summary By Mode

| Mode | Stages | Runs | Collect ms | Backend API ms | OCR ms | Coord ms | Display ms | Observed total ms | Candidates | Rendered | Offensive |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `opt_base_all_nodes_backend` | 1+2 | 10 | 657.1 | 546.3 |  | 0.9 |  | 773.3 | 14.0 | 0.0 | 1.0 |
| `s12_collect_backend` | 1+2 | 10 | 519.3 | 207.1 |  | 2.9 |  | 519.3 | 14.0 | 0.0 | 1.0 |
| `opt_base_fullscreen_ocr` | 1+2+3 | 10 | 766.1 | 292.4 | 174.1 | 5.0 | 65.0 | 766.1 | 14.1 | 0.0 | 1.0 |
| `s123_collect_backend_ocr` | 1+2+3 | 10 | 643.6 | 204.0 | 220.2 | 3.2 | 208.6 | 643.6 | 14.0 | 0.0 | 1.0 |
| `opt_base_full_box_overlay` | 1+2+5 | 10 | 566.4 | 332.8 |  | 8.3 | 19.8 | 566.4 | 14.0 | 3.0 | 1.0 |
| `s12345_full` | 1+2+3+4+5 | 10 | 751.8 | 278.1 | 363.3 | 3.4 | 100.9 | 755.4 | 17.0 | 3.2 | 2.0 |

## Cumulative Stage Delta

| Stage | Isolated mode total ms | Before | After | Delta ms |
| --- | ---: | --- | --- | ---: |
| 02 backend |  | `s1_collect_only` ms | `s12_collect_backend` 519.3ms |  |
| 03 OCR ROI |  | `s12_collect_backend` 519.3ms | `s123_collect_backend_ocr` 643.6ms | 124.3 |
| 04 coordinate |  | `s123_collect_backend_ocr` 643.6ms | `s1234_collect_backend_ocr_coord` ms |  |
| 05 overlay gate |  | `s1234_collect_backend_ocr_coord` ms | `s12345_full` 755.4ms |  |

## Evidence

- Raw CSV: `raw_runs.csv`
- Stage latency CSV: `stage_latency.csv`
- Stage latency summary CSV: `stage_latency_summary.csv`
- Summary CSV: `summary_by_mode.csv`
- Delta CSV: `stage_delta.csv`
- Artifact root: `/private/tmp/chungmaru-android-pipeline-benchmark-20260602T200405/runs`

Manual video review is still required for missed/false/stale mask quality.
