# 청마루 타임라인형 PPT 생성 브리프

> 현재 최종 기준은 `final-ppt-generation-brief.md`입니다.
> 이 파일은 개발 계획/진행상황을 타임라인으로 설명할 때 쓰는 보조 브리프입니다.

이 문서는 현재 NotebookLM 생성 PDF의 아쉬운 점을 보완하기 위한 새 PPT 생성 입력입니다.
핵심은 “무엇을 하기로 했고, 지금 어디까지 왔는지”를 먼저 보여주는 것입니다.

## 현재 PDF에서 보완할 점

- 큰 제목과 카드가 많아 디자인이 과하게 보입니다.
- 모델 평가 수치는 보이지만 최초 계획 대비 현재 위치가 명확하지 않습니다.
- “개발 계획 -> 현재 도달점 -> 남은 검증”의 연결이 약합니다.
- Android OCR/실시간 마스킹이 어느 정도 검증됐는지 구분이 필요합니다.

## 새 PPT 방향

- 톤: 미니멀, 보고서형, 기술 발표용
- 구조: 타임라인 + 계획 대비 현황표 중심
- 디자인: 흰 배경, 얇은 선, 작은 상태 태그, 과한 카드/그라데이션/큰 박스 최소화
- 핵심 질문: “처음 계획은 무엇이었고, 현재는 몇 단계까지 왔으며, 아직 무엇을 검증해야 하는가?”

## AI에게 전달할 최종 프롬프트

아래 내용을 그대로 사용한다.

```md
너는 캡스톤 프로젝트 발표자료를 만드는 기술 문서 편집자다.
아래 자료를 바탕으로 “청마루: 문맥 기반 실시간 유해 텍스트 중재 시스템” 발표용 PPT를 다시 구성해라.

중요한 방향:
- NotebookLM식 큰 제목, 강한 카드, 어두운 배경, 과한 장식은 피한다.
- 발표의 첫 흐름은 “최초 계획 -> 현재 도달점 -> 남은 검증”이어야 한다.
- 모델 성능 수치는 보여주되, 발표의 중심은 모델 자랑이 아니라 개발 계획 대비 진행 상황과 실패 분석이다.
- Android OCR/visual masking은 완성 기능처럼 쓰지 말고 “진행 중인 검증 과제”로 표현한다.
- 상태를 퍼센트로 임의 산정하지 말고 “완료”, “완료+고도화”, “진행 중”, “남은 검증”으로 표시한다.

디자인 요구:
- 16:9 와이드
- 한국어
- 10장 내외
- 흰색/밝은 회색 배경
- 얇은 선, 작은 점, 간결한 표, 타임라인 중심
- 색상은 남색/청록/초록/주황 정도만 제한적으로 사용
- 한 슬라이드에는 핵심 메시지 1개와 짧은 bullet만 넣는다.
- 긴 문단, 복잡한 박스, 큰 그림자, 과한 둥근 카드, 배경 그라데이션은 피한다.

프로젝트 한 줄 정의:
- 청마루는 모바일 앱과 브라우저 익스텐션에서 수집한 온라인 텍스트를 문맥, 신뢰도, 플랫폼 제약까지 고려해 경고, 마스킹, 허용, 보류로 중재하는 문맥형 유해 텍스트 중재 시스템이다.

최초 개발 계획:
1. 1차 기반 정리
   - 저장소 구조 정리
   - Android 수집 파이프라인 동작 확인
   - 공통 계약 및 정책 구조 정리
2. 2차 실사용 연결
   - Chrome Extension 마스킹 UI 연결
   - API 초안 및 판단 응답 형식 정리
   - 플랫폼별 수집 흐름 테스트
3. 3차 평가/발표 정리
   - pipeline 비교 실험 정리
   - Demo 시나리오 확정
   - README 및 발표용 문서 정리
4. 4차 통합 검증
   - 오탐/미탐 사례를 보고서용 평가 표로 정리
   - Android 수집 사례와 Chrome 실시간 중재 사례 연결
   - 한계와 향후 개선 계획 정리

현재 도달점:
- 1차 기반 정리: 완료
  - 저장소 구조, 공통 계약/정책, Android 수집 기반이 정리됨
- 2차 실사용 연결: 완료 + 고도화
  - Backend API, Chrome DOM exact span 마스킹, 입력창 처리, sensitivity/cache/stale response guard가 개선됨
- 3차 평가/발표 정리: 진행 중
  - 1007건 모델 평가 스냅샷, FP/FN 실패 유형 분석, pipeline 비교 프레임, 발표자료 정리가 진행됨
- 4차 통합 검증: 남은 검증
  - Android OCR 실기기 latency, 좌표 정확도, stale overlay, Android+Chrome demo 연결은 아직 최종 검증 필요

현재 영역별 상황:
- Backend: /analyze_batch, /analyze_android, normalization, input filter, safe-context, evidence_spans 중심 계약이 정리됨
- Chrome Extension: DOM exact span, Google 동적 영역 대응, 입력창 UX, sensitivity/cache/stale response guard가 개선됨
- Android App: 접근성 후보 수집, 분석 API 연동, overlay planner, OCR ROI 후보 분리, visual text mask planner 구조가 개발됨
- Evaluation: keyword-only, ML-only, ML+normalization, ML+safe-context, full pipeline 비교 프레임을 관리 중
- Docs/협업: GitHub/Linear/docs로 문제-제약-선택-검증 흐름을 기록 중

모델 평가 스냅샷:
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

실패 유형:
- kold_clean FP 72건: 전체 FP의 핵심이며 topic bias 가능성이 큼
- offensive_other FN 55건: 간접 공격, 우회 표현, 낮은 점수 미탐 보강 필요
- racial_hate FN 21건: 인종/국적 혐오 미탐
- gender_hate FN 17건: 성별 혐오 미탐
- 기타: 초성, romanized, mixed-language, ultra-short, meme/비꼼 표현에서 긴 꼬리 실패 발생

평가 해석:
- 정확도 하나로는 서비스 품질을 설명하기 어렵다.
- 높은 점수 오탐과 낮은 점수 미탐이 동시에 존재하므로 threshold 조정만으로 해결하기 어렵다.
- 모델 점수 + 정규화 + safe-context + evidence span 검증을 함께 관리해야 한다.
- 화면 마스킹은 backend boolean만으로 적용하지 않고, 실제 DOM range 또는 OCR bbox에 투영 가능한 evidence가 있을 때만 적용하는 방향이 안전하다.

권장 슬라이드 구성:
1. 제목: 청마루 / 문맥 기반 실시간 유해 텍스트 중재 시스템
2. 제안 배경: 왜 단어 필터만으로 부족한가
3. 최초 계획: 1차~4차 milestone
4. 현재 위치: 최초 계획 대비 완료/진행/남은 검증
5. 시스템 구조: Chrome + Android + Backend
6. 개발 흐름 타임라인: 문제 재정의 -> Backend 계약 -> Chrome 안정화 -> 모델 평가 -> Android 확장 -> 현재 단계
7. 모델 평가 스냅샷: 1007건, 정확도 78.55%, FP 86, FN 130
8. 실패 유형 우선순위: kold_clean FP, offensive_other FN, hate FN
9. 남은 검증: Android OCR 실기기, pipeline 비교표, 통합 demo, 최종 보고서
10. 결론: 청마루는 욕설 단어 차단 도구가 아니라 문맥과 플랫폼 제약을 관리하는 중재 시스템

우선 사용할 이미지 에셋:
- docs/presentation/ppt-assets/09_minimal_initial_plan_timeline.png
- docs/presentation/ppt-assets/10_minimal_plan_progress_matrix.png
- docs/presentation/ppt-assets/11_minimal_development_timeline.png
- docs/presentation/ppt-assets/12_minimal_timeline_ppt_outline.png
- docs/presentation/ppt-assets/13_minimal_model_evaluation_snapshot.png
- docs/presentation/ppt-assets/14_minimal_failure_priority_bars.png

보조 이미지 에셋:
- docs/presentation/ppt-assets/01_model_evaluation_summary.png
- docs/presentation/ppt-assets/03_failure_type_distribution.png
- docs/presentation/ppt-assets/04_threshold_adjustment_limit.png
- docs/presentation/ppt-assets/05_system_architecture_flow.png
- docs/presentation/ppt-assets/07_pipeline_evaluation_frame.png
```

## 제작 판단 기준

- 첫 3장 안에 최초 계획과 현재 위치가 보여야 합니다.
- 모델 성능 장표는 1~2장만 사용하고, 수치보다 해석과 다음 개발 방향을 강조합니다.
- Android 실시간 OCR은 “완료”가 아니라 “구조 개발 + 실기기 검증 필요”로 표현합니다.
- 색상과 도형은 적게 사용하고, 타임라인과 표의 정보 밀도로 설득합니다.
