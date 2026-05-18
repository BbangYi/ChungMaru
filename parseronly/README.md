# Parser Only

This folder contains the standalone Android comment parser project from Android Studio.

## YouTube Parser Status

The YouTube parsing problem is solved.

The parser now records `author_id` for YouTube comments and prevents duplicate saved data by checking the `author_id + commentText` pair before writing a new JSON file. If a snapshot contains no new YouTube comments after duplicate filtering, no JSON file is created.

## Validation Result

Validation was performed after a 10-minute YouTube automation run on May 18, 2026.

| Check | Result |
| --- | ---: |
| Source YouTube JSON files | 151 |
| Merged comments | 457 |
| Missing `author_id` values | 0 |
| Blank `commentText` values | 0 |
| Duplicate `author_id + commentText` pairs | 0 |
| Duplicate `commentText` values | 0 |

The merged validation file is included at:

```text
results/Youtube_comments_merged.json
```

## Notes

- YouTube empty-comment snapshots are not saved.
- YouTube duplicate comments are filtered using `author_id + commentText`.
- The current validation output contains no blank comments and no duplicate comments.
