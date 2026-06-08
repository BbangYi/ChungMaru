# BbangYi Presentation Hub

This directory is the canonical source for `http://bbangyippt.kro.kr`.

## Files

| Path | Purpose |
| --- | --- |
| `index.html` | Root deck selection hub. |
| `registry.json` | Deck metadata: id, title, status, theme, source path, public path, updatedAt. |
| `deck.html` | Compatibility redirect to the Chungmaru deck. |
| `decks/<deck-id>/current/index.html` | Current public HTML deck for each presentation. |
| `health.txt` | Generated publish metadata in the build output. |
| `qa/<deck-id>/contact-sheet.html` | Generated QA index, created by the snapshot command. |

## Current Decks

- `chungmaru-final` - Technical Editorial deck for the Chungmaru capstone presentation.
- `gofseol-geopolitical` - Market Intelligence Brief deck for the geopolitical risk sector dashboard.

`gofseol-course-guide` is intentionally not registered or published. The public
hub should only expose presentation decks the user asked to share.

## Workflow

Use the publisher script instead of editing `build/presentation/bbangyippt` directly.

```bash
python3 scripts/publish_chungmaru_deck.py validate
python3 scripts/publish_chungmaru_deck.py build
python3 scripts/publish_chungmaru_deck.py check-links
python3 scripts/publish_chungmaru_deck.py audit-layout --viewports 1280x720,1440x900,1920x1080
python3 scripts/publish_chungmaru_deck.py snapshot --viewports 1280x720,1440x900,1920x1080
python3 scripts/publish_chungmaru_deck.py publish --host root@bbangi --with-snapshots --viewports 1280x720,1440x900,1920x1080
```

`build/presentation/bbangyippt` is only a generated staging directory. If a deck
changes, update the source under this directory first, then rebuild and publish.

Deck shortcuts:

- `←` / `→`: previous or next slide
- `H`: return to the hub
- `G`: open or close slide overview
- `F`: fullscreen

The public URL standard is plain HTTP for now:

```text
http://bbangyippt.kro.kr
```
