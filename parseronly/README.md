# Parser Only

This folder contains the standalone Android SNS comment parser project from Android Studio.

## Current Status

The YouTube parsing problem is solved.

The TikTok parser was also updated to reduce non-comment captures, recover more author IDs from clickable username nodes, and prevent duplicate saved data.

## Main Changes

- `MainActivity.kt`
  - Added platform-only automation controls for YouTube, Instagram, and TikTok.
- `AutomationSettingsStore.kt`
  - Added platform mode storage so automation can run only the selected platform.
- `ParserModels.kt`
  - Added accessibility click metadata: `isClickable`, `hasClickAction`, and `hasClickableAncestor`.
- `YoutubeAccessibilityService.kt`
  - Uses platform mode when rotating automation.
  - Keeps TikTok clickable username nodes instead of dropping all button nodes.
  - Filters TikTok UI labels such as search labels, effect labels, post labels, profile labels, and restricted-comment notices.
- `TiktokCommentExtractor.kt`
  - Uses clickable/button-like username nodes as stronger `author_id` candidates.
  - Blocks TikTok non-comment UI text including `search`, `first comment`, effect labels, numeric mention-like labels, profile labels, and comment-restricted notices.
- `JsonFileStore.kt`
  - Keeps YouTube duplicate filtering by `author_id + commentText`.
  - Adds TikTok duplicate filtering by `author_id + commentText`.
  - Adds TikTok sentence-like `commentText` duplicate filtering so repeated full-sentence comments are skipped even when `author_id` is missing.
  - Short one-word reactions and emoji-only comments are not removed by the sentence-like duplicate rule.

## Validation Results

### YouTube

Validation was performed after a 10-minute YouTube automation run on May 18, 2026.

| Check | Result |
| --- | ---: |
| Source YouTube JSON files | 151 |
| Merged comments | 457 |
| Missing `author_id` values | 0 |
| Blank `commentText` values | 0 |
| Duplicate `author_id + commentText` pairs | 0 |
| Duplicate `commentText` values | 0 |

Merged output:

```text
results/Youtube_comments_merged.json
```

### TikTok

Validation was performed using TikTok files created after the final filter update on May 18, 2026, from 23:34 onward.

| Check | Result |
| --- | ---: |
| Source TikTok JSON files | 35 |
| Merged comments | 112 |
| Comments with `author_id` | 48 |
| Comments without `author_id` | 64 |
| Blank `commentText` values | 0 |
| Time-like `author_id` values | 0 |
| Count-like `author_id` values | 0 |
| UI-label `author_id` values | 0 |
| Suspected UI text saved as `commentText` | 0 |
| Duplicate `author_id + commentText` groups | 2 |
| Duplicate text groups containing letters or digits | 0 |

The remaining duplicate groups were emoji-only reactions (`🥰🥰🥰`, `😁😁😁`), which are intentionally allowed.

Merged output:

```text
results/Tiktok_comments_merged.json
```

## Summary

- YouTube: solved.
- TikTok: non-comment UI text is filtered from saved comment output.
- TikTok: text-based duplicate comments are removed when the duplicated text is sentence-like.
- TikTok: author parsing is improved through clickable username nodes, though some comments still have empty `author_id` when TikTok does not expose a reliable username node.
