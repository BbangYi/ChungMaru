#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ADB="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
PACKAGE_NAME="${PACKAGE_NAME:-com.capstone.design}"
BATCH_ID="${BATCH_ID:-$(date +%Y%m%dT%H%M%S)}"
BATCH_ROOT="${BATCH_ROOT:-/private/tmp/chungmaru-android-pipeline-benchmark-${BATCH_ID}}"
FIXTURE_DIR="${FIXTURE_DIR:-${BATCH_ROOT}/fixture}"
FIXTURE_PORT="${FIXTURE_PORT:-4191}"
RUNS_PER_MODE="${RUNS_PER_MODE:-10}"
START_INDEX="${START_INDEX:-1}"
RECORD_SECONDS="${RECORD_SECONDS:-12}"
REPORT_DIR="${REPORT_DIR:-${REPO_ROOT}/docs/evidence/android-pipeline-benchmark}"
ONLINE_ANALYSIS_INPUT="${ONLINE_ANALYSIS_INPUT:-127.0.0.1:8000}"
FIXTURE_ANALYSIS_HOST="${FIXTURE_ANALYSIS_HOST:-127.0.0.1}"
ADB_REVERSE_BACKEND="${ADB_REVERSE_BACKEND:-1}"
ADB_REVERSE_FIXTURE="${ADB_REVERSE_FIXTURE:-1}"
DEFAULT_MODES="s1_collect_only s2_backend_only s3_ocr_roi_only s4_coord_only s5_overlay_only s12_collect_backend s123_collect_backend_ocr s1234_collect_backend_ocr_coord s12345_full"
MODES="${MODES:-${DEFAULT_MODES}}"
AGGREGATE_MODES="${AGGREGATE_MODES:-${DEFAULT_MODES}}"
CHROME_PACKAGE="${CHROME_PACKAGE:-com.android.chrome}"

adb_cmd() {
  "${ADB}" "$@"
}

adb_device_cmd() {
  MSYS2_ARG_CONV_EXCL="*" MSYS_NO_PATHCONV=1 "${ADB}" "$@"
}

usage() {
  cat <<'USAGE'
Usage: scripts/android-pipeline-benchmark.sh [run|fixture|aggregate|help]

Runs the Android masking pipeline benchmark with real runtime stage toggles.

Default matrix:
  s1_collect_only
  s2_backend_only
  s3_ocr_roi_only
  s4_coord_only
  s5_overlay_only
  s12_collect_backend
  s123_collect_backend_ocr
  s1234_collect_backend_ocr_coord
  s12345_full

Environment:
  RUNS_PER_MODE=10
  START_INDEX=1
  RECORD_SECONDS=12
  ONLINE_ANALYSIS_INPUT=127.0.0.1:8000
  FIXTURE_ANALYSIS_HOST=127.0.0.1
  ADB_REVERSE_BACKEND=1  Reverse device/emulator tcp:8000 to the host backend.
  ADB_REVERSE_FIXTURE=1  Reverse device/emulator fixture port to the host fixture.
  MODES="s1_collect_only ... s12345_full"
  AGGREGATE_MODES="s1_collect_only ... s12345_full"
  BATCH_ROOT=/private/tmp/chungmaru-android-pipeline-benchmark-<timestamp>
  REPORT_DIR=docs/evidence/android-pipeline-benchmark

Outputs:
  raw_runs.csv
  stage_latency.csv
  stage_latency_summary.csv
  summary_by_mode.csv
  stage_delta.csv
  ppt_table.md

Notes:
  This script writes pipeline_experiment_mode into youtube_parser_settings.xml.
  The app build must include PipelineExperimentStore/YoutubeAccessibilityService
  stage gating for the numbers to be meaningful.
USAGE
}

require_device() {
  if ! adb_cmd devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'; then
    echo "[ERROR] No adb device is ready. Start an emulator first."
    exit 2
  fi
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
    opt_base_all_nodes_backend) echo "1+2" ;;
    opt_base_fullscreen_ocr) echo "1+2+3" ;;
    opt_base_full_box_overlay) echo "1+2+5" ;;
    *) echo "unknown" ;;
  esac
}

scenario_for_mode() {
  case "$1" in
    s1_collect_only) echo "pipeline-01-collect" ;;
    s2_backend_only) echo "pipeline-02-backend" ;;
    s3_ocr_roi_only) echo "pipeline-03-ocr-roi" ;;
    s4_coord_only) echo "pipeline-04-coordinate" ;;
    s5_overlay_only) echo "pipeline-05-overlay-gate" ;;
    s12_collect_backend) echo "pipeline-01-02" ;;
    s123_collect_backend_ocr) echo "pipeline-01-02-03" ;;
    s1234_collect_backend_ocr_coord) echo "pipeline-01-02-03-04" ;;
    s12345_full) echo "pipeline-01-02-03-04-05" ;;
    opt_base_all_nodes_backend) echo "optimization-baseline-all-nodes-backend" ;;
    opt_base_fullscreen_ocr) echo "optimization-baseline-fullscreen-ocr" ;;
    opt_base_full_box_overlay) echo "optimization-baseline-full-box-overlay" ;;
    *) echo "pipeline-custom" ;;
  esac
}

write_fixture() {
  mkdir -p "${FIXTURE_DIR}"
  cat > "${FIXTURE_DIR}/index.html" <<'HTML'
<!doctype html>
<html lang="ko" translate="no" class="notranslate">
<head>
  <meta charset="utf-8">
  <meta name="google" content="notranslate">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Chungmaru Android Pipeline Fixture</title>
  <style>
    :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
    body { margin: 0; padding: 24px; background: #f6f7f8; color: #111; }
    main { display: grid; gap: 16px; max-width: 760px; margin: 0 auto; }
    section { background: white; border: 1px solid #d8dee4; border-radius: 12px; padding: 18px; }
    h1 { font-size: 28px; margin: 0 0 10px; }
    h2 { font-size: 20px; margin: 0 0 8px; }
    p, li { font-size: 18px; line-height: 1.5; }
    .safe { color: #1f6f43; }
    .harmful { color: #a01818; font-weight: 700; }
    .wide { min-height: 120px; }
    canvas { width: 100%; height: 180px; border: 1px dashed #9aa4b2; border-radius: 8px; background: #fff7ed; }
    .spacer { height: 420px; }
    @media (prefers-color-scheme: dark) {
      body { background: #111; color: #eee; }
      section { background: #1b1f23; border-color: #30363d; }
      canvas { background: #251a0f; }
    }
  </style>
</head>
<body translate="no" class="notranslate">
<main>
  <section>
    <h1>청마루 Android 파이프라인 테스트</h1>
    <p class="safe">정상 문장입니다. 오탐이 있으면 false mask로 기록합니다.</p>
    <p class="harmful">접근성 후보 욕설 A: ㅅㅂ 같은 표현이 포함된 댓글입니다.</p>
    <p class="harmful">접근성 후보 욕설 B: 진짜 ㅈ같네 라는 공격적 표현입니다.</p>
  </section>
  <section class="wide">
    <h2>후보 수집 영역</h2>
    <ul>
      <li class="safe">정상 댓글: 오늘 자료 정리가 잘 됐습니다.</li>
      <li class="harmful">문제 댓글: 이건 개같아서 보기 힘들다는 표현입니다.</li>
      <li class="safe">정상 댓글: 단계별 latency를 반복 측정합니다.</li>
    </ul>
  </section>
  <section>
    <h2>OCR ROI 영역</h2>
    <canvas id="ocrCanvas" width="680" height="220" aria-label="이미지 안의 테스트 텍스트"></canvas>
  </section>
  <div class="spacer"></div>
  <section>
    <h2>스크롤 stale mask 확인</h2>
    <p class="harmful">스크롤 후 남으면 안 되는 욕설 후보: ㅅㅂ 왜 이렇게 느려.</p>
    <p class="safe">마지막 안전 문장입니다. 여기에 마스크가 뜨면 false mask입니다.</p>
  </section>
</main>
<script>
  const canvas = document.getElementById("ocrCanvas");
  const ctx = canvas.getContext("2d");
  ctx.fillStyle = "#fff7ed";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "#111827";
  ctx.font = "34px sans-serif";
  ctx.fillText("이미지 텍스트 OCR 후보", 28, 62);
  ctx.fillStyle = "#9f1239";
  ctx.font = "32px sans-serif";
  ctx.fillText("캔버스 안 욕설 테스트: ㅅㅂ 너무 화남", 28, 126);
  ctx.fillStyle = "#166534";
  ctx.font = "26px sans-serif";
  ctx.fillText("정상 이미지 문장도 함께 배치", 28, 176);
</script>
</body>
</html>
HTML
}

start_fixture_server() {
  write_fixture
  python3 -m http.server "${FIXTURE_PORT}" --directory "${FIXTURE_DIR}" > "${BATCH_ROOT}/fixture-server.log" 2>&1 &
  FIXTURE_SERVER_PID=$!
  sleep 1
  echo "[OK] fixture=http://127.0.0.1:${FIXTURE_PORT}/index.html pid=${FIXTURE_SERVER_PID}"
}

stop_fixture_server() {
  if [[ -n "${FIXTURE_SERVER_PID:-}" ]]; then
    kill "${FIXTURE_SERVER_PID}" 2>/dev/null || true
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

ensure_accessibility_enabled() {
  local service="${PACKAGE_NAME}/com.capstone.design.youtubeparser.YoutubeAccessibilityService"
  local current
  adb_cmd shell settings put secure enabled_accessibility_services "${service}"
  adb_cmd shell settings put secure accessibility_enabled 1
  sleep 0.2
  current="$(adb_cmd shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')"
  if [[ "${current}" != *"${service}"* ]]; then
    adb_cmd shell settings put secure enabled_accessibility_services "${service}"
    adb_cmd shell settings put secure accessibility_enabled 1
    sleep 0.2
    current="$(adb_cmd shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')"
  fi
  if [[ "${current}" != *"${service}"* ]]; then
    echo "[WARN] Accessibility service not confirmed after enable attempt: current=${current:-empty}"
  fi
}

clear_device_json_artifacts() {
  local base="/storage/emulated/0/Android/data/${PACKAGE_NAME}/files"
  adb_device_cmd shell rm -rf \
    "${base}/upload_cache" \
    "${base}/parse_results" \
    "${base}/analysis_results"
}

configure_adb_reverse() {
  if [[ "${ADB_REVERSE_BACKEND}" == "1" ]]; then
    adb_cmd reverse tcp:8000 tcp:8000 || true
  fi
  if [[ "${ADB_REVERSE_FIXTURE}" == "1" ]]; then
    adb_cmd reverse "tcp:${FIXTURE_PORT}" "tcp:${FIXTURE_PORT}" || true
  fi
}

prepare_emulator() {
  require_device
  configure_adb_reverse
  ensure_accessibility_enabled
  ADB_REVERSE=1 "${REPO_ROOT}/scripts/android-demo-evidence.sh" prepare
  configure_adb_reverse
  ensure_accessibility_enabled
  adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

configure_mode() {
  local mode="$1"
  set_pipeline_preferences "${mode}" "${ONLINE_ANALYSIS_INPUT}" 80
  adb_cmd shell am force-stop "${PACKAGE_NAME}" >/dev/null 2>&1 || true
  ensure_accessibility_enabled
  sleep 1
}

open_fixture_in_chrome() {
  local mode="$1"
  local index="$2"
  local url="http://${FIXTURE_ANALYSIS_HOST}:${FIXTURE_PORT}/index.html?case=${mode}-run-${index}"
  adb_cmd shell am force-stop "${CHROME_PACKAGE}" >/dev/null 2>&1 || true
  adb_cmd shell am start -a android.intent.action.VIEW -d "${url}" -p "${CHROME_PACKAGE}" >/dev/null
}

drive_fixture() {
  local mode="$1"
  local index="$2"
  open_fixture_in_chrome "${mode}" "${index}"
  sleep 3
  adb_cmd shell input swipe 520 1780 520 640 500 >/dev/null
  sleep 1
  adb_cmd shell input swipe 520 640 520 1780 450 >/dev/null
  sleep 1
  adb_cmd shell input swipe 520 1780 520 700 500 >/dev/null
  sleep 1
}

run_capture() {
  local mode="$1"
  local index="$2"
  local scenario run_id artifact_dir notes
  scenario="$(scenario_for_mode "${mode}")"
  run_id="${BATCH_ID}-${mode}-${index}"
  artifact_dir="${BATCH_ROOT}/runs/${run_id}"
  notes="automated emulator pipeline benchmark; pipeline_experiment_mode=${mode}; stages=$(stage_mask_for_mode "${mode}")"
  mkdir -p "${artifact_dir}"

  configure_mode "${mode}"
  clear_device_json_artifacts
  adb_cmd logcat -c

  RUN_ID="${run_id}" \
    ARTIFACT_DIR="${artifact_dir}" \
    FEATURE_SET="${mode}" \
    SCENARIO="${scenario}" \
    RUN_NOTES="${notes}" \
    RECORD_SECONDS="${RECORD_SECONDS}" \
    "${REPO_ROOT}/scripts/android-demo-evidence.sh" capture &
  local capture_pid=$!

  sleep 1
  drive_fixture "${mode}" "${index}"
  wait "${capture_pid}"

  RUN_ID="${run_id}" \
    ARTIFACT_DIR="${artifact_dir}" \
    FEATURE_SET="${mode}" \
    SCENARIO="${scenario}" \
    RUN_NOTES="${notes}" \
    EXPECTED_HARMFUL=5 \
    PPT_READY=no \
    QUALITY_NOTES="automated pipeline benchmark; manual video review still required for mask quality" \
    "${REPO_ROOT}/scripts/android-demo-evidence.sh" collect
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

manifest_value_from_dir() {
  local dir="$1"
  local key="$2"
  local file="${dir}/manifest.txt"
  [[ -f "${file}" ]] || return 0
  sed -n -E "s/^${key}=(.*)$/\\1/p" "${file}" | tail -n 1
}

log_max_value_from_dir() {
  local dir="$1"
  local pattern="$2"
  local file="${dir}/logs/mask-logcat.txt"
  [[ -f "${file}" ]] || return 0
  sed -n -E "s/.*${pattern}.*/\\1/p" "${file}" | sort -n | tail -n 1
}

log_elapsed_ms_from_dir() {
  local dir="$1"
  local start_pattern="$2"
  local end_pattern="$3"
  local file="${dir}/logs/mask-logcat.txt"
  [[ -f "${file}" ]] || return 0
  awk -v start_pattern="${start_pattern}" -v end_pattern="${end_pattern}" '
    BEGIN {
      start_ms = -1
      max_elapsed_ms = -1
    }
    function timestamp_ms(line, hh, mm, ss, ms) {
      if (length(line) < 18) {
        return -1
      }
      hh = substr(line, 7, 2)
      mm = substr(line, 10, 2)
      ss = substr(line, 13, 2)
      ms = substr(line, 16, 3)
      if (hh !~ /^[0-9][0-9]$/ || mm !~ /^[0-9][0-9]$/ || ss !~ /^[0-9][0-9]$/ || ms !~ /^[0-9][0-9][0-9]$/) {
        return -1
      }
      return (((hh * 60) + mm) * 60 + ss) * 1000 + ms
    }
    $0 ~ start_pattern {
      start_ms = timestamp_ms($0)
      next
    }
    $0 ~ end_pattern && start_ms >= 0 {
      end_ms = timestamp_ms($0)
      elapsed_ms = end_ms - start_ms
      if (elapsed_ms >= 0 && elapsed_ms > max_elapsed_ms) {
        max_elapsed_ms = elapsed_ms
      }
      start_ms = -1
    }
    END {
      if (max_elapsed_ms > 0) print max_elapsed_ms
    }
  ' "${file}"
}

first_nonempty() {
  local value
  for value in "$@"; do
    if [[ -n "${value}" ]]; then
      echo "${value}"
      return
    fi
  done
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

observed_total_ms() {
  awk -v collect="${1:-}" -v backend_e2e="${2:-}" -v ocr="${3:-}" -v coord="${4:-}" -v display="${5:-}" '
    function clean(value) {
      if (value == "" || value == "n/a") return -1
      return value + 0
    }
    BEGIN {
      values[1] = clean(collect)
      values[2] = clean(backend_e2e)
      values[3] = clean(ocr)
      values[4] = clean(coord)
      values[5] = clean(display)
      max = -1
      for (i = 1; i <= 5; i++) if (values[i] > max) max = values[i]
      if (max >= 0) print max
      else print ""
    }
  '
}

write_raw_csv() {
  local raw_csv="$1"
  shift
  local dirs=("$@")
  {
    echo "run_id,mode,stages,scenario,device,collect_ms,node_collection_ms,visual_roi_planning_ms,screen_candidate_extraction_ms,candidate_post_processing_ms,candidate_parallel_wait_ms,backend_api_ms,backend_e2e_ms,ocr_ms,coord_ms,display_ms,node_count,screen_candidates,char_nodes,char_range_candidates,overlay_candidates,overlay_rendered,offensive,filtered,visual_roi_candidates,visual_roi_selected,visual_ocr_raw,visual_ocr_selected,observed_total_ms,risk_gate_mask_ms,risk_gate_event_age_ms,risk_gate_receive_to_mask_ms,fast_provisional_mask_ms,fast_provisional_event_age_ms,fast_provisional_build_ms,fast_provisional_overlay_ms,fast_provisional_receive_to_mask_ms,artifact_dir"
    local dir
    for dir in "${dirs[@]}"; do
      local run_id mode stages scenario device collect_ms node_collection_ms visual_roi_planning_ms
      local screen_candidate_extraction_ms candidate_post_processing_ms parallel_ms backend_api_ms backend_e2e_ms
      local ocr_ms coord_ms display_ms node_count screen_candidates char_nodes char_range_candidates
      local overlay_candidates overlay_rendered offensive filtered roi_candidates roi_selected ocr_raw ocr_selected total_ms
      local risk_gate_mask_ms risk_gate_event_age_ms risk_gate_receive_to_mask_ms
      local fast_provisional_mask_ms fast_provisional_event_age_ms fast_provisional_build_ms
      local fast_provisional_overlay_ms fast_provisional_receive_to_mask_ms
      run_id="$(manifest_value_from_dir "${dir}" run_id)"
      mode="$(first_nonempty "$(xml_value_from_dir "${dir}" analysis_diagnostics_experiment_mode)" "$(manifest_value_from_dir "${dir}" feature_set)")"
      stages="$(first_nonempty "$(xml_value_from_dir "${dir}" analysis_diagnostics_experiment_stages)" "$(stage_mask_for_mode "${mode}")")"
      scenario="$(manifest_value_from_dir "${dir}" scenario)"
      device="$(manifest_value_from_dir "${dir}" device_model)"
      collect_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_candidate_extraction_ms)" \
        "$(log_max_value_from_dir "${dir}" 'candidateExtractionMs=([0-9]+)')")"
      node_collection_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_node_collection_ms)" \
        "$(log_max_value_from_dir "${dir}" 'nodeCollectionMs=([0-9]+)')")"
      visual_roi_planning_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_roi_planning_ms)" \
        "$(log_max_value_from_dir "${dir}" 'visualRoiPlanningMs=([0-9]+)')")"
      screen_candidate_extraction_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_screen_candidate_extraction_ms)" \
        "$(log_max_value_from_dir "${dir}" 'screenCandidateExtractionMs=([0-9]+)')")"
      candidate_post_processing_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_candidate_post_processing_ms)" \
        "$(log_max_value_from_dir "${dir}" 'candidatePostProcessingMs=([0-9]+)')")"
      parallel_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_candidate_parallel_wait_ms)" \
        "$(log_max_value_from_dir "${dir}" 'candidateParallelWaitMs=([0-9]+)')")"
      backend_api_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_latency_ms)" \
        "$(max_nonnegative \
          "$(log_max_value_from_dir "${dir}" 'analysisLatencyMs=([0-9]+)')" \
          "$(log_max_value_from_dir "${dir}" 'latencyMs=([0-9]+)')")")"
      backend_e2e_ms="$(xml_value_from_dir "${dir}" analysis_diagnostics_backend_mask_latency_ms)"
      ocr_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_ocr_latency_ms)" \
        "$(log_elapsed_ms_from_dir "${dir}" 'start visual OCR' 'visual OCR candidates selected=')")"
      coord_ms="$(max_nonnegative "$(xml_value_from_dir "${dir}" analysis_diagnostics_accessibility_mask_latency_ms)" "${parallel_ms}")"
      display_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_mask_latency_ms)" \
        "$(log_elapsed_ms_from_dir "${dir}" 'render mask overlay package=' 'render maskCount=|render skipped candidates=')")"
      node_count="$(xml_value_from_dir "${dir}" analysis_diagnostics_node_count)"
      screen_candidates="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_screen_candidate_count)" \
        "$(log_max_value_from_dir "${dir}" 'screenCandidates=([0-9]+)')")"
      char_nodes="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_char_location_node_count)" \
        "$(log_max_value_from_dir "${dir}" 'charLocationNodes=([0-9]+)')")"
      char_range_candidates="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_char_range_candidate_count)" \
        "$(log_max_value_from_dir "${dir}" 'charRangeCandidates=([0-9]+)')")"
      overlay_candidates="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_overlay_candidate_count)" \
        "$(log_max_value_from_dir "${dir}" 'render maskCount=([0-9]+)')")"
      overlay_rendered="$(max_nonnegative \
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
      roi_candidates="$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_roi_candidate_count)"
      roi_selected="$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_roi_selected_count)"
      ocr_raw="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_ocr_raw_count)" \
        "$(log_max_value_from_dir "${dir}" 'visual OCR candidates selected=[0-9]+ raw=([0-9]+)')")"
      ocr_selected="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_visual_ocr_selected_count)" \
        "$(log_max_value_from_dir "${dir}" 'visual OCR candidates selected=([0-9]+)')")"
      risk_gate_mask_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_risk_gate_mask_ms)" \
        "$(log_max_value_from_dir "${dir}" 'risk gate mask specs=[0-9]+ elapsedMs=([0-9]+)')")"
      risk_gate_event_age_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_risk_gate_event_age_ms)" \
        "$(log_max_value_from_dir "${dir}" 'risk gate mask specs=[0-9]+ .*eventAgeMs=([0-9]+)')")"
      risk_gate_receive_to_mask_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_risk_gate_receive_to_mask_ms)" \
        "$(log_max_value_from_dir "${dir}" 'risk gate mask specs=[0-9]+ .*receiveToMaskMs=([0-9]+)')")"
      fast_provisional_mask_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_fast_provisional_mask_ms)" \
        "$(log_max_value_from_dir "${dir}" 'fast provisional mask results=[0-9]+ elapsedMs=([0-9]+)')")"
      fast_provisional_event_age_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_fast_provisional_event_age_ms)" \
        "$(log_max_value_from_dir "${dir}" 'fast provisional mask results=[0-9]+ .*eventAgeMs=([0-9]+)')")"
      fast_provisional_build_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_fast_provisional_build_ms)" \
        "$(log_max_value_from_dir "${dir}" 'fast provisional mask results=[0-9]+ .*buildMs=([0-9]+)')")"
      fast_provisional_overlay_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_fast_provisional_overlay_ms)" \
        "$(log_max_value_from_dir "${dir}" 'fast provisional mask results=[0-9]+ .*overlayMs=([0-9]+)')")"
      fast_provisional_receive_to_mask_ms="$(max_nonnegative \
        "$(xml_value_from_dir "${dir}" analysis_diagnostics_fast_provisional_receive_to_mask_ms)" \
        "$(log_max_value_from_dir "${dir}" 'fast provisional mask results=[0-9]+ .*receiveToMaskMs=([0-9]+)')")"
      total_ms="$(observed_total_ms \
        "${collect_ms}" \
        "$(max_nonnegative "${backend_api_ms}" "${backend_e2e_ms}")" \
        "${ocr_ms}" \
        "${coord_ms}" \
        "${display_ms}")"
      printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
        "${run_id}" "${mode}" "${stages}" "${scenario}" "${device}" \
        "${collect_ms}" "${node_collection_ms}" "${visual_roi_planning_ms}" "${screen_candidate_extraction_ms}" \
        "${candidate_post_processing_ms}" "${parallel_ms}" "${backend_api_ms}" "${backend_e2e_ms}" \
        "${ocr_ms}" "${coord_ms}" "${display_ms}" \
        "${node_count}" "${screen_candidates}" "${char_nodes}" "${char_range_candidates}" \
        "${overlay_candidates}" "${overlay_rendered}" "${offensive}" "${filtered}" \
        "${roi_candidates}" "${roi_selected}" "${ocr_raw}" "${ocr_selected}" "${total_ms}" \
        "${risk_gate_mask_ms}" "${risk_gate_event_age_ms}" "${risk_gate_receive_to_mask_ms}" \
        "${fast_provisional_mask_ms}" "${fast_provisional_event_age_ms}" "${fast_provisional_build_ms}" \
        "${fast_provisional_overlay_ms}" "${fast_provisional_receive_to_mask_ms}" "${dir}"
    done
  } > "${raw_csv}"
}

write_summary_csv() {
  local raw_csv="$1"
  local summary_csv="$2"
  awk -F ',' '
    NR == 1 { next }
    function add(metric, mode, value, key) {
      if (value == "" || value == "n/a") return
      value += 0
      if (value < 0) return
      key = mode SUBSEP metric
      sums[key] += value
      counts[key] += 1
      if (!(key in mins) || value < mins[key]) mins[key] = value
      if (!(key in maxs) || value > maxs[key]) maxs[key] = value
    }
    function avg(mode, metric, key) {
      key = mode SUBSEP metric
      return counts[key] ? sprintf("%.1f", sums[key] / counts[key]) : ""
    }
    function minv(mode, metric, key) {
      key = mode SUBSEP metric
      return counts[key] ? mins[key] : ""
    }
    function maxv(mode, metric, key) {
      key = mode SUBSEP metric
      return counts[key] ? maxs[key] : ""
    }
    {
      mode = $2
      stages[mode] = $3
      if (!(mode in seen)) {
        order[++mode_count] = mode
        seen[mode] = 1
      }
      runs[mode] += 1
      add("collect", mode, $6)
      add("node_collection", mode, $7)
      add("visual_roi_planning", mode, $8)
      add("screen_candidate_extraction", mode, $9)
      add("candidate_post_processing", mode, $10)
      add("parallel", mode, $11)
      add("backend_api", mode, $12)
      add("backend_e2e", mode, $13)
      add("ocr", mode, $14)
      add("coord", mode, $15)
      add("display", mode, $16)
      add("nodes", mode, $17)
      add("screen_candidates", mode, $18)
      add("char_nodes", mode, $19)
      add("char_range_candidates", mode, $20)
      add("overlay_candidates", mode, $21)
      add("overlay_rendered", mode, $22)
      add("offensive", mode, $23)
      add("filtered", mode, $24)
      add("roi_candidates", mode, $25)
      add("roi_selected", mode, $26)
      add("ocr_raw", mode, $27)
      add("ocr_selected", mode, $28)
      add("observed_total", mode, $29)
      add("risk_gate_mask", mode, $30)
      add("risk_gate_event_age", mode, $31)
      add("risk_gate_receive_to_mask", mode, $32)
      add("fast_provisional_mask", mode, $33)
      add("fast_provisional_event_age", mode, $34)
      add("fast_provisional_build", mode, $35)
      add("fast_provisional_overlay", mode, $36)
      add("fast_provisional_receive_to_mask", mode, $37)
    }
    BEGIN {
      print "mode,stages,runs,avg_collect_ms,min_collect_ms,max_collect_ms,avg_node_collection_ms,avg_visual_roi_planning_ms,avg_screen_candidate_extraction_ms,avg_candidate_post_processing_ms,avg_candidate_parallel_wait_ms,avg_backend_api_ms,avg_backend_e2e_ms,avg_ocr_ms,avg_coord_ms,avg_display_ms,avg_observed_total_ms,avg_screen_candidates,avg_char_nodes,avg_char_range_candidates,avg_overlay_candidates,avg_overlay_rendered,avg_offensive,avg_filtered,avg_roi_selected,avg_ocr_selected,avg_risk_gate_mask_ms,avg_risk_gate_event_age_ms,avg_risk_gate_receive_to_mask_ms,avg_fast_provisional_mask_ms,avg_fast_provisional_event_age_ms,avg_fast_provisional_build_ms,avg_fast_provisional_overlay_ms,avg_fast_provisional_receive_to_mask_ms"
    }
    END {
      for (i = 1; i <= mode_count; i++) {
        mode = order[i]
        print mode "," stages[mode] "," runs[mode] "," \
          avg(mode, "collect") "," minv(mode, "collect") "," maxv(mode, "collect") "," \
          avg(mode, "node_collection") "," avg(mode, "visual_roi_planning") "," \
          avg(mode, "screen_candidate_extraction") "," avg(mode, "candidate_post_processing") "," \
          avg(mode, "parallel") "," avg(mode, "backend_api") "," avg(mode, "backend_e2e") "," avg(mode, "ocr") "," \
          avg(mode, "coord") "," avg(mode, "display") "," avg(mode, "observed_total") "," \
          avg(mode, "screen_candidates") "," avg(mode, "char_nodes") "," avg(mode, "char_range_candidates") "," \
          avg(mode, "overlay_candidates") "," avg(mode, "overlay_rendered") "," avg(mode, "offensive") "," \
          avg(mode, "filtered") "," avg(mode, "roi_selected") "," avg(mode, "ocr_selected") "," \
          avg(mode, "risk_gate_mask") "," avg(mode, "risk_gate_event_age") "," \
          avg(mode, "risk_gate_receive_to_mask") "," \
          avg(mode, "fast_provisional_mask") "," avg(mode, "fast_provisional_event_age") "," \
          avg(mode, "fast_provisional_build") "," avg(mode, "fast_provisional_overlay") "," \
          avg(mode, "fast_provisional_receive_to_mask")
      }
    }
  ' "${raw_csv}" > "${summary_csv}"
}

write_stage_latency_csv() {
  local raw_csv="$1"
  local stage_csv="$2"
  awk -F ',' '
    function is_active(stage_num) {
      return ("+" stages "+") ~ ("\\+" stage_num "\\+")
    }
    function clean(value) {
      if (value == "" || value == "n/a" || value < 0) return ""
      return value
    }
    function emit(stage_num, stage_id, stage_name, component, latency_ms, latency_metric, secondary_ms, secondary_metric, count_metric, count_value, quality_metric, quality_value, notes) {
      active = is_active(stage_num)
      if (!active) {
        latency_ms = ""
        secondary_ms = ""
        count_value = ""
        quality_value = ""
        notes = "stage disabled in this mode"
      }
      print run_id "," mode "," stages "," scenario "," stage_id "," stage_name "," component "," \
        (active ? "yes" : "no") "," clean(latency_ms) "," latency_metric "," clean(secondary_ms) "," secondary_metric "," \
        count_metric "," clean(count_value) "," quality_metric "," clean(quality_value) "," notes "," artifact_dir
    }
    function emit_risk_gate(latency_ms) {
      if (clean(latency_ms) == "") return
      print run_id "," mode "," stages "," scenario ",00,risk_gate,Bounded pre-analysis overlay,yes," \
        clean(latency_ms) ",risk_gate_mask_ms," clean($32) \
        ",risk_gate_receive_to_mask_ms,overlay_rendered," clean($22) \
        ",overlay_candidates," clean($21) ",bounded provisional overlay before collection/backend," artifact_dir
    }
    function emit_fast(latency_ms) {
      if (clean(latency_ms) == "") return
      print run_id "," mode "," stages "," scenario ",00b,fast_provisional,Accessibility event source,yes," \
        clean(latency_ms) ",fast_provisional_mask_ms," clean($37) \
        ",fast_provisional_receive_to_mask_ms,overlay_rendered," clean($22) \
        ",overlay_candidates," clean($21) ",event-source provisional mask before full collection/backend," artifact_dir
    }
    NR == 1 { next }
    {
      run_id = $1
      mode = $2
      stages = $3
      scenario = $4
      artifact_dir = $38

      emit_risk_gate($30)
      emit_fast($33)
      emit("1", "01", "collect", "Accessibility Service", $6, "collect_ms", $7, "node_collection_ms", "screen_candidates", $18, "node_count", $17, "candidate extraction total; submetrics include node collection, ROI planning, candidate extraction, post processing")
      emit("2", "02", "analyze", "Android Analysis API", $12, "backend_api_ms", $13, "backend_e2e_ms", "offensive", $23, "filtered", $24, "backend/API analysis and masking decision")
      emit("3", "03", "ocr_roi", "OCR ROI", $14, "ocr_ms", $8, "visual_roi_planning_ms", "visual_roi_selected", $26, "visual_ocr_selected", $28, "ROI-limited OCR supplement")
      emit("4", "04", "coordinate", "char box / line estimate", $15, "coord_ms", $11, "candidate_parallel_wait_ms", "char_range_candidates", $20, "overlay_candidates", $21, "coordinate recovery and renderable mask planning")
      emit("5", "05", "display", "overlay gate", $16, "display_ms", $10, "candidate_post_processing_ms", "overlay_rendered", $22, "visual_ocr_raw", $27, "overlay render/gate; blank latency means not instrumented in latest diagnostics")
    }
    BEGIN {
      print "run_id,mode,stages,scenario,stage_id,stage_name,component,stage_active,latency_ms,latency_metric,secondary_latency_ms,secondary_metric,count_metric,count_value,quality_metric,quality_value,notes,artifact_dir"
    }
  ' "${raw_csv}" > "${stage_csv}"
}

write_stage_latency_summary_csv() {
  local stage_csv="$1"
  local summary_csv="$2"
  awk -F ',' '
    NR == 1 { next }
    function add(key, value) {
      if (value == "" || value == "n/a") return
      value += 0
      if (value < 0) return
      sums[key] += value
      counts[key] += 1
      if (!(key in mins) || value < mins[key]) mins[key] = value
      if (!(key in maxs) || value > maxs[key]) maxs[key] = value
    }
    function fmt(value) {
      return value == "" ? "" : sprintf("%.1f", value)
    }
    {
      if ($8 != "yes") next
      key = $2 SUBSEP $5 SUBSEP $6 SUBSEP $7 SUBSEP $10
      if (!(key in seen)) {
        order[++order_count] = key
        mode[key] = $2
        stages[key] = $3
        stage_id[key] = $5
        stage_name[key] = $6
        component[key] = $7
        metric[key] = $10
        seen[key] = 1
      }
      if ($8 == "yes") {
        run_counts[key] += 1
        add(key, $9)
      }
    }
    BEGIN {
      print "mode,stages,stage_id,stage_name,component,latency_metric,runs,measured_runs,avg_latency_ms,min_latency_ms,max_latency_ms"
    }
    END {
      for (i = 1; i <= order_count; i++) {
        key = order[i]
        measured = counts[key] + 0
        avg = measured ? sums[key] / measured : ""
        print mode[key] "," stages[key] "," stage_id[key] "," stage_name[key] "," component[key] "," metric[key] "," \
          run_counts[key] "," measured "," fmt(avg) "," fmt(mins[key]) "," fmt(maxs[key])
      }
    }
  ' "${stage_csv}" > "${summary_csv}"
}

write_stage_delta_csv() {
  local raw_csv="$1"
  local delta_csv="$2"
  awk -F ',' '
    NR == 1 { next }
    function add(mode, value) {
      if (value == "" || value == "n/a") return
      value += 0
      if (value < 0) return
      sums[mode] += value
      counts[mode] += 1
    }
    function avg(mode) {
      return counts[mode] ? sums[mode] / counts[mode] : -1
    }
    function fmt(value) {
      return value >= 0 ? sprintf("%.1f", value) : ""
    }
    function diff(after, before) {
      return after >= 0 && before >= 0 ? sprintf("%.1f", after - before) : ""
    }
    {
      add($2, $29)
    }
    BEGIN {
      print "stage,isolated_mode,isolated_total_ms,cumulative_before,cumulative_before_ms,cumulative_after,cumulative_after_ms,delta_ms"
    }
    END {
      s1 = avg("s1_collect_only")
      s2 = avg("s2_backend_only")
      s3 = avg("s3_ocr_roi_only")
      s4 = avg("s4_coord_only")
      s5 = avg("s5_overlay_only")
      s12 = avg("s12_collect_backend")
      s123 = avg("s123_collect_backend_ocr")
      s1234 = avg("s1234_collect_backend_ocr_coord")
      s12345 = avg("s12345_full")
      print "02 backend,s2_backend_only," fmt(s2) ",s1_collect_only," fmt(s1) ",s12_collect_backend," fmt(s12) "," diff(s12, s1)
      print "03 OCR ROI,s3_ocr_roi_only," fmt(s3) ",s12_collect_backend," fmt(s12) ",s123_collect_backend_ocr," fmt(s123) "," diff(s123, s12)
      print "04 coordinate,s4_coord_only," fmt(s4) ",s123_collect_backend_ocr," fmt(s123) ",s1234_collect_backend_ocr_coord," fmt(s1234) "," diff(s1234, s123)
      print "05 overlay gate,s5_overlay_only," fmt(s5) ",s1234_collect_backend_ocr_coord," fmt(s1234) ",s12345_full," fmt(s12345) "," diff(s12345, s1234)
    }
  ' "${raw_csv}" > "${delta_csv}"
}

write_ppt_table() {
  local summary_csv="$1"
  local delta_csv="$2"
  local output="$3"
  {
    echo "# Android Pipeline Benchmark"
    echo
    echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "This report is generated from real Android runtime stage toggles via \`pipeline_experiment_mode\`."
    echo
    echo "## Summary By Mode"
    echo
    echo "| Mode | Stages | Runs | Collect ms | Node ms | Candidate ms | Backend API ms | OCR ms | Coord ms | Display ms | Observed total ms | Candidates | Rendered | Offensive |"
    echo "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
    awk -F ',' 'NR > 1 {
      print "| `" $1 "` | " $2 " | " $3 " | " $4 " | " $7 " | " $9 " | " $12 " | " $14 " | " $15 " | " $16 " | " $17 " | " $18 " | " $22 " | " $23 " |"
    }' "${summary_csv}"
    echo
    echo "## Cumulative Stage Delta"
    echo
    echo "| Stage | Isolated mode total ms | Before | After | Delta ms |"
    echo "| --- | ---: | --- | --- | ---: |"
    awk -F ',' 'NR > 1 {
      print "| " $1 " | " $3 " | `" $4 "` " $5 "ms | `" $6 "` " $7 "ms | " $8 " |"
    }' "${delta_csv}"
    echo
    echo "## Evidence"
    echo
    echo "- Raw CSV: \`raw_runs.csv\`"
    echo "- Stage latency CSV: \`stage_latency.csv\`"
    echo "- Stage latency summary CSV: \`stage_latency_summary.csv\`"
    echo "- Summary CSV: \`summary_by_mode.csv\`"
    echo "- Delta CSV: \`stage_delta.csv\`"
    echo "- Artifact root: \`${BATCH_ROOT}/runs\`"
    echo
    echo "Manual video review is still required for missed/false/stale mask quality."
  } > "${output}"
}

aggregate_pipeline() {
  local dirs=()
  local mode
  for mode in ${AGGREGATE_MODES}; do
    while IFS= read -r dir; do
      dirs+=("${dir}")
    done < <(
      find "${BATCH_ROOT}/runs" -maxdepth 1 -type d -name "${BATCH_ID}-${mode}-*" 2>/dev/null |
        awk -v batch_id="${BATCH_ID}" -v mode="${mode}" '
          {
            base = $0
            sub(/^.*\//, "", base)
            run_index = base
            sub("^" batch_id "-" mode "-", "", run_index)
            if (run_index ~ /^[0-9]+$/) print run_index "\t" $0
          }
        ' |
        sort -n |
        cut -f2-
    )
  done
  if [[ ${#dirs[@]} -eq 0 ]]; then
    echo "[ERROR] No run artifacts found under ${BATCH_ROOT}/runs"
    exit 2
  fi

  mkdir -p "${REPORT_DIR}"
  local raw_csv="${REPORT_DIR}/raw_runs.csv"
  local stage_csv="${REPORT_DIR}/stage_latency.csv"
  local stage_summary_csv="${REPORT_DIR}/stage_latency_summary.csv"
  local summary_csv="${REPORT_DIR}/summary_by_mode.csv"
  local delta_csv="${REPORT_DIR}/stage_delta.csv"
  local ppt_md="${REPORT_DIR}/ppt_table.md"
  write_raw_csv "${raw_csv}" "${dirs[@]}"
  write_stage_latency_csv "${raw_csv}" "${stage_csv}"
  write_stage_latency_summary_csv "${stage_csv}" "${stage_summary_csv}"
  write_summary_csv "${raw_csv}" "${summary_csv}"
  write_stage_delta_csv "${raw_csv}" "${delta_csv}"
  write_ppt_table "${summary_csv}" "${delta_csv}" "${ppt_md}"

  echo "[OK] batch_root=${BATCH_ROOT}"
  echo "[OK] raw_csv=${raw_csv}"
  echo "[OK] stage_csv=${stage_csv}"
  echo "[OK] stage_summary_csv=${stage_summary_csv}"
  echo "[OK] summary_csv=${summary_csv}"
  echo "[OK] delta_csv=${delta_csv}"
  echo "[OK] ppt_table=${ppt_md}"
}

run_batch() {
  mkdir -p "${BATCH_ROOT}/runs"
  trap stop_fixture_server EXIT
  start_fixture_server
  prepare_emulator

  local mode index
  for mode in ${MODES}; do
    for index in $(seq "${START_INDEX}" "${RUNS_PER_MODE}"); do
      echo "[INFO] run mode=${mode} stages=$(stage_mask_for_mode "${mode}") index=${index}/${RUNS_PER_MODE}"
      run_capture "${mode}" "${index}"
    done
  done

  aggregate_pipeline
}

case "${1:-run}" in
  run)
    run_batch
    ;;
  fixture)
    write_fixture
    echo "[OK] fixture_dir=${FIXTURE_DIR}"
    ;;
  aggregate)
    aggregate_pipeline
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
