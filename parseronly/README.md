# Parser Only

이 폴더는 Android Studio에서 작업한 독립 실행형 SNS 댓글 파서 프로젝트입니다.

## 현재 상태

YouTube 파서 문제는 해결되었습니다.

TikTok 파서도 댓글이 아닌 UI 텍스트 저장을 줄이고, 클릭 가능한 사용자명 노드에서 `author_id`를 더 잘 가져오도록 수정했습니다. 또한 중복 저장을 막는 로직을 추가했습니다.

## 주요 변경 사항

- `MainActivity.kt`
  - YouTube, Instagram, TikTok 중 하나만 자동화할 수 있는 플랫폼별 실행 버튼을 추가했습니다.
- `AutomationSettingsStore.kt`
  - 선택한 플랫폼만 자동화할 수 있도록 플랫폼 모드 저장 기능을 추가했습니다.
- `ParserModels.kt`
  - 접근성 노드의 클릭 정보를 저장하기 위해 `isClickable`, `hasClickAction`, `hasClickableAncestor` 필드를 추가했습니다.
- `YoutubeAccessibilityService.kt`
  - 자동화 전환 시 선택된 플랫폼 모드를 반영하도록 수정했습니다.
  - TikTok에서 버튼 노드를 무조건 버리지 않고, 클릭 가능한 사용자명 노드를 작성자 후보로 사용할 수 있게 했습니다.
  - 검색 라벨, 효과 라벨, 게시물 라벨, 프로필 라벨, 댓글 제한 안내 등 TikTok UI 텍스트를 필터링하도록 수정했습니다.
- `TiktokCommentExtractor.kt`
  - 클릭 가능한 버튼형 사용자명 노드를 `author_id` 후보로 더 강하게 사용하도록 수정했습니다.
  - `검색`, `첫 댓글`, 효과 라벨, 숫자형 멘션 라벨, 프로필 라벨, 댓글 제한 안내 등 댓글이 아닌 TikTok UI 텍스트를 차단했습니다.
- `JsonFileStore.kt`
  - YouTube는 기존처럼 `author_id + commentText` 기준으로 중복 저장을 막습니다.
  - TikTok도 `author_id + commentText` 기준 중복 저장 방지를 추가했습니다.
  - TikTok에서 `author_id`가 비어 있어도 문장형 `commentText`가 반복되면 중복으로 보고 저장하지 않도록 추가했습니다.
  - 짧은 단어형 반응과 이모지만 있는 반응은 문장형 중복 제거 대상에서 제외했습니다.

## 검증 결과

### YouTube

2026년 5월 18일, YouTube 자동화를 10분 동안 실행한 뒤 검증했습니다.

| 확인 항목 | 결과 |
| --- | ---: |
| 원본 YouTube JSON 파일 수 | 151 |
| 병합된 댓글 수 | 457 |
| 누락된 `author_id` 값 | 0 |
| 빈 `commentText` 값 | 0 |
| 중복 `author_id + commentText` 쌍 | 0 |
| 중복 `commentText` 값 | 0 |

병합 결과 파일:

```text
results/Youtube_comments_merged.json
```

### TikTok

2026년 5월 18일 23:34 이후, 최종 필터 수정이 적용된 상태에서 생성된 TikTok 파일로 검증했습니다.

| 확인 항목 | 결과 |
| --- | ---: |
| 원본 TikTok JSON 파일 수 | 35 |
| 병합된 댓글 수 | 112 |
| `author_id`가 있는 댓글 수 | 48 |
| `author_id`가 없는 댓글 수 | 64 |
| 빈 `commentText` 값 | 0 |
| 시간처럼 보이는 `author_id` 값 | 0 |
| 숫자/카운트처럼 보이는 `author_id` 값 | 0 |
| UI 라벨이 들어간 `author_id` 값 | 0 |
| `commentText`로 저장된 의심 UI 텍스트 | 0 |
| 중복 `author_id + commentText` 그룹 | 2 |
| 글자 또는 숫자가 포함된 중복 텍스트 그룹 | 0 |

남은 중복 그룹은 이모지만 있는 반응 2종이며, 의도적으로 허용했습니다.

병합 결과 파일:

```text
results/Tiktok_comments_merged.json
```

## 요약

- YouTube: 해결 완료.
- TikTok: 댓글이 아닌 UI 텍스트가 저장되지 않도록 필터링했습니다.
- TikTok: 문장형 텍스트가 반복되는 경우 중복 댓글로 보고 제거합니다.
- TikTok: 클릭 가능한 사용자명 노드를 활용해 작성자 파싱을 개선했습니다. 다만 TikTok이 신뢰할 수 있는 사용자명 노드를 노출하지 않는 경우에는 일부 댓글의 `author_id`가 비어 있을 수 있습니다.
