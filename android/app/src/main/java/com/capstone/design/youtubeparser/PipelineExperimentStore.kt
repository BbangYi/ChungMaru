package com.capstone.design.youtubeparser

import android.content.Context
import androidx.core.content.edit

enum class PipelineExperimentMode(
    val id: String,
    val label: String,
    val stageMask: String,
    val collectStageEnabled: Boolean,
    val backendStageEnabled: Boolean,
    val ocrStageEnabled: Boolean,
    val coordinateStageEnabled: Boolean,
    val overlayStageEnabled: Boolean,
    val candidateCollectionStrategy: CandidateCollectionStrategy = CandidateCollectionStrategy.OPTIMIZED,
    val visualRoiStrategy: VisualRoiStrategy = VisualRoiStrategy.OPTIMIZED_ROI
) {
    S1_COLLECT_ONLY(
        id = "s1_collect_only",
        label = "01 collect",
        stageMask = "1",
        collectStageEnabled = true,
        backendStageEnabled = false,
        ocrStageEnabled = false,
        coordinateStageEnabled = false,
        overlayStageEnabled = false
    ),
    S2_BACKEND_ONLY(
        id = "s2_backend_only",
        label = "02 backend",
        stageMask = "2",
        collectStageEnabled = false,
        backendStageEnabled = true,
        ocrStageEnabled = false,
        coordinateStageEnabled = false,
        overlayStageEnabled = false
    ),
    S3_OCR_ROI_ONLY(
        id = "s3_ocr_roi_only",
        label = "03 OCR ROI",
        stageMask = "3",
        collectStageEnabled = false,
        backendStageEnabled = false,
        ocrStageEnabled = true,
        coordinateStageEnabled = false,
        overlayStageEnabled = false
    ),
    S4_COORD_ONLY(
        id = "s4_coord_only",
        label = "04 coordinate",
        stageMask = "4",
        collectStageEnabled = false,
        backendStageEnabled = false,
        ocrStageEnabled = false,
        coordinateStageEnabled = true,
        overlayStageEnabled = false
    ),
    S5_OVERLAY_ONLY(
        id = "s5_overlay_only",
        label = "05 overlay gate",
        stageMask = "5",
        collectStageEnabled = false,
        backendStageEnabled = false,
        ocrStageEnabled = false,
        coordinateStageEnabled = false,
        overlayStageEnabled = true
    ),
    S12_COLLECT_BACKEND(
        id = "s12_collect_backend",
        label = "01+02 collect/backend",
        stageMask = "1+2",
        collectStageEnabled = true,
        backendStageEnabled = true,
        ocrStageEnabled = false,
        coordinateStageEnabled = false,
        overlayStageEnabled = false
    ),
    S123_COLLECT_BACKEND_OCR(
        id = "s123_collect_backend_ocr",
        label = "01+02+03 collect/backend/OCR",
        stageMask = "1+2+3",
        collectStageEnabled = true,
        backendStageEnabled = true,
        ocrStageEnabled = true,
        coordinateStageEnabled = false,
        overlayStageEnabled = false
    ),
    S1234_COLLECT_BACKEND_OCR_COORD(
        id = "s1234_collect_backend_ocr_coord",
        label = "01+02+03+04 collect/backend/OCR/coordinate",
        stageMask = "1+2+3+4",
        collectStageEnabled = true,
        backendStageEnabled = true,
        ocrStageEnabled = true,
        coordinateStageEnabled = true,
        overlayStageEnabled = false
    ),
    S12345_FULL(
        id = "s12345_full",
        label = "01+02+03+04+05 full",
        stageMask = "1+2+3+4+5",
        collectStageEnabled = true,
        backendStageEnabled = true,
        ocrStageEnabled = true,
        coordinateStageEnabled = true,
        overlayStageEnabled = true
    ),
    OPT_BASE_ALL_NODES_BACKEND(
        id = "opt_base_all_nodes_backend",
        label = "baseline all visible nodes + backend",
        stageMask = "1+2",
        collectStageEnabled = true,
        backendStageEnabled = true,
        ocrStageEnabled = false,
        coordinateStageEnabled = false,
        overlayStageEnabled = false,
        candidateCollectionStrategy = CandidateCollectionStrategy.ALL_VISIBLE_TEXT
    ),
    OPT_BASE_FULLSCREEN_OCR(
        id = "opt_base_fullscreen_ocr",
        label = "baseline full-screen OCR",
        stageMask = "1+2+3",
        collectStageEnabled = true,
        backendStageEnabled = true,
        ocrStageEnabled = true,
        coordinateStageEnabled = false,
        overlayStageEnabled = false,
        visualRoiStrategy = VisualRoiStrategy.FULL_SCREEN
    ),
    OPT_BASE_FULL_BOX_OVERLAY(
        id = "opt_base_full_box_overlay",
        label = "baseline full-node-box overlay",
        stageMask = "1+2+5",
        collectStageEnabled = true,
        backendStageEnabled = true,
        ocrStageEnabled = false,
        coordinateStageEnabled = false,
        overlayStageEnabled = true,
        candidateCollectionStrategy = CandidateCollectionStrategy.ALL_VISIBLE_TEXT
    );

    companion object {
        val DEFAULT = S12345_FULL

        fun fromRaw(raw: String?): PipelineExperimentMode {
            val normalized = raw?.trim().orEmpty()
            return values().firstOrNull { mode ->
                mode.id == normalized || mode.name == normalized
            } ?: DEFAULT
        }
    }
}

enum class CandidateCollectionStrategy {
    OPTIMIZED,
    ALL_VISIBLE_TEXT
}

enum class VisualRoiStrategy {
    OPTIMIZED_ROI,
    FULL_SCREEN
}

object PipelineExperimentStore {
    const val KEY_PIPELINE_EXPERIMENT_MODE = "pipeline_experiment_mode"

    fun get(context: Context): PipelineExperimentMode {
        val prefs = context.getSharedPreferences(AnalysisSensitivityStore.PREFS_NAME, Context.MODE_PRIVATE)
        return PipelineExperimentMode.fromRaw(prefs.getString(KEY_PIPELINE_EXPERIMENT_MODE, null))
    }

    fun save(context: Context, mode: PipelineExperimentMode) {
        val prefs = context.getSharedPreferences(AnalysisSensitivityStore.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_PIPELINE_EXPERIMENT_MODE, mode.id)
        }
    }
}
