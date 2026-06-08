# Chungmaru Chrome Demo Script

- Run ID: `chrome-google-youtube-demo-20260608-final6`
- Video: `evaluation/latency/results/current/chungmaru-google-demo.mp4`
- Duration: `150.0` seconds
- FPS: `30`
- Backend: `http://127.0.0.1:8000`

## Scene Summary

- Settings: backend 연결, site policy, wellbeing widget debug override 확인
- Mode 보호 OFF: `ㅈ댄다 진짜` protection=False, effective masks=0, hover=`not-recorded`
- Mode 가리기: `ㅈ댄다 진짜` protection=True, effective masks=9, hover=`청마루 보호: 모욕 99%`
- Mode 흐리기: `ㅈ댄다 진짜` protection=True, effective masks=4, hover=`청마루 보호: 모욕 97%, 공격 74%`
- Mode 숨기기: `ㅈ댄다 진짜` protection=True, effective masks=4, hover=`청마루 보호: 모욕 97%, 공격 74%`
- Mode 삭제: `ㅈ댄다 진짜` protection=True, effective masks=4, hover=`not-recorded`
- Search 1: `ㅈ댄다 진짜` (custom) 입력, effective masks=4, hover=`청마루 보호: 모욕 97%, 공격 74%`, url=https://www.google.com/search?q=%E3%85%88%EB%8C%84%EB%8B%A4+%EC%A7%84%EC%A7%9C&hl=ko&num=10&pws=0&safe=off
- Search 2: `한남충 뜻` (hate) 입력, effective masks=13, hover=`청마루 보호: 모욕 99%`, url=https://www.google.com/search?q=%ED%95%9C%EB%82%A8%EC%B6%A9+%EB%9C%BB&hl=ko&num=10&pws=0&safe=off
- Search 3: `죽여버릴거야 협박` (toxicity) 입력, effective masks=7, hover=`청마루 보호: 모욕 99%`, url=https://www.google.com/search?q=%EC%A3%BD%EC%97%AC%EB%B2%84%EB%A6%B4%EA%B1%B0%EC%95%BC+%ED%98%91%EB%B0%95&hl=ko&num=10&pws=0&safe=off
- YouTube: `시발 또 다시 보여줘야해?` 검색 결과에서 `Hook - 식케이 (Sik-K) Hook - 식케이 (Sik-K) 0:45` 진입, comments masks=1, hover=`청마루 보호: 유해 표현`, url=https://www.youtube.com/watch?v=tE_pO5b5cY4&t=45s
- Site warning: `https://adult-webtoon-plus.kr/` 접속, continueHidden=True, continueDisabled=True

## Scene Timing Summary

| Scene | Time | Input / Status | Category | Expected |
| --- | ---: | --- | --- | --- |
| Settings | 0.0s-20.0s | operated | - | - |
| Protection OFF | 24.0s-26.0s | ㅈ댄다 진짜 | masking-mode-showcase | raw-visible |
| Mode 가리기 | 26.0s-30.3s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied |
| Mode 흐리기 | 30.3s-34.6s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied |
| Mode 숨기기 | 34.6s-38.9s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied |
| Mode 삭제 | 38.9s-42.0s | ㅈ댄다 진짜 | masking-mode-showcase | mode-applied |
| Search 1 | 42.0s-60.3s | ㅈ댄다 진짜 | custom | - |
| Search 2 | 60.3s-78.567s | 한남충 뜻 | hate | mask-spans |
| Search 3 | 78.567s-96.967s | 죽여버릴거야 협박 | toxicity | mask-spans |
| YouTube | 96.967s-114.867s | 시발 또 다시 보여줘야해? | youtube-comment-profanity | mask-spans |
| Site warning | 116.867s-124.867s | blocked | - | - |

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
| 33.400s | 34.600s | mode showcase: hover blur |
| 34.600s | 35.300s | mode showcase: apply hide |
| 35.300s | 37.700s | mode showcase: result hide |
| 37.700s | 38.900s | mode showcase: hover hide |
| 38.900s | 39.600s | mode showcase: apply remove |
| 39.600s | 42.000s | mode showcase: result remove |
| 42.000s | 42.033s | search 1: type ㅈ |
| 42.033s | 42.067s | search 1: type ㅈ댄 |
| 42.067s | 42.100s | search 1: type ㅈ댄다 |
| 42.100s | 42.133s | search 1: type ㅈ댄다  |
| 42.133s | 42.167s | search 1: type ㅈ댄다 진 |
| 42.167s | 42.200s | search 1: type ㅈ댄다 진짜 |
| 42.200s | 44.700s | search 1: typed query: ㅈ댄다 진짜 |
| 44.700s | 46.700s | search 1: result loaded: ㅈ댄다 진짜 |
| 46.700s | 47.900s | search 1: analysis request: ㅈ댄다 진짜 |
| 47.900s | 54.900s | search 1: masked results: ㅈ댄다 진짜 |
| 54.900s | 57.400s | search 1: hover mask evidence: ㅈ댄다 진짜 |
| 57.400s | 58.000s | search 1: scroll-down |
| 58.000s | 60.000s | search 1: after scroll: ㅈ댄다 진짜 |
| 60.000s | 60.300s | search 1: scroll-up |
| 60.300s | 60.333s | search 2: type 한 |
| 60.333s | 60.367s | search 2: type 한남 |
| 60.367s | 60.400s | search 2: type 한남충 |
| 60.400s | 60.433s | search 2: type 한남충  |
| 60.433s | 60.467s | search 2: type 한남충 뜻 |
| 60.467s | 62.967s | search 2: typed query: 한남충 뜻 |
| 62.967s | 64.967s | search 2: result loaded: 한남충 뜻 |
| 64.967s | 66.167s | search 2: analysis request: 한남충 뜻 |
| 66.167s | 73.167s | search 2: masked results: 한남충 뜻 |
| 73.167s | 75.667s | search 2: hover mask evidence: 한남충 뜻 |
| 75.667s | 76.267s | search 2: scroll-down |
| 76.267s | 78.267s | search 2: after scroll: 한남충 뜻 |
| 78.267s | 78.567s | search 2: scroll-up |
| 78.567s | 78.600s | search 3: type 죽 |
| 78.600s | 78.633s | search 3: type 죽여 |
| 78.633s | 78.667s | search 3: type 죽여버 |
| 78.667s | 78.700s | search 3: type 죽여버릴 |
| 78.700s | 78.733s | search 3: type 죽여버릴거 |
| 78.733s | 78.767s | search 3: type 죽여버릴거야 |
| 78.767s | 78.800s | search 3: type 죽여버릴거야  |
| 78.800s | 78.833s | search 3: type 죽여버릴거야 협 |
| 78.833s | 78.867s | search 3: type 죽여버릴거야 협박 |
| 78.867s | 81.367s | search 3: typed query: 죽여버릴거야 협박 |
| 81.367s | 83.367s | search 3: result loaded: 죽여버릴거야 협박 |
| 83.367s | 84.567s | search 3: analysis request: 죽여버릴거야 협박 |
| 84.567s | 91.567s | search 3: masked results: 죽여버릴거야 협박 |
| 91.567s | 94.067s | search 3: hover mask evidence: 죽여버릴거야 협박 |
| 94.067s | 94.667s | search 3: scroll-down |
| 94.667s | 96.667s | search 3: after scroll: 죽여버릴거야 협박 |
| 96.667s | 96.967s | search 3: scroll-up |
| 96.967s | 98.967s | youtube: search results: 시발 또 다시 보여줘야해? |
| 98.967s | 99.967s | youtube: watch page loaded |
| 99.967s | 100.767s | youtube: scroll-to-comments |
| 100.767s | 103.767s | youtube: comments visible |
| 103.767s | 104.967s | youtube: analysis request |
| 104.967s | 109.967s | youtube: masked comments |
| 109.967s | 112.467s | youtube: hover mask evidence |
| 112.467s | 112.867s | youtube comments: scroll-down |
| 112.867s | 114.867s | youtube: after comment scroll |
| 114.867s | 116.867s | site warning: before navigation |
| 116.867s | 124.867s | site warning |
| 124.867s | 150.000s | final evidence review |

## Generated Evidence Files

- `metadata.json`
- `demo-timeline.csv`
- `chrome-demo-scene-summary.csv`
- `chrome-demo-qa-report.md`
- `chrome-demo-attempt-latency.csv`
- `chrome-demo-attempt-latency.jsonl`
