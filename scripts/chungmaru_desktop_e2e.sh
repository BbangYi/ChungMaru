#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

DEFAULT_METRICS="candidate_collect_ms,parser_ms,pre_backend_ms,local_preflight_ms,risk_gate_mask_ms,risk_gate_event_age_ms,risk_gate_receive_to_mask_ms,fast_provisional_mask_ms,fast_provisional_event_age_ms,fast_provisional_build_ms,fast_provisional_overlay_ms,fast_provisional_receive_to_mask_ms,backend_roundtrip_ms,backend_internal_avg_ms,backend_model_avg_ms,decision_build_ms,mask_apply_ms,post_backend_to_mask_ms,first_mask_ms,total_to_mask_ms"
DEFAULT_CHROME_SCENARIOS="mixed,search-result,profanity,bypass"
DEFAULT_CHROME_BATCH_SIZES="4,8,16"
DEFAULT_ANDROID_MODES="s1_collect_only s12_collect_backend s1234_collect_backend_ocr_coord s12345_full"

usage() {
  cat <<'USAGE'
Usage: scripts/chungmaru_desktop_e2e.sh [doctor|chrome|android|android-google|all|loop|share-init|share-loop|share-status|remote-chrome|remote-android|remote-android-google|remote-all|remote-loop|pull|help]

Runs Chungmaru long-run evidence on a strong desktop host.

Local desktop commands:
  doctor   Check backend, Chrome executable, and adb readiness.
  chrome   Run Chrome extension E2E latency, then render CSV/summary/report.
  android  Run Android pipeline benchmark, then import raw_runs.csv into latency CSV.
  android-google  Run actual Android Chrome Google Search capture, video, logs, and stage CSV.
  all      Run chrome, then android.
  loop     Run repeated Chrome/Android cycles every INTERVAL_MINUTES.

Shared-folder autonomous commands:
  share-init    Create runner.env, control, state, and log paths in SHARE_ROOT.
  share-loop    Run repeated cycles by reading SHARE_ROOT/runner.env and control/command.txt.
  share-status  Print current shared-folder runner config, command, state, and heartbeat.

Remote commands from this Mac:
  remote-chrome   SSH into DESKTOP_SSH and run chrome.
  remote-android  SSH into DESKTOP_SSH and run android.
  remote-android-google  SSH into DESKTOP_SSH and run Android Google mobile capture.
  remote-all      SSH into DESKTOP_SSH and run all.
  remote-loop     SSH into DESKTOP_SSH and run loop.
  pull            Pull a remote result directory via rsync.

Common environment:
  RUN_ID=desktop-e2e-<timestamp>
  BACKEND_URL=http://127.0.0.1:8000
  RESULT_ROOT=evaluation/latency/results
  RESULT_DIR=<RESULT_ROOT>/<RUN_ID>

Loop environment:
  INTERVAL_MINUTES=30
  MAX_CYCLES=0              0 means unlimited
  RUN_ID_PREFIX=desktop-e2e
  RUN_CHROME=1
  RUN_ANDROID=auto          auto|1|0; auto skips when adb has no ready device
  RUN_ANDROID_GOOGLE=0      1 runs actual Android Chrome Google Search capture

Shared-folder environment:
  SHARE_ROOT=<mounted shared folder>   defaults to RESULT_ROOT or evaluation/latency/results
  SHARE_CONFIG=<path>/runner.env       optional override
  SHARE_COMMAND=<path>/command.txt     optional override
  SHARE_STATE=<path>/runner-state.txt  optional override
  SHARE_HEARTBEAT=<path>/heartbeat.txt optional override
  SHARE_LOG_DIR=<path>/desktop-loop-logs

Shared control commands:
  run          keep running cycles
  pause        keep the runner alive without starting new cycles
  stop         stop the runner
  once         run one cycle, set command to pause, then exit
  chrome-only  run Chrome only for the next cycles
  android-only run Android only for the next cycles
  android-google-only run Android Chrome Google Search capture only

Chrome environment:
  CHROME_PATH=/path/to/Chrome
  TARGET_DETECTIONS=10000
  MAX_RUNTIME_MINUTES=180
  SAMPLE_TIMEOUT=35
  CHROME_SCENARIOS="mixed,search-result,profanity,bypass"
  CHROME_BATCH_SIZES="4,8,16"
  DEBUGGING_PORT=9233
  PAGE_PORT=8765
  CLEAN_PROFILE=1
  APPEND=1

Android environment:
  ADB=/path/to/adb
  JAVA_HOME=/path/to/android-studio-jbr
  RUNS_PER_MODE=10
  RECORD_SECONDS=12
  MODES="s1_collect_only s12_collect_backend s1234_collect_backend_ocr_coord s12345_full"
  ANDROID_ANALYSIS_INPUT=127.0.0.1:8000
  ANDROID_IMPORT_BACKEND=http://127.0.0.1:8000
  ANDROID_BACKEND_WARMUP_REQUESTS=1
  ADB_REVERSE_BACKEND=1
  ADB_REVERSE_FIXTURE=1
  FIXTURE_ANALYSIS_HOST=127.0.0.1
  GOOGLE_QUERY_SET=moderation-core
  GOOGLE_RECORD_SECONDS=20
  GOOGLE_RUNS_PER_SCENARIO=1
  TARGET_ROWS=1000
  ANDROID_GOOGLE_METHOD_ID=current
  ANDROID_GOOGLE_METHOD_LABEL=current
  ANDROID_GOOGLE_VIDEO_POLICY=first
  ANDROID_GOOGLE_PIPELINE_MODE=s12345_full
  ANDROID_GOOGLE_METHOD_CSV=<RESULT_ROOT>/android-google-method-runs.csv
  ANDROID_GOOGLE_EVIDENCE_ROOT=<RESULT_ROOT>

Remote environment:
  DESKTOP_SSH=HOME@desktop-gmqfqtr
  DESKTOP_REPO="/Users/giminu0930/Documents/000 Project/Chungmaru"
  PULL_RUN_ID=<RUN_ID>

Examples:
  ssh HOME@desktop-gmqfqtr
  cd "/Users/giminu0930/Documents/000 Project/Chungmaru"
  BACKEND_URL=http://127.0.0.1:8000 scripts/chungmaru_desktop_e2e.sh chrome

  DESKTOP_REPO="/path/to/Chungmaru" scripts/chungmaru_desktop_e2e.sh remote-chrome

  RUNS_PER_MODE=3 RECORD_SECONDS=8 scripts/chungmaru_desktop_e2e.sh android
  GOOGLE_QUERY_SET=moderation-core scripts/chungmaru_desktop_e2e.sh android-google

  RESULT_ROOT="/Volumes/pi-ssd/chungmaru-e2e-results" \
    INTERVAL_MINUTES=30 RUN_ANDROID=auto scripts/chungmaru_desktop_e2e.sh loop

  SHARE_ROOT="/Volumes/pi-ssd/chungmaru-e2e-results" scripts/chungmaru_desktop_e2e.sh share-init
  SHARE_ROOT="/Volumes/pi-ssd/chungmaru-e2e-results" scripts/chungmaru_desktop_e2e.sh share-loop
USAGE
}

timestamp() {
  date +%Y%m%dT%H%M%S
}

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"
}

die() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 2
}

quote() {
  printf '%q' "$1"
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s\n' "${value}"
}

strip_outer_quotes() {
  local value="$1"
  if [[ ${#value} -ge 2 ]]; then
    case "${value}" in
      \"*\")
        if [[ "${value:${#value}-1:1}" == "\"" ]]; then
          value="${value:1:${#value}-2}"
        fi
        ;;
      \'*\')
        if [[ "${value:${#value}-1:1}" == "'" ]]; then
          value="${value:1:${#value}-2}"
        fi
        ;;
    esac
  fi
  printf '%s\n' "${value}"
}

is_nonnegative_int() {
  [[ "$1" =~ ^[0-9]+$ ]]
}

require_nonnegative_int() {
  local name="$1"
  local value="$2"
  is_nonnegative_int "${value}" || die "${name} must be a non-negative integer: ${value}"
}

share_root() {
  printf '%s\n' "${SHARE_ROOT:-${RESULT_ROOT:-evaluation/latency/results}}"
}

share_config_path() {
  local root
  root="$(share_root)"
  printf '%s\n' "${SHARE_CONFIG:-${root%/}/runner.env}"
}

share_command_path() {
  local root
  root="$(share_root)"
  printf '%s\n' "${SHARE_COMMAND:-${root%/}/control/command.txt}"
}

share_state_path() {
  local root
  root="$(share_root)"
  printf '%s\n' "${SHARE_STATE:-${root%/}/state/runner-state.txt}"
}

share_heartbeat_path() {
  local root
  root="$(share_root)"
  printf '%s\n' "${SHARE_HEARTBEAT:-${root%/}/state/heartbeat.txt}"
}

share_log_dir() {
  local root
  root="$(share_root)"
  printf '%s\n' "${SHARE_LOG_DIR:-${root%/}/desktop-loop-logs}"
}

ensure_share_dirs() {
  local root="$1"
  mkdir -p \
    "${root%/}/control" \
    "${root%/}/state" \
    "$(share_log_dir)"
}

is_share_config_key() {
  case "$1" in
    ADB|ADB_REVERSE_BACKEND|ADB_REVERSE_FIXTURE|ANDROID_ANALYSIS_INPUT|ANDROID_BACKEND_WARMUP_REQUESTS|ANDROID_GOOGLE_EVIDENCE_ROOT|ANDROID_GOOGLE_METHOD_CSV|ANDROID_GOOGLE_METHOD_ID|ANDROID_GOOGLE_METHOD_LABEL|ANDROID_GOOGLE_METHOD_MAP|ANDROID_GOOGLE_PIPELINE_MODE|ANDROID_GOOGLE_VIDEO_POLICY|ANDROID_IMPORT_BACKEND|APPEND|BACKEND_URL|BATCH_ROOT|CHROME_BATCH_SIZES|CHROME_PATH|CHROME_SCENARIOS|CLEAN_PROFILE|DEBUGGING_PORT|FIXTURE_ANALYSIS_HOST|GOOGLE_QUERY_SET|GOOGLE_RECORD_SECONDS|GOOGLE_RUNS_PER_SCENARIO|INTERVAL_MINUTES|JAVA_HOME|LATENCY_METRICS|MAX_CYCLES|MAX_RUNTIME_MINUTES|MODES|NO_VIDEO_SETTLE_SECONDS|PAGE_PORT|PROFILE_DIR|RECORD_SECONDS|RESET_CHROME_BEFORE_SCENE|RESULT_ROOT|RUNS_PER_MODE|RUN_ANDROID|RUN_ANDROID_GOOGLE|RUN_CHROME|RUN_ID_PREFIX|SAMPLE_TIMEOUT|TARGET_DETECTIONS|TARGET_ROWS|WAIT_AFTER_LOAD_SECONDS)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

load_share_config() {
  local config
  config="$(share_config_path)"
  [[ -f "${config}" ]] || return 0

  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(trim "${line}")" ]] && continue
    [[ "$(trim "${line}")" == \#* ]] && continue
    [[ "${line}" == *"="* ]] || continue

    key="$(trim "${line%%=*}")"
    value="$(trim "${line#*=}")"
    value="$(strip_outer_quotes "${value}")"

    if is_share_config_key "${key}"; then
      printf -v "${key}" '%s' "${value}"
      export "${key}"
    else
      log "ignored unsupported share config key=${key}"
    fi
  done < "${config}"
}

read_share_command() {
  local command_file
  command_file="$(share_command_path)"
  if [[ -f "${command_file}" ]]; then
    local command
    IFS= read -r command < "${command_file}" || true
    trim "${command:-run}"
  else
    printf 'run\n'
  fi
}

write_share_command() {
  local command="$1"
  local command_file
  command_file="$(share_command_path)"
  mkdir -p "$(dirname "${command_file}")"
  printf '%s\n' "${command}" > "${command_file}"
}

write_share_state() {
  local status="$1"
  local state_file
  state_file="$(share_state_path)"
  mkdir -p "$(dirname "${state_file}")"
  {
    echo "status=${status}"
    echo "updated_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "repo=${REPO_ROOT}"
    echo "share_root=$(share_root)"
    echo "result_root=${RESULT_ROOT:-$(share_root)}"
  } > "${state_file}"
}

write_share_heartbeat() {
  local heartbeat_file
  heartbeat_file="$(share_heartbeat_path)"
  mkdir -p "$(dirname "${heartbeat_file}")"
  date -u +%Y-%m-%dT%H:%M:%SZ > "${heartbeat_file}"
}

require_file() {
  local path="$1"
  [[ -e "${path}" ]] || die "Missing required path: ${path}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"
}

adb_ready() {
  local adb="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
  [[ -x "${adb}" ]] || return 1
  "${adb}" devices 2>/dev/null |
    awk 'NR > 1 && $2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'
}

detect_chrome_path() {
  if [[ -n "${CHROME_PATH:-}" ]]; then
    printf '%s\n' "${CHROME_PATH}"
    return
  fi

  local candidates=(
    "/private/tmp/chungmaru-chrome-for-testing/chrome/mac_arm-149.0.7827.54/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
    "/private/tmp/chungmaru-chrome-for-testing/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
    "/Applications/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    "/usr/bin/google-chrome"
    "/usr/bin/google-chrome-stable"
    "/usr/bin/chromium"
    "/usr/bin/chromium-browser"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return
    fi
  done

  die "Chrome executable not found. Set CHROME_PATH=/path/to/chrome."
}

check_backend() {
  local backend_url="${BACKEND_URL:-http://127.0.0.1:8000}"
  python3 - "${backend_url}" <<'PY'
import json
import sys
import urllib.request

backend = sys.argv[1].rstrip("/")
try:
    with urllib.request.urlopen(f"{backend}/health", timeout=5) as response:
        payload = json.loads(response.read().decode("utf-8"))
except Exception as error:  # noqa: BLE001 - shell diagnostic
    raise SystemExit(f"backend health failed: {backend}/health ({error})")

print(f"backend={backend} health={payload}")
PY
}

warm_android_backend() {
  local backend_url="${BACKEND_URL:-http://127.0.0.1:8000}"
  local warmup_requests="${ANDROID_BACKEND_WARMUP_REQUESTS:-1}"
  if [[ "${warmup_requests}" == "0" ]]; then
    return
  fi
  python3 - "${backend_url}" "${warmup_requests}" <<'PY'
import json
import sys
import time
import urllib.request

backend = sys.argv[1].rstrip("/")
count = max(1, int(sys.argv[2]))
payload = json.dumps(
    {
        "timestamp": int(time.time() * 1000),
        "sensitivity": 80,
        "comments": [
            {
                "commentText": "청마루 Android backend warmup ㅅㅂ 테스트",
                "author_id": "warmup",
                "boundsInScreen": {"left": 0, "top": 0, "right": 100, "bottom": 40},
            }
        ],
    },
    ensure_ascii=False,
).encode("utf-8")
for index in range(count):
    request = urllib.request.Request(
        f"{backend}/analyze_android",
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=30) as response:
        response.read()
    elapsed_ms = (time.perf_counter() - started) * 1000
    print(f"android_backend_warmup={index + 1}/{count} elapsed_ms={elapsed_ms:.1f}")
PY
}

result_dir_for_run() {
  local run_id="$1"
  local result_root="${RESULT_ROOT:-evaluation/latency/results}"
  if [[ -n "${RESULT_DIR:-}" ]]; then
    printf '%s\n' "${RESULT_DIR}"
  else
    printf '%s/%s\n' "${result_root%/}" "${run_id}"
  fi
}

result_dir_for_lane() {
  local lane="$1"
  local run_id="$2"
  local result_root="${RESULT_ROOT:-evaluation/latency/results}"
  if [[ -n "${RESULT_DIR:-}" ]]; then
    printf '%s\n' "${RESULT_DIR}"
    return
  fi
  case "${lane}" in
    chrome)
      if [[ -n "${CHROME_RESULT_DIR:-}" ]]; then
        printf '%s\n' "${CHROME_RESULT_DIR}"
      else
        result_dir_for_run "${run_id}"
      fi
      ;;
    android)
      if [[ -n "${ANDROID_RESULT_DIR:-}" ]]; then
        printf '%s\n' "${ANDROID_RESULT_DIR}"
      else
        result_dir_for_run "${run_id}"
      fi
      ;;
    *)
      result_dir_for_run "${run_id}"
      ;;
  esac
}

append_csv_file() {
  local source_csv="$1"
  local target_csv="$2"
  python3 - "${source_csv}" "${target_csv}" <<'PY'
import csv
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
if not source.exists() or source.stat().st_size == 0:
    raise SystemExit(f"source CSV missing or empty: {source}")

target.parent.mkdir(parents=True, exist_ok=True)
with source.open(encoding="utf-8", newline="") as handle:
    reader = csv.DictReader(handle)
    fieldnames = reader.fieldnames or []
    rows = list(reader)

write_header = not target.exists() or target.stat().st_size == 0
with target.open("a", encoding="utf-8", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
    if write_header:
        writer.writeheader()
    for row in rows:
        writer.writerow({field: row.get(field, "") for field in fieldnames})
PY
}

merge_latency_csvs() {
  local output_csv="$1"
  shift
  python3 - "${output_csv}" "$@" <<'PY'
import csv
import sys
from pathlib import Path

output = Path(sys.argv[1])
inputs = [Path(value) for value in sys.argv[2:]]
fieldnames = None
rows = []

for path in inputs:
    if not path.exists() or path.stat().st_size == 0:
        continue
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            continue
        if fieldnames is None:
            fieldnames = reader.fieldnames
        rows.extend(reader)

if fieldnames is None:
    raise SystemExit(0)

output.parent.mkdir(parents=True, exist_ok=True)
with output.open("w", encoding="utf-8", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
    writer.writeheader()
    for row in rows:
        writer.writerow({field: row.get(field, "") for field in fieldnames})
PY
}

aggregate_android_google_methods() {
  local result_root="${RESULT_ROOT:-evaluation/latency/results}"
  local evidence_root="${ANDROID_GOOGLE_EVIDENCE_ROOT:-${result_root}}"
  local output_csv="${ANDROID_GOOGLE_METHOD_CSV:-${result_root%/}/android-google-method-runs.csv}"
  local method_map="${ANDROID_GOOGLE_METHOD_MAP:-evaluation/latency/android-google-method-map.tsv}"

  if [[ ! -f "scripts/android_google_method_runs.py" ]]; then
    log "android-google method CSV skipped: missing scripts/android_google_method_runs.py"
    return 0
  fi

  python3 scripts/android_google_method_runs.py \
    --evidence-root "${evidence_root}" \
    --method-map "${method_map}" \
    --output "${output_csv}"
}

run_chrome() {
  require_command python3
  check_backend

  local run_id="${RUN_ID:-desktop-chrome-$(timestamp)}"
  local result_dir
  result_dir="$(result_dir_for_lane chrome "${run_id}")"
  local chrome_path
  chrome_path="$(detect_chrome_path)"

  local backend_url="${BACKEND_URL:-http://127.0.0.1:8000}"
  local output_jsonl="${result_dir}/chrome-last-stats.jsonl"
  local samples_csv="${result_dir}/chrome-e2e-samples.csv"
  local summary_csv="${result_dir}/chrome-e2e-summary.csv"
  local report_md="${result_dir}/chrome-e2e-report.md"
  local manifest="${result_dir}/run-manifest.txt"
  local profile_dir="${PROFILE_DIR:-/tmp/chungmaru-chrome-latency-profile-${run_id}}"
  local chrome_log="${CHROME_LOG:-${result_dir}/chrome.log}"
  local unified_csv="${LATENCY_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/chungmaru-latency-samples.csv}"
  local android_samples_csv="${ANDROID_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/android-e2e/android-e2e-samples.csv}"
  local debugging_port="${DEBUGGING_PORT:-9233}"
  local page_port="${PAGE_PORT:-8765}"

  mkdir -p "${result_dir}"

  local chrome_args=(
    python3 scripts/chungmaru_chrome_latency_smoke.py
    --chrome-path "${chrome_path}"
    --backend "${backend_url}"
    --output "${output_jsonl}"
    --run-id "${run_id}"
    --target-detections "${TARGET_DETECTIONS:-10000}"
    --max-runtime-minutes "${MAX_RUNTIME_MINUTES:-30}"
    --scenarios "${CHROME_SCENARIOS:-${DEFAULT_CHROME_SCENARIOS}}"
    --batch-sizes "${CHROME_BATCH_SIZES:-${DEFAULT_CHROME_BATCH_SIZES}}"
    --sample-timeout "${SAMPLE_TIMEOUT:-35}"
    --debugging-port "${debugging_port}"
    --page-port "${page_port}"
    --profile-dir "${profile_dir}"
    --chrome-log "${chrome_log}"
  )

  if [[ "${APPEND:-1}" == "1" ]]; then
    chrome_args+=(--append)
  fi
  if [[ "${CLEAN_PROFILE:-1}" == "1" ]]; then
    chrome_args+=(--clean-profile)
  fi

  log "chrome run_id=${run_id}"
  log "chrome result_dir=${result_dir}"
  "${chrome_args[@]}" > "${result_dir}/latest-run.log" 2>&1

  python3 scripts/chungmaru_latency_csv.py \
    --output "${samples_csv}" \
    --overwrite \
    extension-export \
    --input "${output_jsonl}" \
    --scenario chrome-fixture \
    --platform chrome \
    --run-id "${run_id}"

  python3 scripts/chungmaru_latency_csv.py aggregate \
    --input "${samples_csv}" \
    --output "${summary_csv}" \
    --metrics "${LATENCY_METRICS:-${DEFAULT_METRICS}}"

  python3 scripts/chungmaru_latency_csv.py report-md \
    --input "${samples_csv}" \
    --output "${report_md}" \
    --metrics "${LATENCY_METRICS:-${DEFAULT_METRICS}}"

  merge_latency_csvs "${unified_csv}" "${samples_csv}" "${android_samples_csv}"

  {
    echo "run_id=${run_id}"
    echo "lane=extension"
    echo "backend=${backend_url}"
    echo "chrome_path=${chrome_path}"
    echo "chrome_log=${chrome_log}"
    echo "jsonl=${output_jsonl}"
    echo "samples_csv=${samples_csv}"
    echo "summary_csv=${summary_csv}"
    echo "report_md=${report_md}"
    echo "unified_csv=${unified_csv}"
    echo "append=${APPEND:-1}"
    echo "target_detections=${TARGET_DETECTIONS:-10000}"
    echo "scenarios=${CHROME_SCENARIOS:-${DEFAULT_CHROME_SCENARIOS}}"
    echo "batch_sizes=${CHROME_BATCH_SIZES:-${DEFAULT_CHROME_BATCH_SIZES}}"
  } > "${manifest}"

  log "chrome complete"
  log "samples=${samples_csv}"
  log "summary=${summary_csv}"
  log "report=${report_md}"
}

run_android() {
  require_command python3
  check_backend
  warm_android_backend

  local run_id="${RUN_ID:-desktop-android-$(timestamp)}"
  local result_dir
  result_dir="$(result_dir_for_lane android "${run_id}")"
  local latest_dir="${result_dir}/latest"
  local batch_root="${BATCH_ROOT:-/private/tmp/chungmaru-android-pipeline-benchmark-${run_id}}"
  local samples_csv="${result_dir}/android-e2e-samples.csv"
  local summary_csv="${result_dir}/android-e2e-summary.csv"
  local report_md="${result_dir}/android-e2e-report.md"
  local raw_runs_csv="${result_dir}/raw_runs.csv"
  local manifest="${result_dir}/run-manifest.txt"
  local unified_csv="${LATENCY_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/chungmaru-latency-samples.csv}"
  local chrome_samples_csv="${CHROME_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/chrome-e2e/chrome-e2e-samples.csv}"
  local method_runs_csv="${ANDROID_GOOGLE_METHOD_CSV:-${RESULT_ROOT:-evaluation/latency/results}/android-google-method-runs.csv}"

  mkdir -p "${result_dir}"
  rm -rf "${latest_dir}"
  mkdir -p "${latest_dir}"

  log "android run_id=${run_id}"
  log "android result_dir=${result_dir}"
  log "android batch_root=${batch_root}"

  RUNS_PER_MODE="${RUNS_PER_MODE:-10}" \
    RECORD_SECONDS="${RECORD_SECONDS:-12}" \
    MODES="${MODES:-${DEFAULT_ANDROID_MODES}}" \
    AGGREGATE_MODES="${AGGREGATE_MODES:-${MODES:-${DEFAULT_ANDROID_MODES}}}" \
    BATCH_ID="${run_id}" \
    BATCH_ROOT="${batch_root}" \
    REPORT_DIR="${latest_dir}" \
    ONLINE_ANALYSIS_INPUT="${ANDROID_ANALYSIS_INPUT:-127.0.0.1:8000}" \
    FIXTURE_ANALYSIS_HOST="${FIXTURE_ANALYSIS_HOST:-127.0.0.1}" \
    ADB_REVERSE_BACKEND="${ADB_REVERSE_BACKEND:-1}" \
    ADB_REVERSE_FIXTURE="${ADB_REVERSE_FIXTURE:-1}" \
    ADB="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}" \
    JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    scripts/android-pipeline-benchmark.sh run

  require_file "${latest_dir}/raw_runs.csv"
  append_csv_file "${latest_dir}/raw_runs.csv" "${raw_runs_csv}"

  python3 scripts/chungmaru_latency_csv.py \
    --output "${samples_csv}" \
    android-pipeline-import \
    --input "${latest_dir}/raw_runs.csv" \
    --backend "${ANDROID_IMPORT_BACKEND:-http://127.0.0.1:8000}" \
    --platform android \
    --run-id "${run_id}"

  python3 scripts/chungmaru_latency_csv.py aggregate \
    --input "${samples_csv}" \
    --output "${summary_csv}" \
    --metrics "${LATENCY_METRICS:-${DEFAULT_METRICS}}"

  python3 scripts/chungmaru_latency_csv.py report-md \
    --input "${samples_csv}" \
    --output "${report_md}" \
    --metrics "${LATENCY_METRICS:-${DEFAULT_METRICS}}"

  merge_latency_csvs "${unified_csv}" "${chrome_samples_csv}" "${samples_csv}"
  ANDROID_GOOGLE_METHOD_CSV="${method_runs_csv}" aggregate_android_google_methods || \
    log "android-google method CSV aggregation failed: ${method_runs_csv}"

  {
    echo "run_id=${run_id}"
    echo "lane=android"
    echo "backend=${BACKEND_URL:-http://127.0.0.1:8000}"
    echo "android_analysis_input=${ANDROID_ANALYSIS_INPUT:-127.0.0.1:8000}"
    echo "android_import_backend=${ANDROID_IMPORT_BACKEND:-http://127.0.0.1:8000}"
    echo "android_backend_warmup_requests=${ANDROID_BACKEND_WARMUP_REQUESTS:-1}"
    echo "adb_reverse_backend=${ADB_REVERSE_BACKEND:-1}"
    echo "adb_reverse_fixture=${ADB_REVERSE_FIXTURE:-1}"
    echo "fixture_analysis_host=${FIXTURE_ANALYSIS_HOST:-127.0.0.1}"
    echo "batch_root=${batch_root}"
    echo "raw_runs=${raw_runs_csv}"
    echo "latest_raw_runs=${latest_dir}/raw_runs.csv"
    echo "samples_csv=${samples_csv}"
    echo "summary_csv=${summary_csv}"
    echo "report_md=${report_md}"
    echo "unified_csv=${unified_csv}"
    echo "runs_per_mode=${RUNS_PER_MODE:-10}"
    echo "record_seconds=${RECORD_SECONDS:-12}"
    echo "modes=${MODES:-${DEFAULT_ANDROID_MODES}}"
  } > "${manifest}"

  log "android complete"
  log "samples=${samples_csv}"
  log "summary=${summary_csv}"
  log "report=${report_md}"
}

run_android_google() {
  require_command python3
  check_backend
  warm_android_backend

  local run_id="${RUN_ID:-desktop-android-google-$(timestamp)}"
  local result_dir
  result_dir="$(result_dir_for_lane android "${run_id}")"
  local google_dir="${result_dir}/google-mobile"
  local batch_root="${BATCH_ROOT:-/private/tmp/chungmaru-android-google-mobile-${run_id}}"
  local samples_csv="${result_dir}/android-e2e-samples.csv"
  local summary_csv="${result_dir}/android-e2e-summary.csv"
  local report_md="${result_dir}/android-e2e-report.md"
  local raw_runs_csv="${result_dir}/raw_runs.csv"
  local manifest="${result_dir}/run-manifest.txt"
  local unified_csv="${LATENCY_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/chungmaru-latency-samples.csv}"
  local chrome_samples_csv="${CHROME_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/chrome-e2e/chrome-e2e-samples.csv}"

  mkdir -p "${result_dir}" "${google_dir}"

  log "android-google run_id=${run_id}"
  log "android-google result_dir=${result_dir}"
  log "android-google batch_root=${batch_root}"

  RUNS_PER_SCENARIO="${GOOGLE_RUNS_PER_SCENARIO:-1}" \
    TARGET_ROWS="${TARGET_ROWS:-0}" \
    RECORD_SECONDS="${GOOGLE_RECORD_SECONDS:-20}" \
    GOOGLE_QUERY_SET="${GOOGLE_QUERY_SET:-moderation-core}" \
    ANDROID_GOOGLE_METHOD_ID="${ANDROID_GOOGLE_METHOD_ID:-current}" \
    ANDROID_GOOGLE_METHOD_LABEL="${ANDROID_GOOGLE_METHOD_LABEL:-${ANDROID_GOOGLE_METHOD_ID:-current}}" \
    ANDROID_GOOGLE_VIDEO_POLICY="${ANDROID_GOOGLE_VIDEO_POLICY:-first}" \
    NO_VIDEO_SETTLE_SECONDS="${NO_VIDEO_SETTLE_SECONDS:-1}" \
    WAIT_AFTER_LOAD_SECONDS="${WAIT_AFTER_LOAD_SECONDS:-7}" \
    RESET_CHROME_BEFORE_SCENE="${RESET_CHROME_BEFORE_SCENE:-1}" \
    PIPELINE_MODE="${ANDROID_GOOGLE_PIPELINE_MODE:-s12345_full}" \
    BATCH_ID="${run_id}" \
    BATCH_ROOT="${batch_root}" \
    REPORT_DIR="${google_dir}" \
    ONLINE_ANALYSIS_INPUT="${ANDROID_ANALYSIS_INPUT:-127.0.0.1:8000}" \
    ADB_REVERSE_BACKEND="${ADB_REVERSE_BACKEND:-1}" \
    ADB="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}" \
    JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    scripts/android-google-mobile-evidence.sh run

  require_file "${google_dir}/raw_runs.csv"
  cp "${google_dir}/stage_latency.csv" "${result_dir}/stage_latency.csv"
  cp "${google_dir}/stage_latency_summary.csv" "${result_dir}/stage_latency_summary.csv"
  cp "${google_dir}/summary_by_mode.csv" "${result_dir}/summary_by_mode.csv"
  cp "${google_dir}/google-mobile-report.md" "${result_dir}/google-mobile-report.md"
  if [[ -f "${batch_root}/google-mobile-scenes.csv" ]]; then
    cp "${batch_root}/google-mobile-scenes.csv" "${google_dir}/google-mobile-scenes.csv"
    cp "${batch_root}/google-mobile-scenes.csv" "${result_dir}/google-mobile-scenes.csv"
  fi
  append_csv_file "${google_dir}/raw_runs.csv" "${raw_runs_csv}"

  python3 scripts/chungmaru_latency_csv.py \
    --output "${samples_csv}" \
    android-pipeline-import \
    --input "${google_dir}/raw_runs.csv" \
    --backend "${ANDROID_IMPORT_BACKEND:-http://127.0.0.1:8000}" \
    --platform android-google-mobile \
    --run-id "${run_id}"

  python3 scripts/chungmaru_latency_csv.py aggregate \
    --input "${samples_csv}" \
    --output "${summary_csv}" \
    --metrics "${LATENCY_METRICS:-${DEFAULT_METRICS}}"

  python3 scripts/chungmaru_latency_csv.py report-md \
    --input "${samples_csv}" \
    --output "${report_md}" \
    --metrics "${LATENCY_METRICS:-${DEFAULT_METRICS}}"

  merge_latency_csvs "${unified_csv}" "${chrome_samples_csv}" "${samples_csv}"

  {
    echo "run_id=${run_id}"
    echo "lane=android-google-mobile"
    echo "backend=${BACKEND_URL:-http://127.0.0.1:8000}"
    echo "android_analysis_input=${ANDROID_ANALYSIS_INPUT:-127.0.0.1:8000}"
    echo "android_import_backend=${ANDROID_IMPORT_BACKEND:-http://127.0.0.1:8000}"
    echo "android_backend_warmup_requests=${ANDROID_BACKEND_WARMUP_REQUESTS:-1}"
    echo "adb_reverse_backend=${ADB_REVERSE_BACKEND:-1}"
    echo "batch_root=${batch_root}"
    echo "google_report=${google_dir}/google-mobile-report.md"
    echo "google_scenes=${batch_root}/google-mobile-scenes.csv"
    echo "raw_runs=${raw_runs_csv}"
    echo "latest_raw_runs=${google_dir}/raw_runs.csv"
    echo "stage_latency=${google_dir}/stage_latency.csv"
    echo "stage_latency_summary=${google_dir}/stage_latency_summary.csv"
    echo "samples_csv=${samples_csv}"
    echo "summary_csv=${summary_csv}"
    echo "report_md=${report_md}"
    echo "unified_csv=${unified_csv}"
    echo "method_runs_csv=${method_runs_csv}"
    echo "method_runs_evidence_root=${ANDROID_GOOGLE_EVIDENCE_ROOT:-${RESULT_ROOT:-evaluation/latency/results}}"
    echo "google_query_set=${GOOGLE_QUERY_SET:-moderation-core}"
    echo "google_runs_per_scenario=${GOOGLE_RUNS_PER_SCENARIO:-1}"
    echo "target_rows=${TARGET_ROWS:-0}"
    echo "google_record_seconds=${GOOGLE_RECORD_SECONDS:-20}"
    echo "android_google_method_id=${ANDROID_GOOGLE_METHOD_ID:-current}"
    echo "android_google_method_label=${ANDROID_GOOGLE_METHOD_LABEL:-${ANDROID_GOOGLE_METHOD_ID:-current}}"
    echo "android_google_video_policy=${ANDROID_GOOGLE_VIDEO_POLICY:-first}"
    echo "pipeline_mode=${ANDROID_GOOGLE_PIPELINE_MODE:-s12345_full}"
  } > "${manifest}"

  log "android-google complete"
  log "samples=${samples_csv}"
  log "summary=${summary_csv}"
  log "report=${report_md}"
}

write_android_skip() {
  local run_id="$1"
  local result_dir
  result_dir="$(result_dir_for_lane android "${run_id}")"
  local raw_runs_csv="${result_dir}/raw_runs.csv"
  local latest_skip_csv="${result_dir}/latest-skip-raw_runs.csv"
  local samples_csv="${result_dir}/android-e2e-samples.csv"
  local unified_csv="${LATENCY_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/chungmaru-latency-samples.csv}"
  local chrome_samples_csv="${CHROME_SAMPLES_CSV:-${RESULT_ROOT:-evaluation/latency/results}/chrome-e2e/chrome-e2e-samples.csv}"
  mkdir -p "${result_dir}"
  {
    echo "run_id=${run_id}"
    echo "lane=android"
    echo "status=skipped"
    echo "reason=No adb device or emulator is ready."
    echo "next_action=Start an Android Studio emulator or connect a device, then rerun."
    echo "adb=${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
  } > "${result_dir}/run-manifest.txt"
  {
    echo "# Android E2E Skipped"
    echo
    echo "- Run ID: \`${run_id}\`"
    echo "- Reason: no adb device or emulator was ready."
    echo "- Next action: start an Android Studio emulator or connect a device."
  } > "${result_dir}/android-e2e-report.md"
  python3 - "${latest_skip_csv}" "${run_id}" "${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}" <<'PY'
import csv
import sys
from pathlib import Path

output = Path(sys.argv[1])
run_id = sys.argv[2]
adb = sys.argv[3]
fieldnames = [
    "run_id", "mode", "stages", "scenario", "device", "collect_ms", "node_collection_ms",
    "visual_roi_planning_ms", "screen_candidate_extraction_ms", "candidate_post_processing_ms",
    "candidate_parallel_wait_ms", "backend_api_ms", "backend_e2e_ms", "ocr_ms", "coord_ms",
    "display_ms", "node_count", "screen_candidates", "char_nodes", "char_range_candidates",
    "overlay_candidates", "overlay_rendered", "offensive", "filtered", "visual_roi_candidates",
    "visual_roi_selected", "visual_ocr_raw", "visual_ocr_selected", "observed_total_ms", "artifact_dir"
]
row = {field: "" for field in fieldnames}
row.update({
    "run_id": run_id,
    "mode": "skipped",
    "scenario": "android-no-device",
    "device": "none",
    "observed_total_ms": "-1",
    "artifact_dir": f"skip: no adb device or emulator; adb={adb}",
})
output.parent.mkdir(parents=True, exist_ok=True)
with output.open("w", encoding="utf-8", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
    writer.writeheader()
    writer.writerow(row)
PY
  append_csv_file "${latest_skip_csv}" "${raw_runs_csv}"
  python3 scripts/chungmaru_latency_csv.py \
    --output "${samples_csv}" \
    android-pipeline-import \
    --input "${latest_skip_csv}" \
    --backend "${ANDROID_IMPORT_BACKEND:-http://127.0.0.1:8000}" \
    --platform android \
    --run-id "${run_id}" || true
  merge_latency_csvs "${unified_csv}" "${chrome_samples_csv}" "${samples_csv}"
  log "android skipped result_dir=${result_dir}"
}

run_android_auto() {
  local run_id="$1"
  local mode="${RUN_ANDROID:-auto}"
  case "${mode}" in
    0|false|no|off)
      log "android disabled"
      return 0
      ;;
    1|true|yes|on)
      RUN_ID="${run_id}" run_android
      return
      ;;
    auto)
      if adb_ready; then
        RUN_ID="${run_id}" run_android
      else
        write_android_skip "${run_id}"
      fi
      return
      ;;
    *)
      die "Invalid RUN_ANDROID=${mode}; use auto, 1, or 0."
      ;;
  esac
}

run_cycle() {
  local cycle_id="$1"
  local cycle_dir
  cycle_dir="$(result_dir_for_run "${cycle_id}")"
  mkdir -p "${cycle_dir}"

  local cycle_status="${cycle_dir}/cycle-status.txt"
  {
    echo "cycle_id=${cycle_id}"
    echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "result_root=${RESULT_ROOT:-evaluation/latency/results}"
  } > "${cycle_status}"

  if [[ "${RUN_CHROME:-1}" == "1" ]]; then
    if RUN_ID="${cycle_id}-chrome" run_chrome; then
      echo "chrome=pass" >> "${cycle_status}"
    else
      echo "chrome=fail" >> "${cycle_status}"
    fi
  else
    echo "chrome=disabled" >> "${cycle_status}"
  fi

  if run_android_auto "${cycle_id}-android"; then
    echo "android=done_or_skipped" >> "${cycle_status}"
  else
    echo "android=fail" >> "${cycle_status}"
  fi

  if [[ "${RUN_ANDROID_GOOGLE:-0}" == "1" ]]; then
    if RUN_ID="${cycle_id}-android-google" run_android_google; then
      echo "android_google=pass" >> "${cycle_status}"
    else
      echo "android_google=fail" >> "${cycle_status}"
    fi
  else
    echo "android_google=disabled" >> "${cycle_status}"
  fi

  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "${cycle_status}"
  log "cycle complete id=${cycle_id}"
}

run_loop() {
  if [[ -n "${RESULT_DIR:-}" ]]; then
    die "RESULT_DIR is for a single run. Use RESULT_ROOT for loop output."
  fi

  require_nonnegative_int INTERVAL_MINUTES "${INTERVAL_MINUTES:-30}"
  require_nonnegative_int MAX_CYCLES "${MAX_CYCLES:-0}"
  local interval_seconds=$(( ${INTERVAL_MINUTES:-30} * 60 ))
  local max_cycles="${MAX_CYCLES:-0}"
  local cycle_index=1
  local log_root="${RESULT_ROOT:-evaluation/latency/results}/desktop-loop-logs"
  mkdir -p "${log_root}"

  log "loop interval_minutes=${INTERVAL_MINUTES:-30} max_cycles=${max_cycles} result_root=${RESULT_ROOT:-evaluation/latency/results}"
  while true; do
    local cycle_id="${RUN_ID_PREFIX:-desktop-e2e}-$(timestamp)"
    log "cycle start ${cycle_index} id=${cycle_id}"
    run_cycle "${cycle_id}" > "${log_root}/${cycle_id}.log" 2>&1 || true
    log "cycle log=${log_root}/${cycle_id}.log"

    if [[ "${max_cycles}" != "0" && "${cycle_index}" -ge "${max_cycles}" ]]; then
      log "loop finished after ${cycle_index} cycles"
      break
    fi

    cycle_index=$((cycle_index + 1))
    sleep "${interval_seconds}"
  done
}

run_share_init() {
  local root
  root="$(share_root)"
  ensure_share_dirs "${root}"

  local config
  config="$(share_config_path)"
  if [[ ! -f "${config}" ]]; then
    {
      echo "# Chungmaru shared-folder autonomous runner config"
      echo "# Edit this file from the shared folder. share-loop reloads it before each cycle."
      echo "INTERVAL_MINUTES=30"
      echo "MAX_CYCLES=0"
      echo "RUN_ID_PREFIX=desktop-e2e"
      echo "RUN_CHROME=1"
      echo "RUN_ANDROID=auto"
      echo "RUN_ANDROID_GOOGLE=0"
      echo "TARGET_DETECTIONS=10000"
      echo "MAX_RUNTIME_MINUTES=180"
      echo "SAMPLE_TIMEOUT=35"
      echo "BACKEND_URL=http://127.0.0.1:8000"
      echo "RESULT_ROOT=${root}"
      echo "CHROME_SCENARIOS=${DEFAULT_CHROME_SCENARIOS}"
      echo "CHROME_BATCH_SIZES=${DEFAULT_CHROME_BATCH_SIZES}"
      echo "RUNS_PER_MODE=10"
      echo "RECORD_SECONDS=12"
      echo "MODES=${DEFAULT_ANDROID_MODES}"
      echo "ANDROID_ANALYSIS_INPUT=127.0.0.1:8000"
      echo "ANDROID_IMPORT_BACKEND=http://127.0.0.1:8000"
      echo "ANDROID_BACKEND_WARMUP_REQUESTS=1"
      echo "ADB_REVERSE_BACKEND=1"
      echo "ADB_REVERSE_FIXTURE=1"
      echo "FIXTURE_ANALYSIS_HOST=127.0.0.1"
      echo "GOOGLE_QUERY_SET=all"
      echo "GOOGLE_RECORD_SECONDS=20"
      echo "GOOGLE_RUNS_PER_SCENARIO=1"
      echo "TARGET_ROWS=1000"
      echo "ANDROID_GOOGLE_METHOD_ID=shared_android_google_accumulating"
      echo "ANDROID_GOOGLE_METHOD_LABEL=Shared Android Google accumulating run"
      echo "ANDROID_GOOGLE_VIDEO_POLICY=first"
      echo "ANDROID_GOOGLE_PIPELINE_MODE=s12345_full"
      echo "WAIT_AFTER_LOAD_SECONDS=2"
      echo "NO_VIDEO_SETTLE_SECONDS=0"
      echo "RESET_CHROME_BEFORE_SCENE=0"
      echo "SLEEP_AFTER_RUN=1"
      echo "ANDROID_GOOGLE_EVIDENCE_ROOT=${root}"
      echo "ANDROID_GOOGLE_METHOD_CSV=${root%/}/android-google-method-runs.csv"
    } > "${config}"
  fi

  local command_file
  command_file="$(share_command_path)"
  if [[ ! -f "${command_file}" ]]; then
    write_share_command run
  fi

  write_share_state initialized
  write_share_heartbeat
  log "share_root=${root}"
  log "config=${config}"
  log "command=${command_file}"
  log "state=$(share_state_path)"
  log "heartbeat=$(share_heartbeat_path)"
  log "logs=$(share_log_dir)"
}

apply_share_command_mode() {
  local command="$1"
  case "${command}" in
    chrome-only)
      RUN_CHROME=1
      RUN_ANDROID=0
      RUN_ANDROID_GOOGLE=0
      export RUN_CHROME RUN_ANDROID RUN_ANDROID_GOOGLE
      ;;
    android-only)
      RUN_CHROME=0
      RUN_ANDROID=auto
      RUN_ANDROID_GOOGLE=0
      export RUN_CHROME RUN_ANDROID RUN_ANDROID_GOOGLE
      ;;
    android-google-only)
      RUN_CHROME=0
      RUN_ANDROID=0
      RUN_ANDROID_GOOGLE=1
      export RUN_CHROME RUN_ANDROID RUN_ANDROID_GOOGLE
      ;;
  esac
}

run_share_loop() {
  if [[ -n "${RESULT_DIR:-}" ]]; then
    die "RESULT_DIR is for a single run. Use RESULT_ROOT or SHARE_ROOT for share-loop output."
  fi

  local root
  root="$(share_root)"
  ensure_share_dirs "${root}"

  local cycle_index=1
  write_share_state starting
  log "share-loop root=${root}"
  log "share-loop config=$(share_config_path)"
  log "share-loop command=$(share_command_path)"

  while true; do
    load_share_config
    root="$(share_root)"
    ensure_share_dirs "${root}"

    RESULT_ROOT="${RESULT_ROOT:-${root}}"
    export RESULT_ROOT

    require_nonnegative_int INTERVAL_MINUTES "${INTERVAL_MINUTES:-30}"
    require_nonnegative_int MAX_CYCLES "${MAX_CYCLES:-0}"
    local interval_seconds=$(( ${INTERVAL_MINUTES:-30} * 60 ))
    local max_cycles="${MAX_CYCLES:-0}"
    local command
    command="$(read_share_command)"
    command="${command:-run}"

    write_share_heartbeat

    case "${command}" in
      run|once|chrome-only|android-only|android-google-only)
        ;;
      pause)
        write_share_state paused
        log "share-loop paused; next check in ${INTERVAL_MINUTES:-30} minutes"
        sleep "${interval_seconds}"
        continue
        ;;
      stop)
        write_share_state stopped
        log "share-loop stopped by command file"
        break
        ;;
      *)
        write_share_state "invalid-command:${command}"
        log "invalid share command=${command}; use run, pause, stop, once, chrome-only, android-only, or android-google-only"
        sleep "${interval_seconds}"
        continue
        ;;
    esac

    local original_run_chrome="${RUN_CHROME:-}"
    local original_run_android="${RUN_ANDROID:-}"
    local original_run_android_google="${RUN_ANDROID_GOOGLE:-}"
    apply_share_command_mode "${command}"

    local cycle_id="${RUN_ID_PREFIX:-desktop-e2e}-$(timestamp)"
    local log_root
    log_root="$(share_log_dir)"
    mkdir -p "${log_root}"

    write_share_state "running:${cycle_id}"
    log "share cycle start index=${cycle_index} id=${cycle_id} command=${command}"
    run_cycle "${cycle_id}" > "${log_root}/${cycle_id}.log" 2>&1 || true
    log "share cycle log=${log_root}/${cycle_id}.log"

    if [[ -n "${original_run_chrome}" ]]; then
      RUN_CHROME="${original_run_chrome}"
      export RUN_CHROME
    else
      unset RUN_CHROME || true
    fi
    if [[ -n "${original_run_android}" ]]; then
      RUN_ANDROID="${original_run_android}"
      export RUN_ANDROID
    else
      unset RUN_ANDROID || true
    fi
    if [[ -n "${original_run_android_google}" ]]; then
      RUN_ANDROID_GOOGLE="${original_run_android_google}"
      export RUN_ANDROID_GOOGLE
    else
      unset RUN_ANDROID_GOOGLE || true
    fi

    write_share_state "sleeping:${cycle_id}"

    if [[ "${command}" == "once" ]]; then
      write_share_command pause
      write_share_state "paused-after-once:${cycle_id}"
      log "share-loop once complete; command set to pause"
      break
    fi

    if [[ "${max_cycles}" != "0" && "${cycle_index}" -ge "${max_cycles}" ]]; then
      write_share_state "finished:${cycle_index}-cycles"
      log "share-loop finished after ${cycle_index} cycles"
      break
    fi

    cycle_index=$((cycle_index + 1))
    sleep "${interval_seconds}"
  done
}

run_share_status() {
  load_share_config
  local root
  root="$(share_root)"
  printf 'share_root=%s\n' "${root}"
  printf 'config=%s\n' "$(share_config_path)"
  printf 'command=%s\n' "$(share_command_path)"
  printf 'state=%s\n' "$(share_state_path)"
  printf 'heartbeat=%s\n' "$(share_heartbeat_path)"
  printf 'log_dir=%s\n' "$(share_log_dir)"
  printf 'result_root=%s\n' "${RESULT_ROOT:-${root}}"
  printf 'current_command=%s\n' "$(read_share_command)"
  if [[ -f "$(share_state_path)" ]]; then
    printf '\n[state]\n'
    sed -n '1,80p' "$(share_state_path)"
  fi
  if [[ -f "$(share_heartbeat_path)" ]]; then
    printf '\n[heartbeat]\n'
    sed -n '1,5p' "$(share_heartbeat_path)"
  fi
}

run_doctor() {
  require_command python3
  log "repo=${REPO_ROOT}"
  check_backend
  local chrome_path
  chrome_path="$(detect_chrome_path)"
  log "chrome=${chrome_path}"

  local adb="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
  if [[ -x "${adb}" ]]; then
    log "adb=${adb}"
    "${adb}" devices || true
  else
    log "adb not found or not executable: ${adb}"
  fi
}

remote_run() {
  local remote_command="$1"
  local desktop_ssh="${DESKTOP_SSH:-HOME@desktop-gmqfqtr}"
  local desktop_repo="${DESKTOP_REPO:-/Users/giminu0930/Documents/000 Project/Chungmaru}"
  local run_id="${RUN_ID:-desktop-${remote_command}-$(timestamp)}"
  local env_parts=(
    "RUN_ID=$(quote "${run_id}")"
    "BACKEND_URL=$(quote "${BACKEND_URL:-http://127.0.0.1:8000}")"
    "TARGET_DETECTIONS=$(quote "${TARGET_DETECTIONS:-10000}")"
    "MAX_RUNTIME_MINUTES=$(quote "${MAX_RUNTIME_MINUTES:-30}")"
    "SAMPLE_TIMEOUT=$(quote "${SAMPLE_TIMEOUT:-35}")"
    "CHROME_SCENARIOS=$(quote "${CHROME_SCENARIOS:-${DEFAULT_CHROME_SCENARIOS}}")"
    "CHROME_BATCH_SIZES=$(quote "${CHROME_BATCH_SIZES:-${DEFAULT_CHROME_BATCH_SIZES}}")"
    "RUNS_PER_MODE=$(quote "${RUNS_PER_MODE:-10}")"
    "RECORD_SECONDS=$(quote "${RECORD_SECONDS:-12}")"
    "INTERVAL_MINUTES=$(quote "${INTERVAL_MINUTES:-30}")"
    "MAX_CYCLES=$(quote "${MAX_CYCLES:-0}")"
    "RUN_ID_PREFIX=$(quote "${RUN_ID_PREFIX:-desktop-e2e}")"
    "RUN_CHROME=$(quote "${RUN_CHROME:-1}")"
    "RUN_ANDROID=$(quote "${RUN_ANDROID:-auto}")"
    "RUN_ANDROID_GOOGLE=$(quote "${RUN_ANDROID_GOOGLE:-0}")"
    "MODES=$(quote "${MODES:-${DEFAULT_ANDROID_MODES}}")"
    "ANDROID_ANALYSIS_INPUT=$(quote "${ANDROID_ANALYSIS_INPUT:-127.0.0.1:8000}")"
    "ANDROID_IMPORT_BACKEND=$(quote "${ANDROID_IMPORT_BACKEND:-http://127.0.0.1:8000}")"
    "ANDROID_BACKEND_WARMUP_REQUESTS=$(quote "${ANDROID_BACKEND_WARMUP_REQUESTS:-1}")"
    "ADB_REVERSE_BACKEND=$(quote "${ADB_REVERSE_BACKEND:-1}")"
    "ADB_REVERSE_FIXTURE=$(quote "${ADB_REVERSE_FIXTURE:-1}")"
    "FIXTURE_ANALYSIS_HOST=$(quote "${FIXTURE_ANALYSIS_HOST:-127.0.0.1}")"
    "GOOGLE_QUERY_SET=$(quote "${GOOGLE_QUERY_SET:-moderation-core}")"
    "GOOGLE_RECORD_SECONDS=$(quote "${GOOGLE_RECORD_SECONDS:-20}")"
    "GOOGLE_RUNS_PER_SCENARIO=$(quote "${GOOGLE_RUNS_PER_SCENARIO:-1}")"
    "TARGET_ROWS=$(quote "${TARGET_ROWS:-0}")"
    "WAIT_AFTER_LOAD_SECONDS=$(quote "${WAIT_AFTER_LOAD_SECONDS:-7}")"
    "NO_VIDEO_SETTLE_SECONDS=$(quote "${NO_VIDEO_SETTLE_SECONDS:-1}")"
    "RESET_CHROME_BEFORE_SCENE=$(quote "${RESET_CHROME_BEFORE_SCENE:-1}")"
    "ANDROID_GOOGLE_METHOD_ID=$(quote "${ANDROID_GOOGLE_METHOD_ID:-current}")"
    "ANDROID_GOOGLE_METHOD_LABEL=$(quote "${ANDROID_GOOGLE_METHOD_LABEL:-${ANDROID_GOOGLE_METHOD_ID:-current}}")"
    "ANDROID_GOOGLE_VIDEO_POLICY=$(quote "${ANDROID_GOOGLE_VIDEO_POLICY:-first}")"
    "ANDROID_GOOGLE_PIPELINE_MODE=$(quote "${ANDROID_GOOGLE_PIPELINE_MODE:-s12345_full}")"
  )

  local optional_key
  for optional_key in CHROME_PATH RESULT_ROOT RESULT_DIR PROFILE_DIR DEBUGGING_PORT PAGE_PORT ADB JAVA_HOME BATCH_ROOT LATENCY_METRICS GOOGLE_SCENARIOS_TSV ANDROID_GOOGLE_EVIDENCE_ROOT ANDROID_GOOGLE_METHOD_CSV ANDROID_GOOGLE_METHOD_MAP NO_VIDEO_SETTLE_SECONDS; do
    if [[ -n "${!optional_key:-}" ]]; then
      env_parts+=("${optional_key}=$(quote "${!optional_key}")")
    fi
  done

  local cmd="cd $(quote "${desktop_repo}") && ${env_parts[*]} scripts/chungmaru_desktop_e2e.sh $(quote "${remote_command}")"
  log "ssh ${desktop_ssh} ${cmd}"
  ssh "${desktop_ssh}" "${cmd}"
}

pull_remote_results() {
  require_command rsync
  local desktop_ssh="${DESKTOP_SSH:-HOME@desktop-gmqfqtr}"
  local desktop_repo="${DESKTOP_REPO:-/Users/giminu0930/Documents/000 Project/Chungmaru}"
  local pull_run_id="${PULL_RUN_ID:-${RUN_ID:-}}"
  [[ -n "${pull_run_id}" ]] || die "Set PULL_RUN_ID=<remote run id> or RUN_ID=<remote run id>."

  local remote_path="${desktop_repo}/evaluation/latency/results/${pull_run_id}/"
  local local_path="${REPO_ROOT}/evaluation/latency/results/${pull_run_id}/"
  mkdir -p "${local_path}"
  rsync -az "${desktop_ssh}:$(quote "${remote_path}")" "${local_path}"
  log "pulled=${local_path}"
}

main() {
  cd "${REPO_ROOT}"
  case "${1:-help}" in
    doctor)
      run_doctor
      ;;
    chrome)
      run_chrome
      ;;
    android)
      run_android
      ;;
    android-google)
      run_android_google
      ;;
    all)
      run_chrome
      run_android
      ;;
    loop)
      run_loop
      ;;
    share-init)
      run_share_init
      ;;
    share-loop)
      run_share_loop
      ;;
    share-status)
      run_share_status
      ;;
    remote-chrome)
      remote_run chrome
      ;;
    remote-android)
      remote_run android
      ;;
    remote-android-google)
      remote_run android-google
      ;;
    remote-all)
      remote_run all
      ;;
    remote-loop)
      remote_run loop
      ;;
    pull)
      pull_remote_results
      ;;
    -h|--help|help)
      usage
      ;;
    *)
      die "Unknown command: $1"
      ;;
  esac
}

main "$@"
