# 청마루 PPT 생성 AI 브리프

> 현재 추천 브리프는 `timeline-ppt-generation-brief.md`입니다.
> 이 파일은 기존 일반형 PPT 생성 브리프이며, NotebookLM식 결과처럼 최초 계획 대비 현재 위치가 약하게 보일 수 있습니다.

이 문서는 발표자료 생성 AI에게 그대로 전달하기 위한 입력 자료입니다.
목표는 제안서와 기존 개발 계획의 흐름을 유지하면서, 현재 개발 상황과 모델 성능 평가 결과를 간단하지만 내용이 빠지지 않는 PPT로 만드는 것입니다.

## 제작 목표

- 형식: 16:9 와이드 PPT, 10~12장
- 언어: 한국어
- 톤: 캡스톤/프로젝트 발표용, 과장 없이 기술적으로 명확하게
- 디자인: 흰색 또는 밝은 회색 배경, 남색/청록색 포인트, 표와 간단한 흐름도 중심
- 구성: 슬라이드마다 핵심 문장 1개 + 근거 bullet 3~5개
- 이미지 자료: `docs/presentation/ppt-assets/`의 PNG 파일을 우선 사용
- 금지: 긴 문단 나열, 근거 없는 수치 추가, Android OCR/실시간 마스킹을 완성 기능처럼 표현

## AI에게 전달할 최종 프롬프트

아래 내용을 그대로 사용해 PPT를 생성한다.

```md
너는 캡스톤 프로젝트 발표자료를 만드는 전문 디자이너이자 기술 문서 편집자다.
아래 정보를 바탕으로 "청마루: 문맥 기반 실시간 유해 텍스트 중재 시스템" 발표용 PPT를 만들어라.

요구사항:
- 16:9 와이드 PPT
- 총 10~12장
- 한국어
- 단순하고 깔끔한 레이아웃
- 과장된 마케팅 문구보다 개발 과정, 기술 선택, 평가 결과가 잘 보이게 구성
- 각 슬라이드는 제목, 핵심 메시지, 3~5개 bullet, 필요 시 표/간단한 다이어그램으로 구성
- 수치는 아래 제공된 값만 사용하고 임의로 만들지 말 것
- Android 실시간 OCR/visual masking은 "진행 중인 개선 과제"로 표현하고, 이미 완성된 기능처럼 표현하지 말 것

프로젝트명:
- 청마루 / Chungmaru

한 줄 정의:
- 모바일 앱과 브라우저 익스텐션에서 수집한 온라인 텍스트를 문맥, 신뢰도, 플랫폼 제약까지 고려해 경고, 마스킹, 허용, 보류로 중재하는 문맥형 유해 텍스트 중재 시스템

발표의 핵심 메시지:
- 청마루는 단순 욕설 키워드 필터가 아니라, 실제 플랫폼 화면에서 텍스트를 수집하고 backend 모델의 문맥 판정과 evidence span을 이용해 화면에 안전하게 반영하려는 중재 파이프라인이다.

문제 정의:
- 유해 표현은 단어 하나만으로 결정되지 않는다.
- 같은 단어도 직접 공격, 사전 설명, 고유명사, 뉴스 인용, 검색 추천어, 댓글 조롱에서 의미가 달라진다.
- Chrome DOM, Android 접근성 트리, YouTube 썸네일/OCR 후보처럼 플랫폼마다 텍스트 수집 방식이 다르다.
- 너무 넓게 가리면 정상 정보 접근성이 떨어지고, 너무 늦게 가리면 보호 효과가 줄어든다.

기존 접근의 한계:
- 키워드 필터는 빠르지만 문맥 오탐이 크다.
- 모델 단독 판정은 문맥을 볼 수 있지만 실제 화면 반영에서 latency, stale response, span 좌표 문제가 생긴다.
- 모델 정확도만 비교하면 Chrome/Android 수집 제약, evidence span, UI 반영 품질을 설명하기 어렵다.

시스템 구조:
- Chrome Extension: DOM 텍스트 수집, visible container 우선 분석, exact span 마스킹, 입력창 fixed-token UX
- Android App: AccessibilityNodeInfo 기반 텍스트 수집, `/analyze_android` 호출, 좌표 기반 overlay 진단, OCR/ROI 후보 분리
- Backend API: FastAPI 기반 `/analyze_batch`, `/analyze_android`, 모델 판정, 정규화, safe-context, evidence_spans 반환
- Shared/Evaluation: 공통 JSON 계약, normalization/policy/rules, pipeline 평가 케이스 관리

개발 계획 흐름:
1. 1차: 저장소 구조 정리, Android 수집 파이프라인 동작 확인, 공통 계약/정책 구조 정리
2. 2차: Chrome Extension 마스킹 UI 연결, API 초안 및 판단 응답 형식 정리, 플랫폼별 수집 흐름 테스트
3. 3차: pipeline 비교 실험 정리, Demo 시나리오 확정, README 및 발표 자료 정리
4. 4차: 오탐/미탐 사례를 평가 표로 정리, Android 수집 사례와 Chrome 실시간 중재 사례 연결, 한계와 향후 개선 계획 정리

현재 개발 상황:
- Backend: `/analyze_batch`, `/analyze_android`, 모델 응답, normalization, input filter, pipeline regression 보강이 진행됨
- Chrome Extension: DOM 기반 exact span 마스킹, Google 동적 영역 대응, 입력창 처리, sensitivity/cache/stale response 방지 개선이 진행됨
- Android App: 접근성 기반 후보 수집, 분석 API 연동, overlay planner, OCR ROI 후보 분리, visual text mask planner 구조가 개발됨
- Android 남은 제약: 접근성 bounds는 글자별 좌표가 아니므로 browser/generic 화면에서는 정확하지 않은 overlay를 제한해야 함
- Evaluation: `evaluation/api-vs-ml`에서 keyword-only, ML-only, ML+normalization, ML+safe-context, full pipeline 비교 프레임을 관리함
- Docs/협업: GitHub PR, Linear, docs를 통해 문제-제약-해결-검증 흐름을 기록함

모델 성능 평가 스냅샷:
- 생성 시각: 2026-05-06T17:14:04.424942
- 전체 케이스: 1007건
- KOLD 케이스: 760건
- 수제 케이스: 247건
- 정답: 791건
- 정확도: 78.55%
- 실패: 216건
- 실패율: 21.45%
- False Positive: 86건
- False Negative: 130건

모델 실패 분석:
- kold_clean FP 72건: 전체 FP의 대부분이며, 사회/정체성/정책 주제어를 hate 신호로 과하게 보는 topic bias 가능성이 있음
- kold_offensive_other FN 55건: 공격성이 있지만 모델 점수가 낮게 나온 표현 보강 필요
- kold_racial_hate FN 21건: 인종/국적 혐오 표현 미탐
- kold_gender_hate FN 17건: 성별 혐오 표현 미탐
- 기타: 초성, romanized, mixed-language, ultra-short, meme/비꼼 표현에서 긴 꼬리 실패 발생

평가 결과 해석:
- 정확도 하나만으로는 서비스 적용 가능성을 설명하기 어렵다.
- 높은 점수 오탐과 낮은 점수 미탐이 동시에 존재하므로 threshold 조정만으로 해결하기 어렵다.
- 모델 점수 + 정규화 + safe-context + evidence span 검증을 함께 관리해야 한다.
- 화면 마스킹은 backend boolean만으로 적용하지 않고, 실제 DOM range 또는 OCR bbox에 투영 가능한 evidence가 있을 때만 적용하는 방향이 안전하다.

주요 기술적 의사결정:
- 최종 판정은 frontend가 아니라 backend 모델 기준으로 유지한다.
- 전체 요소를 가리지 않고 evidence_spans 기반 exact span만 마스킹한다.
- 입력창은 일반 DOM span 방식이 아니라 fixed-token UX로 분리한다.
- Android에서는 boolean positive와 실제 마스킹 가능한 evidence span을 분리해서 본다.
- Android contentDescription-only 카드나 큰 browser accessibility bounds는 정확한 글자 좌표가 아니므로 overlay 렌더링을 제한한다.
- OCR은 유해성 판정자가 아니라 visual text + bbox 수집기로 제한하고, backend 모델 판정과 연결한다.

발표에서 보여줄 개발 성과:
- 단순 키워드 차단에서 문맥 기반 backend 판정 구조로 전환
- 정상 문맥 오탐과 유해 문맥 미탐을 regression case로 관리
- Chrome DOM exact span 마스킹과 stale response guard 적용
- Android 접근성 후보 수집과 overlay 가능성 진단 구조 구축
- 모델 평가 결과를 정확도 하나가 아니라 FP/FN 유형과 pipeline 개선 방향으로 해석

남은 과제:
- 모델: topic bias 완화, 우회 표현/간접 공격 데이터 보강, KOLD clean FP와 romanized FN regression set 확대
- Chrome: 동적 DOM rescue coverage, first-mask latency, sensitivity 적용 일관성 개선
- Android: ROI OCR 실험, OCR text box와 backend evidence span 투영, 스크롤 중 stale overlay 제거, 실제 기기 검증
- Evaluation: keyword-only, ML-only, ML+normalization, ML+safe-context, full pipeline 비교표 완성
- 보고서: 문제-제약-선택-검증-남은 리스크 흐름으로 정리

권장 슬라이드 구성:
1. 제목: 청마루 / 문맥 기반 실시간 유해 텍스트 중재 시스템
2. 제안 배경: 왜 단순 욕설 필터로 부족한가
3. 기존 접근의 한계: keyword-only와 model-only의 실패
4. 제안 시스템: Chrome Extension + Android App + Backend API 구조
5. 개발 계획: 1차~4차 milestone
6. 현재 개발 상황: Backend / Chrome / Android / Evaluation 상태표
7. 모델 성능 평가 결과: 1007건, 정확도 78.55%, FP 86, FN 130
8. 실패 유형 분석: kold_clean FP, offensive_other FN, hate FN, 우회 표현
9. 평가 결과가 개발 방향에 준 영향: threshold가 아니라 pipeline 개선이 필요
10. 플랫폼별 구현 제약과 대응: Chrome DOM, Android Accessibility/OCR, evidence span
11. 향후 개선 계획: 모델, Chrome, Android, 평가, 보고서 정리
12. 결론: 청마루는 욕설 단어 차단 도구가 아니라 문맥과 플랫폼 제약을 관리하는 중재 시스템

시각화 제안:
- 시스템 구조는 3단 흐름도로 표현: 수집 계층 -> backend 판단 계층 -> 화면 중재 계층
- 개발 현황은 표로 표현: 영역 / 구현된 것 / 검증 또는 남은 과제
- 모델 평가는 큰 숫자 카드로 표현: 1007 cases, 78.55% accuracy, 86 FP, 130 FN
- 실패 유형은 막대그래프 또는 간단한 표로 표현
- 향후 계획은 4개 영역 카드로 표현: Model, Chrome, Android, Evaluation

사용 가능한 이미지 에셋:
- docs/presentation/ppt-assets/01_model_evaluation_summary.png
- docs/presentation/ppt-assets/02_false_positive_false_negative_balance.png
- docs/presentation/ppt-assets/03_failure_type_distribution.png
- docs/presentation/ppt-assets/04_threshold_adjustment_limit.png
- docs/presentation/ppt-assets/05_system_architecture_flow.png
- docs/presentation/ppt-assets/06_development_status_matrix.png
- docs/presentation/ppt-assets/07_pipeline_evaluation_frame.png
- docs/presentation/ppt-assets/08_ppt_storyboard_overview.png
```

## 발표자료에 반드시 남길 균형

- 성과: backend 모델 기반 판단, Chrome exact span, Android 접근성/overlay 진단, 평가 프레임 구축
- 한계: 모델 오탐/미탐, Android OCR/좌표 정확도, 실시간 지연, 동적 DOM 변화
- 방향: 단순 accuracy 경쟁이 아니라 실제 화면 적용 가능한 pipeline 품질 개선

## 최종 제출 전 확인할 것

- 2026-05-06 모델 평가 스냅샷 이후 backend가 바뀌었다면 평가를 다시 실행해 수치를 갱신한다.
- Android OCR/실시간 visual masking은 실기기 검증 전까지 “진행 중” 또는 “개선 과제”로 표현한다.
- PPT에는 raw 로그, 대용량 JSON, 내부 파일 경로를 길게 넣지 말고 핵심 수치와 해석만 넣는다.
