# Chrome E2E Fixture Latency Worst-Case Report

- Run ID: `chrome-10k-fixture-20260607T2314`
- Row count: `10000`
- Detection count: `57812` requested text units
- Analysis unit count: `78947`
- Latency basis: `total_to_mask_ms`, measured from fixture row start to masking completion.

## Summary

| Segment | Rows | Avg ms | Median ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| All rows | 10000 | 78.42 | 68.0 | 203.0 | 270.0 | 2161.0 |
| Actual rows >16ms | 9515 | 82.18 | 69.0 | 205.0 | 273.0 | 2161.0 |
| All rows excluding >=1000ms outlier | 9999 | 78.21 | 68.0 | 203.0 | 269.0 | 830.0 |
| Actual rows >16ms and <1000ms | 9514 | 81.96 | 69.0 | 205.0 | 272.0 | 830.0 |

## Scenario Distribution

| Scenario | Rows |
| --- | ---: |
| bypass | 2498 |
| mixed | 2502 |
| profanity | 2499 |
| search-result | 2501 |

## Latency Buckets

| Bucket | Rows |
| --- | ---: |
| extreme>1000ms | 1 |
| fast<=50ms | 1544 |
| frame-fast<=16ms | 485 |
| normal<=100ms | 6672 |
| noticeable<=250ms | 1136 |
| slow<=500ms | 161 |
| very-slow<=1000ms | 1 |

## Dominant Stage

| Stage | Rows |
| --- | ---: |
| backend_roundtrip_ms | 9675 |
| backend_internal_avg_ms | 252 |
| pre_backend_ms | 57 |
| candidate_collect_ms | 14 |
| first_mask_ms | 2 |

## Top 10 Worst Rows

| Rank | Sample | Scenario | Batch | Requested | Total ms | Backend ms | Dominant stage | Notes |
| ---: | ---: | --- | ---: | ---: | ---: | ---: | --- | --- |
| 1 | 324 | bypass | 16 | 10 | 2161 | 2158 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 2 | 323 | bypass | 8 | 5 | 830 | 828 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 3 | 1 | mixed | 4 | 5 | 484 | 481 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 4 | 440 | profanity | 8 | 8 | 467 | 465 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 5 | 4521 | bypass | 4 | 5 | 456 | 456 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 6 | 2940 | mixed | 4 | 5 | 448 | 447 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 7 | 3537 | bypass | 4 | 5 | 445 | 444 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 8 | 322 | bypass | 4 | 5 | 438 | 437 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 9 | 2584 | search-result | 8 | 5 | 431 | 429 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |
| 10 | 1522 | bypass | 4 | 5 | 418 | 417 | backend_roundtrip_ms | real Chrome unpacked extension content-script pipeline |

## Absolute Worst

- sample `324`, scenario `bypass`, batch `16`: `2161ms` total, `2158ms` backend.

## Practical Worst Under 1000ms

- sample `323`, scenario `bypass`, batch `8`: `830ms` total, `828ms` backend.

## Interpretation

- `backend_roundtrip_ms` is the dominant stage for nearly all slow rows, so spikes are backend/API roundtrip dominated rather than DOM mask application dominated.
- Rows at or below 16ms are fast/cache-like rows and should not be mixed with user-visible first-analysis latency when presenting average masking speed.
- Use the breakdown CSV for row-level worst-case inspection and this report for presentation-ready summary.
