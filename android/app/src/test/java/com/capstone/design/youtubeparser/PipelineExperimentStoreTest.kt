package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineExperimentStoreTest {

    @Test
    fun fromRaw_defaultsToFullPipelineForUnknownValues() {
        assertEquals(PipelineExperimentMode.S12345_FULL, PipelineExperimentMode.fromRaw(null))
        assertEquals(PipelineExperimentMode.S12345_FULL, PipelineExperimentMode.fromRaw(""))
        assertEquals(PipelineExperimentMode.S12345_FULL, PipelineExperimentMode.fromRaw("missing"))
    }

    @Test
    fun singleStageModesEnableOnlyTheMeasuredStage() {
        assertTrue(PipelineExperimentMode.S1_COLLECT_ONLY.collectStageEnabled)
        assertFalse(PipelineExperimentMode.S1_COLLECT_ONLY.backendStageEnabled)
        assertFalse(PipelineExperimentMode.S1_COLLECT_ONLY.ocrStageEnabled)
        assertFalse(PipelineExperimentMode.S1_COLLECT_ONLY.coordinateStageEnabled)
        assertFalse(PipelineExperimentMode.S1_COLLECT_ONLY.overlayStageEnabled)

        assertTrue(PipelineExperimentMode.S4_COORD_ONLY.coordinateStageEnabled)
        assertFalse(PipelineExperimentMode.S4_COORD_ONLY.backendStageEnabled)
        assertFalse(PipelineExperimentMode.S4_COORD_ONLY.overlayStageEnabled)
    }

    @Test
    fun cumulativeFullModeKeepsAllStagesEnabled() {
        val mode = PipelineExperimentMode.S12345_FULL

        assertEquals("1+2+3+4+5", mode.stageMask)
        assertTrue(mode.collectStageEnabled)
        assertTrue(mode.backendStageEnabled)
        assertTrue(mode.ocrStageEnabled)
        assertTrue(mode.coordinateStageEnabled)
        assertTrue(mode.overlayStageEnabled)
    }

    @Test
    fun optimizationBaselineModesCarryUnoptimizedStrategies() {
        val allNodes = PipelineExperimentMode.OPT_BASE_ALL_NODES_BACKEND
        assertEquals(allNodes, PipelineExperimentMode.fromRaw("opt_base_all_nodes_backend"))
        assertEquals("1+2", allNodes.stageMask)
        assertTrue(allNodes.collectStageEnabled)
        assertTrue(allNodes.backendStageEnabled)
        assertFalse(allNodes.ocrStageEnabled)
        assertEquals(CandidateCollectionStrategy.ALL_VISIBLE_TEXT, allNodes.candidateCollectionStrategy)

        val fullScreenOcr = PipelineExperimentMode.OPT_BASE_FULLSCREEN_OCR
        assertEquals(fullScreenOcr, PipelineExperimentMode.fromRaw("opt_base_fullscreen_ocr"))
        assertTrue(fullScreenOcr.ocrStageEnabled)
        assertEquals(VisualRoiStrategy.FULL_SCREEN, fullScreenOcr.visualRoiStrategy)

        val fullBoxOverlay = PipelineExperimentMode.OPT_BASE_FULL_BOX_OVERLAY
        assertEquals(fullBoxOverlay, PipelineExperimentMode.fromRaw("opt_base_full_box_overlay"))
        assertTrue(fullBoxOverlay.overlayStageEnabled)
        assertFalse(fullBoxOverlay.coordinateStageEnabled)
        assertEquals(CandidateCollectionStrategy.ALL_VISIBLE_TEXT, fullBoxOverlay.candidateCollectionStrategy)
    }
}
