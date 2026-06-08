# Chungmaru Latency Report

- Generated: 2026-06-06T16:21:46+09:00
- Source CSV: `evaluation/latency/results/mac-android-smoke-20260606T1735KST/android-e2e-samples.csv`
- Cell format: `avg / p95 ms`

## candidate_collect_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 2253.0 / 2253.0 |

## pre_backend_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 2253.0 / 2253.0 |

## backend_roundtrip_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 389.0 / 389.0 |

## decision_build_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 388.0 / 388.0 |

## mask_apply_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 111.0 / 111.0 |

## post_backend_to_mask_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 388.0 / 388.0 |

## first_mask_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 2253.0 / 2253.0 |

## total_to_mask_ms

| Scenario | 14 |
| --- | --- |
| pipeline-01-02-03-04-05 | 2253.0 / 2253.0 |

## Slowest sample candidates

| Metric | ms | Source | Scenario | Batch | Sample | Likely first check |
| --- | ---: | --- | --- | ---: | ---: | --- |
| candidate_collect_ms | 2253.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | screen state / accessibility node count |
| pre_backend_ms | 2253.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | DOM candidates / parser noise / dedupe |
| first_mask_ms | 2253.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | combined visible pipeline |
| total_to_mask_ms | 2253.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | screen state / accessibility node count |
| backend_roundtrip_ms | 389.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | backend cold path / queue / network reuse |
| decision_build_ms | 388.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | span/bounds verification or overlay render |
| post_backend_to_mask_ms | 388.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | span/bounds verification or overlay render |
| mask_apply_ms | 111.0 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 14 | 1 | span/bounds verification or overlay render |
