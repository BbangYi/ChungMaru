# Parser / Extractor Evaluation Fixtures

This folder keeps small, report-ready fixtures for explaining the Android parser and extractor pipeline.
Large runtime JSONL files should stay out of Git; keep only compact examples here.

## Goal

Show the flow:

1. raw visible UI nodes
2. cleaned text candidates
3. backend model input
4. evidence span
5. final mask bounds

## Files

| Path | Purpose |
| --- | --- |
| `fixtures/*_raw.jsonl` | One raw UI snapshot per platform/surface |
| `fixtures/*_cleaned.jsonl` | Cleaned candidates that would be uploaded to the model |
| `scripts/compare_snapshots.py` | Prints Markdown before/after tables |

## Run

```bash
python3 evaluation/parser-extractor/scripts/compare_snapshots.py \
  evaluation/parser-extractor/fixtures/youtube_search_raw.jsonl \
  evaluation/parser-extractor/fixtures/youtube_search_cleaned.jsonl
```

## Cleaning Rules To Explain In Reports

| Rule | Examples Removed | Reason |
| --- | --- | --- |
| Top controls | `All`, `Shorts`, `Unwatched`, `Videos` | Navigation/filter UI, not user content |
| Metadata | `917K views`, `7 months ago`, `25 chapters` | Context metadata, not target body |
| Actions | `Reply`, `Like`, `Share`, action menu labels | Interaction controls |
| Browser chrome | address/search bar decorations, tab/tool buttons | App controls |
| Duplicate event rows | same normalized text + nearby bounds + same source | Scroll/accessibility events repeat nodes |

## Bounds Usage

`boundsInScreen` is preserved because Android cannot insert DOM spans inside another app.
The backend decides whether a text is harmful, but the app can only mask a region if the candidate keeps a credible screen rectangle.

For exact visual text in thumbnails or rendered images, OCR should produce a tighter box.
For accessibility comments and titles, the overlay planner estimates the evidence span inside the candidate bounds and rejects large/unstable containers.
