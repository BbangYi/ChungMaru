# Chungmaru Chrome Demo Script

- Run ID: `chrome-google-demo-20260609T104101`
- Video: `evaluation/latency/results/current/chungmaru-google-demo.mp4`
- Duration: `104.933` seconds
- FPS: `30`
- Backend: `http://127.0.0.1:8000`

## Scene Summary

- Settings: backend 연결, site policy, wellbeing widget debug override 확인
- Mode 보호 OFF: `병신아 꺼져` protection=False, effective masks=0, hover=`not-recorded`
- Mode 가리기: `병신아 꺼져` protection=True, effective masks=10, hover=`청마루 보호: 모욕 99%`
- Mode 흐리기: `병신아 꺼져` protection=True, effective masks=10, hover=`not-recorded`
- Mode 숨기기: `병신아 꺼져` protection=True, effective masks=10, hover=`not-recorded`
- Mode 삭제: `병신아 꺼져` protection=True, effective masks=3, hover=`not-recorded`
- Search 1: `병신아 꺼져` (profanity) 입력, effective masks=10, hover=`청마루 보호: 모욕 99%`, url=https://www.google.com/search?q=%EB%B3%91%EC%8B%A0%EC%95%84+%EA%BA%BC%EC%A0%B8&hl=ko&num=10&pws=0&safe=off
- Search 2: `한남충은 답이 없다` (hate) 입력, effective masks=15, hover=`not-recorded`, url=https://www.google.com/search?q=%ED%95%9C%EB%82%A8%EC%B6%A9%EC%9D%80+%EB%8B%B5%EC%9D%B4+%EC%97%86%EB%8B%A4&hl=ko&num=10&pws=0&safe=off
- Search 3: `너 한번만 더 그러면 죽여버린다` (toxicity) 입력, effective masks=1, hover=`not-recorded`, url=https://www.google.com/search?q=%EB%84%88+%ED%95%9C%EB%B2%88%EB%A7%8C+%EB%8D%94+%EA%B7%B8%EB%9F%AC%EB%A9%B4+%EC%A3%BD%EC%97%AC%EB%B2%84%EB%A6%B0%EB%8B%A4&hl=ko&num=10&pws=0&safe=off
- YouTube: `시발 또 다시 보여줘야해?` 검색 결과에서 `Hook - 식케이 (Sik-K) Hook - 식케이 (Sik-K) 0:45` 진입, comments masks=3, hover=`not-recorded`, url=https://www.youtube.com/watch?v=tE_pO5b5cY4&t=45s
- Site warning: `https://adult-webtoon-plus.kr/` 접속, continueHidden=True, continueDisabled=True

## Scene Timing Summary

| Scene | Time | Input / Status | Category | Expected |
| --- | ---: | --- | --- | --- |
| Settings | 0.0s-20.0s | operated | - | - |
| Protection OFF | 24.0s-26.0s | 병신아 꺼져 | masking-mode-showcase | raw-visible |
| Mode 가리기 | 26.0s-30.3s | 병신아 꺼져 | masking-mode-showcase | mode-applied |
| Mode 흐리기 | 30.3s-33.4s | 병신아 꺼져 | masking-mode-showcase | mode-applied |
| Mode 숨기기 | 33.4s-36.5s | 병신아 꺼져 | masking-mode-showcase | mode-applied |
| Mode 삭제 | 36.5s-39.6s | 병신아 꺼져 | masking-mode-showcase | mode-applied |
| Search 1 | 39.6s-57.467s | 병신아 꺼져 | profanity | mask-spans |
| Search 2 | 57.467s-72.967s | 한남충은 답이 없다 | hate | mask-spans |
| Search 3 | 72.967s-88.7s | 너 한번만 더 그러면 죽여버린다 | toxicity | mask-spans |
| YouTube | 88.7s-99.933s | 시발 또 다시 보여줘야해? | youtube-comment-profanity | mask-spans |
| Site warning | 101.933s-104.933s | blocked | - | - |

## Timeline

| Start | End | What happens |
| ---: | ---: | --- |
| 0.000s | 8.000s | settings: open options |
| 8.000s | 10.000s | settings: open developer tools |
| 10.000s | 12.000s | settings: enable backend |
| 12.000s | 14.000s | settings: check backend connection |
| 14.000s | 16.000s | settings: run site policy |
| 16.000s | 18.000s | settings: apply wellbeing override |
| 18.000s | 20.000s | settings: clear wellbeing override |
| 20.000s | 24.000s | Google home |
| 24.000s | 26.000s | mode showcase: protection off raw screen |
| 26.000s | 26.700s | mode showcase: apply mask |
| 26.700s | 29.100s | mode showcase: result mask |
| 29.100s | 30.300s | mode showcase: hover mask |
| 30.300s | 31.000s | mode showcase: apply blur |
| 31.000s | 33.400s | mode showcase: result blur |
| 33.400s | 34.100s | mode showcase: apply hide |
| 34.100s | 36.500s | mode showcase: result hide |
| 36.500s | 37.200s | mode showcase: apply remove |
| 37.200s | 39.600s | mode showcase: result remove |
| 39.600s | 39.633s | search 1: type 병 |
| 39.633s | 39.667s | search 1: type 병신 |
| 39.667s | 39.700s | search 1: type 병신아 |
| 39.700s | 39.733s | search 1: type 병신아  |
| 39.733s | 39.767s | search 1: type 병신아 꺼 |
| 39.767s | 39.800s | search 1: type 병신아 꺼져 |
| 39.800s | 42.300s | search 1: typed query: 병신아 꺼져 |
| 42.300s | 44.300s | search 1: result loaded: 병신아 꺼져 |
| 44.300s | 45.500s | search 1: analysis request: 병신아 꺼져 |
| 45.500s | 52.500s | search 1: masked results: 병신아 꺼져 |
| 52.500s | 55.000s | search 1: hover mask evidence: 병신아 꺼져 |
| 55.000s | 55.467s | search 1: scroll-down |
| 55.467s | 57.467s | search 1: after scroll: 병신아 꺼져 |
| 57.467s | 57.500s | search 2: type 한 |
| 57.500s | 57.533s | search 2: type 한남 |
| 57.533s | 57.567s | search 2: type 한남충 |
| 57.567s | 57.600s | search 2: type 한남충은 |
| 57.600s | 57.633s | search 2: type 한남충은  |
| 57.633s | 57.667s | search 2: type 한남충은 답 |
| 57.667s | 57.700s | search 2: type 한남충은 답이 |
| 57.700s | 57.733s | search 2: type 한남충은 답이  |
| 57.733s | 57.767s | search 2: type 한남충은 답이 없 |
| 57.767s | 57.800s | search 2: type 한남충은 답이 없다 |
| 57.800s | 60.300s | search 2: typed query: 한남충은 답이 없다 |
| 60.300s | 62.300s | search 2: result loaded: 한남충은 답이 없다 |
| 62.300s | 63.500s | search 2: analysis request: 한남충은 답이 없다 |
| 63.500s | 70.500s | search 2: masked results: 한남충은 답이 없다 |
| 70.500s | 70.967s | search 2: scroll-down |
| 70.967s | 72.967s | search 2: after scroll: 한남충은 답이 없다 |
| 72.967s | 73.000s | search 3: type 너 |
| 73.000s | 73.033s | search 3: type 너  |
| 73.033s | 73.067s | search 3: type 너 한 |
| 73.067s | 73.100s | search 3: type 너 한번 |
| 73.100s | 73.133s | search 3: type 너 한번만 |
| 73.133s | 73.167s | search 3: type 너 한번만  |
| 73.167s | 73.200s | search 3: type 너 한번만 더 |
| 73.200s | 73.233s | search 3: type 너 한번만 더  |
| 73.233s | 73.267s | search 3: type 너 한번만 더 그 |
| 73.267s | 73.300s | search 3: type 너 한번만 더 그러 |
| 73.300s | 73.333s | search 3: type 너 한번만 더 그러면 |
| 73.333s | 73.367s | search 3: type 너 한번만 더 그러면  |
| 73.367s | 73.400s | search 3: type 너 한번만 더 그러면 죽 |
| 73.400s | 73.433s | search 3: type 너 한번만 더 그러면 죽여 |
| 73.433s | 73.467s | search 3: type 너 한번만 더 그러면 죽여버 |
| 73.467s | 73.500s | search 3: type 너 한번만 더 그러면 죽여버린 |
| 73.500s | 73.533s | search 3: type 너 한번만 더 그러면 죽여버린다 |
| 73.533s | 76.033s | search 3: typed query: 너 한번만 더 그러면 죽여버린다 |
| 76.033s | 78.033s | search 3: result loaded: 너 한번만 더 그러면 죽여버린다 |
| 78.033s | 79.233s | search 3: analysis request: 너 한번만 더 그러면 죽여버린다 |
| 79.233s | 86.233s | search 3: masked results: 너 한번만 더 그러면 죽여버린다 |
| 86.233s | 86.700s | search 3: scroll-down |
| 86.700s | 88.700s | search 3: after scroll: 너 한번만 더 그러면 죽여버린다 |
| 88.700s | 90.700s | youtube: search results: 시발 또 다시 보여줘야해? |
| 90.700s | 91.200s | youtube: watch page loaded |
| 91.200s | 91.733s | youtube: scroll-to-comments |
| 91.733s | 93.733s | youtube: comments visible |
| 93.733s | 94.933s | youtube: analysis request |
| 94.933s | 99.933s | youtube: masked comments |
| 99.933s | 101.933s | site warning: before navigation |
| 101.933s | 104.933s | site warning |

## Generated Evidence Files

- `metadata.json`
- `demo-timeline.csv`
- `chrome-demo-scene-summary.csv`
- `chrome-demo-qa-report.md`
- `chrome-demo-attempt-latency.csv`
- `chrome-demo-attempt-latency.jsonl`
