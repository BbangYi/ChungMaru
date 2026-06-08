#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ADB="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
PACKAGE_NAME="${PACKAGE_NAME:-com.capstone.design}"
CHROME_PACKAGE="${CHROME_PACKAGE:-com.android.chrome}"
BATCH_ID="${BATCH_ID:-android-google-mobile-$(date +%Y%m%dT%H%M%S)}"
BATCH_ROOT="${BATCH_ROOT:-/private/tmp/chungmaru-android-google-mobile-${BATCH_ID}}"
REPORT_DIR="${REPORT_DIR:-${REPO_ROOT}/docs/evidence/android-google-mobile}"
RUNS_PER_SCENARIO="${RUNS_PER_SCENARIO:-1}"
START_INDEX="${START_INDEX:-1}"
TARGET_ROWS="${TARGET_ROWS:-0}"
RECORD_SECONDS="${RECORD_SECONDS:-20}"
VIDEO_POLICY="${ANDROID_GOOGLE_VIDEO_POLICY:-first}"
NO_VIDEO_SETTLE_SECONDS="${NO_VIDEO_SETTLE_SECONDS:-1}"
WAIT_AFTER_LOAD_SECONDS="${WAIT_AFTER_LOAD_SECONDS:-7}"
PIPELINE_MODE="${PIPELINE_MODE:-s12345_full}"
ANDROID_GOOGLE_METHOD_ID="${ANDROID_GOOGLE_METHOD_ID:-current}"
ANDROID_GOOGLE_METHOD_LABEL="${ANDROID_GOOGLE_METHOD_LABEL:-${ANDROID_GOOGLE_METHOD_ID}}"
ONLINE_ANALYSIS_INPUT="${ONLINE_ANALYSIS_INPUT:-127.0.0.1:8000}"
ADB_REVERSE_BACKEND="${ADB_REVERSE_BACKEND:-1}"
MIN_VIDEO_BYTES="${MIN_VIDEO_BYTES:-120000}"
STRICT_VIDEO="${STRICT_VIDEO:-0}"
GOOGLE_QUERY_SET="${GOOGLE_QUERY_SET:-moderation-core}"
RESET_CHROME_BEFORE_SCENE="${RESET_CHROME_BEFORE_SCENE:-1}"

DEFAULT_SCENARIOS=$'core-profanity-direct\t병신아 꺼져\tprofanity\tmask-spans\ncore-hate-gender-slur\t한남충은 답이 없다\thate\tmask-spans\ncore-toxicity-threat\t너 한번만 더 그러면 죽여버린다\ttoxicity\tmask-spans\nclean-policy-law\t차별금지법 기사\tclean-topic-bias\tallow\nadult-commerce-compact\t콜걸성인마사지\tadult-commerce\tmask'

usage() {
  cat <<'USAGE'
Usage: scripts/android-google-mobile-evidence.sh [run|aggregate|help]

Runs a real Android Chrome Google Search demo and keeps the same evidence shape
as the Android pipeline benchmark: demo.mp4, screen.png, logcat, diagnostics XML,
raw_runs.csv, stage_latency.csv, stage_latency_summary.csv, and report markdown.

Environment:
  RUNS_PER_SCENARIO=1
  TARGET_ROWS=0                       if >0, derives RUNS_PER_SCENARIO to reach roughly this many rows
  ANDROID_GOOGLE_METHOD_ID=current
  ANDROID_GOOGLE_METHOD_LABEL=current
  RECORD_SECONDS=20
  ANDROID_GOOGLE_VIDEO_POLICY=first     first|every|none
  NO_VIDEO_SETTLE_SECONDS=1
  GOOGLE_QUERY_SET=moderation-core     moderation-core|balanced|all
  GOOGLE_SCENARIOS_TSV=<id<TAB>query<TAB>category<TAB>expected lines>
  PIPELINE_MODE=s12345_full
  ONLINE_ANALYSIS_INPUT=127.0.0.1:8000
  ADB_REVERSE_BACKEND=1
  RESET_CHROME_BEFORE_SCENE=1
  MIN_VIDEO_BYTES=120000
  STRICT_VIDEO=0
  BATCH_ROOT=/private/tmp/chungmaru-android-google-mobile-<id>
  REPORT_DIR=docs/evidence/android-google-mobile
USAGE
}

adb_cmd() {
  "${ADB}" "$@"
}

adb_device_cmd() {
  MSYS2_ARG_CONV_EXCL="*" MSYS_NO_PATHCONV=1 "${ADB}" "$@"
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

  echo "[ERROR] No adb device is ready. Start an emulator or connect a device first."
  exit 2
}

wait_for_adb_device() {
  local timeout_seconds="${1:-60}"
  local state attempt
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
  wait_for_adb_device 30 || true
  sleep 2
}

adb_shell_retry() {
  local attempt
  for attempt in 1 2 3; do
    if adb_device_cmd shell "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep "${attempt}"
  done
  return 1
}

configure_adb_reverse() {
  if [[ "${ADB_REVERSE_BACKEND}" == "1" ]]; then
    adb_cmd reverse tcp:8000 tcp:8000 || true
  fi
}

set_pipeline_preferences() {
  local mode="$1"
  local analysis_input="$2"
  local sensitivity="$3"
  adb_cmd shell run-as "${PACKAGE_NAME}" mkdir -p shared_prefs
  {
    echo '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>'
    echo '<map>'
    echo "    <string name=\"analysis_input\">${analysis_input}</string>"
    echo "    <int name=\"analysis_sensitivity\" value=\"${sensitivity}\" />"
    echo "    <string name=\"pipeline_experiment_mode\">${mode}</string>"
    echo '</map>'
  } | adb_cmd shell run-as "${PACKAGE_NAME}" tee shared_prefs/youtube_parser_settings.xml >/dev/null
}

stage_mask_for_mode() {
  case "$1" in
    s1_collect_only) echo "1" ;;
    s2_backend_only) echo "2" ;;
    s3_ocr_roi_only) echo "3" ;;
    s4_coord_only) echo "4" ;;
    s5_overlay_only) echo "5" ;;
    s12_collect_backend) echo "1+2" ;;
    s123_collect_backend_ocr) echo "1+2+3" ;;
    s1234_collect_backend_ocr_coord) echo "1+2+3+4" ;;
    s12345_full) echo "1+2+3+4+5" ;;
    *) echo "unknown" ;;
  esac
}

ensure_accessibility_enabled() {
  local service="${PACKAGE_NAME}/com.capstone.design.youtubeparser.YoutubeAccessibilityService"
  local current
  adb_shell_retry settings put secure enabled_accessibility_services "${service}" || \
    echo "[WARN] failed to write enabled_accessibility_services; continuing with current device state"
  adb_shell_retry settings put secure accessibility_enabled 1 || \
    echo "[WARN] failed to write accessibility_enabled; continuing with current device state"
  sleep 0.5
  current="$(adb_cmd shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r' || true)"
  if [[ "${current}" != *"${service}"* ]]; then
    echo "[WARN] Chungmaru accessibility service is not confirmed: current=${current:-empty}"
  fi
}

prepare_device() {
  mkdir -p "${BATCH_ROOT}/runs"
  require_device
  configure_adb_reverse
  RUN_ID="${BATCH_ID}-prepare" \
    ARTIFACT_DIR="${BATCH_ROOT}/prepare" \
    ADB_REVERSE="${ADB_REVERSE_BACKEND}" \
    ADB="${ADB}" \
    JAVA_HOME="${JAVA_HOME}" \
    "${REPO_ROOT}/scripts/android-demo-evidence.sh" prepare
  configure_adb_reverse
  set_pipeline_preferences "${PIPELINE_MODE}" "${ONLINE_ANALYSIS_INPUT}" 80
  ensure_accessibility_enabled
  prepare_chrome_first_run
  adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

clear_device_json_artifacts() {
  local base="/storage/emulated/0/Android/data/${PACKAGE_NAME}/files"
  adb_device_cmd shell rm -rf \
    "${base}/upload_cache" \
    "${base}/parse_results" \
    "${base}/analysis_results" >/dev/null 2>&1 || true
}

urlencode() {
  python3 - "$1" <<'PY'
import sys
import urllib.parse

print(urllib.parse.quote_plus(sys.argv[1]))
PY
}

google_search_url() {
  local query="$1"
  local encoded
  encoded="$(urlencode "${query}")"
  printf 'https://www.google.com/search?q=%s&hl=ko&num=10&pws=0&safe=off' "${encoded}"
}

open_google_query() {
  local query="$1"
  local url shell_url
  url="$(google_search_url "${query}")"
  shell_url="${url//\'/%27}"
  adb_device_cmd shell "am start -a android.intent.action.VIEW -d '${shell_url}' -p '${CHROME_PACKAGE}' >/dev/null"
}

reset_chrome_if_requested() {
  if [[ "${RESET_CHROME_BEFORE_SCENE}" != "1" ]]; then
    return
  fi

  adb_shell_retry am force-stop "${CHROME_PACKAGE}" || true
  sleep 1
  adb_device_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
}

chrome_anr_visible() {
  local window_dump ui_dump
  window_dump="$(adb_cmd shell dumpsys window windows 2>/dev/null | tr -d '\r' || true)"
  if printf '%s\n' "${window_dump}" |
    grep -Eiq "Application Error|Application Not Responding|${CHROME_PACKAGE}.*not responding"; then
    return 0
  fi

  adb_cmd shell uiautomator dump /sdcard/chungmaru-window.xml >/dev/null 2>&1 || return 1
  ui_dump="$(adb_cmd shell cat /sdcard/chungmaru-window.xml 2>/dev/null | tr -d '\r' || true)"
  adb_cmd shell rm -f /sdcard/chungmaru-window.xml >/dev/null 2>&1 || true
  printf '%s\n' "${ui_dump}" |
    grep -Eiq "Chrome isn'?t responding|${CHROME_PACKAGE} isn'?t responding|isn'?t responding"
}

dump_ui_xml() {
  local remote="/sdcard/chungmaru-window.xml"
  adb_cmd shell uiautomator dump "${remote}" >/dev/null 2>&1 || return 1
  adb_cmd shell cat "${remote}" 2>/dev/null | tr -d '\r'
  adb_cmd shell rm -f "${remote}" >/dev/null 2>&1 || true
}

tap_ui_text() {
  local needle="$1"
  local xml xml_file coords
  xml="$(dump_ui_xml || true)"
  [[ -n "${xml}" ]] || return 1
  xml_file="$(mktemp)"
  printf '%s' "${xml}" >"${xml_file}"
  coords="$(python3 - "${needle}" "${xml_file}" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

needle = sys.argv[1].casefold()
xml = Path(sys.argv[2]).read_text(encoding="utf-8", errors="ignore")
try:
    root = ET.fromstring(xml)
except ET.ParseError:
    raise SystemExit(1)

for node in root.iter("node"):
    haystack = " ".join(
        (node.attrib.get(key) or "")
        for key in ("text", "content-desc", "resource-id")
    ).casefold()
    if needle not in haystack:
        continue
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    print(f"{(left + right) // 2} {(top + bottom) // 2}")
    raise SystemExit(0)
raise SystemExit(1)
PY
)"
  rm -f "${xml_file}"
  [[ -n "${coords}" ]] || return 1
  adb_device_cmd shell input tap ${coords} >/dev/null 2>&1
}

prepare_chrome_first_run() {
  local attempt
  adb_shell_retry am force-stop "${CHROME_PACKAGE}" || true
  open_google_query "chrome setup"
  sleep 3
  for attempt in 1 2 3 4; do
    if tap_ui_text "Use without an account"; then
      sleep 2
      continue
    fi
    if tap_ui_text "No thanks"; then
      sleep 2
      continue
    fi
    if tap_ui_text "Accept & continue"; then
      sleep 2
      continue
    fi
    if tap_ui_text "Skip"; then
      sleep 2
      continue
    fi
    break
  done
  adb_shell_retry am force-stop "${CHROME_PACKAGE}" || true
}

drive_google_scene() {
  local query="$1"
  reset_chrome_if_requested
  open_google_query "${query}"
  sleep "${WAIT_AFTER_LOAD_SECONDS}"
  if chrome_anr_visible; then
    echo "[WARN] Chrome ANR dialog detected after opening Google; force-stopping Chrome and retrying once"
    RESET_CHROME_BEFORE_SCENE=1 reset_chrome_if_requested
    open_google_query "${query}"
    sleep "${WAIT_AFTER_LOAD_SECONDS}"
  fi
  adb_cmd shell input swipe 520 1780 520 720 550 >/dev/null 2>&1 || true
  sleep 2
  adb_cmd shell input swipe 520 720 520 1780 450 >/dev/null 2>&1 || true
  sleep 2
  adb_cmd shell input swipe 520 1780 520 860 450 >/dev/null 2>&1 || true
  sleep 1
}

scenario_lines() {
  if [[ -n "${GOOGLE_SCENARIOS_TSV:-}" ]]; then
    printf '%s\n' "${GOOGLE_SCENARIOS_TSV}"
    return
  fi

  case "${GOOGLE_QUERY_SET}" in
    moderation-core)
      printf '%s\n' "${DEFAULT_SCENARIOS}" | awk -F '\t' 'NR <= 3'
      ;;
    balanced)
      printf '%s\n' "${DEFAULT_SCENARIOS}" | awk -F '\t' 'NR == 1 || NR == 4 || NR == 5'
      ;;
    all)
      printf '%s\n' "${DEFAULT_SCENARIOS}"
      ;;
    *)
      echo "[ERROR] Unknown GOOGLE_QUERY_SET=${GOOGLE_QUERY_SET}; use moderation-core, balanced, all, or GOOGLE_SCENARIOS_TSV." >&2
      exit 2
      ;;
  esac
}

validate_video() {
  local artifact_dir="$1"
  local video="${artifact_dir}/demo.mp4"
  local size
  if [[ ! -f "${video}" ]]; then
    echo "[WARN] missing demo video: ${video}"
    [[ "${STRICT_VIDEO}" == "1" ]] && exit 2
    return
  fi
  size="$(wc -c < "${video}" | tr -d ' ')"
  if [[ "${size}" -lt "${MIN_VIDEO_BYTES}" ]]; then
    echo "[WARN] demo video is very small (${size} bytes): ${video}"
    [[ "${STRICT_VIDEO}" == "1" ]] && exit 2
  fi
}

should_record_video() {
  local index="$1"
  case "${VIDEO_POLICY}" in
    every)
      return 0
      ;;
    first)
      [[ "${index}" == "1" ]]
      return
      ;;
    none)
      return 1
      ;;
    *)
      echo "[ERROR] Unknown ANDROID_GOOGLE_VIDEO_POLICY=${VIDEO_POLICY}; use first, every, or none." >&2
      exit 2
      ;;
  esac
}

capture_logcat_only() {
  local artifact_dir="$1"
  local query="$2"
  local live_logcat="${artifact_dir}/logcat-live.txt"
  local logcat_pid

  mkdir -p "${artifact_dir}"
  adb_cmd logcat -v time -s \
    YTParserService \
    AndroidAnalysisClient \
    VisualTextOcrProcessor \
    MaskOverlayController \
    JsonFileStore > "${live_logcat}" &
  logcat_pid=$!

  sleep 1
  drive_google_scene "${query}"
  sleep "${NO_VIDEO_SETTLE_SECONDS}"
  kill "${logcat_pid}" 2>/dev/null || true
  wait "${logcat_pid}" 2>/dev/null || true
}

append_scene_row() {
  python3 - "${BATCH_ROOT}/google-mobile-scenes.csv" "$@" <<'PY'
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1])
fieldnames = [
    "run_id",
    "method_id",
    "method_label",
    "scenario_id",
    "repeat_index",
    "query",
    "category",
    "expected",
    "video_recorded",
    "artifact_dir",
    "demo_mp4",
    "screen_png",
    "summary_md",
]
values = sys.argv[2:]
row = {field: values[index] if index < len(values) else "" for index, field in enumerate(fieldnames)}
with path.open("a", encoding="utf-8", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
    writer.writerow(row)
PY
}

run_scene() {
  local scenario_id="$1"
  local query="$2"
  local category="$3"
  local expected="$4"
  local index="$5"
  local repeat="$6"
  local run_id="${BATCH_ID}-${PIPELINE_MODE}-${index}"
  local artifact_dir="${BATCH_ROOT}/runs/${run_id}"
  local record_video=0
  local demo_mp4=""
  local quality_notes="actual Google mobile Chrome capture; latency/log evidence row"
  if should_record_video "${index}"; then
    record_video=1
    demo_mp4="${artifact_dir}/demo.mp4"
    quality_notes="actual Google mobile Chrome capture; video evidence row"
  fi
  local notes="actual Android Chrome Google Search; query_id=${scenario_id}; category=${category}; expected=${expected}; pipeline_experiment_mode=${PIPELINE_MODE}; stages=$(stage_mask_for_mode "${PIPELINE_MODE}"); method_id=${ANDROID_GOOGLE_METHOD_ID}; method_label=${ANDROID_GOOGLE_METHOD_LABEL}; repeat=${repeat}; video_policy=${VIDEO_POLICY}; video_recorded=${record_video}"

  mkdir -p "${artifact_dir}"
  set_pipeline_preferences "${PIPELINE_MODE}" "${ONLINE_ANALYSIS_INPUT}" 80
  ensure_accessibility_enabled
  clear_device_json_artifacts
  adb_cmd logcat -c || true

  if [[ "${record_video}" == "1" ]]; then
    RUN_ID="${run_id}" \
      ARTIFACT_DIR="${artifact_dir}" \
      FEATURE_SET="${PIPELINE_MODE}" \
      SCENARIO="google-mobile-${scenario_id}" \
      RUN_NOTES="${notes}" \
      EXPECTED_HARMFUL="${EXPECTED_HARMFUL:-}" \
      PPT_READY=no \
      QUALITY_NOTES="${quality_notes}" \
      RECORD_SECONDS="${RECORD_SECONDS}" \
      ADB="${ADB}" \
      JAVA_HOME="${JAVA_HOME}" \
      "${REPO_ROOT}/scripts/android-demo-evidence.sh" capture &
    local capture_pid=$!

    sleep 1
    drive_google_scene "${query}"
    wait "${capture_pid}"
  else
    echo "[INFO] google-mobile video skipped by policy=${VIDEO_POLICY} run_id=${run_id}"
    capture_logcat_only "${artifact_dir}" "${query}"
  fi

  RUN_ID="${run_id}" \
    ARTIFACT_DIR="${artifact_dir}" \
    FEATURE_SET="${PIPELINE_MODE}" \
    SCENARIO="google-mobile-${scenario_id}" \
    RUN_NOTES="${notes}" \
    EXPECTED_HARMFUL="${EXPECTED_HARMFUL:-}" \
    PPT_READY=no \
    QUALITY_NOTES="${quality_notes}" \
    JSON_PREFIXES="${JSON_PREFIXES:-generic}" \
    COLLECT_DEVICE_DUMPS="${COLLECT_DEVICE_DUMPS:-0}" \
    COLLECT_VIDEO="${record_video}" \
    ADB="${ADB}" \
    JAVA_HOME="${JAVA_HOME}" \
    "${REPO_ROOT}/scripts/android-demo-evidence.sh" collect

  if [[ "${record_video}" == "1" ]]; then
    validate_video "${artifact_dir}"
  fi

  append_scene_row \
    "${run_id}" \
    "${ANDROID_GOOGLE_METHOD_ID}" \
    "${ANDROID_GOOGLE_METHOD_LABEL}" \
    "${scenario_id}" \
    "${repeat}" \
    "${query}" \
    "${category}" \
    "${expected}" \
    "${record_video}" \
    "${artifact_dir}" \
    "${demo_mp4}" \
    "${artifact_dir}/screen.png" \
    "${artifact_dir}/summary.md"
}

write_google_report() {
  local report="${REPORT_DIR}/google-mobile-report.md"
  {
    echo "# Chungmaru Android Google Mobile Evidence"
    echo
    echo "- Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "- Batch ID: \`${BATCH_ID}\`"
    echo "- Pipeline mode: \`${PIPELINE_MODE}\`"
    echo "- Method: \`${ANDROID_GOOGLE_METHOD_ID}\` - ${ANDROID_GOOGLE_METHOD_LABEL}"
    echo "- Video policy: \`${VIDEO_POLICY}\`"
    echo "- Query set: \`${GOOGLE_QUERY_SET}\`"
    echo "- Batch root: \`${BATCH_ROOT}\`"
    echo
    echo "## Scene Artifacts"
    echo
    echo "| Run | Method | Query ID | Repeat | Category | Expected | Video recorded | Video | Screenshot | Summary |"
    echo "| --- | --- | --- | ---: | --- | --- | ---: | --- | --- | --- |"
    if [[ -f "${BATCH_ROOT}/google-mobile-scenes.csv" ]]; then
      python3 - "${BATCH_ROOT}/google-mobile-scenes.csv" <<'PY'
import csv
import sys

with open(sys.argv[1], encoding="utf-8", newline="") as handle:
    for row in csv.DictReader(handle):
        print(
            "| `{run_id}` | `{method_id}` | {scenario_id} | {repeat} | {category} | {expected} | {video_recorded} | `{video}` | `{screen}` | `{summary}` |".format(
                run_id=row.get("run_id", ""),
                method_id=row.get("method_id", ""),
                scenario_id=row.get("scenario_id", ""),
                repeat=row.get("repeat_index", ""),
                category=row.get("category", ""),
                expected=row.get("expected", ""),
                video_recorded=row.get("video_recorded", ""),
                video=row.get("demo_mp4", ""),
                screen=row.get("screen_png", ""),
                summary=row.get("summary_md", ""),
            )
        )
PY
    fi
    echo
    echo "## Latency Files"
    echo
    echo "- Raw runs: \`raw_runs.csv\`"
    echo "- Stage latency: \`stage_latency.csv\`"
    echo "- Stage latency summary: \`stage_latency_summary.csv\`"
    echo "- Summary by mode: \`summary_by_mode.csv\`"
    echo
    echo "Manual quality review is still required for exact missed, false, and stale mask counts."
  } > "${report}"
  echo "[OK] google_report=${report}"
}

aggregate_google() {
  AGGREGATE_MODES="${PIPELINE_MODE}" \
    BATCH_ID="${BATCH_ID}" \
    BATCH_ROOT="${BATCH_ROOT}" \
    REPORT_DIR="${REPORT_DIR}" \
    "${REPO_ROOT}/scripts/android-pipeline-benchmark.sh" aggregate
  write_google_report
}

run_all() {
  mkdir -p "${BATCH_ROOT}"
  {
    echo "run_id,method_id,method_label,scenario_id,repeat_index,query,category,expected,video_recorded,artifact_dir,demo_mp4,screen_png,summary_md"
  } > "${BATCH_ROOT}/google-mobile-scenes.csv"

  prepare_device

  local runs_per_scenario="${RUNS_PER_SCENARIO}"
  if [[ "${TARGET_ROWS}" =~ ^[1-9][0-9]*$ ]]; then
    local scenario_count
    scenario_count="$(scenario_lines | awk 'NF { count += 1 } END { print count + 0 }')"
    if [[ "${scenario_count}" -gt 0 ]]; then
      runs_per_scenario="$(( (TARGET_ROWS + scenario_count - 1) / scenario_count ))"
      echo "[INFO] target_rows=${TARGET_ROWS} scenario_count=${scenario_count} derived_runs_per_scenario=${runs_per_scenario}"
    fi
  fi

  local absolute_index=1
  local scenario_id query category expected repeat
  while IFS=$'\t' read -r scenario_id query category expected; do
    [[ -n "${scenario_id}" ]] || continue
    for repeat in $(seq "${START_INDEX}" "${runs_per_scenario}"); do
      echo "[INFO] google-mobile method=${ANDROID_GOOGLE_METHOD_ID} scenario=${scenario_id} repeat=${repeat}/${runs_per_scenario} query=${query}"
      run_scene "${scenario_id}" "${query}" "${category}" "${expected}" "${absolute_index}" "${repeat}"
      absolute_index=$((absolute_index + 1))
    done
  done < <(scenario_lines)

  aggregate_google
}

case "${1:-run}" in
  run)
    run_all
    ;;
  aggregate)
    aggregate_google
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
