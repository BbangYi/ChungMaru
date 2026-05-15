# 청마루 최종 PPT 생성 브리프

이 브리프가 현재 기준 최종본입니다.
발표 흐름은 타임라인 자체가 아니라 아래 순서를 따릅니다.

1. 표지
2. 개발 계획
3. 개발 진행상황
4. 완료된 산출 지점
5. 모델 평가
6. 제약 사항 / 어려운 부분
7. 앞으로의 계획

## 핵심 방향

- 발표는 “모델 평가가 좋다/나쁘다”가 아니라, 계획한 개발이 어디까지 왔고 무엇이 남았는지 보여주는 구조입니다.
- 타임라인 이미지는 개발 계획과 진행상황을 설명하는 보조 자료일 뿐, 발표 전체를 타임라인 위주로 끌고 가지 않습니다.
- 디자인은 NotebookLM식 큰 카드/강한 박스보다 더 단순하게 갑니다.
- Android OCR/visual masking은 완성 기능이 아니라 “구조 개발 + 실기기 검증 필요”로 표현합니다.

## AI에게 전달할 최종 프롬프트

아래 내용을 그대로 전달합니다.

```md
너는 캡스톤 프로젝트 발표자료를 만드는 기술 문서 편집자다.
아래 자료를 바탕으로 “청마루: 문맥 기반 실시간 유해 텍스트 중재 시스템” PPT를 만들어라.

가장 중요한 요구:
- 발표 순서는 반드시 다음 흐름을 따른다.
  1. 표지
  2. 개발 계획
  3. 개발 진행상황
  4. 완료된 산출 지점
  5. 모델 평가
  6. 제약 사항 / 어려운 부분
  7. 앞으로의 계획
- 타임라인은 개발 계획 또는 진행상황을 설명하는 보조 자료로만 사용한다.
- 모델 평가가 발표 전체를 지배하지 않게 한다.
- 최초 계획 대비 현재 어디까지 왔는지가 분명하게 보여야 한다.
- 완료된 산출물과 아직 검증 중인 기능을 같은 “완성”으로 표현하지 않는다.

디자인 요구:
- 16:9 와이드
- 한국어
- 9~11장
- 흰색/밝은 회색 배경
- 얇은 선, 간결한 표, 작은 상태 태그 중심
- 큰 카드, 어두운 배경, 강한 그라데이션, 과한 장식은 피한다.
- 각 장은 핵심 메시지 1개와 짧은 bullet 3~5개로 제한한다.

프로젝트명:
- 청마루 / Chungmaru

한 줄 정의:
- 모바일 앱과 브라우저 익스텐션에서 수집한 온라인 텍스트를 문맥, 신뢰도, 플랫폼 제약까지 고려해 경고, 마스킹, 허용, 보류로 중재하는 문맥형 유해 텍스트 중재 시스템

1. 표지:
- 제목: 청마루 / 문맥 기반 실시간 유해 텍스트 중재 시스템
- 보조 문장: 단어가 아니라 화면과 문맥 기준으로 유해 텍스트를 판단하고 중재한다.

2. 개발 계획:
- 1차 기반 정리
  - 저장소 구조 정리
  - Android 수집 파이프라인 동작 확인
  - 공통 계약 및 정책 구조 정리
- 2차 실사용 연결
  - Chrome Extension 마스킹 UI 연결
  - API 초안 및 판단 응답 형식 정리
  - 플랫폼별 수집 흐름 테스트
- 3차 평가/발표 정리
  - pipeline 비교 실험 정리
  - Demo 시나리오 확정
  - README 및 발표용 문서 정리
- 4차 통합 검증
  - 오탐/미탐 사례를 보고서용 평가 표로 정리
  - Android 수집 사례와 Chrome 실시간 중재 사례 연결
  - 한계와 향후 개선 계획 정리

3. 개발 진행상황:
- 1차 기반 정리: 완료
  - 저장소 구조, 공통 계약/정책, Android 수집 기반 정리
- 2차 실사용 연결: 완료 + 고도화
  - Backend API, Chrome exact span, 입력창 처리, sensitivity/cache/stale response guard 개선
- 3차 평가/발표 정리: 진행 중
  - 1007건 모델 평가 스냅샷, FP/FN 실패 유형 분석, pipeline 비교 프레임, 발표자료 정리
- 4차 통합 검증: 남은 검증
  - Android OCR 실기기 latency, 좌표 정확도, stale overlay, Android+Chrome demo 연결

4. 완료된 산출 지점:
- Backend API
  - `/analyze_batch`, `/analyze_android`, evidence_spans 중심 계약
- Chrome Extension
  - DOM exact span 마스킹, 입력창 UX, stale response guard
- Android App
  - 접근성 후보 수집, 분석 API 연동, overlay planner, OCR ROI 구조
- Evaluation
  - 1007건 평가 스냅샷, FP/FN 실패 유형 분석, pipeline 비교 프레임
- Docs / PPT Assets
  - 개발 이력, 제약 문서, 발표 이미지 에셋 정리

5. 모델 평가:
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

모델 평가 해석:
- 정확도 하나만으로는 서비스 적용 가능성을 설명하기 어렵다.
- 높은 점수 오탐과 낮은 점수 미탐이 동시에 존재한다.
- threshold 조정만으로 해결하기 어렵다.
- 모델 점수 + 정규화 + safe-context + evidence span 검증이 함께 필요하다.

6. 제약 사항 / 어려운 부분:
- 문맥에 따라 같은 단어도 safe/offensive가 달라진다.
- kold_clean FP 72건은 topic bias 가능성을 보여준다.
- offensive_other FN 55건은 우회 표현, 간접 공격, 낮은 점수 미탐 보강이 필요함을 보여준다.
- Chrome DOM과 Android 접근성/OCR은 좌표 체계가 다르다.
- Android OCR/overlay는 실기기 latency와 정확도 검증이 남아 있다.
- backend가 positive를 반환해도 evidence span이나 bbox가 없으면 안전하게 화면에 적용하기 어렵다.

7. 앞으로의 계획:
- KOLD clean FP와 우회 표현 FN 회귀셋 보강
- keyword-only, ML-only, ML+normalization, ML+safe-context, full pipeline 단계별 비교표 완성
- Android OCR bbox와 backend evidence span 투영 검증
- Chrome/Android 통합 demo 정리
- 최종 보고서에 문제-제약-선택-검증-남은 리스크 흐름 반영

권장 슬라이드 구성:
1. 표지
2. 개발 계획: 1차~4차 milestone
3. 개발 진행상황: 완료 / 완료+고도화 / 진행 중 / 남은 검증
4. 완료된 산출 지점: Backend, Chrome, Android, Evaluation, Docs
5. 시스템 구조: Chrome + Android + Backend
6. 모델 평가: 1007건, 정확도 78.55%, FP 86, FN 130
7. 실패 유형: kold_clean FP, offensive_other FN, hate FN
8. 제약 사항 / 어려운 부분
9. 앞으로의 계획
10. 결론: 청마루는 욕설 단어 차단 도구가 아니라 문맥과 플랫폼 제약을 관리하는 중재 시스템

우선 사용할 이미지 에셋:
- docs/presentation/ppt-assets/15_final_ppt_flow_order.png
- docs/presentation/ppt-assets/09_minimal_initial_plan_timeline.png
- docs/presentation/ppt-assets/10_minimal_plan_progress_matrix.png
- docs/presentation/ppt-assets/16_completed_outputs_current_state.png
- docs/presentation/ppt-assets/13_minimal_model_evaluation_snapshot.png
- docs/presentation/ppt-assets/14_minimal_failure_priority_bars.png
- docs/presentation/ppt-assets/17_constraints_and_future_plan.png

보조 이미지 에셋:
- docs/presentation/ppt-assets/05_system_architecture_flow.png
- docs/presentation/ppt-assets/07_pipeline_evaluation_frame.png
- docs/presentation/ppt-assets/11_minimal_development_timeline.png
```

## 사용 시 주의

- `01`~`08` 이미지는 보조로만 사용합니다.
- `09`~`14`는 타임라인 보조 자료입니다.
- 최종 순서 기준은 `15_final_ppt_flow_order.png`와 이 문서입니다.
