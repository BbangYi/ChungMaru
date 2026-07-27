# Chrome Extension (MVP)

브라우저 환경 텍스트 추출/마스킹 UI를 개발하는 위치입니다.

## 책임 범위
- DOM 텍스트 수집(content script)
- 증분 텍스트 수집 + span 단위 마스킹 UI
- 설정 패널
- FastAPI backend `/analyze_batch` 연동
- FastAPI backend `/site/check` 연동
- 사이트 접속 전 경고/차단 오버레이 표시
- 브라우징 사용량 + 현재 사이트 탐지 밀도 기반 웰빙 위젯 표시

## 실행 순서
1. `backend/requirements.txt` 기준으로 Python 의존성을 설치합니다.
2. `backend/scripts/download_models.py`로 모델 가중치를 내려받습니다.
3. 실제 브라우저 검증은 `backend/api`에서 `uvicorn app:app --host 127.0.0.1 --port 8000`로 서버를 실행합니다.
4. 코드 수정 중 자동 재시작이 필요할 때만 `--reload`를 사용합니다. `--reload`는 개발 편의용이며 실사용 지연/재연결 검증 기준은 아닙니다.
5. Chrome의 `chrome://extensions`에서 개발자 모드로 `extension/chrome` 폴더를 로드합니다.
6. 확장 프로그램 `상세 설정`에서 `API 주소`를 확인하고 `연결 확인`을 누릅니다.
7. 실제 웹 페이지에서 `현재 탭 즉시 분석`을 눌러 실시간 증분 분석과 span 마스킹이 동작하는지 확인합니다.

## 사이트 보호와 위젯

- 사이트 보호는 `/site/check` 결과와 사용자가 설정한 `항상 차단할 도메인` / `항상 경고할 도메인`을 함께 사용합니다.
- 접속 전 경고 페이지는 `webNavigation.onBeforeNavigate`에서 위험 판정을 확인한 뒤, 대상 사이트 대신 `site-warning.html`로 먼저 이동시킵니다. 사용자가 계속 접속을 선택하면 해당 탭/URL 조합을 5분 동안 허용합니다.
- 검색 결과 보호는 Google 검색 결과에 노출된 링크를 수동 차단/경고 도메인, 확장 내 curated fallback, `/site/check`의 domain-level 판정으로 확인합니다. 검색어/요약에 성인 키워드가 있다는 이유만으로 정상 도메인을 가리지 않도록, 백엔드 결과는 exact domain 또는 보안 위협 수준의 사이트 신호가 있을 때만 적용합니다. 차단 판정은 링크와 요약을 숨기고, 경고 판정은 흐림 처리합니다. 백엔드 판정은 한 번에 최대 8개 결과만 제한적으로 확인하고 10분 동안 캐시합니다.
- Google 이미지 검색 탭(`tbm=isch`, `udm=2`)은 결과 DOM이 매우 커서 일반 검색결과 보호/본문 텍스트 분석을 light mode로 전환합니다. 이때는 검색 입력창 중심의 아주 작은 후보만 유지하고, 이미지 그리드의 대량 mutation/scroll 분석과 사이트 판정 스캔은 실행하지 않습니다.
- Google 일반 검색 탭은 유해 이미지 차단의 media scan 대상에서 제외합니다. 정보성 검색 결과의 썸네일/아이콘 오탐을 줄이고, 텍스트 마스킹과 검색결과 site policy가 각자 역할을 나눠 처리하게 하기 위함입니다.
- 유해 이미지 차단은 현재 viewport의 `img`, `picture`, `video`, `background-image` 후보를 먼저 보고, 위험 URL/domain/text 신호가 있는 linked media grid는 제한된 범위에서 보강 수집합니다. 기본 동작은 화면을 먼저 가리는 startup pre-mask가 아니라 bootstrap 즉시 scan으로 판정 후 필요한 후보만 숨기는 decision-first 방식입니다. 짧은 startup gate는 내부 설정으로만 남겨 고위험 host 비교 smoke 또는 비상 fallback에서 명시적으로 켤 수 있고, 이미지/영상이 늦게 로드되면 load/metadata 이벤트를 통해 재스캔합니다.
- 유해 이미지 smoke runner는 기본적으로 Chrome for Testing을 headless로 실행합니다. 테스트 창이 화면 위로 떠서 작업을 방해하지 않으며, 수동 시각 디버깅이 필요한 경우에만 `scripts/chungmaru_chrome_media_safety_smoke.py --headed`를 사용합니다.
- seed 기반 live smoke는 최종 URL이 Chrome error page 또는 비 HTTP 페이지면 scan/action을 건너뛰고 `invalid_page`로 기록합니다. 정상 페이지도 최종 URL과 매칭되는 탭에만 메시지를 보내 이전 탭의 media action이 섞이지 않게 합니다.
- 현재 발표용 live smoke는 `evaluation/media-safety/fixtures/live-visual-rich-urls.csv`를 입력으로 씁니다. 주소가이드형 도박 배너 2개와 benign negative 2개를 3회 반복해 `evaluation/media-safety/results/current/media-safety-live-*` 산출물을 갱신합니다.
- 같은 smoke에 `--capture-visual-evidence`를 붙이면 headless screenshot을 `evaluation/media-safety/results/current/visual/`에 남기고 `media-safety-visual-evidence.*` manifest로 row와 연결합니다. 기본은 repeat 1만 캡처합니다.
- 웰빙 위젯은 현재 탭 하나가 아니라 당일 활성 웹 사용량을 누적합니다.
- 위젯의 탐지 건수와 화남 정도는 현재 도메인 전체가 아니라 현재 페이지 URL 기준으로 계산합니다. 리디렉션 이후 새 페이지로 이동하면 이전 페이지의 욕설/유해표현 수가 섞이지 않습니다.
- 표정의 노화 정도는 누적 웹 사용 시간에 따라 바뀌고, 화남 정도는 현재 페이지의 유해/욕설 탐지 수가 늘어날수록 단계적으로 강해집니다. 기본은 각각 5단계이며, 상세 설정에서 단계 수와 단계 간격을 조정할 수 있습니다.
- 위젯 위치는 페이지 위에서 드래그해 옮길 수 있고, `+` / `-` 버튼으로 크기를 조절합니다. 위치와 크기는 로컬 브라우저 저장소에 유지됩니다.
- 위젯 스타일은 상세 설정에서 `부드럽게`, `선명하게`, `미니멀` 중 선택할 수 있습니다.
- 백엔드 연동은 기본값이 꺼짐입니다. 평소에는 로컬/확장 내 규칙과 캐시만 쓰며 `/health`, `/analyze_batch`, `/site/check`, warmup 요청을 보내지 않습니다.
- 백엔드 모델 검증이 필요할 때만 상세 설정의 개발자 테스트 모드에서 비밀번호 `chungmaru-dev`를 입력하고 `백엔드 연동 켜기`를 체크한 뒤 `연결 확인` 또는 사이트 판정 테스트를 실행합니다.
- 개발자 테스트 모드에서는 비밀번호 `chungmaru-dev`로 위젯 사용 시간, 탐지 개수, 사이트 판정, 단계별 이미지 매핑을 임시로 시뮬레이션할 수 있습니다. 단계별 이미지는 URL/data URL을 직접 쓰거나, 단계 선택 후 이미지를 붙여넣어 `local:<단계>` 참조로 저장할 수 있습니다. 이미지 슬롯은 설정된 늙음/화남 단계 수에 맞춰 `age1`부터 최대 `age10`, `anger1`부터 최대 `anger10`까지 표시됩니다.
- 개발자 테스트 모드의 `사이트 판정 확인`은 실제 이동 없이 URL 하나를 백엔드 `/site/check` 또는 확장 내 fallback 정책으로 조회합니다. 백엔드 연동이 꺼져 있으면 수동 도메인/내장 fallback만 사용합니다. `adult-webtoon-plus.kr`, `jusoguide1.com`, `jusowhy1.com`는 `block`, `dcinside.com`은 `warning` smoke에 사용합니다.
- 같은 영역의 `체크리스트 복사`는 Chrome 새로고침, 백엔드 연결, 사이트 판정, 검색 결과 보호, 위젯 초기화 확인 순서를 Markdown으로 복사합니다.
- 이미 로드된 페이지에서 뒤늦게 `block` 판정이 확인되면 화면 중앙에 확대한 청마루 얼굴과 함께 전면 보호 화면을 띄웁니다.

## 설치/배포 메모

- 개발 중에는 Chrome 정책상 `chrome://extensions`의 개발자 모드에서 `extension/chrome`을 로드해야 합니다. 코드 변경 후에는 확장 프로그램 카드의 새로고침 버튼을 눌러야 최신 파일이 반영됩니다.
- 일반 사용자에게 배포할 때는 Chrome Web Store의 비공개/미등록 배포가 가장 덜 번거롭습니다. 사용자는 스토어 설치 한 번으로 업데이트를 받을 수 있고, 매번 `로드 언팩`을 반복하지 않아도 됩니다.
- `.crx` 파일 직접 배포는 최신 Chrome에서 제한이 많아 일반 사용자 설치 경로로는 권장하지 않습니다.
- Web Store 업로드용 zip은 아래 명령으로 만들 수 있습니다.

```bash
./scripts/package-extension.sh
```

- 기본 출력 위치는 `extension/chrome/dist/chungmaru-chrome-extension-<version>.zip`입니다. CI나 임시 검증에서는 출력 폴더를 인자로 넘기면 됩니다.

```bash
./scripts/package-extension.sh /tmp/chungmaru-extension-dist
```

## 사이트 예시

백엔드 seed 스크립트 기준 테스트용 범주는 아래와 같습니다.

- 성인 콘텐츠: `adult-webtoon-plus.kr`, `secret-room-adult.kr`
- 성인/도박 주소 허브: `jusoguide1.com`, `jusowhy1.com`
- 도박/토토: `vip-toto-365.kr`, `power-casino-choice.kr`
- 피싱/계정 탈취: `naver-secure-login.kr`, `paypal-password-reset.com`
- 악성코드/비공식 설치 파일: `driver-update-korea.kr`, `office-free-patch.kr`

위 seed 도메인 중 일부는 실제 접속 테스트용 사이트가 아니라 정책/API 결과를 만들기 위한 예시 데이터입니다. 브라우저에서 바로 접속 전 경고 화면을 보려면 `상세 설정`의 `항상 차단할 도메인`에 안전한 테스트 도메인(예: `example.com`)을 임시로 넣고 저장한 뒤 해당 사이트에 접속합니다.
검색 결과 보호와 접속 전 경고는 백엔드가 켜져 있으면 `/site/check`가 `block` 또는 `warning`으로 판단한 결과를 카드 단위로 보호합니다. 빠른 smoke를 위해 extension 쪽에도 백엔드 seed와 동일한 최소 fallback이 들어 있습니다. Google에서 `디시인사이드`를 검색하면 `dcinside.com` 결과는 `warning`, `adult-webtoon-plus.kr`, `jusoguide1.com`, `jusowhy1.com`, `google-account-verify.com` 결과는 `block`으로 처리됩니다. 같은 도메인으로 직접 이동해도 백엔드 상태와 무관하게 접속 전 경고가 뜹니다. 추가 수동 테스트가 필요하면 Google 검색 결과에 실제로 보이는 도메인을 `항상 차단할 도메인`에 임시로 넣고 저장하면 됩니다.

위젯 사용시간이나 탐지 카운트가 이전 테스트 때문에 남아 있으면 `상세 설정` > `개발자 테스트`에서 비밀번호 `chungmaru-dev`로 연 뒤 `위젯 통계 초기화`를 누르면 됩니다.

## 원격 브랜치 참고

2026-05-23 기준 `git fetch --all --prune` 후 확인한 사이트 인텔 관련 원격 브랜치는 `origin/codex/site-risk-intel-agent`입니다. 커밋 시각은 2026-05-19 17:41 KST이며, `/site/check` API 계약과 `site_risk_agent`, `site_intel_store`, `generate_massive_site_seed.py` 기반 seed 생성 흐름이 포함되어 있습니다.
