#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_DIR="${REPO_ROOT}/android"
ADB="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
MODE="${1:-fast}"
ARTIFACT_DIR="${ARTIFACT_DIR:-/private/tmp/chungmaru-android-mask-check}"
WAIT_SECONDS="${WAIT_SECONDS:-3}"

FOCUSED_TESTS=(
  "com.capstone.design.youtubeparser.ProvisionalAccessibilityMaskBuilderTest"
  "com.capstone.design.youtubeparser.MaskOverlayEventPolicyTest"
  "com.capstone.design.youtubeparser.MaskOverlayPlannerTest"
  "com.capstone.design.youtubeparser.ScreenTextCandidateExtractorTest"
  "com.capstone.design.youtubeparser.VisualTextRoiPlannerTest"
  "com.capstone.design.youtubeparser.VisualTextOcrCandidateFilterTest"
  "com.capstone.design.youtubeparser.YoutubeAnalysisTargetExtractorTest"
)

usage() {
  cat <<'USAGE'
Usage: scripts/android-mask-check.sh [fast|unit|build|device]

Modes:
  fast    Focused Android mask unit tests + android diff whitespace check.
  unit    Full :app:testDebugUnitTest.
  build   Full unit test + :app:assembleDebug.
  device  Build, install debug APK, then capture one screenshot/logcat snapshot.

Environment:
  ADB=/path/to/adb
  JAVA_HOME=/path/to/android-studio-jbr
  ARTIFACT_DIR=/private/tmp/chungmaru-android-mask-check
  WAIT_SECONDS=3
USAGE
}

gradle() {
  (
    cd "${ANDROID_DIR}"
    env JAVA_HOME="${JAVA_HOME}" ./gradlew "$@"
  )
}

run_diff_check() {
  git -C "${REPO_ROOT}" diff --check -- \
    android/app/src/main/java/com/capstone/design/youtubeparser \
    android/app/src/test/java/com/capstone/design/youtubeparser
}

run_focused_unit() {
  local args=(":app:testDebugUnitTest")
  local test_name
  for test_name in "${FOCUSED_TESTS[@]}"; do
    args+=("--tests" "${test_name}")
  done
  gradle "${args[@]}"
}

run_full_unit() {
  gradle :app:testDebugUnitTest
}

run_build() {
  gradle :app:assembleDebug
}

run_device_snapshot() {
  mkdir -p "${ARTIFACT_DIR}"

  if ! "${ADB}" devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'; then
    echo "[ERROR] No adb device is ready."
    exit 2
  fi

  "${ADB}" install -r "${REPO_ROOT}/android/app/build/outputs/apk/debug/app-debug.apk"
  "${ADB}" logcat -c
  sleep "${WAIT_SECONDS}"

  "${ADB}" shell screencap -p /sdcard/chungmaru-mask-check.png
  "${ADB}" pull /sdcard/chungmaru-mask-check.png "${ARTIFACT_DIR}/screen.png" >/dev/null

  "${ADB}" logcat -d -s \
    YTParserService AndroidAnalysisClient VisualTextOcrProcessor MaskOverlayController \
    > "${ARTIFACT_DIR}/mask-logcat.txt"

  "${ADB}" shell '[ -f /storage/emulated/0/Android/data/com.capstone.design/files/upload_cache/youtube_comments_latest.json ]' &&
    "${ADB}" pull \
      /storage/emulated/0/Android/data/com.capstone.design/files/upload_cache/youtube_comments_latest.json \
      "${ARTIFACT_DIR}/youtube_comments_latest.json" >/dev/null || true

  echo "artifact_dir=${ARTIFACT_DIR}"
  echo "screen=${ARTIFACT_DIR}/screen.png"
  echo "logcat=${ARTIFACT_DIR}/mask-logcat.txt"
  if [[ -f "${ARTIFACT_DIR}/youtube_comments_latest.json" ]]; then
    echo "latest_json=${ARTIFACT_DIR}/youtube_comments_latest.json"
  fi
}

case "${MODE}" in
  fast)
    run_diff_check
    run_focused_unit
    ;;
  unit)
    run_diff_check
    run_full_unit
    ;;
  build)
    run_diff_check
    run_full_unit
    run_build
    ;;
  device)
    run_diff_check
    run_full_unit
    run_build
    run_device_snapshot
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "[ERROR] Unknown mode: ${MODE}"
    usage
    exit 2
    ;;
esac
