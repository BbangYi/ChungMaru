# Parser and Extractor Evidence Plan

Date: 2026-05-14
Lane: docs-evaluation

## Purpose

This note turns the parser/extractor work into final-report and presentation evidence.
The goal is not only to say that Chungmaru can collect comments, but to show how text is collected, cleaned, mapped to screen coordinates, analyzed by the backend, and converted into a mask.

## Can We Read App Sections Or Search Result Areas Directly?

Short answer: partially, but not through a stable public API that returns "the current YouTube app section" or "the current Chrome/Google app search result card with exact coordinates."

| Source | What We Can Get | What We Cannot Reliably Get | Practical Use |
| --- | --- | --- | --- |
| Android Accessibility tree | Visible nodes, text/contentDescription, class name, view id if exposed, visibility, bounds on screen/window | Private app data, unexposed text, offscreen items, exact semantic card type from every app | Primary low-latency source for visible UI text and approximate regions |
| YouTube native app accessibility | Some title rows, chips, search input, comments, composite card contentDescription | Thumbnail-image text, stable per-word coordinates for every card, official section identifiers | Platform adapter + OCR ROI fallback |
| Google app / Chrome app accessibility | Visible search result titles/snippets/buttons when exposed | Full DOM semantics from another native app, offscreen results, exact search result ranking contract | Search-result region clustering from visible nodes |
| Chrome extension DOM path | DOM text nodes, element rectangles, exact span insertion on web pages where extension has permission | Native Android Chrome app internals, other apps' private views | Best path for desktop/browser demo, not Android native app masking |
| YouTube Data API / external search APIs | Metadata such as videos, channels, playlists, titles, ids, snippets | Current app viewport, user-specific ranking parity, screen coordinates, thumbnail text rendered in app | Evaluation/enrichment only, not primary masking geometry |
| Accessibility screenshot / MediaProjection + OCR | Pixels for visible screen or window, image text after OCR, true blur input if capture is available | Semantic author/card identity unless combined with UI tree, zero-consent background capture | Fallback for thumbnail/frame/search-result visual text |
| UIAutomator | Test-time visible hierarchy snapshots | Production-safe always-on collection | Android Studio verification and parser regression tests |

## Engineering Conclusion

The strongest production path is hybrid:

1. Use AccessibilityService as the real-time visible UI source.
2. Build platform-specific surface adapters that infer regions from visible nodes and bounds:
   - YouTube: search input, filter chips, result card/title, Shorts grid card, comments panel.
   - Google app: search box, result title, snippet, AI overview-like block, related chips.
   - Chrome app: browser-like title/snippet/result rows from accessibility nodes.
3. Use OCR only where the UI tree has no exact text, especially thumbnail text and image/video-frame captions.
4. Keep external APIs as optional metadata or evaluation references, not as the source of truth for screen masking.
5. Store raw and cleaned JSONL snapshots so every masking example can be replayed and explained.

This is also the best story for technical evaluation: the difficult part is not calling a model, but reconciling three imperfect signals: UI tree, OCR pixels, and backend evidence spans.

## Parser / Extractor Deliverables

| Deliverable | What To Include | Why It Matters For Review |
| --- | --- | --- |
| Raw JSONL vs cleaned JSONL comparison | One snapshot before filtering and one after filtering for YouTube, Instagram, TikTok, Google/Chrome search | Shows which platform noise is removed |
| UI text removal rules | Time text, reply/like/share buttons, filter chips, nav labels, view counts, action menus | Proves the parser is not blindly sending UI chrome to the model |
| No-comment / non-target screen detection | Examples of home/search/video/comment screens and why each is or is not parsed as comments | Prevents false collection outside the intended surface |
| Duplicate removal rule | Key fields: normalized text, top/left bounds bucket, author/source id, timestamp window | Explains scroll/event duplicate control |
| Platform issue matrix | YouTube, Instagram, TikTok, Google app, Chrome app split by accessibility/OCR/overlay failure | Shows engineering depth and platform-specific handling |
| Cleaned JSONL as model input | `commentText`, `author_id`, `boundsInScreen`, `timestamp`, `source` | Connects collection quality to backend quality |
| boundsInScreen usage example | Original candidate bounds, evidence span, final mask bounds | Shows why screen coordinates are preserved |
| Removed vs kept examples | Before/after table with UI text removed and user text kept | Makes cleaning rules auditable |

## Proposed JSONL Shape

Keep raw and cleaned records separate.

```json
{
  "timestamp": 1778339680523,
  "platform": "youtube",
  "screen": "search_results",
  "snapshot_id": "youtube-search-1778339680523",
  "raw_nodes": [
    {
      "text": "All",
      "className": "android.widget.TextView",
      "boundsInScreen": { "left": 18, "top": 118, "right": 92, "bottom": 176 },
      "reason": "filter_chip"
    }
  ],
  "comments": [
    {
      "author_id": "android-accessibility-comment:youtube",
      "commentText": "tlqkf 뭐냐 진짜",
      "boundsInScreen": { "left": 120, "top": 580, "right": 720, "bottom": 640 },
      "source": "accessibility_comment",
      "cleaning": {
        "kept": true,
        "reason": "comment_body"
      }
    }
  ]
}
```

For model upload, keep the payload smaller:

```json
{
  "timestamp": 1778339680523,
  "comments": [
    {
      "author_id": "android-accessibility-comment:youtube",
      "commentText": "tlqkf 뭐냐 진짜",
      "boundsInScreen": { "left": 120, "top": 580, "right": 720, "bottom": 640 }
    }
  ]
}
```

## End-To-End Evidence Example Format

| Case | Original Text | Normalized Text | Classifier Scores | Evidence Span | Source Bounds | Final Mask |
| --- | --- | --- | --- | --- | --- | --- |
| Clean topical sentence | "차별금지법 관련 기사입니다" | same | profanity 0.01 / toxicity 0.04 / hate 0.03 | none | title row bounds | no mask |
| Profanity | "tlqkf 뭐냐 진짜" | "시발 뭐냐 진짜" | profanity high | `tlqkf` | comment body bounds | span mask translated on scroll |
| Hate | "성소수자는 ..." | same | hate high if abusive context | abusive phrase | comment/body bounds | span mask |
| Bypass spelling | "qudtls..." | normalized variant | profanity high | `qudtls` | OCR/accessibility bounds | span mask |

## Surface Region Extraction Plan

Add a region layer between raw nodes and candidates.

| Region Type | Detection Signal | Candidate Output |
| --- | --- | --- |
| Top app controls | top band, buttons/chips/search field labels | analysis skip unless user input |
| YouTube result card | composite contentDescription or title/channel/view-count cluster | title candidate + OCR ROI for thumbnail |
| YouTube comments | author/time/body vertical cluster | `android-accessibility-comment:youtube` |
| Shorts grid card | two-column card bounds/title cluster | title candidate + semantic OCR ROI |
| Google/Chrome result | title/snippet/url cluster below toolbar | `android-accessibility-browser:title/snippet` for analysis, OCR/range for mask |
| AI overview-like block | wide multiline paragraph near search results | analysis-only unless exact OCR/range exists |

This layer should make screenshots easier to explain: raw nodes become regions, regions become cleaned candidates, candidates become model inputs, and model spans become masks.

## Immediate Follow-Up Tasks

1. Add a small parser-evaluation fixture set under `evaluation/parser-extractor/`.
2. Export one raw and one cleaned JSONL snapshot per platform.
3. Add a script that prints before/after tables from the JSONL fixtures.
4. Add platform issue matrix rows for YouTube, Instagram, TikTok, Google app, and Chrome app.
5. Link four end-to-end examples to screenshots and final mask bounds.
6. Use this material in the final report's technology and completion sections.

## References

- Android AccessibilityService can use AccessibilityNodeInfo bounds for overlay positioning and has screenshot APIs for supported services: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.html
- YouTube Data API `search.list` returns video/channel/playlist metadata search results, not the current native app viewport or screen coordinates: https://developers.google.com/youtube/v3/docs/search/list
- Android MediaProjection can capture screen/app-window pixels with foreground-service requirements and user consent constraints: https://developer.android.com/media/grow/media-projection
