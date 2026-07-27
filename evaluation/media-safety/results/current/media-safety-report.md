# Chungmaru Media Safety Report

- generated_at: `2026-07-07T12:02:44+09:00`
- results_dir: `evaluation/media-safety/results/current`
- input_files: `3`
- raw_rows: `26`
- summary_groups: `16`

## Report-Ready Summary

이 파일은 smoke raw CSV를 보고서 작성용으로 줄인 산출물이다. 발표에는 screenshot이 있는 `live_harmful_visual_evidence`와 `live_benign_negative_evidence`를 우선 쓰고, controlled fixture는 regression evidence로 분리한다.

- coverage_verdict: `mechanism_proof_only`
- 현재 수치는 기능 메커니즘과 속도 검증용이다. 다양한 실제 유해 사이트를 넓게 커버했다는 주장에는 아직 부족하다.

| status | count |
| --- | --- |
| benign_negative | 9 |
| disabled_control | 1 |
| strong_visual_block | 6 |

## Coverage Audit

seed 목록 대비 실제 live screenshot evidence가 얼마나 있는지 보는 표다. 이 표의 목적은 과장 방지다.

| scope | category | seed block domains | live smoke domains | live harmful evidence | benign negatives | coverage % | status | note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| harmful_visual_total | adult+gambling | 217 | 2 | 2 | 6 | 0.9 | mechanism_proof_only | 현재 유해 visual live evidence 2개, benign negative 6개다. 최소 목표는 유해 25개 이상, benign 30개 이상이다. |
| category | adult | 117 | 0 | 0 |  | 0.0 | no_live_visual_evidence | 해당 카테고리 live screenshot evidence가 아직 없다. 완성도 주장 금지. |
| category | gambling | 100 | 2 | 2 |  | 2.0 | mechanism_proof_only | 동작 예시는 있으나 10개 도메인 미만이라 coverage proof가 아니다. |
| category | malware | 80 | 0 | 0 |  | 0.0 | not_media_safety_scope | 이미지 차단 품질보다 유해사이트 차단/접속 정책 검증에 가까운 카테고리다. |
| category | phishing | 270 | 0 | 0 |  | 0.0 | not_media_safety_scope | 이미지 차단 품질보다 유해사이트 차단/접속 정책 검증에 가까운 카테고리다. |

## Latency Budget Table

현재 v1에서 실제 계측된 stage와 향후 classifier/OCR 계측 예정 stage를 한 표에 둔다. `not_instrumented`는 아직 기능을 붙이지 않았다는 뜻이지 통과가 아니다.

| stage | name | n | p50 ms | p95 ms | max ms | p95 budget | status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 01_collect | DOM 후보 수집 | 26 | 2 | 10 | 10 | 50 | within_budget |
| 02_cheap_filter | cheap filter | 26 | 0 | 0 | 0 | 120 | within_budget |
| 03_apply | hide/remove 적용 | 26 | 0 | 6 | 7 | 80 | within_budget |
| 04_dom_to_action | DOM 추가 후 action | 26 | 0 | 5 | 5 | 120 | within_budget |
| 05_late_load_decision | late-load decision | 26 | 0 | 0 | 41 | 120 | within_budget |
| 06_image_fetch_future | image fetch | 0 |  |  |  | 80 | not_instrumented |
| 07_bitmap_decode_future | bitmap decode | 0 |  |  |  | 80 | not_instrumented |
| 08_classifier_future | NSFW/banner classifier | 0 |  |  |  | 120 | not_instrumented |
| 09_ocr_future | image OCR | 0 |  |  |  | 200 | not_instrumented |

## Live Screenshot Evidence

| tier | status | case/domain | category | risk | runs | action runs | action max | false hidden max | collect p95 | filter p95 | apply p95 | dom->action p95 | screens |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| live_benign_negative_evidence | benign_negative | developer.mozilla.org | benign | allow | 1 | 0 | 0 | 0 | 4 | 0 | 0 | 0 | 1 |
| live_benign_negative_evidence | benign_negative | example.com | benign | allow | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 1 |
| live_benign_negative_evidence | benign_negative | google.com | benign-thumbnail | allow | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 2 |
| live_benign_negative_evidence | benign_negative | python.org | benign | allow | 1 | 0 | 0 | 0 | 3 | 0 | 0 | 0 | 1 |
| live_benign_negative_evidence | benign_negative | wikipedia.org | benign | allow | 1 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 1 |
| live_benign_negative_evidence | benign_negative | youtube.com | benign-thumbnail | allow | 2 | 0 | 0 | 0 | 10 | 0 | 0 | 0 | 2 |
| live_benign_negative_evidence | benign_negative | example.com | benign | allow | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| live_harmful_visual_evidence | strong_visual_block | jusoguide1.com | gambling | block | 3 | 3 | 18 | 0 | 4 | 0 | 3 | 1 | 1 |
| live_harmful_visual_evidence | strong_visual_block | jusowhy1.com | gambling | block | 3 | 3 | 34 | 0 | 3 | 0 | 7 | 5 | 1 |
| live_benign_negative_evidence | benign_negative | wikipedia.org | benign | allow | 3 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 1 |

## Controlled Regression Evidence

controlled fixture는 실제 사이트 screenshot evidence가 아니라 기능 토글, 로그 on/off, late-load, clean negative 회귀 검증으로 해석한다.

| tier | status | case | category | risk | runs | action runs | action max | false hidden max | late max |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| controlled_harmful_regression | strong_visual_block | log_off_harmful | harmful | block | 1 | 1 | 2 | 0 | 0 |
| controlled_harmful_regression | strong_visual_block | log_on_address_guide_video | address-guide-video | block | 1 | 1 | 1 | 0 | 0 |
| controlled_negative_or_control | benign_negative | log_on_clean | clean | allow | 1 | 0 | 0 | 0 | 0 |
| controlled_harmful_regression | strong_visual_block | log_on_harmful | harmful | block | 1 | 1 | 2 | 0 | 0 |
| controlled_harmful_regression | strong_visual_block | log_on_late_load | late-load | block | 1 | 1 | 1 | 0 | 41 |
| control_row | disabled_control | media_off_harmful | harmful | disabled-control | 1 | 0 | 0 | 0 | 0 |

## Coverage Warnings

_없음_

## Rows Needing Review

_없음_

## Evidence Interpretation

- `collect_ms`, `cheap_filter_ms`, `apply_ms`는 한 scan cycle 내부 stage latency다.
- `dom_added_to_action_ms`는 DOM/viewport에 후보가 들어온 뒤 action까지의 지연이다. 사용자가 보기 전에 가리는 목표와 가장 직접적으로 연결된다.
- `late_decision_ms`는 늦게 로드된 이미지 fixture에서 삽입 후 숨김까지 걸린 시간이다.
- `candidate_sized_visible_media_element_count`는 30px 아이콘을 제외한 보고서용 잔여 visual 후보 지표다.
- `false_hidden_count`는 controlled/benign fixture에서만 오탐 지표로 해석한다. live 위험 사이트 row의 truth label로 과해석하지 않는다.

## Known Gaps

- v1은 YOLO/NSFW classifier/OCR을 붙이지 않았다. 따라서 classifier/OCR 속도는 아직 `not_instrumented`로 보고한다.
- live harmful visual evidence는 현재 주소가이드 계열 2개 도메인에 머문다. Chrome 이미지 차단을 성숙하다고 말하기에는 부족하다.
- live URL seed는 reachable 여부와 visual banner 존재 여부가 섞여 있으므로, `live_page_ok`, visible candidate, screenshot을 통과한 row만 evidence로 승격한다.
- Google Images/YouTube harmful query와 화면 녹화 evidence는 다음 반복에서 추가해야 한다.

## Generated Files

- latency_summary_csv: `evaluation/media-safety/results/current/media-safety-latency-summary.csv`
- stage_latency_csv: `evaluation/media-safety/results/current/media-safety-stage-latency.csv`
- coverage_audit_csv: `evaluation/media-safety/results/current/media-safety-coverage-audit.csv`
- report_json: `evaluation/media-safety/results/current/media-safety-report.json`
- report_md: `evaluation/media-safety/results/current/media-safety-report.md`

## Input Files

- `evaluation/media-safety/results/current/benign-thumbnail/media-safety-live-smoke.csv`
- `evaluation/media-safety/results/current/media-safety-live-smoke.csv`
- `evaluation/media-safety/results/current/media-safety-smoke.csv`
