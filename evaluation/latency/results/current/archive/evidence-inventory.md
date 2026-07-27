# Latency Evidence Inventory

## Purpose

`results/current` root 정리는 파일명을 유지한 relocation이 아니라, 일부 Chrome demo artifact를 새 실행 결과로 교체한 evidence migration이다. 이 inventory는 발표·보고서에서 서로 다른 run의 영상, QA report, metadata, latency row를 섞지 않기 위한 기준이다.

## Baseline And Verification

- Baseline revision: `6289339`
- Deleted root artifact paths: `37`
- Archive counterpart paths: `37`
- Byte-identical relocations: `26`
- Replaced Chrome demo artifacts: `11`
- Canonical presentation demo: `chrome-google-demo-20260609T104101`
- Canonical controlled benchmark: `chrome-10k-fixture-20260607T2314`

The 26 identical paths were verified by comparing the Git blob from the baseline revision with the corresponding `archive/root-raw-20260609/` file. The eleven paths below are intentional run replacements, not relocation-equivalent files.

## Replaced Artifact Set

| Path under `root-raw-20260609/` | Legacy source | Archive source | Use rule |
| --- | --- | --- | --- |
| `chrome-demo-attempt-latency.csv` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `chrome-demo-attempt-latency.jsonl` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `chrome-demo-latency.csv` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `chrome-demo-latency.jsonl` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `chrome-demo-qa-report.md` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `chrome-demo-scene-summary.csv` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `chungmaru-google-demo.mp4` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Canonical presentation video. |
| `demo-script.md` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `demo-timeline.csv` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `demo-timeline.json` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |
| `metadata.json` | `chrome-google-youtube-demo-20260608-final6` | `chrome-google-demo-20260609T104101` | Pair only with the archive demo set. |

## Presentation Rules

1. Chrome demo slide, video, scene table, and per-attempt latency must use the same archive demo run.
2. `chrome-e2e-summary.csv` is a separate controlled benchmark and must retain its own run ID in all tables.
3. Android evidence remains `Validation Needed` unless a run contains latency, bounds, stale-mask, and failure evidence together.
4. A newer run replaces a canonical source only after this inventory and the relevant report source paths are updated in the same docs-evaluation change.
