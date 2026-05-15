# Chungmaru Codex 작업 Lane 가이드

작성일: 2026-05-11

목적: Codex 작업 시간이 길어지는 원인을 줄이고, Android/backend/docs 변경이 한 요청에 섞이는 것을 막는다.

## 기본 원칙

Chungmaru 작업은 항상 하나의 primary lane을 선택한 뒤 시작한다.

작업 요청에는 아래 세 가지가 있어야 한다.

1. lane
2. 수정 대상 파일 또는 디렉터리
3. 검증 방식과 종료 조건

권장 요청 예시:

```text
Chungmaru backend lane만 작업해줘.
수정 범위는 backend/api/input_filter.py와 backend/tests/test_input_filter.py로 제한해.
서버는 띄우지 말고 backend 테스트만 짧게 돌리고 끝내.
```

```text
Chungmaru android lane만 작업해줘.
youtubeparser 패키지 안의 OCR 후보 필터만 수정해.
backend와 docs는 건드리지 말고 관련 Gradle test만 확인해.
```

## Lane 정의

| lane | 주요 경로 | 포함되는 작업 | 기본 검증 |
| --- | --- | --- | --- |
| `android` | `android/app/**` | Android UI, accessibility service, overlay, OCR, Kotlin tests | 관련 Gradle unit test 또는 compile 범위 |
| `backend` | `backend/api/**`, `backend/tests/**` | FastAPI, classifier/pipeline, input filtering, backend tests | backend pytest 또는 TestClient 범위 |
| `extension` | `extension/**` | Chrome extension capture, popup, content script | extension smoke/build 또는 관련 JS test |
| `shared-contract` | `shared/**` | JSON schema, normalization, policy/rules contracts | 변경된 contract를 쓰는 최소 consumer 확인 |
| `docs-evaluation` | `docs/**`, `evaluation/**` | 보고서, 발표, 평가 케이스, engineering history | 링크/형식/JSONL 확인 |

## Cross-lane 규칙

교차 변경은 예외로만 허용한다.

허용 예시:

- backend 응답 계약 변경 때문에 Android parser model을 같이 바꾸는 경우
- shared contract 변경 때문에 backend와 extension consumer를 최소 수정하는 경우
- 평가 케이스 변경과 그 케이스를 통과시키는 작은 backend fix를 같이 묶는 경우

교차 변경을 할 때는 작업 시작 전에 아래를 명시한다.

```text
primary lane: backend
secondary lane: android parser model
reason: API response field rename
verification: backend test + Android parser model unit test only
```

## 현재 dirty tree 스냅샷

2026-05-11 기준 현재 checkout은 여러 lane이 동시에 열려 있다.

| lane | 현재 변경 성격 |
| --- | --- |
| `android` | manifest, MainActivity, youtubeparser package, OCR/ROI/mask planner, Android tests |
| `backend` | API app, input filter, normalizer, pipeline, backend tests |
| `docs-evaluation` | constraints, engineering history, evaluation cases, backend model notes, presentation material |

이 상태에서 새 작업을 시작하면 먼저 `git status --short --branch`를 보고, 새 작업이 기존 변경 중 어느 lane에 붙는지 정해야 한다.

## 작업 시작 체크

작업 시작 전:

```bash
git status --short --branch
```

확인할 것:

- 이번 작업의 primary lane
- 기존 dirty files와 겹치는지
- 서버, emulator, model download가 필요한지
- 검증을 짧게 끝낼 수 있는지

## 검증 기준

### Android lane

가능하면 관련 단위 테스트만 실행한다.

```bash
./gradlew :app:testDebugUnitTest
```

무거운 emulator 검증은 사용자가 요청한 경우에만 수행한다.

### Backend lane

가능하면 backend 디렉터리의 좁은 테스트만 실행한다.

```bash
./.venv/bin/python -m pytest backend/tests/test_input_filter.py
```

서버를 띄우는 검증은 API runtime 자체가 작업 대상일 때만 수행한다.

### Docs/evaluation lane

문서 변경은 링크와 형식을 확인한다. JSONL은 한 줄씩 유효한 JSON이어야 한다.

## Runtime 운영 기준

Chungmaru API는 `workspace-services/services/chungmaru-api/service.yml`에 등록된 서비스다.

운영 기준:

- 기본 배치: `remote-managed`
- 권장 실행 호스트: stronger host
- Raspberry Pi auto-deploy: 사용하지 않음
- Mac-local 실행: 개발과 짧은 검증용

따라서 Codex 작업 중 backend 서버를 오래 상주시켜 Mac 부하를 만들지 않는다.

## 종료 기준

작업 종료 시 아래를 남긴다.

- 변경한 lane
- 수정 파일
- 실행한 검증
- 건드리지 않은 lane
- 남은 위험 또는 다음 작업

좋은 종료 예시:

```text
backend lane만 수정했습니다.
변경 파일은 backend/api/input_filter.py와 backend/tests/test_input_filter.py입니다.
검증은 해당 pytest만 통과했습니다.
Android와 extension은 건드리지 않았습니다.
```
