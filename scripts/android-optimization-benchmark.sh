#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

export REPORT_DIR="${REPORT_DIR:-${REPO_ROOT}/docs/evidence/android-optimization-benchmark}"
export MODES="${MODES:-opt_base_all_nodes_backend s12_collect_backend opt_base_fullscreen_ocr s123_collect_backend_ocr opt_base_full_box_overlay s12345_full}"
export AGGREGATE_MODES="${AGGREGATE_MODES:-${MODES}}"

command="${1:-run}"
"${SCRIPT_DIR}/android-pipeline-benchmark.sh" "${command}"

case "${command}" in
  run|aggregate)
    python3 "${SCRIPT_DIR}/android_optimization_benchmark_report.py" \
      --summary "${REPORT_DIR}/summary_by_mode.csv" \
      --out-dir "${REPORT_DIR}"
    ;;
esac
