# Chungmaru Latency Chart Report

Generated from CSV files in `evaluation/latency/results/current`.

## Scope

- Total parsed rows: 11725
- Display-relevant rows: 10646
- Backend-direct rows are separated from display latency because they do not measure parsing, DOM rendering, or mask application.

## Overall

| Scope | Rows | Avg ms | Median ms | P90 ms | P95 ms | Max ms | Stddev ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| All rows | 11725 | 82.2 | 67.0 | 146.0 | 206.0 | 4120.9 | 123.8 |
| Display rows | 10646 | 79.5 | 67.0 | 148.0 | 204.0 | 2253.0 | 63.3 |

## Charts

- `total-latency-timeseries.svg`: every display-relevant row in input order.
- `source-average-bars.svg`: source-level average and p95.
- `stage-breakdown-bars.svg`: average stage timing when stage columns exist.
- `latency-buckets.svg`: distribution against 100/250/500/1000ms buckets.

## Worst Cases

| Rank | Source | Query / Scenario | Total ms | Basis | Backend ms | Candidate ms | Parser ms | Mask ms |
| ---: | --- | --- | ---: | --- | ---: | ---: | ---: | ---: |
| 1 | android-pipeline-benchmark | pipeline-01-02-03-04-05 | 2253.0 | total_to_mask_ms | 389.0 | 2253.0 |  | 111.0 |
| 2 | extension-lastStats | http://127.0.0.1:8877/ | 2161.0 | total_to_mask_ms | 2158.0 | 1.0 |  |  |
| 3 | chrome-quick-qa | 틀딱들은 버스에서 내려라 | 903.0 | total_to_mask_ms | 482.0 | 397.0 | 1.0 | 0.0 |
| 4 | extension-lastStats | http://127.0.0.1:8877/ | 830.0 | total_to_mask_ms | 828.0 | 1.0 |  |  |
| 5 | chrome-quick-qa | 병신아 꺼져 | 808.0 | total_to_mask_ms | 384.0 | 366.0 | 38.0 | 0.0 |
| 6 | chrome-quick-qa | 차별금지법 기사 | 722.0 | total_to_mask_ms | 424.0 |  |  |  |
| 7 | chrome-quick-qa | 출장 일정 안내 | 720.0 | total_to_mask_ms | 655.0 | 46.0 | 5.0 | 0.0 |
| 8 | chrome-quick-qa | 차별금지법 기사 | 578.0 | total_to_mask_ms | 258.0 |  |  |  |
| 9 | chrome-demo:google-search | ㅅ ㅂ 뜻 | 504.0 | total_to_mask_ms | 402.0 | 88.0 | 2.0 | 0.0 |
| 10 | extension-lastStats | http://127.0.0.1:8877/ | 484.0 | total_to_mask_ms | 481.0 | 1.0 |  |  |
| 11 | extension-lastStats | http://127.0.0.1:8877/ | 467.0 | total_to_mask_ms | 465.0 | 1.0 |  |  |
| 12 | extension-lastStats | http://127.0.0.1:8877/ | 456.0 | total_to_mask_ms | 456.0 |  |  |  |

## Source Summary

| Source | Rows | Display rows | Avg ms | P95 ms | Max ms | Display avg | Display p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| android-pipeline-benchmark | 1 | 1 | 2253.0 | 2253.0 | 2253.0 | 2253.0 | 2253.0 |
| chrome-demo-attempt:google-mode-showcase | 8 | 8 | 38.8 | 47.0 | 47.0 | 38.8 | 47.0 |
| chrome-demo-attempt:google-search | 6 | 6 | 77.5 | 151.8 | 180.0 | 77.5 | 151.8 |
| chrome-demo-attempt:youtube-watch-comments | 1 | 1 | 345.0 | 345.0 | 345.0 | 345.0 | 345.0 |
| chrome-demo:google-mode-showcase | 24 | 24 | 100.5 | 238.1 | 253.0 | 100.5 | 238.1 |
| chrome-demo:google-search | 124 | 124 | 121.4 | 368.8 | 504.0 | 121.4 | 368.8 |
| chrome-demo:youtube-watch-comments | 14 | 14 | 155.1 | 371.6 | 421.0 | 155.1 | 371.6 |
| android-pipeline-benchmark | 8 | 8 | 24.9 | 38.0 | 44.0 | 24.9 | 38.0 |
| backend-direct | 1000 | 0 | 78.3 | 220.8 | 578.2 | 0.0 | 0.0 |
| chrome-quick-qa | 539 | 460 | 145.2 | 253.0 | 4120.9 | 84.2 | 199.1 |
| extension-lastStats | 10000 | 10000 | 78.4 | 203.0 | 2161.0 | 78.4 | 203.0 |
