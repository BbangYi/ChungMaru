# PPT 이미지 에셋

이 폴더는 PPT 생성 AI나 수동 편집기에 바로 넣을 수 있는 발표용 이미지 자료를 모아 둔 위치입니다.
새로 만든 그래프/도식 PNG와 기존 `청마루_발표자료_v1.pptx` 내부 media 추출본을 함께 보관합니다.

## 새로 생성한 발표용 이미지

### 최종 순서 기준 이미지

| 파일 | 용도 |
| --- | --- |
| `15_final_ppt_flow_order.png` | 표지 -> 개발 계획 -> 개발 진행상황 -> 완료된 산출 지점 -> 모델 평가 -> 제약/어려움 -> 앞으로의 계획 순서 고정 |
| `16_completed_outputs_current_state.png` | 현재 완료된 산출 지점 정리 |
| `17_constraints_and_future_plan.png` | 제약 사항과 앞으로의 계획 연결 |

### 타임라인형 미니멀 이미지

| 파일 | 용도 |
| --- | --- |
| `09_minimal_initial_plan_timeline.png` | 최초 1차~4차 계획 대비 현재 위치 타임라인 |
| `10_minimal_plan_progress_matrix.png` | 최초 계획, 현재 도달점, 남은 검증을 나란히 보여주는 표 |
| `11_minimal_development_timeline.png` | 문제 재정의부터 현재 단계까지의 개발 흐름 |
| `12_minimal_timeline_ppt_outline.png` | 타임라인형 10장 PPT 구성안 |
| `13_minimal_model_evaluation_snapshot.png` | 과하지 않은 모델 평가 핵심 수치 요약 |
| `14_minimal_failure_priority_bars.png` | 실패 유형 우선순위 막대그래프 |

### 기존 그래프/도식 이미지

| 파일 | 용도 |
| --- | --- |
| `01_model_evaluation_summary.png` | 모델 평가 핵심 수치 카드: 1007건, 정확도 78.55%, 실패 216건, FP/FN |
| `02_false_positive_false_negative_balance.png` | False Positive와 False Negative의 의미 비교 |
| `03_failure_type_distribution.png` | 주요 실패 유형별 분포 막대그래프 |
| `04_threshold_adjustment_limit.png` | threshold 조정만으로 해결하기 어려운 이유 |
| `05_system_architecture_flow.png` | Chrome/Android 수집 계층, backend 판단 계층, 화면 중재 계층 구조도 |
| `06_development_status_matrix.png` | Backend, Chrome, Android, Evaluation, Docs 개발 현황 표 |
| `07_pipeline_evaluation_frame.png` | keyword-only부터 full pipeline까지 평가 프레임 |
| `08_ppt_storyboard_overview.png` | 10~12장 발표 흐름 개요 |

## 기존 PPTX 추출 이미지

`source-pptx-image01.png`부터 `source-pptx-image20.png`까지는 기존 `docs/presentation/청마루_발표자료_v1.pptx` 안에 들어 있던 media를 그대로 추출한 파일입니다.
새 PPT를 만들 때 기존 시각 요소를 재사용하거나 비교할 때 사용합니다.

## 수치 출처

- 모델 평가 스냅샷: `docs/presentation/materials/backend-model-failure-summary-20260506.md`
- 전체 케이스: 1007건
- 정답: 791건
- 정확도: 78.55%
- 실패: 216건
- False Positive: 86건
- False Negative: 130건
- 주요 실패 유형: `kold_clean` FP 72건, `kold_offensive_other` FN 55건, `kold_racial_hate` FN 21건, `kold_gender_hate` FN 17건
- threshold 한계 예시: `>=0.70` 구간 FP 66건, `0.30~0.50` 구간 FN 85건

## 사용 원칙

- 최종 PPT에서는 `15`, `16`, `17` 이미지를 우선 사용합니다.
- `09`~`14` 이미지는 개발 계획/진행상황/모델 평가를 보조 설명할 때 사용합니다.
- `01`~`08` 이미지는 모델 평가나 구조 설명이 더 크게 필요한 경우에만 보조로 사용합니다.
- 기존 PPTX 추출 이미지는 보조 자료로 사용합니다.
- 2026-05-06 이후 backend 모델 또는 평가 케이스가 바뀌면 수치를 다시 평가하고 이미지도 갱신해야 합니다.
- Android OCR/visual masking은 실기기 검증 전까지 완성 기능이 아니라 진행 중인 개선 과제로 표현합니다.
