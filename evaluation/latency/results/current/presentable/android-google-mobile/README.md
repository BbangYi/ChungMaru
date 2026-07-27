# Android Google Mobile Evidence

Status: partial, needs recapture

## What Is Confirmed

`manual-google-search-screen.png` confirms that the emulator can reach a real Google mobile search result page in Chrome.

The screenshot also shows the Android overlay masking the harmful query text in the Google search box. This is useful as current-state visual evidence, but it is not enough for a polished E2E demo video.

## What Failed

The first automated capture ended successfully at the script level, but the screen stayed on the old local fixture tab. That output is not valid Google evidence and was moved to:

`archive/invalid-fixture-capture-20260609T2012/`

A second automated run started after fixing the navigation path, but ADB lost the emulator device during recovery. No valid Google video was produced in that run.

## Script Fix Applied

`scripts/android-google-mobile-evidence.sh` now:

- Uses a shorter demo default: `RECORD_SECONDS=12`, `WAIT_AFTER_LOAD_SECONDS=3`.
- Opens Chrome through the explicit `com.android.chrome/com.google.android.apps.chrome.Main` component.
- Keeps the URL quoted inside the remote shell command so Google query parameters are not split at `&`.
- Checks the UI tree for the query or `google.com/search` and retries once if the Google page is not confirmed.
- Handles the extra Chrome first-run `Continue` button.

## Next Valid Capture Command

Run this only when `adb devices` shows a ready emulator and the backend is available at `127.0.0.1:8000`:

```bash
SKIP_BUILD=1 \
RUNS_PER_SCENARIO=1 \
GOOGLE_SCENARIOS_TSV=$'android-google-profanity\t병신아 꺼져\tprofanity\tmask-spans' \
BATCH_ID=android-google-quick-$(date +%Y%m%dT%H%M) \
BATCH_ROOT=/private/tmp/chungmaru-android-google-quick \
REPORT_DIR=evaluation/latency/results/current/presentable/android-google-mobile \
RECORD_SECONDS=12 \
WAIT_AFTER_LOAD_SECONDS=3 \
SWIPE_SETTLE_SECONDS=0.7 \
FINAL_SETTLE_SECONDS=0.5 \
ANDROID_GOOGLE_VIDEO_POLICY=first \
RESET_CHROME_BEFORE_SCENE=1 \
scripts/android-google-mobile-evidence.sh run
```

## Presentation Use

Use this folder only to explain the current gap:

- Real Google mobile page access is confirmed.
- Search-box masking is visible in a still frame.
- A clean Google-search E2E video still needs a stable ADB/emulator run.
