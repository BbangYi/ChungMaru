# Android Pipeline Benchmark

Generated: 2026-06-03T06:01:04Z

This report is generated from real Android runtime stage toggles via `pipeline_experiment_mode`.

## Summary By Mode

| Mode | Stages | Runs | Collect ms | Node ms | Candidate ms | Backend API ms | OCR ms | Coord ms | Display ms | Observed total ms | Candidates | Rendered | Offensive |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `s1_collect_only` | 1 | 10 | 697.3 |  |  | 0.0 |  | 34.2 |  | 697.3 | 14.0 | 0.0 | 0.0 |
| `s2_backend_only` | 2 | 10 | 607.2 |  |  | 378.6 |  | 24.7 |  | 668.1 | 14.0 | 0.0 | 1.0 |
| `s3_ocr_roi_only` | 3 | 10 | 734.9 |  |  | 0.0 | 351.7 | 60.9 |  | 734.9 | 14.0 | 0.0 | 0.0 |
| `s4_coord_only` | 4 | 10 | 806.6 |  |  | 0.0 |  | 52.7 |  | 806.6 | 17.0 | 0.0 | 0.0 |
| `s5_overlay_only` | 5 | 10 | 642.4 |  |  | 0.0 |  | 52.8 |  | 642.4 | 14.0 | 0.0 | 0.0 |
| `s12_collect_backend` | 1+2 | 10 | 558.2 |  |  | 267.3 |  | 21.6 |  | 561.2 | 14.0 | 0.0 | 1.0 |
| `s123_collect_backend_ocr` | 1+2+3 | 10 | 602.0 |  |  | 252.5 | 279.8 | 49.3 | 231.2 | 602.0 | 14.0 | 0.0 | 1.0 |
| `s1234_collect_backend_ocr_coord` | 1+2+3+4 | 10 | 680.7 |  |  | 277.5 | 177.4 | 35.9 | 159.1 | 680.7 | 17.0 | 0.0 | 2.0 |
| `s12345_full` | 1+2+3+4+5 | 10 | 710.4 |  |  | 375.2 | 354.2 | 58.1 | 151.2 | 713.9 | 17.0 | 3.3 | 2.0 |

## Cumulative Stage Delta

| Stage | Isolated mode total ms | Before | After | Delta ms |
| --- | ---: | --- | --- | ---: |
| 02 backend | 668.1 | `s1_collect_only` 697.3ms | `s12_collect_backend` 561.2ms | -136.1 |
| 03 OCR ROI | 734.9 | `s12_collect_backend` 561.2ms | `s123_collect_backend_ocr` 602.0ms | 40.8 |
| 04 coordinate | 806.6 | `s123_collect_backend_ocr` 602.0ms | `s1234_collect_backend_ocr_coord` 680.7ms | 78.7 |
| 05 overlay gate | 642.4 | `s1234_collect_backend_ocr_coord` 680.7ms | `s12345_full` 713.9ms | 33.2 |

## Evidence

- Raw CSV: `raw_runs.csv`
- Stage latency CSV: `stage_latency.csv`
- Stage latency summary CSV: `stage_latency_summary.csv`
- Summary CSV: `summary_by_mode.csv`
- Delta CSV: `stage_delta.csv`
- Artifact root: `/private/tmp/chungmaru-android-pipeline-benchmark-20260602T174241/runs`

Manual video review is still required for missed/false/stale mask quality.
