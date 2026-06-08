#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ADB="${ADB:-/Users/giminu0930/Library/Android/sdk/platform-tools/adb}"
PACKAGE_NAME="${PACKAGE_NAME:-com.capstone.design}"
BATCH_ID="${BATCH_ID:-$(date +%Y%m%dT%H%M%S)}"
BATCH_ROOT="${BATCH_ROOT:-/private/tmp/chungmaru-android-emulator-batch-${BATCH_ID}}"
FIXTURE_DIR="${FIXTURE_DIR:-${BATCH_ROOT}/fixture}"
FIXTURE_PORT="${FIXTURE_PORT:-4189}"
RUNS_PER_FEATURE="${RUNS_PER_FEATURE:-10}"
RECORD_SECONDS="${RECORD_SECONDS:-12}"
FEATURE_SETS="${FEATURE_SETS:-full-pipeline accessibility-only backend-only ocr-roi charbox-line overlay-gate backend-offline}"
REPORT_PATH="${REPORT_PATH:-${REPO_ROOT}/docs/evidence/android-emulator-batch-results.md}"
CHROME_PACKAGE="${CHROME_PACKAGE:-com.android.chrome}"
ONLINE_ANALYSIS_INPUT="${ONLINE_ANALYSIS_INPUT:-10.0.2.2:8000}"
OFFLINE_ANALYSIS_INPUT="${OFFLINE_ANALYSIS_INPUT:-10.0.2.2:65530}"

usage() {
  cat <<'USAGE'
Usage: scripts/android-emulator-evidence-batch.sh [run|fixture|aggregate|help]

Runs repeated emulator evidence captures against a deterministic Chrome fixture.

Environment:
  RUNS_PER_FEATURE=10
  FEATURE_SETS="full-pipeline accessibility-only backend-only ocr-roi charbox-line overlay-gate backend-offline"
  RECORD_SECONDS=12
  BATCH_ROOT=/private/tmp/chungmaru-android-emulator-batch-<timestamp>
  REPORT_PATH=docs/evidence/android-emulator-batch-results.md
  ONLINE_ANALYSIS_INPUT=10.0.2.2:8000
  OFFLINE_ANALYSIS_INPUT=10.0.2.2:65530

Notes:
  FEATURE_SETS are run labels and fixture emphases. This script does not rewrite
  app code to disable individual pipeline stages.
USAGE
}

require_device() {
  if ! "${ADB}" devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit(found ? 0 : 1) }'; then
    echo "[ERROR] No adb device is ready. Start an emulator first."
    exit 2
  fi
}

write_fixture() {
  mkdir -p "${FIXTURE_DIR}"
  cat > "${FIXTURE_DIR}/index.html" <<'HTML'
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Chungmaru Android Mask Fixture</title>
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
<body>
<main>
  <section>
    <h1>청마루 Android 마스킹 테스트</h1>
    <p class="safe">이 문장은 안전한 일반 텍스트입니다. 정상 텍스트 오탐 여부를 확인합니다.</p>
    <p class="harmful">테스트 유해 표현 A: ㅅㅂ 같은 욕설이 포함된 댓글입니다.</p>
    <p class="harmful">테스트 유해 표현 B: 진짜 개같네 라는 공격적 표현이 들어갑니다.</p>
  </section>
  <section class="wide">
    <h2>접근성 텍스트 후보</h2>
    <ul>
      <li class="safe">정상 댓글: 오늘 자료 정리가 잘 됐습니다.</li>
      <li class="harmful">문제 댓글: 이건 ㅈ같아서 보기 힘들다는 표현입니다.</li>
      <li class="safe">정상 댓글: 기능별 latency를 반복 측정합니다.</li>
    </ul>
  </section>
  <section>
    <h2>OCR ROI 후보</h2>
    <canvas id="ocrCanvas" width="680" height="220" aria-label="이미지 안의 텍스트 영역"></canvas>
  </section>
  <div class="spacer"></div>
  <section>
    <h2>스크롤 후 stale mask 확인</h2>
    <p class="harmful">스크롤 이후에도 남으면 안 되는 욕설 후보: ㅅㅂ 왜 이렇게 느려.</p>
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

set_analysis_preferences() {
  local analysis_input="$1"
  local sensitivity="$2"
  "${ADB}" shell run-as "${PACKAGE_NAME}" mkdir -p shared_prefs
  {
    echo '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>'
    echo '<map>'
    echo "    <string name=\"analysis_input\">${analysis_input}</string>"
    echo "    <int name=\"analysis_sensitivity\" value=\"${sensitivity}\" />"
    echo '</map>'
  } | "${ADB}" shell run-as "${PACKAGE_NAME}" tee shared_prefs/youtube_parser_settings.xml >/dev/null
}

ensure_accessibility_enabled() {
  local service="${PACKAGE_NAME}/com.capstone.design.youtubeparser.YoutubeAccessibilityService"
  "${ADB}" shell settings put secure enabled_accessibility_services "${service}"
  "${ADB}" shell settings put secure accessibility_enabled 1
}

clear_device_json_artifacts() {
  local base="/storage/emulated/0/Android/data/${PACKAGE_NAME}/files"
  "${ADB}" shell rm -rf \
    "${base}/upload_cache" \
    "${base}/parse_results" \
    "${base}/analysis_results"
}

prepare_emulator() {
  require_device
  ensure_accessibility_enabled
  ADB_REVERSE=1 "${REPO_ROOT}/scripts/android-demo-evidence.sh" prepare
  "${ADB}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "${ADB}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

feature_scenario() {
  local feature="$1"
  case "${feature}" in
    accessibility-only) echo "chrome-accessibility-text" ;;
    backend-only) echo "chrome-backend-analysis" ;;
    ocr-roi) echo "chrome-ocr-roi" ;;
    charbox-line) echo "chrome-charbox-line" ;;
    overlay-gate) echo "chrome-overlay-gate-scroll" ;;
    backend-offline) echo "chrome-backend-offline" ;;
    *) echo "chrome-full-pipeline" ;;
  esac
}

configure_feature() {
  local feature="$1"
  if [[ "${feature}" == "backend-offline" ]]; then
    set_analysis_preferences "${OFFLINE_ANALYSIS_INPUT}" 80
  else
    set_analysis_preferences "${ONLINE_ANALYSIS_INPUT}" 80
  fi
  "${ADB}" shell am force-stop "${PACKAGE_NAME}" >/dev/null 2>&1 || true
  ensure_accessibility_enabled
  sleep 1
}

open_fixture_in_chrome() {
  local feature="$1"
  local index="$2"
  local url="http://10.0.2.2:${FIXTURE_PORT}/index.html?case=${feature}-run-${index}"
  "${ADB}" shell am force-stop "${CHROME_PACKAGE}" >/dev/null 2>&1 || true
  "${ADB}" shell am start -a android.intent.action.VIEW -d "${url}" -p "${CHROME_PACKAGE}" >/dev/null
}

drive_fixture() {
  local feature="$1"
  local index="$2"
  open_fixture_in_chrome "${feature}" "${index}"
  sleep 3
  "${ADB}" shell input swipe 520 1780 520 640 500 >/dev/null
  sleep 1
  "${ADB}" shell input swipe 520 640 520 1780 450 >/dev/null
  sleep 1
  "${ADB}" shell input swipe 520 1780 520 700 500 >/dev/null
  sleep 1
}

run_capture() {
  local feature="$1"
  local index="$2"
  local scenario run_id artifact_dir notes
  scenario="$(feature_scenario "${feature}")"
  run_id="${BATCH_ID}-${feature}-${index}"
  artifact_dir="${BATCH_ROOT}/runs/${run_id}"
  notes="automated emulator fixture run; feature_set is a label/scenario emphasis, not a code-level feature toggle"
  mkdir -p "${artifact_dir}"

  configure_feature "${feature}"
  clear_device_json_artifacts
  "${ADB}" logcat -c

  RUN_ID="${run_id}" \
    ARTIFACT_DIR="${artifact_dir}" \
    FEATURE_SET="${feature}" \
    SCENARIO="${scenario}" \
    RUN_NOTES="${notes}" \
    RECORD_SECONDS="${RECORD_SECONDS}" \
    "${REPO_ROOT}/scripts/android-demo-evidence.sh" capture &
  local capture_pid=$!

  sleep 1
  drive_fixture "${feature}" "${index}"
  wait "${capture_pid}"

  RUN_ID="${run_id}" \
    ARTIFACT_DIR="${artifact_dir}" \
    FEATURE_SET="${feature}" \
    SCENARIO="${scenario}" \
    RUN_NOTES="${notes}" \
    EXPECTED_HARMFUL=5 \
    PPT_READY=no \
    QUALITY_NOTES="manual video review still required; automated fixture capture only" \
    "${REPO_ROOT}/scripts/android-demo-evidence.sh" collect
}

aggregate_batch() {
  mapfile -t dirs < <(find "${BATCH_ROOT}/runs" -maxdepth 1 -type d -name "${BATCH_ID}-*" 2>/dev/null | sort)
  if [[ ${#dirs[@]} -eq 0 ]]; then
    echo "[ERROR] No run artifacts found under ${BATCH_ROOT}/runs"
    exit 2
  fi
  REPORT_PATH="${REPORT_PATH}" "${REPO_ROOT}/scripts/android-demo-evidence.sh" aggregate "${dirs[@]}"
  echo "[OK] batch_root=${BATCH_ROOT}"
  echo "[OK] batch_report=${REPORT_PATH}"
}

run_batch() {
  mkdir -p "${BATCH_ROOT}/runs"
  trap stop_fixture_server EXIT
  start_fixture_server
  prepare_emulator

  local feature index
  for feature in ${FEATURE_SETS}; do
    for index in $(seq 1 "${RUNS_PER_FEATURE}"); do
      echo "[INFO] run feature=${feature} index=${index}/${RUNS_PER_FEATURE}"
      run_capture "${feature}" "${index}"
    done
  done

  aggregate_batch
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
    aggregate_batch
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
