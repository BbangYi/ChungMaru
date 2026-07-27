# Android Pipeline Benchmark

Generated: 2026-06-09T11:12:36Z

This report is generated from real Android runtime stage toggles via `pipeline_experiment_mode`.

## Summary By Mode

| Mode | Stages | Runs | Collect ms | Node ms | Candidate ms | Backend API ms | OCR ms | Coord ms | Display ms | Observed total ms | Candidates | Rendered | Offensive |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `opt_base_fullscreen_ocr` | 1+2+3 | 1 | 243.0 | 230.0 | 13.0 | 591.0 |  | 13.0 |  | 867.0 | 7.0 | 0.0 | 0.0 |

## Cumulative Stage Delta

| Stage | Isolated mode total ms | Before | After | Delta ms |
| --- | ---: | --- | --- | ---: |
| 02 backend |  | `s1_collect_only` ms | `s12_collect_backend` ms |  |
| 03 OCR ROI |  | `s12_collect_backend` ms | `s123_collect_backend_ocr` ms |  |
| 04 coordinate |  | `s123_collect_backend_ocr` ms | `s1234_collect_backend_ocr_coord` ms |  |
| 05 overlay gate |  | `s1234_collect_backend_ocr_coord` ms | `s12345_full` ms |  |

## Evidence

- Raw CSV: `raw_runs.csv`
- Stage latency CSV: `stage_latency.csv`
- Stage latency summary CSV: `stage_latency_summary.csv`
- Summary CSV: `summary_by_mode.csv`
- Delta CSV: `stage_delta.csv`
- Artifact root: `/private/tmp/chungmaru-android-google-quick-20260609T2012/runs`

Manual video review is still required for missed/false/stale mask quality.
