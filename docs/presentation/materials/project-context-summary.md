# 청마루 발표용 프로젝트 맥락 요약

## 한 줄 정의

청마루는 모바일 앱과 브라우저 익스텐션에서 수집한 온라인 텍스트를 문맥, 신뢰도, 플랫폼 제약까지 함께 고려해 경고, 마스킹, 허용, 보류로 중재하는 문맥형 유해 텍스트 중재 시스템입니다.

## 차별점

- 단순 키워드 필터가 아니라 backend 모델의 문맥 판정을 기준으로 합니다.
- `evidence_spans`가 있는 근거 구간만 화면에 반영해 과차단을 줄입니다.
- Chrome DOM과 Android 접근성/OCR 경로를 분리해 플랫폼별 실패 원인을 추적합니다.
- 오탐, 미탐, 지연, UI 깨짐을 regression과 개발 이력으로 남깁니다.

## 핵심 제약

| 제약 | 의미 | 대응 |
| --- | --- | --- |
| 문맥성 | 같은 단어도 공격, 인용, 설명, 고유명사에서 의미가 달라짐 | backend 모델, safe-context, regression set |
| 실시간성 | backend 호출과 화면 변화 사이에 지연과 race가 생김 | foreground/reconcile 분리, stale response drop |
| 위치 근거 | 유해 판정과 실제 화면 좌표가 항상 일치하지 않음 | boolean과 evidence span 분리 |
| Android 접근성 | 이미지 내부 글자와 DOM span을 직접 얻을 수 없음 | 접근성 text path와 OCR visual path 분리 |
| 모바일 overlay | 큰 bounds를 그대로 가리면 카드 전체가 덮이거나 위치가 어긋남 | exact box 또는 작은 고신뢰 bounds만 사용 |

## Android 실시간 탐지 방향

Android에서는 실시간 탐지를 “무조건 빠르게 전체 화면을 가리는 기능”으로 잡지 않습니다.
접근성 텍스트, OCR 후보, backend 판정, overlay 가능 위치를 분리해 어느 단계에서 실패했는지 진단 가능한 구조를 우선합니다.

1. 접근성 text node는 기존 `/analyze_android` 경로로 빠르게 분석합니다.
2. `contentDescription` only 카드나 이미지 내부 글자는 접근성 path에서 억지로 마스킹하지 않습니다.
3. 이미지 텍스트는 ROI OCR 후보로 분리하고, OCR text box가 있을 때만 backend evidence를 투영합니다.
4. 빠른 스크롤 중 오래된 overlay는 폐기하고 안정화된 viewport에서 다시 분석합니다.
5. 실기기 검증 전에는 unit test와 APK build로 계약과 렌더링 정책을 먼저 고정합니다.
