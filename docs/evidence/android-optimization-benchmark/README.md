# Android Optimization Benchmark

This evidence folder is for optimization-before/after comparisons, not stage-cost measurement.

Use this when the question is:

> 각 최적화를 적용하면서 baseline 대비 latency/work가 얼마나 줄었고, 그 결과 어떤 처리 상황을 얻었는가?

It differs from `docs/evidence/android-pipeline-benchmark`, which measures the cost of enabling individual stages.

## Modes

The wrapper runs these modes by default:

- `opt_base_all_nodes_backend`: broad all-visible-node backend baseline.
- `s12_collect_backend`: optimized candidate selection + backend.
- `opt_base_fullscreen_ocr`: full-screen OCR baseline.
- `s123_collect_backend_ocr`: optimized ROI OCR.
- `opt_base_full_box_overlay`: broad full-node-box overlay baseline.
- `s12345_full`: optimized full pipeline.

## Outputs

- `raw_runs.csv`: one row per emulator run.
- `summary_by_mode.csv`: mode-level averages.
- `optimization_summary.csv`: before/after reduction table.
- `optimization_ppt_table.md`: presentation-ready table with missing-baseline warnings.
- `optimization_findings.md`: short interpretation notes and presentation cautions.

## Command

```bash
RUNS_PER_MODE=10 RECORD_SECONDS=8 ./scripts/android-optimization-benchmark.sh run
```

If a run is interrupted, reuse the same `BATCH_ID` and `BATCH_ROOT`, then rerun a single mode:

```bash
BATCH_ID=<batch-id> \
BATCH_ROOT=/private/tmp/chungmaru-android-pipeline-benchmark-<batch-id> \
MODES=s12345_full \
START_INDEX=6 \
RUNS_PER_MODE=10 \
RECORD_SECONDS=8 \
./scripts/android-optimization-benchmark.sh run
```

Then aggregate:

```bash
BATCH_ID=<batch-id> \
BATCH_ROOT=/private/tmp/chungmaru-android-pipeline-benchmark-<batch-id> \
./scripts/android-optimization-benchmark.sh aggregate
```

## Remaining Gap

`overlay_gate` still needs a dedicated no-gate runtime mode or manual video labels for stale/unstable mask counts. Until that exists, do not claim measured latency reduction or stale-mask reduction for overlay gate.
