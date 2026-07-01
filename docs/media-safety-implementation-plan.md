# Media Safety Implementation Plan

## 목표

청마루의 이미지/배너/영상 썸네일 고도화 목표는 특정 모델을 붙이는 것이 아니라, 유해 시각 요소가 보였을 때 매우 빠른 속도로 `삭제`, `숨김`, `마스킹`, `블러`, `placeholder 대체` 중 하나를 적용하는 것이다.

성공 기준은 다음 네 가지다.

- 사용자가 인식하기 전 또는 거의 직후에 유해 시각 요소가 화면에서 사라지거나 가려진다.
- 기능별로 켜고 끌 수 있다. 예: 텍스트 마스킹, 유해 사이트 차단, 유해 이미지 차단.
- 모든 처리 결과는 로그와 evidence로 남는다.
- 무거운 모델보다 빠른 DOM/텍스트/URL 신호를 먼저 쓰고, classifier/OCR은 필요한 후보에만 적용한다.

## 현재 구현 상태 (2026-07-01)

현재 `유해 이미지 차단`은 모델/OCR 이전의 v1 골격이 들어간 상태다. 계획 대비 위치는 Phase 1과 Phase 2의 1차 구현 완료, Phase 3 smoke benchmark의 controlled fixture + live URL 1차 검증 완료 단계로 본다.

완료된 범위:

- Options 개발자 패널에만 `Runtime Log` 토글을 추가했고, 기본값은 off다.
- Service worker의 `recordRuntimeLogEvent` 경로를 중앙 게이트로 정리해 개발자 로그가 꺼져 있으면 `runtimeEventLog`에 기록하지 않는다.
- `mediaSafetyEnabled`가 켜진 경우에만 content script가 visible media 후보를 수집한다.
- 첫 버전은 모델 없이 `alt`, `title`, `aria-label`, 주변 카드 텍스트, link URL/domain, 제한된 페이지 문맥의 cheap signal로 adult/gambling 후보를 판단한다.
- Google 일반 검색 결과는 media safety에서 제외하고 텍스트/사이트 보호만 적용한다. 검색어가 `NSFW`라는 이유만으로 Naver 같은 정상 검색결과 이미지를 가리지 않기 위한 보수화다.
- 검색결과 보호는 수동/curated/exact domain 또는 보안 위협 수준의 site-level 신호가 있을 때만 적용한다. 검색결과 제목/요약의 성인 키워드만으로 정상 도메인을 가리지 않는다.
- 후보 주변 텍스트가 약해도 페이지 전체에 도박/먹튀/주소 링크 신호가 겹치거나 주소가이드형 배너 grid가 확인되면 strict media mode로 visible media를 빠르게 숨긴다.
- 주소가이드 계열 live test 도메인인 `jusoguide1.com`, `jusowhy1.com`은 유해사이트 차단 fallback에서도 `block`으로 판정한다.
- 위험 후보는 `data-chungmaru-media-hidden="true"`와 CSS class를 붙여 reversible 처리한다. 일반 카드형 결과는 placeholder를 유지하고, 팝업/상단 고정 배너처럼 floating overlay로 판단되는 후보는 `display: none` 기반 영역 제거를 우선 적용한다.
- 주소가이드/배너 grid처럼 페이지 자체가 위험하고 유해 이미지가 sibling tile로 쪼개진 경우에는 개별 placeholder를 남기지 않고 compact group으로 병합한다. 자식 tile은 제거하고 부모 영역에는 최소 summary 1개만 남긴다.
- `video > source[src]`만 있는 주소가이드 WebM 배너를 잡기 위해 `video` 후보와 주소가이드 전용 `.jbanner-*` selector를 우선 수집한다.
- scroll/resize/mutation 재스캔에는 최소 간격을 두고, 실제 media 후보가 추가된 mutation에서만 media scan을 예약해 배너 많은 페이지의 렉을 줄인다.
- 개발자 패널에서만 이미지 처리 방식을 `자동`, `가림 유지`, `영역 제거`로 바꿀 수 있다. 일반 사용자 UI에는 노출하지 않는다.
- `media-safety-scan`, `media-safety-action`, `media-safety-error`는 aggregate event만 남기도록 설계했다.
- Runtime log와 smoke CSV에 `removedCount`, `placeholderCount`, `mergedTargetCount`, `collapsedGroupCount`, `hiddenAreaPx`, `viewportCoveragePct`, `remainingVisibleTileCount`, `candidateSizedVisibleMediaElementCount`를 남겨 무엇을 얼마나 가렸고 무엇이 남았는지 설명할 수 있게 했다.
- fixture 기반 Chrome smoke harness를 추가해 `mediaSafetyEnabled`/`developerRuntimeLogEnabled` 조합별 동작과 latency 지표를 CSV/JSONL로 남길 수 있게 했다.

검증된 범위:

- `node --check` 기준 service worker, content script, options, popup 문법 검증 통과
- smoke harness `py_compile` 통과
- media safety 관련 diff whitespace check 통과
- Chrome for Testing 150.0.7871.46 기준 fixture smoke 통과
- `evaluation/media-safety/results/current/media-safety-smoke.csv`와 `.jsonl` 생성
- `evaluation/media-safety/results/current/media-safety-live-smoke.csv`와 `.jsonl` 생성

현재 smoke 결과:

| 케이스 | 후보 수 | 처리 수 | 영역 제거 | compact group | summary | remaining | 후보 크기 visible | 화면 점유율 | clean 오탐 | collectMs | cheapFilterMs | applyMs | domAddedToActionMs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| controlled harmful fixture | 3 | 2 | 0 | 0 | 0 | 0 | 0 | 13.0% | 0 | 1 | 0 | 1 | 2 |
| controlled clean fixture | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0.0% | 0 | 1 | 0 | 0 | 0 |
| address-guide video fixture | 8 | 1 | 1 | 0 | 0 | 0 | 0 | 41.9% | 0 | 1 | 0 | 0 | 1 |
| jusoguide1.com live | 21 | 21 | 21 | 0 | 0 | 0 | 0 | 88.4% | N/A | 3 | 0 | 3 | 268 |
| jusowhy1.com live | 25 | 25 | 25 | 1 | 1 | 0 | 0 | 85.0% | N/A | 2 | 0 | 9 | 123 |

해석 주의:

- `visible_media_element_count`에는 30~32px 아이콘도 포함된다. 실제 차단 후보 크기 기준 잔여는 `candidate_sized_visible_media_element_count`로 확인한다.
- live URL에서 `domAddedToActionMs`는 자동 scan과 수동 smoke scan 사이의 동적 로딩 타이밍을 포함한다. hot path 비용은 `collectMs`, `cheapFilterMs`, `applyMs`를 함께 봐야 한다.

아직 evidence가 부족한 범위:

- Google Chrome stable은 여전히 `--load-extension`을 차단하므로 smoke runner는 Chrome for Testing 또는 Chromium이 필요하다.
- live smoke는 현재 두 URL의 1회 실행 기준이다. p50/p95는 반복 실행으로 보강해야 한다.
- live URL은 truth label이 없으므로 `falseHiddenCount`를 오탐률로 해석하면 안 된다. clean fixture 또는 별도 benign live page로 negative sample을 유지해야 한다.
- 실제 Google Images/도박 배너 페이지의 화면 녹화와 row-level latency evidence는 아직 필요하다.

다음 단계는 classifier/OCR이 아니라, Chrome for Testing 또는 Chromium 기반 runner에서 실제 Google Images/도박 배너 페이지 smoke를 추가하고 현재 v1 cheap filter의 속도와 오탐/미탐 수치를 고정하는 것이다.

## 기능 단위

일반 사용자가 켜고 끄는 기능은 세 가지로 제한한다.

| 사용자 기능 | 기본 역할 | 기존 상태 | 추가 계획 |
| --- | --- | --- | --- |
| 텍스트 마스킹 | 유해 표현 span 마스킹 | 구현됨 | 독립 토글로 분리 |
| 유해 사이트 차단 | 위험 사이트 접속 전 경고/차단 | 구현됨 | 기존 `siteProtectionEnabled` 유지 |
| 유해 이미지 차단 | 이미지/배너/썸네일 hide/mask/blur | 신규 | `mediaSafetyEnabled` 추가 |

검색 결과 보호, 성인 이미지 classifier, 도박 배너 classifier, OCR, visible tile 수, timeout은 일반 사용자에게 별도 토글로 노출하지 않는다. 이들은 `유해 사이트 차단` 또는 `유해 이미지 차단` 내부 동작으로 묶고, 개발 진단/로그에서만 확인한다.

내부 모듈은 다음처럼 둔다.

| 내부 모듈 | 연결되는 사용자 기능 | 역할 |
| --- | --- | --- |
| 검색 결과 보호 | 유해 사이트 차단 | 검색 결과 카드의 위험 도메인/사이트를 숨김 |
| 성인 이미지 감지 | 유해 이미지 차단 | 선정적 이미지/썸네일 분류 |
| 도박 배너 감지 | 유해 이미지 차단 | 카지노/토토/베팅 배너 분류 |
| 이미지 OCR | 유해 이미지 차단 | 이미지 안 유해 문구 추출 |

## 전체 처리 흐름

```text
페이지 변경 감지
-> page profile 판단
-> visible media/card 후보 수집
-> cheap filter 즉시 판단
-> 위험 후보에 처리 적용
-> 남은 후보만 썸네일 classifier
-> 애매한 후보만 OCR
-> 기존 텍스트 파이프라인/evidence 연결
-> runtime log + latency stats 저장
```

## 세부 설계

### 1. Page Profile

페이지 타입을 먼저 판단한다. 같은 이미지라도 Google 이미지, YouTube 썸네일, 광고 배너, 일반 본문 이미지는 처리 우선순위가 다르다.

| Profile | 예시 | 처리 전략 |
| --- | --- | --- |
| `google_images` | Google 이미지 결과 | 검색어/카드 텍스트/이미지 alt를 강하게 사용 |
| `youtube_results` | YouTube 검색/추천 | 썸네일 + 제목 + 채널/설명 텍스트 사용 |
| `ad_grid` | 도박/카지노 배너 목록 | 배너 classifier + OCR/URL 키워드 |
| `generic_page` | 일반 웹페이지 | visible image top-N만 분석 |

### 2. Candidate Collector

DOM에서 이미지 자체만 보지 않고, 이미지가 속한 카드 단위를 후보로 잡는다.

수집 필드:

- `candidateId`
- `profile`
- `elementKind`: `img`, `picture`, `background-image`, `video-poster`, `thumbnail-card`, `banner-card`
- `src`, `href`, `domain`
- `alt`, `ariaLabel`, `titleText`, `nearbyText`
- `bounds`: `x`, `y`, `width`, `height`
- `visibleRatio`
- `area`
- `firstSeenAt`
- `parentSelectorHint`

우선순위:

1. viewport 안에 있는 큰 tile
2. 영상 썸네일/card
3. 광고 배너 크기 비율에 가까운 이미지
4. 위험 검색어 또는 위험 URL 근처의 이미지
5. 아직 처리하지 않은 새 이미지

### 3. Cheap Filter

모델보다 먼저 바로 판단할 수 있는 신호를 쓴다.

예시 rule:

- 검색어에 `19금`, `성인`, `야동`, `섹스`, `카지노`, `토토`, `바카라`, `스포츠카지노`가 포함됨
- 카드 텍스트에 `가입코드`, `첫충`, `페이백`, `콤프`, `베팅`, `카지노`, `토토`, `바카라`, `성인`, `무삭제`, `노출` 등이 포함됨
- URL/domain이 수동 차단 도메인 또는 도박/성인 패턴과 일치함
- Google 이미지 결과 카드의 제목/출처가 위험 키워드와 일치함

Cheap filter는 매우 빠르므로 위험도가 높으면 classifier/OCR 전에 바로 처리한다.

### 4. Thumbnail Classifier

Cheap filter로 잡히지 않는 후보만 classifier에 보낸다.

- 성인 이미지: NSFW/adult image classifier
- 도박 배너: 별도 gambling/banner classifier
- 입력 크기: 160-224px thumbnail
- 대상: visible tile top-N
- 캐시: `src` 또는 content hash 기준
- 실행 위치: content script 직접 처리보다 worker/offscreen document 경유를 우선 검토

NSFW classifier는 도박 배너를 안정적으로 잡지 못하므로 두 classifier를 분리한다.

### 5. OCR

OCR은 느릴 수 있으므로 1차 경로가 아니다.

OCR 적용 대상:

- classifier confidence가 애매한 후보
- 이미지 안 텍스트가 큰 배너
- URL/domain 근거는 약하지만 카드 형태가 도박/성인 광고와 유사한 후보
- evidence span이 필요한 보고서/검증용 후보

OCR 결과는 기존 청마루 텍스트 정규화와 모델 판단 경로에 연결한다.

### 6. Intervention

처리는 기능별/위험도별로 달라야 한다.

| 처리 방식 | 구현 의미 | 사용처 |
| --- | --- | --- |
| `remove` | 실제 제거가 아니라 우선 `display: none` 처리 | 명확한 도박/성인 배너 |
| `hide` | `visibility: hidden` 또는 카드 숨김 | grid 유지가 필요한 경우 |
| `mask` | overlay/placeholder 표시 | 썸네일/이미지 결과 카드 |
| `blur` | 약한 블러와 경고 스타일 | 애매한 후보 |

초기 구현에서 `remove`는 실제 `node.remove()`가 아니라 `data-chungmaru-media-hidden="true"`를 붙이고 `display: none`으로 처리한다. 설정 변경이나 오탐 확인 시 되돌릴 수 있어야 하기 때문이다.

상단 고정 팝업이나 모달형 배너는 이미지 노드만 가리면 닫기 바, 재열람 방지 바, 뒤쪽 배너 조각이 남을 수 있다. v1은 이미지의 가까운 조상 중 `fixed`/`sticky`, 높은 `z-index`, `popup`/`modal`/`banner`류 class/id, 이미지와의 overlap을 함께 보고 floating overlay 컨테이너를 target으로 승격한다. 이렇게 잡힌 target은 자동 모드에서 영역 제거로 처리해 placeholder가 과도하게 커지는 문제를 줄인다.

동일 영역 또는 포함 관계로 겹치는 후보는 한 번만 처리하고 `mergedTargetCount`로 남긴다. 주소가이드/배너 grid처럼 같은 부모 아래에 유해 tile이 여러 개 붙은 경우에는 compact group으로 접어 `collapsedGroupCount`와 `compact_summary_count`를 남긴다. 사용자가 볼 결과와 개발자가 볼 로그를 분리하기 위해, 제품 화면에는 제거/가림 또는 최소 summary만 적용하고 개발자 로그/CSV에서 `removedCount`, `placeholderCount`, `hiddenAreaPx`, `viewportCoveragePct`로 처리 규모를 확인한다.

## 고급 설정 UI

기존 popup/options에는 전체 on/off, 마스킹 방식, 사이트 보호, 검색 결과 보호가 있다. 새 기능은 기존 설정 구조에 붙인다.

### Popup

Popup은 단순해야 한다.

- 상단: 전체 보호 on/off
- 상태 카드: 현재 사이트 보호 상태
- 기능 요약 chip 또는 compact toggle: `텍스트`, `사이트`, `이미지`
- 버튼: `현재 페이지 검사`, `상세 설정`

Popup에서는 세부 classifier/OCR 토글을 모두 노출하지 않는다. 너무 복잡해지면 제품 완성도가 떨어진다.

### Options 고급 설정

`설정 > 보호 기능` 섹션을 분리하되, 일반 사용자에게는 세 토글만 노출한다.

권장 항목:

- `텍스트 마스킹`
  - 기존 텍스트 후보 수집/분석/마스킹
- `유해 사이트 차단`
  - 기존 사이트 정책 확인, 접속 전 경고, 검색 결과 보호를 포함
- `유해 이미지 차단`
  - 이미지/배너/영상 썸네일 처리

아래 항목은 일반 UI에 노출하지 않는다.

- `성인 이미지 감지`
- `도박/카지노 배너 감지`
- `이미지 안 텍스트 OCR`
- `최대 visible tile 수`
- `classifier timeout`
- `OCR timeout`
- classifier/OCR 세부 threshold

이 값들은 내부 기본값과 개발 진단 로그로만 관리한다. 사용자에게 노출하면 제품이 실험 도구처럼 보이고, 잘못된 설정으로 성능이 흔들릴 수 있다.

### 설정 키 제안

```js
{
  textMaskingEnabled: true,
  siteProtectionEnabled: true,
  mediaSafetyEnabled: false
}
```

내부 기본값은 코드 상수로 둔다.

```js
{
  mediaInternalSearchResultProtectionEnabled: true,
  mediaInternalAdultImageClassifierEnabled: true,
  mediaInternalGamblingBannerClassifierEnabled: true,
  mediaInternalImageOcrEnabled: true,
  mediaInternalInterventionMode: "hide",
  mediaInternalMaxVisibleTiles: 5,
  mediaInternalClassifierTimeoutMs: 250,
  mediaInternalOcrTimeoutMs: 900,
  mediaInternalLogEnabled: true
}
```

초기 개발 단계에서는 `mediaSafetyEnabled` 기본값을 `false`로 둔다. 성능/오탐 evidence가 확보되면 발표용 build 또는 release 후보에서 기본 on을 검토한다.

## 로그 수집 설계

기존 확장에는 `runtimeEventLog`와 `recordRuntimeLogEvent`가 있으므로, 새 저장소를 만들기보다 이벤트 타입을 확장한다.

### Runtime Event

새 이벤트 타입:

- `media-safety-scan`
- `media-safety-action`
- `media-safety-classifier`
- `media-safety-ocr`
- `media-safety-error`

공통 필드:

```json
{
  "type": "media-safety-action",
  "ts": 0,
  "source": "content-script",
  "domain": "www.google.com",
  "url": "https://www.google.com/search?...",
  "profile": "google_images",
  "candidateCount": 0,
  "visibleTileCount": 0,
  "cheapFilterHitCount": 0,
  "classifierAttemptCount": 0,
  "classifierPositiveCount": 0,
  "ocrAttemptCount": 0,
  "ocrPositiveCount": 0,
  "actionCount": 0,
  "removedCount": 0,
  "placeholderCount": 0,
  "mergedTargetCount": 0,
  "hiddenAreaPx": 0,
  "viewportCoveragePct": 0,
  "action": "hide",
  "verdict": "block",
  "reason": "query_keyword,card_text",
  "domAddedToActionMs": 0,
  "collectMs": 0,
  "cheapFilterMs": 0,
  "classifierMs": 0,
  "ocrMs": 0,
  "applyMs": 0,
  "cacheHitCount": 0,
  "fetchBlockedCount": 0,
  "missedVisibleTileCount": 0
}
```

기존 `normalizeRuntimeLogEvent`가 필드를 일부만 보존하므로 구현 때 media 전용 필드를 추가해야 한다.

### Evidence CSV

발표/보고서용 smoke script는 CSV/JSONL을 별도로 남긴다.

권장 파일:

- `evaluation/media-safety/results/current/media-safety-smoke.csv`
- `evaluation/media-safety/results/current/media-safety-events.jsonl`

CSV column:

```text
run_id,page_profile,url,query,candidate_count,visible_tile_count,
cheap_filter_hit_count,classifier_attempt_count,classifier_positive_count,
ocr_attempt_count,ocr_positive_count,action_count,action_mode,
removed_count,placeholder_count,merged_target_count,hidden_area_px,viewport_coverage_pct,
collect_ms,cheap_filter_ms,classifier_ms,ocr_ms,apply_ms,
dom_added_to_first_action_ms,total_scan_ms,cache_hit_count,
fetch_blocked_count,missed_visible_tile_count,false_hidden_count
```

## 구현 단계

### Phase 1. 설정과 로그 뼈대

목표:

- Options 고급 설정에 `텍스트 마스킹`, `유해 사이트 차단`, `유해 이미지 차단` 세 토글 추가
- Popup에는 세 기능의 현재 상태를 간단한 chip 또는 compact toggle로 표시
- 설정 저장/로드/변경 감지 연결
- `media-safety-*` runtime log 이벤트 추가
- 실제 이미지 처리는 아직 하지 않고 no-op scan 로그만 남김

검증:

- 설정 저장 후 popup/options 재열기
- `settings-changed` 로그에 `textMaskingEnabled`, `siteProtectionEnabled`, `mediaSafetyEnabled` 변경이 기록되는지 확인
- global off와 media off가 처리 경로를 막는지 확인
- 일반 UI에 tile 수, timeout, classifier threshold가 노출되지 않는지 확인

### Phase 2. DOM 카드 수집과 cheap hide/remove

목표:

- Google 이미지/YouTube/광고 grid에서 visible media candidate 수집
- 검색어/카드 텍스트/URL 기반 cheap filter 적용
- 위험 후보 card에 `display: none` 또는 placeholder 적용
- `dom_added_to_action_ms` 측정

검증:

- `19금` Google 이미지 결과에서 카드 단위 숨김 확인
- 도박 배너 fixture에서 banner wrapper 숨김 확인
- 정상 이미지 검색어에서 false hidden count 확인

### Phase 3. Smoke benchmark

목표:

- controlled fixture와 실제 Google 이미지 화면에서 latency CSV 수집
- top-N 후보 수와 처리 지연 비교
- remove/hide/mask/blur 방식별 체감 차이 확인

검증:

- median/p95 `dom_added_to_first_action_ms`
- `missed_visible_tile_count`
- `false_hidden_count`
- 처리 방식별 layout 안정성 screenshot

### Phase 4. Thumbnail classifier

목표:

- adult image classifier 연결
- gambling/banner classifier 연결 또는 최소 rule/classifier 후보 비교
- thumbnail resize/cache 적용
- classifier timeout과 fallback 처리

검증:

- warm model 기준 top 1/3/5 classifier latency
- cache hit 전후 latency
- classifier failure가 페이지 성능을 막지 않는지 확인

### Phase 5. OCR 보강

목표:

- 애매한 후보에만 OCR 적용
- OCR 텍스트를 기존 청마루 텍스트 파이프라인에 연결
- image evidence에 OCR reason 저장

검증:

- 도박 배너 문구 OCR evidence
- OCR timeout/fetch blocked 로그
- OCR이 전체 페이지 지연을 만들지 않는지 확인

### Phase 6. UX 정리

목표:

- popup에 기능별 상태 chip 추가
- options 고급 설정 정리
- 오탐 시 되돌리기 또는 현재 페이지 재검사 흐름 정리
- 로그 복사/Notion용 리포트에 media safety 요약 추가

검증:

- 기능별 toggle on/off
- 설정 변경 후 기존 hidden card 처리 정책
- runtime log Notion copy에 media summary 포함

## Acceptance Criteria

초기 제품 후보 기준:

- 텍스트 마스킹, 유해 사이트 차단, 유해 이미지 차단을 각각 켜고 끌 수 있다.
- 유해 이미지 차단 off 상태에서는 이미지/card DOM을 건드리지 않는다.
- Google 이미지/도박 배너 fixture에서 cheap filter만으로 일부 유해 tile을 즉시 숨긴다.
- 모든 media action은 runtime log에 남는다.
- smoke CSV에 latency와 missed/false hidden 지표가 남는다.
- classifier/OCR 실패가 기존 텍스트 마스킹과 사이트 보호 기능을 방해하지 않는다.

발표 가능 기준:

- controlled fixture에서 유해 media action latency median/p95를 제시할 수 있다.
- Google 이미지 또는 도박 배너 실제 화면에서 card 단위 hide/remove가 영상 또는 screenshot으로 확인된다.
- 오탐/미탐 사례를 숨기지 않고 `false_hidden_count`, `missed_visible_tile_count`로 설명한다.

## 구현 우선순위

1. 세 기능 토글과 로그 이벤트
2. Google 이미지/card cheap filter hide/remove
3. 도박 배너 fixture cheap filter
4. latency CSV smoke script
5. adult/gambling thumbnail classifier
6. OCR 보강
7. popup/options UX polish

이 순서가 가장 안전하다. 모델을 먼저 붙이면 성능과 브라우저 제약에 막힐 가능성이 높고, 사용자가 원하는 "굉장히 빠른 처리"를 증명하기 어렵다.

## 예상 소요와 마스킹 성능 판단

아래는 구현 전 계획 기준의 예상이다. 최종 판단은 반드시 Chrome smoke CSV와 화면 녹화로 확인한다.

| 단계 | 예상 소요 | 확인할 성능 |
| --- | --- | --- |
| 설정 UI + 로그 뼈대 | 0.5-1일 | 세 토글 저장/복원, 설정 변경 로그 |
| Google 이미지/card cheap filter | 2-4일 | `dom_added_to_first_action_ms`, 숨긴 카드 수, 오탐 수 |
| 도박 배너 cheap filter | 1-2일 | 키워드/URL/카드 텍스트 기반 차단율 |
| latency CSV smoke script | 1-2일 | median/p95, missed/false hidden |
| adult/gambling thumbnail classifier | 1-2주 | classifier latency, clear harmful tile recall |
| OCR 보강 | 3-5일 | OCR evidence 생성률, OCR timeout 영향 |

초기 성능 목표:

- Cheap filter로 잡히는 카드: median 50ms 이하, p95 120ms 이하를 목표로 한다.
- Classifier 대상 tile: warm 상태 top 1/3/5 기준으로 별도 측정한다. 목표는 사용자가 스크롤 중 체감하기 전에 hide/remove가 적용되는 것이다.
- OCR: critical path가 아니라 evidence 보강 경로로 둔다. OCR 때문에 첫 hide/remove가 늦어지면 실패로 본다.

마스킹 품질은 다음 지표로 판단한다.

- `harmful_tile_recall`: fixture의 유해 tile 중 숨긴 비율
- `false_hidden_rate`: 정상 tile 중 잘못 숨긴 비율
- `missed_visible_tile_count`: 첫 pass에서 놓친 visible tile 수
- `action_latency_p50/p95`: 화면에 붙은 뒤 처리까지 걸린 시간
- `evidence_coverage`: 숨김 처리된 항목 중 reason/log가 남은 비율

사용자에게 보여 줄 결과는 "모델 정확도"가 아니라 "유해 요소가 얼마나 빠르게 사라졌고, 몇 개를 놓쳤으며, 오탐이 얼마나 있었는가"여야 한다.
