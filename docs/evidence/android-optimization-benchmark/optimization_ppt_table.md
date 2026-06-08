# Android Optimization Benchmark

Source summary: `docs/evidence/android-optimization-benchmark/summary_by_mode.csv`

This table answers: baseline 대비 각 최적화가 latency/work를 얼마나 줄였는가. `missing_*` rows must not be presented as measured improvement.

| Optimization | Status | Baseline | Optimized | Metric | Baseline ms | Optimized ms | Reduction ms | Reduction % | Work before -> after | Quality/result |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| Candidate pruning | latency_reduced_work_not_reduced | `opt_base_all_nodes_backend` | `s12_collect_backend` | avg_observed_total_ms | 773.3 | 519.3 | 254.0 | 32.8 | avg_screen_candidates 14.0 -> avg_screen_candidates 14.0 | avg_offensive 1.0 |
| ROI OCR | latency_increased | `opt_base_fullscreen_ocr` | `s123_collect_backend_ocr` | avg_ocr_ms | 174.1 | 220.2 | -46.1 | -26.5 | avg_roi_selected 1.0 -> avg_roi_selected 2.0 | avg_ocr_selected 0.9 |
| Char box / line coordinate planning | latency_increased | `opt_base_full_box_overlay` | `s12345_full` | avg_display_ms | 19.8 | 100.9 | -81.1 | -409.6 | avg_overlay_candidates 3.0 -> avg_overlay_candidates 3.2 | avg_overlay_rendered 3.2 |
| Optimized full pipeline | latency_increased | `opt_base_full_box_overlay` | `s12345_full` | avg_observed_total_ms | 566.4 | 755.4 | -189.0 | -33.4 | avg_screen_candidates 14.0 -> avg_screen_candidates 17.0 | avg_overlay_rendered 3.2 |
| Overlay gate | not_measured | `opt_base_no_overlay_gate` | `s12345_full` | stale_or_unstable_overlay_count |  |  |  |  |  | manual_stale_mask_rate |

## Interpretation Notes

- `candidate_pruning`: All visible accessibility text is the baseline; optimized mode sends only selected analysis candidates.
- `roi_ocr`: Full-screen OCR is the baseline; optimized mode restricts OCR to planned ROIs.
- `charbox_overlay`: Full node-box overlay is the baseline; optimized mode uses exact char ranges, visual OCR geometry, and overlay planning.
- `full_pipeline`: End-to-end comparison against the broad full-box overlay baseline. This mixes multiple optimizations.
- `overlay_gate`: Requires a dedicated no-gate baseline or manual video labels; current diagnostics do not expose a no-gate runtime mode.
