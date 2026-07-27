# Chungmaru Latency Report

- Generated: 2026-06-08T00:55:45+09:00
- Source CSV: `evaluation/latency/results/current/chrome-e2e-samples.csv`
- Run ID: `chrome-10k-fixture-20260607T2314`
- Cell format: `avg / p95 ms`

## candidate_collect_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 1.01 / 1.0 | 1.091 / 2.0 | 1.353 / 2.0 |
| bypass | 1.017 / 1.0 | 1.115 / 2.0 | 1.402 / 2.0 |
| search-result | 1.015 / 1.0 | 1.09 / 2.0 | 1.346 / 2.0 |
| mixed | 1.026 / 1.0 | 1.09 / 2.0 | 1.331 / 2.0 |

## pre_backend_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 1.033 / 1.0 | 1.635 / 2.0 | 1.78 / 3.0 |
| bypass | 1.039 / 1.0 | 1.592 / 2.0 | 1.747 / 3.0 |
| search-result | 1.051 / 1.0 | 1.641 / 2.0 | 1.789 / 3.0 |
| mixed | 1.062 / 2.0 | 1.589 / 2.0 | 1.746 / 3.0 |

## local_preflight_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 1.0 / 1.0 | 1.0 / 1.0 | 1.018 / 1.0 |
| bypass | 1.0 / 1.0 | 1.02 / 1.0 | 1.023 / 1.0 |
| search-result | 1.714 / 6.0 | 1.0 / 1.0 | 1.0 / 1.0 |
| mixed | 1.4 / 2.0 | 1.0 / 1.0 | 1.0 / 1.0 |

## backend_roundtrip_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 55.973 / 89.0 | 77.969 / 103.0 | 67.164 / 93.0 |
| bypass | 110.664 / 276.0 | 113.154 / 225.0 | 109.418 / 224.0 |
| search-result | 57.637 / 88.0 | 59.292 / 86.0 | 59.08 / 84.0 |
| mixed | 67.486 / 195.0 | 76.788 / 186.0 | 72.925 / 183.0 |

## backend_internal_avg_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 14.061 / 22.968 | 10.344 / 15.176 | 8.973 / 14.276 |
| bypass | 21.205 / 40.674 | 15.856 / 34.715 | 15.266 / 35.225 |
| search-result | 13.968 / 23.642 | 12.3 / 22.317 | 12.187 / 22.273 |
| mixed | 16.095 / 30.968 | 15.598 / 31.542 | 14.808 / 30.318 |

## backend_model_avg_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 13.997 / 22.791 | 10.17 / 14.992 | 8.78 / 14.094 |
| bypass | 21.213 / 40.601 | 15.768 / 34.961 | 15.168 / 35.315 |
| search-result | 13.955 / 23.748 | 13.279 / 22.262 | 12.932 / 22.725 |
| mixed | 16.088 / 30.827 | 15.679 / 31.347 | 14.872 / 30.09 |

## decision_build_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 1.0 / 1.0 | - | - |
| bypass | - | - | - |
| search-result | - | - | - |
| mixed | - | - | - |

## post_backend_to_mask_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 1.0 / 1.0 | - | - |
| bypass | - | 1.0 / 1.0 | - |
| search-result | 1.0 / 1.0 | - | - |
| mixed | - | - | - |

## first_mask_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 7.823 / 44.0 | 6.237 / 38.0 | 6.374 / 41.0 |
| bypass | 1.246 / 2.0 | 1.864 / 3.0 | 2.169 / 3.0 |
| search-result | 1.28 / 2.0 | 1.819 / 3.0 | 2.048 / 3.0 |
| mixed | 5.112 / 32.0 | 4.015 / 26.0 | 3.832 / 26.0 |

## total_to_mask_ms

| Scenario | 4 | 8 | 16 |
| --- | --- | --- | --- |
| profanity | 56.952 / 89.0 | 79.523 / 105.0 | 69.084 / 94.0 |
| bypass | 111.106 / 274.0 | 114.445 / 226.0 | 111.2 / 225.0 |
| search-result | 58.547 / 90.0 | 60.808 / 88.0 | 60.789 / 86.0 |
| mixed | 68.32 / 197.0 | 77.227 / 187.0 | 73.131 / 184.0 |

## Slowest sample candidates

| Metric | ms | Source | Scenario | Batch | Sample | Likely first check |
| --- | ---: | --- | --- | ---: | ---: | --- |
| total_to_mask_ms | 2161.0 | extension-lastStats | bypass | 16 | 324 | combined visible pipeline |
| backend_roundtrip_ms | 2158.0 | extension-lastStats | bypass | 16 | 324 | backend cold path / queue / network reuse |
| total_to_mask_ms | 830.0 | extension-lastStats | bypass | 8 | 323 | combined visible pipeline |
| backend_roundtrip_ms | 828.0 | extension-lastStats | bypass | 8 | 323 | backend cold path / queue / network reuse |
| total_to_mask_ms | 484.0 | extension-lastStats | mixed | 4 | 1 | combined visible pipeline |
| backend_roundtrip_ms | 481.0 | extension-lastStats | mixed | 4 | 1 | backend cold path / queue / network reuse |
| total_to_mask_ms | 467.0 | extension-lastStats | profanity | 8 | 440 | combined visible pipeline |
| backend_roundtrip_ms | 465.0 | extension-lastStats | profanity | 8 | 440 | backend cold path / queue / network reuse |
| backend_roundtrip_ms | 456.0 | extension-lastStats | bypass | 4 | 4521 | backend cold path / queue / network reuse |
| total_to_mask_ms | 456.0 | extension-lastStats | bypass | 4 | 4521 | combined visible pipeline |
| total_to_mask_ms | 448.0 | extension-lastStats | mixed | 4 | 2940 | combined visible pipeline |
| backend_roundtrip_ms | 447.0 | extension-lastStats | mixed | 4 | 2940 | backend cold path / queue / network reuse |
