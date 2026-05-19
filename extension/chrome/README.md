# Chrome Extension (MVP)

브라우저 환경 텍스트 추출/마스킹 UI를 개발하는 위치입니다.

## 책임 범위
- DOM 텍스트 수집(content script)
- 증분 텍스트 수집 + span 단위 마스킹 UI
- 설정 패널
- FastAPI backend `/analyze_batch` 연동
- FastAPI backend `/site/check` 연동
- 사이트 접속 전 경고 오버레이 표시

## 실행 순서
1. `backend/requirements.txt` 기준으로 Python 의존성을 설치합니다.
2. `backend/scripts/download_models.py`로 모델 가중치를 내려받습니다.
3. 실제 브라우저 검증은 `backend/api`에서 `uvicorn app:app --host 127.0.0.1 --port 8000`로 서버를 실행합니다.
4. 코드 수정 중 자동 재시작이 필요할 때만 `--reload`를 사용합니다. `--reload`는 개발 편의용이며 실사용 지연/재연결 검증 기준은 아닙니다.
5. Chrome의 `chrome://extensions`에서 개발자 모드로 `extension/chrome` 폴더를 로드합니다.
6. 확장 프로그램 `상세 설정`에서 `API 주소`를 확인하고 `연결 확인`을 누릅니다.
7. 실제 웹 페이지에서 `현재 탭 즉시 분석`을 눌러 실시간 증분 분석과 span 마스킹이 동작하는지 확인합니다.

## 사이트 안전성 Agent 연동

현재 확장 프로그램은 텍스트 유해성 분석과 별도로 사이트 접속 전 위험도 판별 흐름도 포함합니다.

1. `service-worker.js`가 URL 변경 시 `/site/check`를 호출합니다.
2. `content-script.js`가 판정 결과를 받아 경고 또는 차단 오버레이를 띄웁니다.
3. `options.html` / `options.js`에서 `사이트 접속 전 위험도 경고 사용` 설정을 제어합니다.

즉 현재 구조는:

- 텍스트 보호: `/analyze_batch`
- 사이트 보호: `/site/check`

의 두 축으로 분리되어 있습니다.
