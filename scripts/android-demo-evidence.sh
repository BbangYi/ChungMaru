#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ADB="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
PACKAGE_NAME="${PACKAGE_NAME:-com.capstone.design}"
RUN_ID="${RUN_ID:-$(date +%Y%m%dT%H%M%S)}"
SCENARIO="${SCENARIO:-youtube-scroll-mask}"
FEATURE_SET="${FEATURE_SET:-full-pipeline}"
RUN_NOTES="${RUN_NOTES:-}"
MASK_ALIGN="${MASK_ALIGN:-}"
EXPECTED_HARMFUL="${EXPECTED_HARMFUL:-}"
MISSED_HARMFUL="${MISSED_HARMFUL:-}"
FALSE_MASK="${FALSE_MASK:-}"
STALE_MASK="${STALE_MASK:-}"
PPT_READY="${PPT_READY:-}"
QUALITY_NOTES="${QUALITY_NOTES:-}"
RECORD_SECONDS="${RECORD_SECONDS:-45}"
WAIT_SECONDS="${WAIT_SECONDS:-3}"
ARTIFACT_DIR="${ARTIFACT_DIR:-/private/tmp/chungmaru-android-demo-${RUN_ID}}"
DEVICE_VIDEO="/sdcard/chungmaru-demo-${RUN_ID}.mp4"
DEVICE_SCREEN="/sdcard/chungmaru-demo-screen-${RUN_ID}.png"
JSON_PREFIXES="${JSON_PREFIXES:-youtube instagram tiktok generic}"
COLLECT_DEVICE_DUMPS="${COLLECT_DEVICE_DUMPS:-1}"
COLLECT_VIDEO="${COLLECT_VIDEO:-1}"

LOG_TAGS=(
  YTParserService
  AndroidAnalysisClient
  VisualTextOcrProcessor
  MaskOverlayController
  JsonFileStore
)

adb_cmd() {
  "${ADB}" "$@"
}

adb_device_cmd() {
  MSYS2_ARG_CONV_EXCL="*" MSYS_NO_PATHCONV=1 "${ADB}" "$@"
}

cleanup_host_logcat() {
  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -EncodedCommand \
      "JAB0AGEAcgBnAGUAdABzACAAPQAgAEcAZQB0AC0AQwBpAG0ASQBuAHMAdABhAG4AYwBlACAAVwBpAG4AMwAyAF8AUAByAG8AYwBlAHMAcwAgAC0ARgBpAGwAdABlAHIAIAAiAE4AYQBtAGUAIAA9ACAAJwBhAGQAYgAuAGUAeABlACcAIgAgAHwAIABXAGgAZQByAGUALQBPAGIAagBlAGMAdAAgAHsAIAAkAF8ALgBDAG8AbQBtAGEAbgBkAEwAaQBuAGUAIAAtAGwAaQBrAGUAIAAiACoAbABvAGcAYwBhAHQAIAAtAHYAIAB0AGkAbQBlACAALQBzACAAWQBUAFAAYQByAHMAZQByAFMAZQByAHYAaQBjAGUAKgAiACAAfQAKACQAdABhAHIAZwBlAHQAcwAgAHwAIABGAG8AcgBFAGEAYwBoAC0ATwBiAGoAZQBjAHQAIAB7ACAAJgAgAHQAYQBzAGsAawBpAGwAbAAuAGUAeABlACAALwBGACAALwBQAEkARAAgACQAXwAuAFAAcgBvAGMAZQBzAHMASQBkACAAfAAgAE8AdQB0AC0ATgB1AGwAbAAgAH0ACgA=" \
      >/dev/null 2>&1 || true
    return
  fi

  if command -v pkill >/dev/null 2>&1; then
    pkill -f "adb.*logcat -v time -s YTParserService" >/dev/null 2>&1 || true
  fi
}

usage() {
  cat <<'USAGE'
Usage: scripts/android-demo-evidence.sh [prepare|capture|collect|review|full|aggregate|help]

Modes:
  prepare  Build, install debug APK, clear logcat, and optionally adb reverse.
  capture  Record screen + live logcat while you manually run the demo scenario.
  collect  Pull screenshot, video, logcat, diagnostics prefs, JSON artifacts, and write summary.md.
  review   Update manual quality fields for an existing ARTIFACT_DIR and rewrite summary.md.
  full     prepare -> capture -> collect.
  aggregate  Write a report table from one or more artifact directories.

Environment:
  ADB=/path/to/adb
  JAVA_HOME=/path/to/android-studio-jbr
  PACKAGE_NAME=com.capstone.design
  SCENARIO=youtube-scroll-mask
  FEATURE_SET=full-pipeline
  RUN_NOTES="manual note for this run"
  MASK_ALIGN=yes|partial|no
  EXPECTED_HARMFUL=3
  MISSED_HARMFUL=0
  FALSE_MASK=0
  STALE_MASK=0
  PPT_READY=yes|no
  QUALITY_NOTES="manual review note"
  RECORD_SECONDS=45
  ARTIFACT_DIR=/private/tmp/chungmaru-android-demo-<timestamp>
  REPORT_PATH=docs/evidence/android-demo-results.md
  SKIP_BUILD=1          Skip Gradle build during prepare.
  ADB_REVERSE=1         Run adb reverse tcp:8000 tcp:8000 for emulator/local backend tests.

FEATURE_SET is a label for comparing runs. It does not toggle app code by itself.
Suggested labels: full-pipeline, accessibility-only, backend-only, ocr-roi,
charbox-line, overlay-gate, backend-offline.

Expected manual demo during capture:
  1. Open YouTube, Chrome, Instagram, or TikTok test surface.
  2. Trigger text collection and masking.
  3. Scroll once or twice to expose stale-mask behavior.
  4. Keep the final masked screen visible near the end of recording.
USAGE
}

manifest_value() {
  local key="$1"
  local file="${ARTIFACT_DIR}/manifest.txt"
  [[ -f "${file}" ]] || return 0
  sed -n -E "s/^${key}=(.*)$/\\1/p" "${file}" | tail -n 1
}

require_device() {
  if adb_cmd devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'; then
    return 0
  fi

  echo "[WARN] No adb device is ready; waiting before retry..."
  if wait_for_adb_device 20; then
    return 0
  fi

  recover_adb_transport
  if wait_for_adb_device 60; then
    return 0
  fi

  echo "[ERROR] No adb device is ready. Connect a device or start an emulator, then run again."
  exit 2
}

is_emulator_device() {
  adb_cmd devices -l | awk '
    NR > 1 && $2 == "device" && ($1 ~ /^emulator-/ || $0 ~ /device:emu/ || $0 ~ /model:sdk_/) { found = 1 }
    END { exit(found ? 0 : 1) }
  '
}

wait_for_adb_device() {
  local timeout_seconds="${1:-60}"
  local state=""
  local attempt
  for attempt in $(seq 1 "${timeout_seconds}"); do
    state="$(adb_cmd get-state 2>/dev/null | tr -d '\r' || true)"
    if [[ "${state}" == "device" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

recover_adb_transport() {
  echo "[WARN] refreshing adb transport before retry..."
  adb_cmd kill-server >/dev/null 2>&1 || true
  sleep 1
  adb_cmd start-server >/dev/null 2>&1 || true
  if ! wait_for_adb_device 30; then
    echo "[WARN] adb device did not return within 30s after transport refresh"
  fi
  sleep 2
}

wait_for_android_boot() {
  wait_for_adb_device 75 || return 1
  local boot_completed=""
  local attempt
  for attempt in $(seq 1 45); do
    boot_completed="$(adb_device_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "${boot_completed}" == "1" ]]; then
      sleep 5
      return 0
    fi
    sleep 2
  done
  return 1
}

reboot_emulator_for_install_recovery() {
  if [[ "${ADB_REBOOT_ON_INSTALL_FAILURE:-1}" != "1" ]]; then
    return 1
  fi
  if ! is_emulator_device; then
    echo "[WARN] install recovery reboot skipped because the connected adb target does not look like an emulator"
    return 1
  fi

  echo "[WARN] package manager install is still failing; rebooting emulator before retry"
  adb_cmd reboot >/dev/null 2>&1 || true
  if wait_for_android_boot; then
    adb_reverse_if_requested
    return 0
  fi
  echo "[WARN] emulator reboot recovery did not reach boot_completed=1 in time"
  return 1
}

host_path_for_adb() {
  local path="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "${path}"
    return
  fi
  printf '%s\n' "${path}"
}

run_gradle_build() {
  if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
    echo "[INFO] SKIP_BUILD=1; skipping Gradle build."
    return
  fi

  env JAVA_HOME="${JAVA_HOME}" "${REPO_ROOT}/scripts/android-mask-check.sh" build
}

install_apk() {
  local apk="${REPO_ROOT}/android/app/build/outputs/apk/debug/app-debug.apk"
  local host_apk
  host_apk="$(host_path_for_adb "${apk}")"
  local device_apk="/data/local/tmp/chungmaru-app-debug.apk"
  local attempt
  for attempt in 1 2 3 4; do
    echo "[INFO] adb install attempt ${attempt}/4: streamed"
    if adb_device_cmd install -r "${host_apk}"; then
      return 0
    fi

    echo "[WARN] streamed adb install failed on attempt ${attempt}/4; trying --no-streaming"
    if adb_device_cmd install -r --no-streaming "${host_apk}"; then
      return 0
    fi

    echo "[WARN] --no-streaming adb install failed on attempt ${attempt}/4; trying push + pm install"
    if adb_device_cmd push "${host_apk}" "${device_apk}" >/dev/null && \
       adb_device_cmd shell pm install -r "${device_apk}"; then
      adb_device_cmd shell rm -f "${device_apk}" >/dev/null 2>&1 || true
      return 0
    fi

    echo "[WARN] adb install failed on attempt ${attempt}/4"
    if [[ "${attempt}" == "2" ]]; then
      reboot_emulator_for_install_recovery || recover_adb_transport
      continue
    fi
    recover_adb_transport
  done
  echo "[ERROR] adb install failed after retries: ${apk}"
  return 1
}

adb_reverse_if_requested() {
  if [[ "${ADB_REVERSE:-0}" == "1" ]]; then
    adb_cmd reverse tcp:8000 tcp:8000 || true
  fi
}

prepare() {
  mkdir -p "${ARTIFACT_DIR}"
  require_device
  run_gradle_build
  install_apk
  adb_reverse_if_requested
  adb_cmd logcat -c
  write_device_info
  echo "[OK] prepared artifact_dir=${ARTIFACT_DIR}"
}

capture() {
  mkdir -p "${ARTIFACT_DIR}"
  require_device

  local live_logcat="${ARTIFACT_DIR}/logcat-live.txt"
  cleanup_host_logcat
  adb_cmd logcat -v time -s "${LOG_TAGS[@]}" > "${live_logcat}" &
  local logcat_pid=$!

  echo "[INFO] Recording ${RECORD_SECONDS}s. Perform scenario now: ${SCENARIO}"
  adb_device_cmd shell screenrecord --time-limit "${RECORD_SECONDS}" "${DEVICE_VIDEO}" >/dev/null 2>&1 &
  local screenrecord_pid=$!

  sleep "${RECORD_SECONDS}"
  wait "${screenrecord_pid}" 2>/dev/null || true
  kill "${logcat_pid}" 2>/dev/null || true
  wait "${logcat_pid}" 2>/dev/null || true
  cleanup_host_logcat

  echo "[OK] capture complete"
}

pull_if_exists() {
  local remote_path="$1"
  local local_path="$2"
  if adb_device_cmd shell "[ -f '${remote_path}' ]" >/dev/null 2>&1; then
    mkdir -p "$(dirname "${local_path}")"
    if adb_device_cmd exec-out cat "${remote_path}" > "${local_path}"; then
      :
    else
      rm -f "${local_path}"
      echo "[WARN] failed to pull device file: ${remote_path}"
      return 0
    fi
    echo "[OK] pulled ${local_path}"
  else
    echo "[WARN] missing device file: ${remote_path}"
  fi
}

pull_run_as_file() {
  local remote_path="$1"
  local local_path="$2"
  mkdir -p "$(dirname "${local_path}")"
  if adb_cmd shell run-as "${PACKAGE_NAME}" cat "${remote_path}" > "${local_path}" 2>/dev/null; then
    if [[ -s "${local_path}" ]]; then
      echo "[OK] pulled ${local_path}"
    else
      rm -f "${local_path}"
      echo "[WARN] empty run-as file: ${remote_path}"
    fi
  else
    rm -f "${local_path}"
    echo "[WARN] run-as failed for ${remote_path}; is this a debug build?"
  fi
}

collect() {
  mkdir -p "${ARTIFACT_DIR}/json" "${ARTIFACT_DIR}/logs"
  require_device

  if adb_device_cmd shell screencap "${DEVICE_SCREEN}" >/dev/null 2>&1; then
    pull_if_exists "${DEVICE_SCREEN}" "${ARTIFACT_DIR}/screen.png"
  else
    mkdir -p "${ARTIFACT_DIR}"
    if adb_device_cmd exec-out screencap -p > "${ARTIFACT_DIR}/screen.png"; then
      echo "[OK] captured ${ARTIFACT_DIR}/screen.png"
    else
      rm -f "${ARTIFACT_DIR}/screen.png"
      echo "[WARN] screencap failed"
    fi
  fi
  if [[ "${COLLECT_VIDEO}" == "1" ]]; then
    pull_if_exists "${DEVICE_VIDEO}" "${ARTIFACT_DIR}/demo.mp4"
  fi

  if [[ "${COLLECT_DEVICE_DUMPS}" == "1" ]]; then
    adb_cmd logcat -d -v time -s "${LOG_TAGS[@]}" > "${ARTIFACT_DIR}/logs/mask-logcat.txt"
    adb_cmd shell dumpsys accessibility > "${ARTIFACT_DIR}/logs/accessibility-dumpsys.txt" 2>/dev/null || true
    adb_cmd shell dumpsys window windows > "${ARTIFACT_DIR}/logs/window-dumpsys.txt" 2>/dev/null || true
    {
      echo "enabled_accessibility_services=$(
        adb_cmd shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r' || true
      )"
      echo "accessibility_enabled=$(
        adb_cmd shell settings get secure accessibility_enabled 2>/dev/null | tr -d '\r' || true
      )"
      echo "foreground_package=$(
        adb_cmd shell dumpsys window 2>/dev/null |
          sed -n -E 's#.*mCurrentFocus=.* ([^/ ]+)/.*#\1#p' |
          tail -n 1 |
          tr -d '\r' || true
      )"
    } > "${ARTIFACT_DIR}/logs/device-state.txt"
  fi
  write_device_info
  pull_json_artifacts
  pull_run_as_file "shared_prefs/youtube_parser_settings.xml" "${ARTIFACT_DIR}/analysis-diagnostics.xml"
  write_summary

  echo "[OK] collected artifact_dir=${ARTIFACT_DIR}"
  echo "[OK] summary=${ARTIFACT_DIR}/summary.md"
}

pull_json_artifacts() {
  local base="/storage/emulated/0/Android/data/${PACKAGE_NAME}/files"
  local prefix
  for prefix in ${JSON_PREFIXES}; do
    local remote_comments_latest remote_comments remote_analysis local_prefix
    if [[ "${prefix}" == "generic" ]]; then
      remote_comments_latest="comments_latest.json"
      remote_comments="comments.jsonl"
      remote_analysis="analysis.jsonl"
      local_prefix="generic_"
    else
      remote_comments_latest="${prefix}_comments_latest.json"
      remote_comments="${prefix}_comments.jsonl"
      remote_analysis="${prefix}_analysis.jsonl"
      local_prefix="${prefix}_"
    fi
    pull_if_exists "${base}/upload_cache/${remote_comments_latest}" \
      "${ARTIFACT_DIR}/json/${local_prefix}comments_latest.json"
    pull_if_exists "${base}/parse_results/${remote_comments}" \
      "${ARTIFACT_DIR}/json/${local_prefix}comments.jsonl"
    pull_if_exists "${base}/analysis_results/${remote_analysis}" \
      "${ARTIFACT_DIR}/json/${local_prefix}analysis.jsonl"
  done
}

write_device_info() {
  mkdir -p "${ARTIFACT_DIR}"
  local run_notes mask_align expected_harmful missed_harmful false_mask stale_mask ppt_ready quality_notes
  run_notes="${RUN_NOTES:-$(manifest_value run_notes)}"
  mask_align="${MASK_ALIGN:-$(manifest_value mask_align)}"
  expected_harmful="${EXPECTED_HARMFUL:-$(manifest_value expected_harmful)}"
  missed_harmful="${MISSED_HARMFUL:-$(manifest_value missed_harmful)}"
  false_mask="${FALSE_MASK:-$(manifest_value false_mask)}"
  stale_mask="${STALE_MASK:-$(manifest_value stale_mask)}"
  ppt_ready="${PPT_READY:-$(manifest_value ppt_ready)}"
  quality_notes="${QUALITY_NOTES:-$(manifest_value quality_notes)}"
  {
    echo "run_id=${RUN_ID}"
    echo "scenario=${SCENARIO}"
    echo "feature_set=${FEATURE_SET}"
    echo "run_notes=${run_notes}"
    echo "mask_align=${mask_align}"
    echo "expected_harmful=${expected_harmful}"
    echo "missed_harmful=${missed_harmful}"
    echo "false_mask=${false_mask}"
    echo "stale_mask=${stale_mask}"
    echo "ppt_ready=${ppt_ready}"
    echo "quality_notes=${quality_notes}"
    echo "record_seconds=${RECORD_SECONDS}"
    echo "package=${PACKAGE_NAME}"
    echo "git_branch=$(git -C "${REPO_ROOT}" branch --show-current 2>/dev/null || true)"
    echo "git_head=$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || true)"
    echo "device_model=$(adb_cmd shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
    echo "device_sdk=$(adb_cmd shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    echo "device_release=$(adb_cmd shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
    echo "captured_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } > "${ARTIFACT_DIR}/manifest.txt"
}

upsert_manifest_key() {
  local key="$1"
  local value="$2"
  local file="${ARTIFACT_DIR}/manifest.txt"
  local tmp="${file}.tmp"
  if [[ ! -f "${file}" ]]; then
    echo "[ERROR] Missing manifest: ${file}"
    exit 2
  fi
  if grep -q -E "^${key}=" "${file}"; then
    awk -v key="${key}" -v value="${value}" '
      BEGIN { prefix = key "=" }
      index($0, prefix) == 1 { print prefix value; next }
      { print }
    ' "${file}" > "${tmp}"
    mv "${tmp}" "${file}"
  else
    printf '%s=%s\n' "${key}" "${value}" >> "${file}"
  fi
}

review() {
  if [[ ! -f "${ARTIFACT_DIR}/manifest.txt" ]]; then
    echo "[ERROR] ARTIFACT_DIR must point to an existing run with manifest.txt: ${ARTIFACT_DIR}"
    exit 2
  fi

  [[ -n "${MASK_ALIGN}" ]] && upsert_manifest_key mask_align "${MASK_ALIGN}"
  [[ -n "${EXPECTED_HARMFUL}" ]] && upsert_manifest_key expected_harmful "${EXPECTED_HARMFUL}"
  [[ -n "${MISSED_HARMFUL}" ]] && upsert_manifest_key missed_harmful "${MISSED_HARMFUL}"
  [[ -n "${FALSE_MASK}" ]] && upsert_manifest_key false_mask "${FALSE_MASK}"
  [[ -n "${STALE_MASK}" ]] && upsert_manifest_key stale_mask "${STALE_MASK}"
  [[ -n "${PPT_READY}" ]] && upsert_manifest_key ppt_ready "${PPT_READY}"
  [[ -n "${QUALITY_NOTES}" ]] && upsert_manifest_key quality_notes "${QUALITY_NOTES}"
  upsert_manifest_key reviewed_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  write_summary
  echo "[OK] review updated artifact_dir=${ARTIFACT_DIR}"
  echo "[OK] summary=${ARTIFACT_DIR}/summary.md"
}

xml_value() {
  local key="$1"
  local file="${ARTIFACT_DIR}/analysis-diagnostics.xml"
  [[ -f "${file}" ]] || return 0
  sed -n -E \
    -e "s/.*name=\"${key}\" value=\"([^\"]*)\".*/\\1/p" \
    -e "s/.*<string name=\"${key}\">(.*)<\\/string>.*/\\1/p" \
    "${file}" | tail -n 1
}

render_rate() {
  local rendered="$1"
  local candidates="$2"
  awk -v rendered="${rendered:-0}" -v candidates="${candidates:-0}" \
    'BEGIN { if (candidates + 0 > 0) printf "%.1f%%", (rendered / candidates) * 100; else printf "n/a" }'
}

format_ms() {
  local value="$1"
  if [[ -n "${value}" && "${value}" != "-1" && "${value}" != "n/a" ]]; then
    printf '%sms' "${value}"
  else
    printf 'n/a'
  fi
}

manifest_value_from_dir() {
  local dir="$1"
  local key="$2"
  local file="${dir}/manifest.txt"
  [[ -f "${file}" ]] || return 0
  sed -n -E "s/^${key}=(.*)$/\\1/p" "${file}" | tail -n 1
}

xml_value_from_dir() {
  local dir="$1"
  local key="$2"
  local file="${dir}/analysis-diagnostics.xml"
  [[ -f "${file}" ]] || return 0
  sed -n -E \
    -e "s/.*name=\"${key}\" value=\"([^\"]*)\".*/\\1/p" \
    -e "s/.*<string name=\"${key}\">(.*)<\\/string>.*/\\1/p" \
    "${file}" | tail -n 1
}

log_max_value_from_dir() {
  local dir="$1"
  local pattern="$2"
  local file="${dir}/logs/mask-logcat.txt"
  [[ -f "${file}" ]] || return 0
  sed -n -E "s/.*${pattern}.*/\\1/p" "${file}" | sort -n | tail -n 1
}

max_nonnegative() {
  local left="${1:-}"
  local right="${2:-}"
  awk -v left="${left:-}" -v right="${right:-}" '
    function clean(value) {
      if (value == "" || value == "n/a") return -1
      return value + 0
    }
    BEGIN {
      l = clean(left)
      r = clean(right)
      if (l < 0 && r < 0) print ""
      else if (l >= r) print l
      else print r
    }
  '
}

latest_artifact_dirs() {
  local root="${ARTIFACT_ROOT:-/private/tmp}"
  find "${root}" -maxdepth 1 -type d -name 'chungmaru-android-demo-*' 2>/dev/null | sort | tail -n "${AGGREGATE_LIMIT:-8}"
}

aggregate() {
  local report_path="${REPORT_PATH:-${REPO_ROOT}/docs/evidence/android-demo-results.md}"
  local dirs=("$@")
  local summary_tsv="${TMPDIR:-/tmp}/chungmaru-android-aggregate-${$}.tsv"
  if [[ ${#dirs[@]} -eq 0 ]]; then
    mapfile -t dirs < <(latest_artifact_dirs)
  fi
  if [[ ${#dirs[@]} -eq 0 ]]; then
    echo "[ERROR] No artifact directories found. Pass directories explicitly or set ARTIFACT_ROOT."
    exit 2
  fi

  mkdir -p "$(dirname "${report_path}")"
  : > "${summary_tsv}"
  {
    echo "# Android Demo Results"
    echo
    echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "| Run | Feature set | Scenario | Device | 01 collect ms | 02 API ms | 03 OCR ms | 04 coord ms | 05 display ms | Candidates | Rendered | Render rate | Offensive | Filtered | Missed | False mask | Stale/manual | Stale/log | Artifact |"
    echo "| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |"

    local dir
    for dir in "${dirs[@]}"; do
      local run_id feature_set scenario device collect_ms api_ms ocr_ms coord_ms display_ms
      local candidates rendered offensive filtered stale_count rate artifact_label missed false_mask stale_manual
      run_id="$(manifest_value_from_dir "${dir}" run_id)"
      feature_set="$(manifest_value_from_dir "${dir}" feature_set)"
      scenario="$(manifest_value_from_dir "${dir}" scenario)"
      device="$(manifest_value_from_dir "${dir}" device_model)"
      missed="$(manifest_value_from_dir "${dir}" missed_harmful)"
      false_mask="$(manifest_value_from_dir "${dir}" false_mask)"
      stale_manual="$(manifest_value_from_dir "${dir}" stale_mask)"
      collect_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_candidate_extraction_ms)" \
        "$(log_max_value_from_dir "${dir}" 'candidateExtractionMs=([0-9]+)')")"
      api_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_latency_ms)" \
        "$(max_nonnegative \
          "$(log_max_value_from_dir "${dir}" 'analysisLatencyMs=([0-9]+)')" \
          "$(log_max_value_from_dir "${dir}" 'latencyMs=([0-9]+)')")")"
      ocr_ms="$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_ocr_latency_ms)"
      coord_ms="$(xml_value_from_dir "${dir}" analysis_diagnostics_accessibility_mask_latency_ms)"
      display_ms="$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_mask_latency_ms)"
      candidates="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_overlay_candidate_count)" \
        "$(log_max_value_from_dir "${dir}" 'parsed analysis target count=([0-9]+)')")"
      rendered="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_overlay_rendered_count)" \
        "$(log_max_value_from_dir "${dir}" 'render maskCount=([0-9]+)')")"
      offensive="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_offensive_count)" \
        "$(max_nonnegative \
          "$(log_max_value_from_dir "${dir}" 'offensive=([0-9]+)')" \
          "$(log_max_value_from_dir "${dir}" 'actionableOffensive=([0-9]+)')")")"
      filtered="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_filtered_count)" \
        "$(log_max_value_from_dir "${dir}" 'filtered=([0-9]+)')")"
      stale_count="$(
        if [[ -f "${dir}/logs/mask-logcat.txt" ]]; then
          grep -E -c 'scroll|stale|defer|preserve' "${dir}/logs/mask-logcat.txt" || true
        else
          echo 0
        fi
      )"
      rate="$(render_rate "${rendered}" "${candidates}")"
      artifact_label="$(basename "${dir}")"
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
        "${feature_set:-n/a}" \
        "${collect_ms:-n/a}" \
        "${api_ms:-n/a}" \
        "${ocr_ms:-n/a}" \
        "${coord_ms:-n/a}" \
        "${display_ms:-n/a}" \
        "${candidates:-n/a}" \
        "${rendered:-n/a}" \
        "${offensive:-n/a}" \
        "${filtered:-n/a}" \
        "${stale_count:-0}" >> "${summary_tsv}"
      echo "| ${run_id:-n/a} | ${feature_set:-n/a} | ${scenario:-n/a} | ${device:-n/a} | ${collect_ms:-n/a} | ${api_ms:-n/a} | ${ocr_ms:-n/a} | ${coord_ms:-n/a} | ${display_ms:-n/a} | ${candidates:-n/a} | ${rendered:-n/a} | ${rate} | ${offensive:-n/a} | ${filtered:-n/a} | ${missed:-TBD} | ${false_mask:-TBD} | ${stale_manual:-TBD} | ${stale_count:-0} | ${artifact_label} |"
    done
    echo
    echo "## Feature Summary"
    echo
    echo "Averages ignore missing values and negative diagnostics such as \`-1\`. \`backend-offline\` measures the backend failure path, not successful model latency."
    echo
    awk -F '\t' '
      function add(metric, feature, value, key) {
        if (value == "" || value == "n/a") return
        value += 0
        if (value < 0) return
        key = feature SUBSEP metric
        sums[key] += value
        counts[key] += 1
        if (!(key in mins) || value < mins[key]) mins[key] = value
        if (!(key in maxs) || value > maxs[key]) maxs[key] = value
      }
      function avg(feature, metric, key) {
        key = feature SUBSEP metric
        return counts[key] ? sprintf("%.1f", sums[key] / counts[key]) : "n/a"
      }
      function range(feature, metric, key) {
        key = feature SUBSEP metric
        return counts[key] ? sprintf("%s-%s", mins[key], maxs[key]) : "n/a"
      }
      {
        feature = $1
        if (!(feature in seen)) {
          order[++feature_count] = feature
          seen[feature] = 1
        }
        runs[feature] += 1
        add("collect", feature, $2)
        add("api", feature, $3)
        add("ocr", feature, $4)
        add("coord", feature, $5)
        add("display", feature, $6)
        add("candidates", feature, $7)
        add("rendered", feature, $8)
        add("offensive", feature, $9)
        add("filtered", feature, $10)
        add("stale", feature, $11)
      }
      END {
        print "| Feature set | Runs | Avg collect ms | Collect range | Avg API ms | API range | Avg coord ms | Avg candidates | Avg rendered | Avg offensive | Avg filtered | Avg stale/log |"
        print "| --- | ---: | ---: | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |"
        for (i = 1; i <= feature_count; i++) {
          feature = order[i]
          print "| " feature " | " runs[feature] " | " avg(feature, "collect") " | " range(feature, "collect") " | " avg(feature, "api") " | " range(feature, "api") " | " avg(feature, "coord") " | " avg(feature, "candidates") " | " avg(feature, "rendered") " | " avg(feature, "offensive") " | " avg(feature, "filtered") " | " avg(feature, "stale") " |"
        }
      }
    ' "${summary_tsv}"
    echo
    echo "## Interpretation Notes"
    echo
    echo "- \`FEATURE_SET\` is a scenario label unless the Android build exposes a real stage toggle. The emulator batch keeps app code unchanged."
    echo "- \`backend-offline\` intentionally points the app to a closed backend port to capture degraded behavior and connection-failure latency."
    echo "- \`Rendered\`, \`Missed\`, \`False mask\`, and manual stale-mask quality require reviewing each \`demo.mp4\` and \`screen.png\`."
    echo
    echo "## Manual review"
    echo
    echo "Fill this after watching each \`demo.mp4\` and checking \`screen.png\`."
    echo
    echo "| Run | Feature set | Mask aligns? | Expected harmful | Missed harmful text | False mask | Stale mask after scroll | PPT-ready? | Note |"
    echo "| --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |"
    for dir in "${dirs[@]}"; do
      echo "| $(manifest_value_from_dir "${dir}" run_id) | $(manifest_value_from_dir "${dir}" feature_set) | $(manifest_value_from_dir "${dir}" mask_align) | $(manifest_value_from_dir "${dir}" expected_harmful) | $(manifest_value_from_dir "${dir}" missed_harmful) | $(manifest_value_from_dir "${dir}" false_mask) | $(manifest_value_from_dir "${dir}" stale_mask) | $(manifest_value_from_dir "${dir}" ppt_ready) | $(manifest_value_from_dir "${dir}" quality_notes) ${dir} |"
    done
  } > "${report_path}"
  rm -f "${summary_tsv}"

  echo "[OK] aggregate report=${report_path}"
}

count_log_lines() {
  local pattern="$1"
  local file="${ARTIFACT_DIR}/logs/mask-logcat.txt"
  [[ -f "${file}" ]] || {
    echo 0
    return
  }
  grep -E -c "${pattern}" "${file}" || true
}

write_summary() {
  local summary="${ARTIFACT_DIR}/summary.md"
  local ok package url sensitivity latency parse_delay candidate_ms accessibility_ms backend_ms
  local node_collection_ms visual_roi_planning_ms screen_candidate_extraction_ms candidate_post_processing_ms
  local candidate_parallel_wait_ms risk_gate_ms risk_gate_event_age_ms risk_gate_receive_to_mask_ms
  local fast_provisional_ms fast_provisional_event_age_ms
  local fast_provisional_build_ms fast_provisional_overlay_ms fast_provisional_receive_to_mask_ms
  local visual_ocr_ms visual_mask_ms comment_count offensive_count filtered_count overlay_candidates
  local overlay_rendered overlay_skipped visual_supported visual_reason visual_roi_candidates
  local visual_roi_selected visual_ocr_raw visual_ocr_selected error
  local mask_align expected_harmful missed_harmful false_mask stale_mask ppt_ready quality_notes
  local log_candidate_ms log_node_collection_ms log_visual_roi_planning_ms log_screen_candidate_extraction_ms
  local log_candidate_post_processing_ms log_candidate_parallel_wait_ms log_risk_gate_ms
  local log_risk_gate_event_age_ms log_risk_gate_receive_to_mask_ms log_fast_provisional_ms
  local log_fast_provisional_event_age_ms log_fast_provisional_build_ms log_fast_provisional_overlay_ms
  local log_fast_provisional_receive_to_mask_ms log_api_ms log_candidate_count log_offensive_count log_filtered_count
  local log_overlay_rendered_count observed_overlay_rendered

  ok="$(xml_value analysis_diagnostics_ok)"
  package="$(xml_value analysis_diagnostics_package)"
  url="$(xml_value analysis_diagnostics_url)"
  sensitivity="$(xml_value analysis_diagnostics_sensitivity)"
  latency="$(xml_value analysis_diagnostics_latency_ms)"
  parse_delay="$(xml_value analysis_diagnostics_parse_delay_ms)"
  candidate_ms="$(xml_value analysis_diagnostics_candidate_extraction_ms)"
  node_collection_ms="$(xml_value analysis_diagnostics_node_collection_ms)"
  visual_roi_planning_ms="$(xml_value analysis_diagnostics_visual_roi_planning_ms)"
  screen_candidate_extraction_ms="$(xml_value analysis_diagnostics_screen_candidate_extraction_ms)"
  candidate_post_processing_ms="$(xml_value analysis_diagnostics_candidate_post_processing_ms)"
  candidate_parallel_wait_ms="$(xml_value analysis_diagnostics_candidate_parallel_wait_ms)"
  accessibility_ms="$(xml_value analysis_diagnostics_accessibility_mask_latency_ms)"
  risk_gate_ms="$(xml_value analysis_diagnostics_risk_gate_mask_ms)"
  risk_gate_event_age_ms="$(xml_value analysis_diagnostics_risk_gate_event_age_ms)"
  risk_gate_receive_to_mask_ms="$(xml_value analysis_diagnostics_risk_gate_receive_to_mask_ms)"
  fast_provisional_ms="$(xml_value analysis_diagnostics_fast_provisional_mask_ms)"
  fast_provisional_event_age_ms="$(xml_value analysis_diagnostics_fast_provisional_event_age_ms)"
  fast_provisional_build_ms="$(xml_value analysis_diagnostics_fast_provisional_build_ms)"
  fast_provisional_overlay_ms="$(xml_value analysis_diagnostics_fast_provisional_overlay_ms)"
  fast_provisional_receive_to_mask_ms="$(xml_value analysis_diagnostics_fast_provisional_receive_to_mask_ms)"
  backend_ms="$(xml_value analysis_diagnostics_backend_mask_latency_ms)"
  visual_ocr_ms="$(xml_value analysis_diagnostics_visual_ocr_latency_ms)"
  visual_mask_ms="$(xml_value analysis_diagnostics_visual_mask_latency_ms)"
  comment_count="$(xml_value analysis_diagnostics_comment_count)"
  offensive_count="$(xml_value analysis_diagnostics_offensive_count)"
  filtered_count="$(xml_value analysis_diagnostics_filtered_count)"
  overlay_candidates="$(xml_value analysis_diagnostics_overlay_candidate_count)"
  overlay_rendered="$(xml_value analysis_diagnostics_overlay_rendered_count)"
  overlay_skipped="$(xml_value analysis_diagnostics_overlay_skipped_unstable_count)"
  visual_supported="$(xml_value analysis_diagnostics_visual_capture_supported)"
  visual_reason="$(xml_value analysis_diagnostics_visual_capture_reason)"
  visual_roi_candidates="$(xml_value analysis_diagnostics_visual_roi_candidate_count)"
  visual_roi_selected="$(xml_value analysis_diagnostics_visual_roi_selected_count)"
  visual_ocr_raw="$(xml_value analysis_diagnostics_visual_ocr_raw_count)"
  visual_ocr_selected="$(xml_value analysis_diagnostics_visual_ocr_selected_count)"
  error="$(xml_value analysis_diagnostics_error)"
  mask_align="${MASK_ALIGN:-$(manifest_value mask_align)}"
  expected_harmful="${EXPECTED_HARMFUL:-$(manifest_value expected_harmful)}"
  missed_harmful="${MISSED_HARMFUL:-$(manifest_value missed_harmful)}"
  false_mask="${FALSE_MASK:-$(manifest_value false_mask)}"
  stale_mask="${STALE_MASK:-$(manifest_value stale_mask)}"
  ppt_ready="${PPT_READY:-$(manifest_value ppt_ready)}"
  quality_notes="${QUALITY_NOTES:-$(manifest_value quality_notes)}"
  log_candidate_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'candidateExtractionMs=([0-9]+)')"
  log_node_collection_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'nodeCollectionMs=([0-9]+)')"
  log_visual_roi_planning_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'visualRoiPlanningMs=([0-9]+)')"
  log_screen_candidate_extraction_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'screenCandidateExtractionMs=([0-9]+)')"
  log_candidate_post_processing_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'candidatePostProcessingMs=([0-9]+)')"
  log_candidate_parallel_wait_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'candidateParallelWaitMs=([0-9]+)')"
  log_risk_gate_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'risk gate mask specs=[0-9]+ elapsedMs=([0-9]+)')"
  log_risk_gate_event_age_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'risk gate mask specs=[0-9]+ .*eventAgeMs=([0-9]+)')"
  log_risk_gate_receive_to_mask_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'risk gate mask specs=[0-9]+ .*receiveToMaskMs=([0-9]+)')"
  log_fast_provisional_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'fast provisional mask results=[0-9]+ elapsedMs=([0-9]+)')"
  log_fast_provisional_event_age_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'fast provisional mask results=[0-9]+ .*eventAgeMs=([0-9]+)')"
  log_fast_provisional_build_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'fast provisional mask results=[0-9]+ .*buildMs=([0-9]+)')"
  log_fast_provisional_overlay_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'fast provisional mask results=[0-9]+ .*overlayMs=([0-9]+)')"
  log_fast_provisional_receive_to_mask_ms="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'fast provisional mask results=[0-9]+ .*receiveToMaskMs=([0-9]+)')"
  log_api_ms="$(max_nonnegative \
    "$(log_max_value_from_dir "${ARTIFACT_DIR}" 'analysisLatencyMs=([0-9]+)')" \
    "$(log_max_value_from_dir "${ARTIFACT_DIR}" 'latencyMs=([0-9]+)')")"
  log_candidate_count="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'parsed analysis target count=([0-9]+)')"
  log_offensive_count="$(max_nonnegative \
    "$(log_max_value_from_dir "${ARTIFACT_DIR}" 'offensive=([0-9]+)')" \
    "$(log_max_value_from_dir "${ARTIFACT_DIR}" 'actionableOffensive=([0-9]+)')")"
  log_filtered_count="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'filtered=([0-9]+)')"
  log_overlay_rendered_count="$(log_max_value_from_dir "${ARTIFACT_DIR}" 'render maskCount=([0-9]+)')"
  observed_overlay_rendered="$(max_nonnegative "${overlay_rendered}" "${log_overlay_rendered_count}")"

  {
    echo "# Chungmaru Android Demo Evidence Summary"
    echo
    echo "## Run"
    echo
    echo "| Field | Value |"
    echo "| --- | --- |"
    sed 's/^/| /; s/=/ | /; s/$/ |/' "${ARTIFACT_DIR}/manifest.txt"
    echo
    echo "## Latest Diagnostics"
    echo
    echo "| Metric | Value |"
    echo "| --- | --- |"
    echo "| ok | ${ok:-n/a} |"
    echo "| package | ${package:-n/a} |"
    echo "| analysis url | ${url:-n/a} |"
    echo "| sensitivity | ${sensitivity:-n/a} |"
    echo "| total analysis latency ms | ${latency:-n/a} |"
    echo "| parse delay ms | ${parse_delay:-n/a} |"
    echo "| candidate extraction ms | ${candidate_ms:-n/a} |"
    echo "| node collection ms | ${node_collection_ms:-n/a} |"
    echo "| visual ROI planning ms | ${visual_roi_planning_ms:-n/a} |"
    echo "| screen candidate extraction ms | ${screen_candidate_extraction_ms:-n/a} |"
    echo "| candidate post-processing ms | ${candidate_post_processing_ms:-n/a} |"
    echo "| candidate parallel wait ms | ${candidate_parallel_wait_ms:-n/a} |"
    echo "| risk gate mask ms | ${risk_gate_ms:-n/a} |"
    echo "| risk gate event age ms | ${risk_gate_event_age_ms:-n/a} |"
    echo "| risk gate receive-to-mask ms | ${risk_gate_receive_to_mask_ms:-n/a} |"
    echo "| fast provisional mask ms | ${fast_provisional_ms:-n/a} |"
    echo "| fast provisional event age ms | ${fast_provisional_event_age_ms:-n/a} |"
    echo "| fast provisional build ms | ${fast_provisional_build_ms:-n/a} |"
    echo "| fast provisional overlay ms | ${fast_provisional_overlay_ms:-n/a} |"
    echo "| fast provisional receive-to-mask ms | ${fast_provisional_receive_to_mask_ms:-n/a} |"
    echo "| accessibility mask latency ms | ${accessibility_ms:-n/a} |"
    echo "| backend mask latency ms | ${backend_ms:-n/a} |"
    echo "| visual OCR latency ms | ${visual_ocr_ms:-n/a} |"
    echo "| visual mask latency ms | ${visual_mask_ms:-n/a} |"
    echo "| comments/candidates analyzed | ${comment_count:-n/a} |"
    echo "| offensive/actionable count | ${offensive_count:-n/a} |"
    echo "| filtered count | ${filtered_count:-n/a} |"
    echo "| overlay candidates | ${overlay_candidates:-n/a} |"
    echo "| overlay rendered | ${overlay_rendered:-n/a} |"
    echo "| overlay skipped unstable | ${overlay_skipped:-n/a} |"
    echo "| overlay render rate | $(render_rate "${overlay_rendered}" "${overlay_candidates}") |"
    echo "| visual capture supported | ${visual_supported:-n/a} |"
    echo "| visual capture reason | ${visual_reason:-n/a} |"
    echo "| visual ROI candidates | ${visual_roi_candidates:-n/a} |"
    echo "| visual ROI selected | ${visual_roi_selected:-n/a} |"
    echo "| visual OCR raw count | ${visual_ocr_raw:-n/a} |"
    echo "| visual OCR selected count | ${visual_ocr_selected:-n/a} |"
    echo "| error | ${error:-} |"
    echo
    echo "## Log-Derived Peaks"
    echo
    echo "Latest diagnostics can be overwritten by a final empty/OCR-miss attempt. Use these log-derived peaks when they are higher."
    echo
    echo "| Metric | Value |"
    echo "| --- | --- |"
    echo "| max candidate extraction ms | ${log_candidate_ms:-n/a} |"
    echo "| max node collection ms | ${log_node_collection_ms:-n/a} |"
    echo "| max visual ROI planning ms | ${log_visual_roi_planning_ms:-n/a} |"
    echo "| max screen candidate extraction ms | ${log_screen_candidate_extraction_ms:-n/a} |"
    echo "| max candidate post-processing ms | ${log_candidate_post_processing_ms:-n/a} |"
    echo "| max candidate parallel wait ms | ${log_candidate_parallel_wait_ms:-n/a} |"
    echo "| max risk gate mask ms | ${log_risk_gate_ms:-n/a} |"
    echo "| max risk gate event age ms | ${log_risk_gate_event_age_ms:-n/a} |"
    echo "| max risk gate receive-to-mask ms | ${log_risk_gate_receive_to_mask_ms:-n/a} |"
    echo "| max fast provisional mask ms | ${log_fast_provisional_ms:-n/a} |"
    echo "| max fast provisional event age ms | ${log_fast_provisional_event_age_ms:-n/a} |"
    echo "| max fast provisional build ms | ${log_fast_provisional_build_ms:-n/a} |"
    echo "| max fast provisional overlay ms | ${log_fast_provisional_overlay_ms:-n/a} |"
    echo "| max fast provisional receive-to-mask ms | ${log_fast_provisional_receive_to_mask_ms:-n/a} |"
    echo "| max API/analysis latency ms | ${log_api_ms:-n/a} |"
    echo "| max parsed analysis targets | ${log_candidate_count:-n/a} |"
    echo "| max offensive/actionable count | ${log_offensive_count:-n/a} |"
    echo "| max filtered count | ${log_filtered_count:-n/a} |"
    echo "| max overlay rendered | ${log_overlay_rendered_count:-n/a} |"
    echo
    echo "## Pipeline Stage Breakdown"
    echo
    echo "| Stage | Component | Latency/data source | Value | Quality signal | Missing data to improve |"
    echo "| --- | --- | --- | --- | --- | --- |"
    echo "| 01 Collect | Accessibility Service | parse delay / candidate extraction | parse=$(format_ms "${parse_delay}"), total=$(format_ms "${candidate_ms}"), nodes=$(format_ms "${node_collection_ms}"), candidate=$(format_ms "${screen_candidate_extraction_ms}") | candidates analyzed=${comment_count:-n/a} | per-node traversal attribution still needs a profiler trace |"
    echo "| 02 Analyze | Android API | total analysis latency / backend mask latency | total=$(format_ms "${latency}"), backendMask=$(format_ms "${backend_ms}") | offensive=${offensive_count:-n/a}, filtered=${filtered_count:-n/a}, url=${url:-n/a} | backend cold-start and network phase are not separated yet |"
    echo "| 03 Supplement | OCR ROI | visual ROI / OCR latency | roiPlan=$(format_ms "${visual_roi_planning_ms}"), roi=${visual_roi_selected:-n/a}/${visual_roi_candidates:-n/a}, ocr=$(format_ms "${visual_ocr_ms}") | raw OCR=${visual_ocr_raw:-n/a}, selected=${visual_ocr_selected:-n/a}, supported=${visual_supported:-n/a} | OCR crop time and recognition time are not separated yet |"
    echo "| 04 Coordinate | char box / line estimate | accessibility mask latency | riskGate=$(format_ms "${risk_gate_ms}"), riskReceive=$(format_ms "${risk_gate_receive_to_mask_ms}"), firstProvisional=$(format_ms "${fast_provisional_ms}"), eventAge=$(format_ms "${fast_provisional_event_age_ms}"), receiveToMask=$(format_ms "${fast_provisional_receive_to_mask_ms}"), build=$(format_ms "${fast_provisional_build_ms}"), overlay=$(format_ms "${fast_provisional_overlay_ms}"), coord=$(format_ms "${accessibility_ms}"), parallelWait=$(format_ms "${candidate_parallel_wait_ms}") | skippedUnstable=${overlay_skipped:-n/a} | char-box request ms and line-estimate ms are not separated yet |"
    echo "| 05 Display | overlay gate | visual mask latency / render rate | display=$(format_ms "${visual_mask_ms}"), post=$(format_ms "${candidate_post_processing_ms}"), renderRate=$(render_rate "${observed_overlay_rendered}" "${overlay_candidates}") | rendered=${observed_overlay_rendered:-n/a}/${overlay_candidates:-n/a} observed, latest=${overlay_rendered:-n/a} | manual video review still required for exact visual quality |"
    echo
    echo "## Feature Set Interpretation"
    echo
    echo "- \`feature_set\` is a run label from \`FEATURE_SET\`; it does not toggle code by itself."
    echo "- Use it to compare full-pipeline, accessibility-only, backend-only, ocr-roi, charbox-line, overlay-gate, and backend-offline runs."
    echo "- If you toggle code/settings in Android Studio, set the matching \`FEATURE_SET\` before capture."
    echo
    echo "## Quality Review Inputs"
    echo
    echo "| Field | Value | Meaning |"
    echo "| --- | --- | --- |"
    echo "| mask align | ${mask_align:-TBD} | yes/partial/no after watching demo.mp4 |"
    echo "| expected harmful | ${expected_harmful:-TBD} | visible harmful expressions expected to be masked in the final review window |"
    echo "| missed harmful | ${missed_harmful:-TBD} | harmful expressions still visible after masking |"
    echo "| false mask | ${false_mask:-TBD} | masks shown on safe content |"
    echo "| stale mask | ${stale_mask:-TBD} | masks left behind after scroll or layout change |"
    echo "| PPT-ready | ${ppt_ready:-TBD} | yes only when video, screenshot, logs, JSON, and diagnostics tell the same story |"
    echo "| notes | ${quality_notes:-} | manual review context |"
    echo
    echo "## Log Signals"
    echo
    echo "| Signal | Count |"
    echo "| --- | ---: |"
    echo "| analysis ok | $(count_log_lines 'analysis ok') |"
    echo "| analysis failed/error | $(count_log_lines 'analysis failed|analysis request failed|ERROR|Exception') |"
    echo "| visual OCR start | $(count_log_lines 'start visual OCR') |"
    echo "| visual OCR selected | $(count_log_lines 'visual OCR candidates selected') |"
    echo "| render mask overlay | $(count_log_lines 'render mask overlay') |"
    echo "| scroll/stale/defer/preserve | $(count_log_lines 'scroll|stale|defer|preserve') |"
    echo
    echo "## Artifacts"
    echo
    echo "- Demo video: \`demo.mp4\`"
    echo "- Final screenshot: \`screen.png\`"
    echo "- Live logcat: \`logcat-live.txt\`"
    echo "- Final logcat dump: \`logs/mask-logcat.txt\`"
    echo "- Diagnostics prefs: \`analysis-diagnostics.xml\`"
    echo "- JSON outputs: \`json/*\`"
    echo
    echo "## Manual Review Slots"
    echo
    echo "| Review item | Result | Note |"
    echo "| --- | --- | --- |"
    echo "| Mask aligns with harmful text | TBD | Check demo.mp4 + screen.png |"
    echo "| Harmful text missed | TBD | Count visible harmful text after final frame |"
    echo "| False mask on safe content | TBD | Count masks on unrelated text |"
    echo "| Stale mask after scroll | TBD | Check whether mask remains on wrong region |"
    echo "| PPT-ready? | TBD | Mark yes only after manual review |"
  } > "${summary}"
}

case "${1:-full}" in
  prepare)
    prepare
    ;;
  capture)
    capture
    ;;
  collect)
    collect
    ;;
  review)
    review
    ;;
  full)
    prepare
    capture
    collect
    ;;
  aggregate)
    shift
    aggregate "$@"
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "[ERROR] Unknown mode: $1"
    usage
    exit 2
    ;;
esac
