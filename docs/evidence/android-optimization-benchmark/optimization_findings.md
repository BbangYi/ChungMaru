# Android Optimization Findings

Source summary: `docs/evidence/android-optimization-benchmark/summary_by_mode.csv`

## Executive Read

- Candidate pruning: reduced `avg_observed_total_ms` from 773.3ms to 519.3ms (254.0ms, 32.8%). Work delta: avg_screen_candidates 14.0 -> avg_screen_candidates 14.0 (0.0 / 0.0%). Treat as a latency observation, not proof that candidate count was pruned in this fixture.
- ROI OCR: increased `avg_ocr_ms` from 174.1ms to 220.2ms (-46.1ms, -26.5%). Work delta: avg_roi_selected 1.0 -> avg_roi_selected 2.0 (-1.0 / -100.0%).
- Char box / line coordinate planning: increased `avg_display_ms` from 19.8ms to 100.9ms (-81.1ms, -409.6%). Work delta: avg_overlay_candidates 3.0 -> avg_overlay_candidates 3.2 (-0.2 / -6.7%).
- Optimized full pipeline: increased `avg_observed_total_ms` from 566.4ms to 755.4ms (-189.0ms, -33.4%). Work delta: avg_screen_candidates 14.0 -> avg_screen_candidates 17.0 (-3.0 / -21.4%).
- Overlay gate: not measured yet. Requires a dedicated no-gate baseline or manual video labels; current diagnostics do not expose a no-gate runtime mode.

## Presentation Cautions

- Use `latency_and_work_reduced` as the strongest optimization evidence.
- Use `latency_reduced_work_not_reduced` only with the caveat that the work-count proxy did not improve.
- Use `latency_increased` rows to explain quality/coverage cost or remaining bottlenecks, not as speed wins.
- Manual video review is still required for missed, false, and stale mask quality.
