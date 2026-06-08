# Chungmaru Chrome Demo Video Scenario

- Scenario ID: `chrome-demo-planned-120s-core-coverage`
- Target duration: `120~140s`
- Target FPS: `30fps`
- Output folder: `evaluation/latency/results/current`
- Required latency log: `chrome-demo-latency.csv` plus row-level reference `chrome-e2e-row-breakdown.csv`

## 촬영 원칙

- 검색어는 욕설, 초성 우회, 로마자 우회, 혐오 표현, 공격적 발화 순서로 보여준다.
- 설정 화면은 단순히 열어두는 것이 아니라 backend 연결 확인과 위젯 override 적용이 실제로 작동하는 장면을 넣는다.
- 각 검색 장면은 입력, 결과 로드, 마스킹, hover tooltip, 스크롤 안정성 중 최소 2개 이상을 보여준다.
- latency는 영상 설명이 아니라 CSV에 남긴다. 영상에서는 마지막에 evidence 파일 경로만 짧게 보여준다.
- 위험 사이트는 high-risk일 때 계속 접속 버튼이 없어지고 돌아가기만 가능한 장면으로 정리한다.

## 장면 구성

| Scene | Time | Action | Input / Target | Expected |
| --- | ---: | --- | --- | --- |
| S01 | 0-8s | 데모 시작 / 확장 프로그램 활성 상태 | `Chrome 확장 아이콘과 청마루 상태 확인` | 청마루가 켜져 있고 backend 사용 상태가 보임 |
| S02 | 8-20s | 설정 실제 조작 | `옵션 화면에서 backend on, API 주소, 연결 확인을 수행` | 설정값 저장과 연결 성공이 화면에 남음 |
| S03 | 20-28s | 위젯 override 실제 적용 | `wellbeing/debug override 적용 후 화면 반영 확인` | 위젯 상태 변화가 보임 |
| S04 | 28-38s | 정상 주제 검색 | `차별금지법 관련 기사` | 정상/정책 주제는 과도하게 가리지 않음 |
| S05 | 38-50s | 욕설 검색 | `씨발 뜻` | 검색창/검색 결과 제목/스니펫에서 직접 욕설 span masking |
| S06 | 50-62s | 초성 우회 검색 | `ㅅ ㅂ 뜻` | 초성/띄어쓰기 우회 표현 마스킹 확인 |
| S07 | 62-74s | 로마자 우회 검색 | `tlqkf 뜻` | 로마자 우회 표현이 backend/정규화 경로에서 처리되는지 확인 |
| S08 | 74-86s | 혐오 표현 검색 | `한남충 뜻` | 혐오/비하 표현 검색 결과 마스킹 확인 |
| S09 | 86-98s | 공격적 발화 검색 | `너 한번만 더 그러면 죽여버린다` | 위협/공격 발화 탐지와 마스킹 확인 |
| S10 | 98-108s | AI 개요 영역 확인 | `대표적인 초성 욕설` | AI Overview 본문과 인용 chip 중복 렌더/부분 마스킹 확인 |
| S11 | 108-118s | 스크롤 안정성과 근거 tooltip | `마스킹된 검색 결과 위로 hover 후 스크롤` | 마스크가 위치를 유지하고 tooltip에 보호 사유가 표시됨 |
| S12 | 118-132s | 위험 사이트 차단 | `high-risk adult/toxic test URL` | 민감도 기준 초과 시 계속 접속 버튼 없음, 돌아가기만 활성 |
| S13 | 132-142s | 측정 로그 확인 | `chrome-e2e-row-breakdown.csv` | row별 latency와 worst-case 분류가 파일에 남아 있음을 보여줌 |

## 영상에서 반드시 보여줄 기능

- Backend 연결 설정 저장 및 연결 확인
- Wellbeing widget override 적용/해제
- Google 검색창 입력
- 검색 결과 제목/스니펫 span masking
- AI Overview 또는 AI 요약 영역 masking 및 중복 render 방지 확인
- 마스킹 span hover tooltip
- 스크롤 후 stale mask 없이 위치 유지
- 위험 사이트 high-risk block에서 continue 비활성/숨김
- row별 latency CSV 위치 확인

## 이번 10k latency run과의 관계

- `chrome-e2e-row-breakdown.csv`는 대량 fixture 부하 측정 결과다.
- 이 영상 시나리오는 실사용 Google/설정/위젯/site-warning 기능 확인용이다.
- 두 자료를 섞어 말하지 않는다. 발표에서는 `대량 latency evidence`와 `실사용 데모 evidence`를 분리한다.
