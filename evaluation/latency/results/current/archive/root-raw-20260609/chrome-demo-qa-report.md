# Chungmaru Chrome Demo QA Report

- Run ID: `chrome-google-demo-20260609T104101`
- Video: `evaluation/latency/results/current/chungmaru-google-demo.mp4`
- Duration: `104.933` seconds
- FPS: `30`
- Attempt rows: `15`

## Scene Summary

| Scene | Time | Input / Status | Category | Expected | Mask Count | Backend Attempts | Max Total Latency |
| --- | ---: | --- | --- | --- | ---: | ---: | ---: |
| Settings | 0.0s-20.0s | operated | - | - | masks  | backend attempts 0 | max -ms |
| Protection OFF | 24.0s-26.0s | 병신아 꺼져 | masking-mode-showcase | raw-visible | masks 0 | backend attempts 0 | max -ms |
| Mode 가리기 | 26.0s-30.3s | 병신아 꺼져 | masking-mode-showcase | mode-applied | masks 10 | backend attempts 2 | max 71.000ms |
| Mode 흐리기 | 30.3s-33.4s | 병신아 꺼져 | masking-mode-showcase | mode-applied | masks 10 | backend attempts 2 | max 77.000ms |
| Mode 숨기기 | 33.4s-36.5s | 병신아 꺼져 | masking-mode-showcase | mode-applied | masks 10 | backend attempts 2 | max 79.000ms |
| Mode 삭제 | 36.5s-39.6s | 병신아 꺼져 | masking-mode-showcase | mode-applied | masks 3 | backend attempts 2 | max 137.000ms |
| Search 1 | 39.6s-57.467s | 병신아 꺼져 | profanity | mask-spans | masks 10 | backend attempts 2 | max 71.000ms |
| Search 2 | 57.467s-72.967s | 한남충은 답이 없다 | hate | mask-spans | masks 15 | backend attempts 2 | max 44.000ms |
| Search 3 | 72.967s-88.7s | 너 한번만 더 그러면 죽여버린다 | toxicity | mask-spans | masks 1 | backend attempts 2 | max 55.000ms |
| YouTube | 88.7s-99.933s | 시발 또 다시 보여줘야해? | youtube-comment-profanity | mask-spans | masks 3 | backend attempts 1 | max 552.000ms |
| Site warning | 101.933s-104.933s | blocked | - | - | masks  | backend attempts 0 | max -ms |

## Slowest Attempts

| Scene | Query | Attempt | Total to Mask ms | Backend ms | Candidate ms | Parser ms |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 9 | 시발 또 다시 보여줘야해? | 1 | 552 | 493 | 50 | 7 |
| 5 | 병신아 꺼져 | 2 | 137 | 66 | 61 | - |
| 4 | 병신아 꺼져 | 2 | 79 | 1 | 67 | - |
| 3 | 병신아 꺼져 | 2 | 77 | 1 | 66 | - |
| 2 | 병신아 꺼져 | 1 | 71 | 1 | 60 | - |
| 6 | 병신아 꺼져 | 1 | 71 | 1 | 60 | - |
| 2 | 병신아 꺼져 | 2 | 70 | 1 | 59 | - |
| 4 | 병신아 꺼져 | 1 | 64 | 1 | 54 | - |

## Notes

- Video captions identify the typed query even when the extension masks the search box itself.
- Hover rows use the actual mask `title` / `aria-label` and mirror it as an in-video callout.
- Scene summary is for presentation review; attempt latency CSV is for detailed measurement.
- Site-warning rows record whether continue is hidden or disabled.
