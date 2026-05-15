# 발표자료 구성 계획

## 목표

청마루의 발표는 “AI 모델을 붙였다”가 아니라 실제 화면에서 유해 텍스트를 수집하고, 문맥으로 판정하고, 위치 근거가 있을 때만 안전하게 반영하려고 어떤 제약을 해결했는지 보여주는 데 집중합니다.

## 핵심 흐름

1. 표지: 청마루가 어떤 시스템인지 한 문장으로 정의합니다.
2. 개발 계획: 최초 1차~4차 milestone을 먼저 보여줍니다.
3. 개발 진행상황: 완료, 완료+고도화, 진행 중, 남은 검증을 분리합니다.
4. 완료된 산출 지점: Backend, Chrome, Android, Evaluation, Docs/PPT assets를 정리합니다.
5. 모델 평가: 1007건 평가 수치와 FP/FN 해석을 보여줍니다.
6. 제약 사항 / 어려운 부분: 문맥 오탐, 미탐, 좌표, OCR, 실시간성 문제를 설명합니다.
7. 앞으로의 계획: 회귀셋, pipeline 비교표, Android OCR 검증, 통합 demo, 최종 보고서로 연결합니다.

## 발표에 포함할 근거

| 근거 | 위치 | 사용 목적 |
| --- | --- | --- |
| 서비스 정의 | `materials/project-context-summary.md` | 발표 도입, 차별점, 범위 설명 |
| 모델 실패 분석 | `materials/backend-model-failure-summary-20260506.md` | FP/FN, threshold 한계, regression 필요성 설명 |
| NotebookLM PDF 검토 | `materials/notebooklm-pdf-review-20260513.md` | 현재 PDF의 아쉬운 점과 수정 방향 요약 |
| 최종 PPT 생성 AI 브리프 | `final-ppt-generation-brief.md` | 표지, 개발 계획, 진행상황, 산출 지점, 평가, 제약, 계획 순서로 PPT 생성 |
| 타임라인형 PPT 생성 AI 브리프 | `timeline-ppt-generation-brief.md` | 최초 계획, 현재 도달점, 남은 검증을 설명하는 보조 PPT 생성 |
| 기존 PPT 생성 AI 브리프 | `prmopt.md` | 제안 배경, 개발 계획, 개발 현황, 모델 평가 결과를 일반형 PPT로 생성 |
| 발표 원문 | `청마루_발표자료_v1.source.md` | 슬라이드 본문과 발표자 노트 |
| 생성 결과물 | `청마루_발표자료_v1.pdf`, `청마루_발표자료_v1.pptx` | 제출 또는 리허설용 산출물 |

## Android 실시간 탐지 반영 계획

현재 발표에는 Android 실시간 탐지를 “완성 기능”으로 과장하지 않고, 다음 방향의 진행 중 과제로 반영합니다.

- 접근성 텍스트 후보와 이미지/OCR 후보를 분리합니다.
- backend boolean과 실제 화면에 그릴 수 있는 evidence span을 분리합니다.
- 빠른 스크롤 중 stale overlay가 남지 않도록 stable frame 기준으로 다시 붙입니다.
- 전체 카드/썸네일 마스킹은 금지하고, 정확한 텍스트 box가 있을 때만 반영합니다.
- 실기기 검증 전에는 단위 테스트와 APK build로 계약과 컴파일 안정성을 먼저 확인합니다.

## 완료 기준

- 발표 source가 대용량 원본 폴더를 직접 참조하지 않습니다.
- 발표에 쓰는 수치와 실패 해석은 작은 Markdown 자료로 재확인할 수 있습니다.
- Android 개선이 들어가면 어떤 제약을 줄였는지 `constraints-and-decisions.md` 또는 발표 materials에 요약됩니다.
- PDF/PPTX는 Git 변경량을 키우지 않는 생성 산출물로만 둡니다.
