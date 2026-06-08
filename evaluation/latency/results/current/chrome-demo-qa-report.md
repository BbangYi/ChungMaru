# Chungmaru Chrome Demo QA Report

- Run ID: `chrome-google-youtube-demo-20260608-final6`
- Video: `evaluation/latency/results/current/chungmaru-google-demo.mp4`
- Duration: `150.0` seconds
- FPS: `30`
- Attempt rows: `15`

## Scene Summary

| Scene | Time | Input / Status | Category | Expected | Mask Count | Backend Attempts | Max Total Latency |
| --- | ---: | --- | --- | --- | ---: | ---: | ---: |
| Settings | 0.0s-20.0s | operated | - | - | masks  | backend attempts 0 | max -ms |
| Protection OFF | 24.0s-26.0s | ㅈ댄다 진짜 | masking-mode-showcase | raw-visible | masks 0 | backend attempts 0 | max -ms |
| Mode 가리기 | 26.0s-30.3s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied | masks 9 | backend attempts 2 | max 21.000ms |
| Mode 흐리기 | 30.3s-34.6s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied | masks 4 | backend attempts 0 | max 47.000ms |
| Mode 숨기기 | 34.6s-38.9s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied | masks 4 | backend attempts 0 | max 47.000ms |
| Mode 삭제 | 38.9s-42.0s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied | masks 4 | backend attempts 0 | max 41.000ms |
| Search 1 | 42.0s-60.3s | ㅈ댄다 진짜 | custom | - | masks 4 | backend attempts 0 | max 67.000ms |
| Search 2 | 60.3s-78.567s | 한남충 뜻 | hate | mask-spans | masks 13 | backend attempts 0 | max 64.000ms |
| Search 3 | 78.567s-96.967s | 죽여버릴거야 협박 | toxicity | mask-spans | masks 7 | backend attempts 2 | max 180.000ms |
| YouTube | 96.967s-114.867s | 시발 또 다시 보여줘야해? | youtube-comment-profanity | mask-spans | masks 1 | backend attempts 1 | max 345.000ms |
| Site warning | 116.867s-124.867s | blocked | - | - | masks  | backend attempts 0 | max -ms |

## Slowest Attempts

| Scene | Query | Attempt | Total to Mask ms | Backend ms | Candidate ms | Parser ms |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 9 | 시발 또 다시 보여줘야해? | 1 | 345 | 293 | 43 | 8 |
| 8 | 죽여버릴거야 협박 | 1 | 180 | 104 | 53 | - |
| 6 | ㅈ댄다 진짜 | 3 | 67 | - | - | - |
| 6 | ㅈ댄다 진짜 | 1 | 66 | - | - | - |
| 7 | 한남충 뜻 | 1 | 64 | - | - | - |
| 8 | 죽여버릴거야 협박 | 2 | 48 | 1 | 28 | - |
| 3 | ㅈ댄다 진짜 | 1 | 47 | - | - | - |
| 3 | ㅈ댄다 진짜 | 2 | 47 | - | - | - |

## Notes

- Video captions identify the typed query even when the extension masks the search box itself.
- Hover rows use the actual mask `title` / `aria-label` and mirror it as an in-video callout.
- Scene summary is for presentation review; attempt latency CSV is for detailed measurement.
- Site-warning rows record whether continue is hidden or disabled.
