# Android Pipeline Benchmark

This directory is populated by:

```bash
RUNS_PER_MODE=10 RECORD_SECONDS=12 ./scripts/android-pipeline-benchmark.sh run
```

Expected generated files:

| File | Purpose |
| --- | --- |
| `raw_runs.csv` | Per-run Android stage latency and count metrics |
| `stage_latency.csv` | One row per run and processing stage with latency/count/quality fields |
| `stage_latency_summary.csv` | Mode-by-stage latency average/min/max for presentation tables |
| `summary_by_mode.csv` | Mode-level averages and ranges |
| `stage_delta.csv` | Incremental cumulative-stage comparison |
| `ppt_table.md` | Presentation-ready markdown tables |

The benchmark uses the app runtime preference `pipeline_experiment_mode`, not a label-only `FEATURE_SET`.

Current generated CSVs include both the legacy stage timings and the newer
collect-stage breakdown columns:

- `node_collection_ms`
- `visual_roi_planning_ms`
- `screen_candidate_extraction_ms`
- `candidate_post_processing_ms`
- `candidate_parallel_wait_ms`

Older 2026-06-02 benchmark artifacts predate the collect-stage breakdown, so
those new columns are blank in the 90-run comparison. Use
`../android-pipeline-benchmark-smoke-diagnostics/` to verify that the new fields
are populated by the current APK.
