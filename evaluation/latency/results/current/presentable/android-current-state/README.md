# 청마루 Android 현재 상태 E2E 데모

촬영 시각: 2026-06-09 KST

## 목적

이 영상은 성공 장면만 편집한 완성 데모가 아니라, Android 버전의 현재 상태를 그대로 보여주는 E2E 증거입니다. 따라서 다음 두 가지를 함께 보여줍니다.

- 외부 Android 앱 화면에서 마스킹 자체는 동작합니다.
- 브라우저 스크롤 재수집과 최종 진단값 저장에는 아직 한계가 있습니다.

## 산출물

| 산출물 | 경로 | 용도 |
| --- | --- | --- |
| Android E2E 영상 | `e2e-demo.mp4` | 메인 데모 영상, 52.3초 |
| 마스킹 증거 프레임 | `mask-proof.png` | 유해 표현 위에 검은 마스크가 올라간 대표 프레임 |
| 스크롤 상태 프레임 | `scroll-proof.png` | 재수집 이후 브라우저 화면 상태 확인용 프레임 |
| 콘택트 시트 | `contact-sheet.jpg` | 전체 영상 흐름을 빠르게 검토하는 이미지 |
| Logcat 증거 | `mask-logcat.txt` | 분석 요청과 overlay 렌더링의 런타임 증거 |

## 영상에서 보이는 흐름

1. Android 에뮬레이터에서 현재 설치된 앱 상태를 시작합니다.
2. Chrome에서 Android 파이프라인 테스트 페이지를 엽니다.
3. Accessibility Service가 읽을 수 있는 텍스트 영역에 유해 표현이 등장합니다.
4. Android overlay가 탐지된 span 위에 검은 마스크를 렌더링합니다.
5. 이후 브라우저 스크롤 구간에서 현재 한계도 보입니다. 스크롤 중에는 stale mask를 피하기 위해 마스크가 숨겨지고, 안정화 후 재수집을 기다립니다.

## 확인된 런타임 신호

세션 중 백엔드 `/health`를 확인했습니다.

- `model_ready=true`
- `pipeline_loaded=true`
- `classifier_loaded=true`
- `span_detector_loaded=true`

Logcat 기준 Android 클라이언트가 백엔드에 도달했습니다.

- `analysis ok url=http://127.0.0.1:8000/analyze_android`
- `comments=14`
- `actionableOffensive=2`
- `latencyMs=1324`

Logcat 기준 마스킹 렌더링도 확인됐습니다.

- fast provisional 경로에서 `render maskCount=2`
- Android accessibility char-range 경로에서 `render maskCount=3`
- `com.android.chrome` 화면의 탐지 span에 대해 실제 좌표 생성

## 이 데모가 보여주는 현재 한계

이 영상은 최종 품질 데모가 아니라 현재 상태 증거로 설명하는 것이 맞습니다.

- 마지막 앱 전환이 최신 진단 스냅샷을 덮을 수 있어 summary의 일부 진단값이 `n/a`로 남을 수 있습니다.
- Chrome/browser 스크롤 중에는 안정적인 재수집을 기다리기 위해 마스크를 숨깁니다. stale mask를 막는 장점은 있지만, 사용자 눈에는 마스크가 깜빡이거나 사라지는 것처럼 보일 수 있습니다.
- 영상은 overlay가 실제 화면 영역을 가릴 수 있음을 확인하지만, 정확한 정렬 품질은 프레임 단위 QA가 더 필요합니다.
- 실제 YouTube 댓글 피드가 아니라 에뮬레이터 기반 fixture 화면입니다.

## 발표용 설명 문장

권장 표현:

> Android 버전은 아직 완성형 데모 품질은 아닙니다. 현재 빌드는 외부 Android 화면에서 유해 표현을 탐지하고 마스킹할 수 있지만, 브라우저 스크롤과 재수집 과정에서 overlay가 일시적으로 숨겨지거나 갱신되는 한계가 남아 있습니다. 따라서 이 영상은 “현재 구현된 마스킹 동작과 남은 제약”을 함께 보여주는 current-state E2E 데모입니다.

가장 강한 시각 자료는 마스킹 증거 프레임이고, 전체 영상은 E2E 흐름과 한계를 함께 보여주는 자료로 사용합니다.
