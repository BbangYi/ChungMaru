# Android Performance Evidence For PPT

생성일: 2026-05-31

목적: Android 성능 개선을 발표 자료에 넣을 때, "빠르게 만들었다"가 아니라 어떤 병목을 줄였고 어떤 검증이 끝났으며 무엇이 아직 실기기 검증 대상인지 분리해서 보여준다.

## 한 줄 요약

Android는 Chrome처럼 DOM span을 직접 제어할 수 없어서, 성능 개선의 핵심은 전체 화면 OCR을 피하고, 접근성 후보와 OCR 후보를 분리하며, 좌표가 불확실한 마스크는 렌더링하지 않는 방향이었다.

## 발표에 바로 쓸 수 있는 핵심 근거

| PPT 주장 | 근거 | 현재 상태 |
| --- | --- | --- |
| 전체 화면 OCR은 실시간 앱 UX에 맞지 않다. | `VisualTextRoiPlanner`가 OCR 후보를 선별하고, 기본 ROI 수를 제한한다. `MAX_ROI_COUNT = 6`, browser text ROI도 `BROWSER_TEXT_ROI_MAX_COUNT = 4`로 제한한다. | code-backed, unit-test-backed |
| 네트워크/backend 실패가 화면 루프를 오래 붙잡지 않게 했다. | `AndroidAnalysisClient` timeout을 `connectTimeout = 500ms`, `readTimeout = 1200ms`로 제한하고, 응답 캐시를 둔다. | code-backed, unit-test-backed |
| 접근성 char box 요청도 무제한으로 하지 않는다. | `AccessibilityCharacterBoxPolicy`가 입력 텍스트 96자, actionable text 2000자, 요청 range 8개로 제한한다. | code-backed, unit-test-backed |
| 스크롤 중 stale mask를 줄이기 위해 분석/렌더 타이밍을 분리했다. | `YoutubeAccessibilityService`가 text 12ms, scroll 32ms, content 40ms, window 60ms 지연을 따로 두고, scroll/content 안정화 window를 64ms/48ms로 둔다. | code-backed, unit-test-backed |
| 디버깅 로그가 실사용 성능을 잡아먹지 않게 했다. | raw node dump는 `Log.isLoggable(TAG, Log.VERBOSE)`일 때만 찍는다. | code-backed |
| 잘못된 위치에 빠르게 가리는 것보다, 신뢰 가능한 좌표만 가린다. | `MaskOverlayController`가 browser/accessibility/visual source별 크기, 면적, line count, text length gate를 두고 큰 fallback mask를 제한한다. | code-backed, unit-test-backed |

## 실험 결과 정리 축

Android Studio에서 개별 기능을 켜고 끄며 실험할 때는 아래 5단계를 같은 표에 놓고 비교한다.

| 단계 | 발표용 이름 | 구현/계약 | 성능 지표 | 품질 지표 | 현재 해석 |
| --- | --- | --- | --- | --- | --- |
| 01 수집 | Accessibility Service 후보 수집 | 화면 텍스트와 bounds 후보로 댓글/검색결과 가능 영역을 먼저 좁힘 | `parse delay ms`, `candidate extraction ms` | 후보 수, 후보 누락 여부 | 빠른 기본 경로. 전체 OCR을 피하는 핵심 전제 |
| 02 분석 | Android 분석 API | 후보를 `POST /analyze_android`로 보내 score/evidence span 수신 | `total analysis latency ms`, `backend mask latency ms` | offensive/filtered 수, evidence span 매칭 | backend 연결 상태와 모델 성능의 영향이 큼 |
| 03 보완 | OCR ROI | 전체 화면 OCR 대신 후보 ROI만 문자 인식 | `visual OCR latency ms` | ROI selected/raw OCR/selected OCR | 이미지/썸네일처럼 접근성 텍스트가 없는 영역 보완 |
| 04 좌표 | char box / line estimate | 글자 박스와 줄 단서로 evidence 위치를 좁힘 | `accessibility mask latency ms` | unstable skip, wrong-position mask | 정확도 개선 축이지만 비용 제한 필요 |
| 05 표시 | overlay gate | 불확실한 화면 범위는 분석 결과가 있어도 표시 제외 | `visual mask latency ms`, render rate | missed/false/stale mask | false mask를 줄이는 대신 missed가 생길 수 있는 trade-off |

`scripts/android-pipeline-benchmark.sh`는 이 5단계를 실제 런타임 토글(`pipeline_experiment_mode`)로 켜고 끄며 `raw_runs.csv`, `summary_by_mode.csv`, `stage_delta.csv`, `ppt_table.md`를 만든다. 기존 `FEATURE_SET=accessibility-only`, `ocr-roi`, `charbox-line`, `overlay-gate` 방식은 라벨 기반 historical batch이므로 최종 단계별 비교 근거로 쓰지 않는다.

## 기간별 근거 타임라인

| 날짜 | 개선 묶음 | PPT에서 말할 수 있는 결과 | 증거 |
| --- | --- | --- | --- |
| 2026-05-03 | overlay 재부착, raw log, backend timeout | Android 실시간 루프의 기본 지연과 로그 비용을 줄였다. | commits `08e9418`, `a65702e`, `a9e286b` |
| 2026-05-03 ~ 2026-05-05 | visual text masking, ROI planning, limited OCR | 전체 화면 OCR 대신 제한된 ROI OCR 구조로 바꿨다. | commits `c475bff`, `a943ba5`, `df41f13` |
| 2026-05-12 ~ 2026-05-13 | scroll overlay stabilization | 스크롤 중 mask 보존/폐기/재분석 정책을 반복 조정했다. | commits `d447e1f` through `5563100` family |
| 2026-05-15 ~ 2026-05-17 | candidate routing, latency diagnostics, char box work reduction | 후보가 왜 분석/렌더/제외되는지 진단 가능하게 만들고, char box 비용을 줄였다. | commits `7f0fb06`, `ecbb4ba`, `51d4e1a`, `353b78a` |
| 2026-05-29 | mask geometry stability | browser/generic accessibility 후보를 더 엄격히 게이트해 잘못 뜨는 마스크를 줄였다. | commit `c8a8ba7`, merge `b015ca0` |

## 현재 검증 결과

실행일: 2026-05-31

```bash
./scripts/android-mask-check.sh fast
```

결과:

```text
BUILD SUCCESSFUL in 5s
24 actionable tasks: 24 up-to-date
```

이 검증은 다음 범위를 포함한다.

| 테스트 묶음 | 확인 목적 |
| --- | --- |
| `ProvisionalAccessibilityMaskBuilderTest` | 접근성 후보에서 provisional mask를 만들 때 출처와 bounds가 보존되는지 확인 |
| `MaskOverlayEventPolicyTest` | 스크롤/컨텐츠 이벤트에서 mask를 보존할지 지울지 판단하는 정책 확인 |
| `MaskOverlayPlannerTest` | 좌표, 크기, source별 gate를 거쳐 실제 overlay 후보가 안전한지 확인 |
| `ScreenTextCandidateExtractorTest` | YouTube/브라우저/일반 앱 접근성 텍스트 후보 수집 확인 |
| `VisualTextRoiPlannerTest` | OCR 대상 ROI가 과하게 넓어지지 않고 제한적으로 선택되는지 확인 |
| `VisualTextOcrCandidateFilterTest` | OCR 후보 텍스트에서 분석할 range를 찾는 규칙 확인 |
| `YoutubeAnalysisTargetExtractorTest` | YouTube title/comment 후보 추출과 contentDescription-only 처리 확인 |

## 아직 숫자로 채워야 하는 표

아래 표는 `scripts/android-demo-evidence.sh aggregate`가 생성하는 결과를 발표용으로 줄인 형태다. 아직 실기기 반복 측정이 없으면 값을 추정하지 말고 `Validation Needed`로 둔다.

| Feature set | Scenario | 01 collect ms | 02 API ms | 03 OCR ms | 04 coord ms | 05 display ms | Render rate | Missed harmful | False mask | Stale mask | 결론 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | --- |
| `full-pipeline` | `youtube-comment-scroll` | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | 최종 체감 경로 |
| `accessibility-only` | `youtube-comment-scroll` | Validation Needed | Validation Needed | n/a | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | OCR 없이 가능한 범위 |
| `ocr-roi` | `youtube-thumbnail-ocr` | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | 접근성 누락 보완 범위 |
| `charbox-line` | `chrome-search-result` | Validation Needed | Validation Needed | n/a | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | 위치 정확도 개선 범위 |
| `overlay-gate` | `chrome-search-result` | Validation Needed | Validation Needed | n/a | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | false mask 감소와 missed trade-off |
| `backend-offline` | `backend-offline` | Validation Needed | Validation Needed | n/a | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | Validation Needed | 실패가 빠르게 끝나는지 |

## 에뮬레이터 반복 측정 규칙

실기기가 없을 때는 `scripts/android-pipeline-benchmark.sh`로 Chrome fixture 기반 반복 측정을 먼저 만든다.

```bash
RUNS_PER_MODE=10 \
RECORD_SECONDS=12 \
./scripts/android-pipeline-benchmark.sh run
```

이 결과는 발표에서 다음처럼 표현한다.

| 구분 | 사용 가능 | 아직 불가 |
| --- | --- | --- |
| Emulator evidence | 실제 stage toggle별 latency, 후보 수, overlay render rate, OCR/coord/backend 로그 신호 | 실사용 기기 배터리/발열, 앱별 실제 accessibility tree 차이 확정 |
| Automated fixture | 같은 화면에서 10회 이상 반복 측정, single-stage와 cumulative 조합 비교, screenshot/video/logcat 동시 확보 | 사람 눈 기준 missed/false/stale 최종 판정 |
| Manual review after batch | `review` 모드로 missed/false/stale/PPT-ready 입력 | 자동 수치만으로 “정확히 잘 가려졌다” 확정 |

따라서 emulator report는 “구조와 반복성 근거”로 쓰고, 실기기 결과가 없으면 “실기기 최종 검증 필요” 상태를 유지한다.

## 2026-06-01 레거시 에뮬레이터 반복 측정 결과

실행 조건:

- 기기: Android emulator `sdk_gphone64_arm64`
- 화면: deterministic Chrome fixture
- 반복: feature label별 10회, 총 70회
- 녹화: run당 8초
- 산출물 루트: `/private/tmp/chungmaru-android-emulator-batch-20260601T221413/runs`
- 집계 리포트: `docs/evidence/android-emulator-batch-results.md`

발표에 넣을 때는 아래 표를 “레거시 라벨 기반 에뮬레이터 반복 측정”으로만 표현한다. `FEATURE_SET`은 순수 기능 토글이 아니라 같은 앱 빌드에서 라벨과 시나리오 강조점을 달리한 값이다. `backend-offline`만 실제로 닫힌 포트를 사용해 실패 경로를 측정했다. 단계별 비교는 새 `android-pipeline-benchmark.sh` 결과로 대체한다.

| Feature set | Runs | Avg collect ms | Collect range | Avg API ms | API range | Avg candidates | Avg rendered | Avg offensive | Avg stale/log | 해석 |
| --- | ---: | ---: | --- | ---: | --- | ---: | ---: | ---: | ---: | --- |
| `accessibility-only` | 10 | 1222.6 | 973-1527 | 242.6 | 193-352 | 16.0 | 0.0 | 1.9 | 57.0 | 접근성 후보 수집과 backend 분석 신호는 반복 확인됨 |
| `backend-only` | 10 | 1326.0 | 933-2192 | 231.2 | 184-313 | 16.0 | 0.0 | 2.0 | 62.9 | backend connected 경로의 평균 응답 규모 확인 |
| `ocr-roi` | 10 | 1342.1 | 930-1897 | 217.4 | 182-304 | 16.0 | 0.0 | 2.0 | 58.8 | Chrome fixture에서는 OCR/overlay 최종 판정은 추가 리뷰 필요 |
| `charbox-line` | 10 | 1390.7 | 893-1969 | 225.8 | 178-305 | 16.0 | 0.0 | 2.0 | 56.2 | 일부 coord ms만 기록되어 대표값으로 쓰기엔 제한적 |
| `overlay-gate` | 10 | 1283.2 | 905-1773 | 214.7 | 165-251 | 16.0 | 0.0 | 2.0 | 54.8 | gate가 렌더링을 보수적으로 막는 상태로 보임 |
| `full-pipeline` | 10 | 1417.2 | 977-2002 | 296.5 | 185-797 | 16.0 | 0.0 | 1.9 | 64.7 | 전체 조합의 분석 latency 반복 근거 |
| `backend-offline` | 10 | 1393.7 | 1087-1807 | 34.8 | 9-74 | 16.0 | 0.0 | 0.0 | 30.2 | 서버 미연결 실패 경로가 빠르게 끝나는지 확인 |

중요한 한계:

- 모든 run에서 `Avg rendered = 0.0`이므로 이 배치는 “분석 단계와 후보 수집 latency 증거”로 보는 것이 맞다.
- 실제 “잘 가려졌는지”는 각 run의 `demo.mp4`와 `screen.png`를 보고 `review` 모드로 missed/false/stale 값을 채워야 한다.
- 실기기 또는 Android 앱이 실제로 지원하는 YouTube/Chrome 화면에서 `overlay rendered > 0` 증거가 추가되어야 “마스킹 성공률” 슬라이드로 쓸 수 있다.

## PPT 추천 구성

### 슬라이드 1: Android 병목은 OCR 자체보다 후보 수집 범위다

- 접근성 텍스트가 있으면 OCR 없이 backend로 보낸다.
- 이미지/썸네일/영상 프레임은 접근성 트리에 없으므로 ROI OCR로 분리한다.
- 전체 화면 OCR은 배터리, 개인정보, 지연 문제 때문에 사용하지 않는다.

시각화: "전체 화면 OCR 금지 -> 접근성 후보 우선 -> ROI 최대 6개 -> backend 분석" 흐름도.

### 슬라이드 2: 실시간성과 정확도의 trade-off

- backend timeout을 짧게 둬 화면 루프가 오래 멈추지 않게 했다.
- scroll/content/window 이벤트별 분석 지연을 다르게 둬 stale mask와 늦은 재부착 사이를 조정했다.
- raw accessibility 로그는 verbose일 때만 켜서 실사용 비용을 줄였다.

시각화: delay bar. `text 12ms`, `scroll 32ms`, `content 40ms`, `window 60ms`, `backend read timeout 1200ms`.

### 슬라이드 3: 잘못 가리는 것보다 근거 있는 마스킹

- contentDescription-only 카드나 넓은 browser row는 글자 좌표가 아니므로 바로 overlay하지 않는다.
- evidence span과 bounds가 맞는 후보만 렌더링한다.
- 결과적으로 일부 텍스트는 OCR geometry 보정 전까지 보일 수 있지만, 잘못된 마스크 체감은 줄인다.

시각화: "분석 후보"와 "렌더 후보"를 분리한 funnel.

### 슬라이드 4: 검증 상태

- 통과: focused Android masking unit tests, diff whitespace check.
- 부분 검증: ROI 제한, char box 제한, timeout, scroll stabilization 정책.
- 남은 검증: 실기기 latency, bbox 정확도, stale overlay 반복 재현, Android+Chrome 통합 demo.

시각화: 완료/검증중/남은 검증 3열 표.

## 발표에서 조심해야 할 표현

| 피할 표현 | 더 정확한 표현 |
| --- | --- |
| Android OCR 마스킹 완성 | Android OCR/overlay 구조 개발, 실기기 검증 필요 |
| 모든 유해 텍스트를 즉시 차단 | 접근성 후보와 신뢰 가능한 OCR bbox를 우선 중재 |
| 성능 개선 완료 | 후보 수/timeout/log/scroll policy를 줄였고, live latency는 추가 계측 필요 |
| 안 가려진 것은 모델 실패 | Android에서는 모델 미탐, 후보 누락, 좌표 불확실 제외를 분리해야 함 |

## 남은 실기기 증거 확보 체크리스트

PPT에 "실기기 확인 완료"라고 쓰려면 아래 세 가지를 추가로 남겨야 한다.

1. YouTube 또는 Chrome 화면에서 mask 적용 전/후 screenshot.
2. `scripts/android-mask-check.sh device` artifact: `screen.png`, `mask-logcat.txt`, `youtube_comments_latest.json`.
3. logcat에서 latency, selected ROI count, rendered mask count, rejected/suppressed reason을 확인한 짧은 표.

## 관련 파일

- `docs/engineering-history.md`
- `docs/presentation/final-ppt-generation-brief.md`
- `scripts/android-mask-check.sh`
- `android/app/src/main/java/com/capstone/design/youtubeparser/AndroidAnalysisClient.kt`
- `android/app/src/main/java/com/capstone/design/youtubeparser/AccessibilityCharacterBoxPolicy.kt`
- `android/app/src/main/java/com/capstone/design/youtubeparser/VisualTextRoiPlanner.kt`
- `android/app/src/main/java/com/capstone/design/youtubeparser/YoutubeAccessibilityService.kt`
- `android/app/src/main/java/com/capstone/design/youtubeparser/MaskOverlayController.kt`
