#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"

BRANCH="$(git branch --show-current 2>/dev/null || true)"
printf 'repo=%s\n' "${REPO_ROOT}"
printf 'branch=%s\n' "${BRANCH:-unknown}"

git -c core.quotePath=false status --porcelain=v1 | awk '
function lane(path) {
  if (path ~ /^android\//) return "android"
  if (path ~ /^backend\//) return "backend"
  if (path ~ /^extension\//) return "extension"
  if (path ~ /^shared\//) return "shared-contract"
  if (path ~ /^docs\// || path ~ /^evaluation\// || path == "README.md" || path == "AGENTS.md" || path == "scripts/codex-lane-status.sh") return "docs-evaluation"
  return "unclassified"
}
{
  status = substr($0, 1, 2)
  path = substr($0, 4)
  sub(/^.* -> /, "", path)
  sub(/^"/, "", path)
  sub(/"$/, "", path)
  bucket = lane(path)
  counts[bucket] += 1
  entries[bucket] = entries[bucket] sprintf("  %s %s\n", status, path)
  total += 1
}
END {
  printf("dirty_total=%d\n", total)
  ordered[1] = "android"
  ordered[2] = "backend"
  ordered[3] = "extension"
  ordered[4] = "shared-contract"
  ordered[5] = "docs-evaluation"
  ordered[6] = "unclassified"
  for (i = 1; i <= 6; i += 1) {
    bucket = ordered[i]
    printf("\n[%s] dirty=%d\n", bucket, counts[bucket] + 0)
    if (entries[bucket] != "") {
      printf("%s", entries[bucket])
    }
  }
}
'
