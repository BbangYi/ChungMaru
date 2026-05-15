# Backend 모델 실패 분석 요약 - 2026-05-06

이 문서는 `docs/Backend model/failure_analysis_20260506_171404.json`에서 보고서와 발표에 필요한 핵심만 추린 요약입니다.
대용량 원본 JSON, HWPX, PDF, 차트 이미지는 재검증이나 제출물 생성 중에만 필요한 산출물이므로 Git 추적 대상에서 제외합니다.

## 원본 스냅샷

| 항목 | 값 |
| --- | --- |
| 생성 시각 | 2026-05-06T17:14:04.424942 |
| 실행 시간 | 50.68초 |
| 전체 케이스 | 1007 |
| KOLD 케이스 | 760 |
| 수제 케이스 | 247 |
| 정답 | 791 |
| 정확도 | 78.55% |
| 실패 | 216 |
| 실패율 | 21.45% |
| False Positive | 86 |
| False Negative | 130 |

## 핵심 해석

정확도 하나만으로는 현재 모델의 서비스 적용 가능성을 설명하기 어렵습니다.
실패는 크게 두 축으로 갈립니다.

1. 정상 문맥을 유해로 보는 오탐
2. 우회 표현, 간접 공격, 짧은 표현을 놓치는 미탐

따라서 단순 threshold 조정보다는 `모델 점수 + 정규화 + safe-context + evidence span 검증`을 함께 관리해야 합니다.
화면 마스킹은 backend boolean만으로 적용하지 않고, 실제 DOM range 또는 OCR bbox에 투영 가능한 evidence가 있을 때만 적용하는 방향이 안전합니다.

## 실패 분포

| 유형 | FP | FN | 해석 |
| --- | ---: | ---: | --- |
| kold_clean | 72 | 0 | 전체 FP의 대부분입니다. 사회/정체성/정책 주제어를 hate 신호로 과하게 보는 topic bias 가능성이 큽니다. |
| kold_offensive_other | 0 | 55 | 가장 큰 FN 축입니다. 공격성이 있지만 모델 점수가 낮게 나온 표현을 보강해야 합니다. |
| kold_racial_hate | 0 | 21 | 인종/국적 혐오 표현 미탐이 남아 있습니다. |
| kold_gender_hate | 0 | 17 | 성별 혐오 표현 미탐이 남아 있습니다. |
| kold_religion_hate | 0 | 6 | 종교 혐오 표현 미탐이 남아 있습니다. |
| kold_politics | 0 | 4 | 정치 문맥에서 미탐이 발생했습니다. |
| meme_insult | 0 | 4 | 밈/비꼼 기반 공격은 별도 regression set이 필요합니다. |
| fp_similar_word | 3 | 0 | 유사 단어 오탐입니다. span sanity filter와 safe-context가 필요합니다. |
| chosung_profanity | 0 | 2 | 초성 욕설 정규화가 필요합니다. |
| mild_hate | 0 | 2 | 약한 혐오 표현은 threshold만으로 안정적으로 잡기 어렵습니다. |
| class_discrimination | 0 | 2 | 계층/직업 차별 표현 미탐이 남아 있습니다. |
| 기타 소수 유형 | 11 | 19 | quoted/context/emoji/romanized/mixed-lang/ultra-short 등 긴 꼬리 실패입니다. |

## 발표와 보고서에 남길 결론

- KOLD clean FP 72건은 threshold를 올리는 방식만으로 해결하기 어렵습니다.
- offensive_other FN 55건은 단순 사전 추가로 해결하면 오탐이 다시 늘 수 있습니다.
- 고확신 오탐과 저점수 미탐이 동시에 존재하므로, 민감도 슬라이더는 보조 수단이지 모델 품질 문제의 해결책이 아닙니다.
- Chrome은 DOM range, Android는 Accessibility/OCR bbox처럼 플랫폼별 위치 근거가 다르므로 evidence를 화면 좌표로 안정적으로 투영하는 과정이 별도 품질 축입니다.
- 실패 사례는 원본 전체를 계속 보관하기보다, 재현 가능한 요약과 regression 후보로 관리하는 편이 유지보수에 유리합니다.

## 보존 기준

| 구분 | 보존 방식 | 이유 |
| --- | --- | --- |
| 실패 분석 핵심 수치 | 이 문서에 보존 | 보고서, 발표, Linear 코멘트에 재사용 가능 |
| 발표 원본 문장 | `docs/presentation/청마루_발표자료_v1.source.md` | PDF/PPTX보다 diff와 리뷰가 쉬움 |
| 원본 JSON/HWPX/PDF/차트 | Git 추적 제외 | 재생성 가능한 산출물이고 변경량이 큼 |
| 최종 제출 PDF/PPTX | 필요 시 repo 밖 제출 폴더에 보관 | 코드 리뷰와 작업 추적에는 불필요 |

## 후속 작업 후보

- `kold_clean` 오탐 72건을 safe-context regression set으로 분리합니다.
- `offensive_other`, `racial_hate`, `gender_hate` 미탐을 우선순위 regression set으로 분리합니다.
- 초성, romanized, mixed-language, ultra-short 표현은 정규화 전후 token 위치가 보존되는지 별도 테스트합니다.
- Android 실시간 반영에서는 모델 판정과 실제 overlay 가능 위치를 분리해 기록합니다.
