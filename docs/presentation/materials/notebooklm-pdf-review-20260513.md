# NotebookLM PDF 검토 메모 - 2026-05-13

검토 파일: `/Users/giminu0930/Desktop/Chungmaru_Contextual_Moderation_System.pdf`

## 확인 내용

- PDF는 12페이지, 16:9 비율의 이미지형 슬라이드입니다.
- 텍스트 추출은 거의 되지 않아 시각 렌더링 기준으로 검토했습니다.
- 모델 평가 수치와 pipeline 메시지는 들어 있지만, 최초 계획 대비 현재 위치가 한눈에 보이지 않습니다.

## 아쉬운 점

- 큰 제목과 강한 카드/박스가 많아 디자인이 다소 과합니다.
- 각 슬라이드가 독립적으로는 설명되지만 “계획 -> 진행 -> 검증” 흐름이 약합니다.
- 최초 계획이 무엇이었고 현재 어느 단계까지 왔는지 발표자가 따로 설명해야 합니다.
- Android OCR/실시간 마스킹은 진행 중 과제인데, 일부 슬라이드에서는 완성 기능처럼 읽힐 위험이 있습니다.

## 수정 방향

- PPT 앞부분에 `최초 계획 대비 현재 위치` 타임라인을 배치합니다.
- `완료`, `완료+고도화`, `진행 중`, `남은 검증` 상태로 현재 위치를 설명합니다.
- 디자인은 흰 배경, 얇은 선, 작은 상태 태그, 간단한 표 중심으로 줄입니다.
- 모델 성능 평가는 큰 그래프보다 “평가 결과가 어떤 개발 방향을 만들었는가”를 중심으로 설명합니다.

## 새 에셋

- `docs/presentation/ppt-assets/09_minimal_initial_plan_timeline.png`
- `docs/presentation/ppt-assets/10_minimal_plan_progress_matrix.png`
- `docs/presentation/ppt-assets/11_minimal_development_timeline.png`
- `docs/presentation/ppt-assets/12_minimal_timeline_ppt_outline.png`
- `docs/presentation/ppt-assets/13_minimal_model_evaluation_snapshot.png`
- `docs/presentation/ppt-assets/14_minimal_failure_priority_bars.png`
