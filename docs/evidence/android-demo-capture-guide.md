# Android Demo Capture Guide

생성일: 2026-06-01

목적: Android에서 여러 masking/OCR/overlay 개선을 붙인 뒤, 실제 반응속도와 마스킹 품질을 PPT/보고서에 넣을 수 있는 형태로 수집한다.

## 산출물

한 번의 실행에서 아래 파일이 만들어진다.

| 산출물 | 용도 |
| --- | --- |
| `summary.md` | 발표/보고서에 옮길 핵심 수치 요약 |
| `demo.mp4` | 데모 영상 원본 |
| `screen.png` | 최종 화면 스크린샷 |
| `logs/mask-logcat.txt` | 분석, OCR, overlay, stale/scroll 로그 |
| `analysis-diagnostics.xml` | 앱 내부 최신 진단값 |
| `json/*_comments_latest.json` | 앱이 수집한 최신 후보 JSON |
| `json/*_comments.jsonl` | 누적 parse 결과 |
| `json/*_analysis.jsonl` | 누적 backend 분석 결과 |
| `manifest.txt` | 기기, git head, scenario, 실행 시각 |

기본 저장 위치는 `/private/tmp/chungmaru-android-demo-<timestamp>`이다. 영상과 로그가 커질 수 있으므로 기본값은 repo 밖이다.

## 사전 조건

1. Android 기기 또는 emulator가 `adb devices`에서 `device`로 보여야 한다.
2. 청마루 접근성 서비스가 기기에서 활성화되어 있어야 한다.
3. backend 분석까지 측정하려면 앱의 분석 서버 URL이 실제 `/analyze_android`로 연결되어야 한다.
4. emulator에서 Mac의 `127.0.0.1:8000` backend를 쓰려면 `ADB_REVERSE=1`을 사용한다.
5. 실기기에서는 Tailscale/LAN 주소 또는 앱 설정의 분석 서버 주소를 사용한다.

## 빠른 구조 검증

기기 없이 Android masking 핵심 정책만 확인한다.

```bash
./scripts/android-mask-check.sh fast
```

통과 기준:

- `BUILD SUCCESSFUL`
- focused masking unit tests 통과
- Android masking source diff whitespace check 통과

## 데모 증거 전체 수집

기기를 연결한 뒤 실행한다.

```bash
RECORD_SECONDS=60 \
SCENARIO=youtube-scroll-mask \
FEATURE_SET=full-pipeline \
./scripts/android-demo-evidence.sh full
```

실행 중 해야 할 일:

1. YouTube 또는 Chrome 테스트 화면을 연다.
2. 유해 표현이 있는 제목/댓글/검색 결과를 노출한다.
3. 마스크가 붙는지 확인한다.
4. 한두 번 스크롤해서 stale mask가 남는지 확인한다.
5. 마지막 5초는 최종 화면을 멈춰 스크린샷/영상 마지막 프레임이 해석 가능하게 둔다.

## 5단계 파이프라인 기록 방식

각 실행의 `summary.md`는 아래 5단계 기준으로 정리된다.

| 단계 | 구현 축 | 현재 자동 기록 | 추가로 보면 좋은 데이터 |
| --- | --- | --- | --- |
| 01 수집 | `AccessibilityService`가 화면 텍스트와 bounds 후보를 모아 댓글/검색결과 가능 영역을 좁힘 | `parse delay ms`, `candidate extraction ms`, 후보 수 | 전체 노드 수, 후보 제외 사유, 노드당 순회 비용 |
| 02 분석 | Android 후보를 `/analyze_android`로 보내 점수와 evidence span을 받음 | `total analysis latency ms`, `backend mask latency ms`, offensive/filtered 수 | 서버 cold start, 네트워크 왕복, 모델 추론 시간 분리 |
| 03 보완 | 전체 화면 OCR 대신 후보 ROI만 OCR 대상으로 제한 | `visual OCR latency ms`, ROI 후보/선택 수, OCR raw/selected 수 | crop 시간과 OCR 인식 시간 분리, ROI별 실패 사유 |
| 04 좌표 | char box와 line estimate로 근거 위치를 좁히되 요청 비용 제한 | `accessibility mask latency ms`, unstable skip 수 | char-box 요청 ms, line estimate ms, evidence span 매칭 실패 수 |
| 05 표시 | overlay gate가 불확실한 화면 범위를 렌더링에서 제외 | `visual mask latency ms`, overlay candidates/rendered/render rate | 실제 영상 기준 missed/false/stale 수동 판정 |

중요한 점은 `backend positive`가 곧바로 “화면에 마스크 렌더링”을 의미하지 않는다는 것이다. 분석 결과가 있어도 bounds, evidence span, 화면 안정성 gate를 통과하지 못하면 `05 표시` 단계에서 제외될 수 있다.

## 실제 파이프라인 토글 benchmark

단계별 성능 비교는 `FEATURE_SET` 라벨이 아니라 앱 내부의 `pipeline_experiment_mode`를 실제로 바꿔야 한다. 이 용도로 `scripts/android-pipeline-benchmark.sh`를 사용한다.

```bash
RUNS_PER_MODE=10 \
RECORD_SECONDS=12 \
./scripts/android-pipeline-benchmark.sh run
```

기본 실행은 아래 9개 모드를 10회씩 돌린다.

| Mode | 단계 | 측정 의미 |
| --- | --- | --- |
| `s1_collect_only` | 1 | 접근성 노드/후보 수집만 저장 |
| `s2_backend_only` | 2 | 후보를 backend로 보내는 API latency 중심 |
| `s3_ocr_roi_only` | 3 | ROI OCR 경로만 실행, backend/overlay 제외 |
| `s4_coord_only` | 4 | char box/line 좌표 후보 계산 비용 |
| `s5_overlay_only` | 5 | provisional response 기반 overlay gate/render 비용 |
| `s12_collect_backend` | 1+2 | 수집 후 backend 분석 |
| `s123_collect_backend_ocr` | 1+2+3 | 수집/backend/OCR 보완 |
| `s1234_collect_backend_ocr_coord` | 1+2+3+4 | 좌표 보정까지 포함 |
| `s12345_full` | 1+2+3+4+5 | 최종 사용자 체감 경로 |

출력은 `docs/evidence/android-pipeline-benchmark/` 아래 CSV와 발표용 markdown으로 생성된다.

| 파일 | 용도 |
| --- | --- |
| `raw_runs.csv` | run별 원시 latency, 후보 수, OCR/overlay count |
| `summary_by_mode.csv` | mode별 평균/최소/최대 요약 |
| `stage_delta.csv` | 누적 조합에서 2,3,4,5단계 추가 비용 비교 |
| `ppt_table.md` | PPT로 옮기기 쉬운 표 |

기존 `scripts/android-emulator-evidence-batch.sh`는 historical/legacy batch다. 이 스크립트의 `FEATURE_SET`은 라벨이므로 단계별 성능 비교의 최종 근거로 쓰지 않는다.

## 라벨 기반 수동 데모 실행

영상 확인까지 끝난 단일 실행은 수동 품질값을 같이 남기면 집계표에 바로 들어간다.

예시 실행:

```bash
FEATURE_SET=full-pipeline SCENARIO=youtube-comment-scroll RECORD_SECONDS=60 \
./scripts/android-demo-evidence.sh full

FEATURE_SET=accessibility-only SCENARIO=youtube-comment-scroll RECORD_SECONDS=60 \
./scripts/android-demo-evidence.sh full

FEATURE_SET=ocr-roi SCENARIO=youtube-thumbnail-ocr RECORD_SECONDS=60 \
./scripts/android-demo-evidence.sh full

FEATURE_SET=overlay-gate SCENARIO=chrome-search-result RECORD_SECONDS=60 \
./scripts/android-demo-evidence.sh full

FEATURE_SET=backend-offline SCENARIO=backend-offline RECORD_SECONDS=45 \
./scripts/android-demo-evidence.sh full
```

영상 확인까지 끝난 실행은 수동 품질값을 같이 남기면 집계표에 바로 들어간다. 이미 artifact가 만들어진 뒤라면 `review` 모드만 실행하면 된다.

```bash
ARTIFACT_DIR=/private/tmp/chungmaru-android-demo-20260601T210000 \
FEATURE_SET=full-pipeline \
SCENARIO=youtube-comment-scroll \
MASK_ALIGN=yes \
EXPECTED_HARMFUL=3 \
MISSED_HARMFUL=0 \
FALSE_MASK=0 \
STALE_MASK=0 \
PPT_READY=yes \
QUALITY_NOTES="댓글 3개 모두 최종 프레임에서 마스킹됨" \
./scripts/android-demo-evidence.sh review
```

이 값들은 `manifest.txt`, `summary.md`, aggregate report의 품질 열에 들어간다.

## 레거시 에뮬레이터 10회 반복 배치

아래 배치는 `pipeline_experiment_mode` 도입 전 만들어진 라벨 기반 배치다. historical baseline으로 남기되, 발표용 단계별 비교는 위의 `android-pipeline-benchmark.sh` 결과를 우선한다.

```bash
RUNS_PER_FEATURE=10 \
FEATURE_SETS="full-pipeline accessibility-only backend-only ocr-roi charbox-line overlay-gate backend-offline" \
RECORD_SECONDS=12 \
./scripts/android-emulator-evidence-batch.sh run
```

출력:

| 산출물 | 위치 |
| --- | --- |
| 개별 run artifact | `/private/tmp/chungmaru-android-emulator-batch-<timestamp>/runs/*` |
| fixture HTML | `/private/tmp/chungmaru-android-emulator-batch-<timestamp>/fixture/index.html` |
| aggregate report | `docs/evidence/android-emulator-batch-results.md` |

주의할 점:

- `FEATURE_SET`은 배치에서도 라벨과 scenario emphasis다. 앱 코드를 자동으로 꺼서 순수 `accessibility-only`를 만드는 것은 아니다.
- `backend-offline`은 분석 서버 주소를 닫힌 포트로 바꿔 실패 경로를 측정한다.
- 나머지 라벨은 같은 앱 빌드에서 fixture와 해석 축을 달리해 반복 측정한다. 발표에는 “emulator 반복 측정”으로 쓰고, “실기기 최종 검증”과 구분한다.
- 자동 배치는 `PPT_READY=no`로 남긴다. `demo.mp4`를 보고 `review` 모드로 missed/false/stale 값을 채운 뒤 PPT-ready로 바꾼다.

최근 실행:

```text
2026-06-01 / Medium_Phone emulator / RECORD_SECONDS=8
batch root: /private/tmp/chungmaru-android-emulator-batch-20260601T221413
report: docs/evidence/android-emulator-batch-results.md
```

결과:

| Feature set | Runs | 비고 |
| --- | ---: | --- |
| `full-pipeline` | 10 | 전체 조합 분석 latency와 후보 수집 증거 |
| `accessibility-only` | 10 | 접근성 후보 중심 반복 증거 |
| `backend-only` | 10 | backend connected 경로 반복 증거 |
| `ocr-roi` | 10 | OCR/ROI 시나리오 라벨 반복 증거 |
| `charbox-line` | 10 | 좌표 추정 시나리오 라벨 반복 증거 |
| `overlay-gate` | 10 | gate/stale 스크롤 시나리오 라벨 반복 증거 |
| `backend-offline` | 10 | 닫힌 포트 기반 backend 실패 경로 증거 |

현재 Chrome fixture batch에서는 `overlay rendered`가 0으로 집계되었다. 즉 이 batch는 분석/후보/latency 증거로 쓰고, 마스킹 성공률은 실제 지원 화면에서 별도 수동 리뷰 또는 실기기 증거를 추가해야 한다.

## backend 로컬 연결 예시

emulator에서 Mac의 `localhost:8000` backend를 쓰는 경우:

```bash
ADB_REVERSE=1 \
RECORD_SECONDS=60 \
SCENARIO=youtube-backend-mask \
./scripts/android-demo-evidence.sh full
```

실기기에서는 `ADB_REVERSE=1`이 일반적으로 충분하지 않다. 이 경우 앱 설정에서 Tailscale 또는 LAN 주소를 넣고 실행한다.

## 단계별 실행

빌드/설치만 먼저:

```bash
./scripts/android-demo-evidence.sh prepare
```

녹화만:

```bash
RECORD_SECONDS=45 ./scripts/android-demo-evidence.sh capture
```

수집만:

```bash
./scripts/android-demo-evidence.sh collect
```

영상 리뷰값만 갱신:

```bash
ARTIFACT_DIR=/private/tmp/chungmaru-android-demo-20260601T210000 \
MISSED_HARMFUL=0 FALSE_MASK=0 STALE_MASK=0 PPT_READY=yes \
./scripts/android-demo-evidence.sh review
```

이미 빌드된 APK를 다시 쓰고 싶으면:

```bash
SKIP_BUILD=1 ./scripts/android-demo-evidence.sh full
```

## summary.md 해석 기준

`summary.md`의 핵심 항목은 아래처럼 본다.

| 지표 | 의미 | PPT 사용 기준 |
| --- | --- | --- |
| `total analysis latency ms` | backend 분석 포함 전체 분석 지연 | 반복 측정 평균/범위가 있으면 반응속도 슬라이드에 사용 |
| `candidate extraction ms` | 접근성 후보 추출 비용 | 노드 순회/후보 수집 병목 설명에 사용 |
| `accessibility mask latency ms` | 접근성 기반 mask 적용 지연 | OCR 없이 처리되는 경로의 속도 근거 |
| `backend mask latency ms` | backend 응답 기반 mask 적용 지연 | 모델/API 지연 설명 |
| `visual OCR latency ms` | OCR 보조 경로 지연 | OCR은 보조 경로라는 설명에 사용 |
| `overlay candidates` | 렌더 후보 수 | 마스킹 가능 후보 모수 |
| `overlay rendered` | 실제 화면에 그린 mask 수 | 마스킹 적용 성공 수 |
| `overlay render rate` | rendered / candidates | 좌표 안정성 게이트 결과 |
| `overlay skipped unstable` | 좌표 불안정 등으로 제외된 수 | 왜 일부가 안 가려졌는지 설명 |
| `visual ROI selected` | OCR 대상으로 선택된 ROI 수 | 전체 화면 OCR을 피했다는 근거 |
| `visual OCR selected count` | OCR 결과 중 분석 대상으로 남은 후보 수 | OCR 후보 필터링 근거 |

`Pipeline Stage Breakdown` 표는 발표에서 그대로 5단계 흐름으로 옮기기 위한 표다. 값이 `n/a`로 나오면 그 단계가 실행되지 않았거나, 앱에서 아직 별도 계측값을 저장하지 않는다는 뜻이다. 이 경우 “검증 필요”로 표시하고, `Missing data to improve` 열의 항목을 다음 계측 대상으로 잡는다.

## 마스킹 품질 판정표

자동 수치만으로는 “잘 가려졌다”를 확정할 수 없다. `summary.md`의 Manual Review Slots를 채운다.

| 항목 | 좋은 결과 |
| --- | --- |
| Mask aligns with harmful text | 마스크가 실제 유해 표현 위에 있거나 충분히 가까움 |
| Harmful text missed | 최종 화면에서 유해 표현이 남아 있지 않거나, 남은 이유가 `좌표 불확실/후보 누락/backend 미탐`으로 구분됨 |
| False mask on safe content | 정상 텍스트 위에 잘못 뜬 mask가 없음 |
| Stale mask after scroll | 스크롤 후 이전 위치에 mask가 남지 않음 |
| PPT-ready | 영상, 스크린샷, logcat, JSON, 진단값이 모두 같은 시나리오를 설명함 |

## 반복 측정 권장 세트

PPT에 숫자를 넣으려면 같은 scenario를 최소 3회 반복한다.

| Scenario | 추천 `FEATURE_SET` | 목표 |
| --- | --- | --- |
| `youtube-comment-scroll` | `full-pipeline`, `accessibility-only`, `overlay-gate` | 댓글 접근성 후보, 스크롤 stale mask 확인 |
| `youtube-thumbnail-ocr` | `full-pipeline`, `ocr-roi` | 이미지/썸네일 OCR ROI 후보 확인 |
| `chrome-search-result` | `full-pipeline`, `charbox-line`, `overlay-gate` | 브라우저 접근성 bounds와 overlay gate 확인 |
| `backend-offline` | `backend-offline` | backend 실패 시 빠르게 실패하고 loop가 풀리는지 확인 |

최종 표에는 각 scenario와 feature set별로 `01 collect ms`, `02 API ms`, `03 OCR ms`, `04 coord ms`, `05 display ms`, `overlay rendered`, `overlay render rate`, `missed harmful text`, `false mask`, `stale mask`를 정리한다.

반복 실행 후 표를 자동으로 묶는다.

```bash
REPORT_PATH=docs/evidence/android-demo-results.md \
./scripts/android-demo-evidence.sh aggregate \
  /private/tmp/chungmaru-android-demo-20260601T210000 \
  /private/tmp/chungmaru-android-demo-20260601T211000 \
  /private/tmp/chungmaru-android-demo-20260601T212000
```

artifact 경로를 넘기지 않으면 `/private/tmp/chungmaru-android-demo-*` 중 최신 실행을 묶는다.

```bash
./scripts/android-demo-evidence.sh aggregate
```

## 발표에 쓸 수 있는 문장

- Android는 전체 화면 OCR을 돌리지 않고, 접근성 후보를 우선 사용한 뒤 필요한 경우 ROI OCR만 보조 경로로 실행했다.
- 마스킹은 backend positive만으로 적용하지 않고, evidence span과 화면 bounds를 함께 만족하는 후보만 렌더링했다.
- 성능 검증은 단위 테스트와 실기기 evidence pack을 분리했다. 단위 테스트는 정책 회귀를 막고, 실기기 pack은 지연, 좌표, stale mask를 확인한다.

## 실패했을 때 해석

| 증상 | 먼저 볼 파일 |
| --- | --- |
| 분석 서버 연결 실패 | `summary.md`, `logs/mask-logcat.txt`, `analysis-diagnostics.xml`의 `error`, `analysis url` |
| 마스크가 하나도 안 뜸 | `overlay candidates`, `overlay rendered`, `json/*_analysis.jsonl` |
| 유해 표현이 남음 | `json/*_comments_latest.json`, `json/*_analysis.jsonl`, `overlay skipped unstable` |
| 엉뚱한 위치에 뜸 | `screen.png`, `demo.mp4`, `overlayRenderedSamples`, `logs/mask-logcat.txt` |
| 스크롤 후 잔류 | `demo.mp4`, log signal `scroll/stale/defer/preserve` |

## 관련 스크립트

- `scripts/android-mask-check.sh`
- `scripts/android-demo-evidence.sh`
