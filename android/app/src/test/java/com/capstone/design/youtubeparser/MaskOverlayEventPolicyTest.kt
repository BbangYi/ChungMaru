package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskOverlayEventPolicyTest {

    @Test
    fun resolveScrollTranslationDelta_prefersExplicitScrollDeltaWhenAvailable() {
        val delta = MaskOverlayEventPolicy.resolveScrollTranslationDelta(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
            explicitScrollDeltaX = 0,
            explicitScrollDeltaY = 12,
            absoluteScrollX = 0,
            absoluteScrollY = 400,
            lastAbsoluteScrollX = 0,
            lastAbsoluteScrollY = 200
        )

        assertTrue(delta != null)
        assertEquals(MaskOverlayScrollDeltaSource.EXPLICIT_DELTA, delta?.source)
        assertEquals(-12, delta?.deltaY)
    }

    @Test
    fun resolveScrollTranslationDelta_usesAbsoluteScrollPositionFallback() {
        val delta = MaskOverlayEventPolicy.resolveScrollTranslationDelta(
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            hasActiveMasks = true,
            explicitScrollDeltaX = 0,
            explicitScrollDeltaY = 0,
            absoluteScrollX = 0,
            absoluteScrollY = 460,
            lastAbsoluteScrollX = 0,
            lastAbsoluteScrollY = 400
        )

        assertTrue(delta != null)
        assertEquals(MaskOverlayScrollDeltaSource.ABSOLUTE_POSITION, delta?.source)
        assertEquals(-60, delta?.deltaY)
    }

    @Test
    fun resolveScrollTranslationDelta_rejectsUnknownOrUnsafeInputs() {
        assertFalse(
            MaskOverlayEventPolicy.resolveScrollTranslationDelta(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                explicitScrollDeltaX = 0,
                explicitScrollDeltaY = 12,
                absoluteScrollX = 0,
                absoluteScrollY = 460,
                lastAbsoluteScrollX = 0,
                lastAbsoluteScrollY = 400
            ) != null
        )
        assertFalse(
            MaskOverlayEventPolicy.resolveScrollTranslationDelta(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = false,
                explicitScrollDeltaX = 0,
                explicitScrollDeltaY = 12,
                absoluteScrollX = 0,
                absoluteScrollY = 460,
                lastAbsoluteScrollX = 0,
                lastAbsoluteScrollY = 400
            ) != null
        )
        assertFalse(
            MaskOverlayEventPolicy.resolveScrollTranslationDelta(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                explicitScrollDeltaX = 0,
                explicitScrollDeltaY = 0,
                absoluteScrollX = -1,
                absoluteScrollY = -1,
                lastAbsoluteScrollX = null,
                lastAbsoluteScrollY = null
            ) != null
        )
    }

    @Test
    fun shouldPreserveExistingOnEmptyPlan_keepsMasksOnlyForSameStableViewport() {
        assertTrue(
            MaskOverlayEventPolicy.shouldPreserveExistingOnEmptyPlan(
                hasActiveMasks = true,
                snapshotOverlayRevision = 7L,
                currentOverlayRevision = 7L,
                isScrollStabilizing = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveExistingOnEmptyPlan(
                hasActiveMasks = true,
                snapshotOverlayRevision = 7L,
                currentOverlayRevision = 8L,
                isScrollStabilizing = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveExistingOnEmptyPlan(
                hasActiveMasks = true,
                snapshotOverlayRevision = 7L,
                currentOverlayRevision = 7L,
                isScrollStabilizing = true
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveExistingOnEmptyPlan(
                hasActiveMasks = false,
                snapshotOverlayRevision = 7L,
                currentOverlayRevision = 7L,
                isScrollStabilizing = false
            )
        )
    }

    @Test
    fun shouldRetryAfterStaleOverlayResult_retriesOnlyForOkStaleAnalysis() {
        assertTrue(
            MaskOverlayEventPolicy.shouldRetryAfterStaleOverlayResult(
                analysisOk = true,
                snapshotOverlayRevision = 7L,
                currentOverlayRevision = 8L
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldRetryAfterStaleOverlayResult(
                analysisOk = true,
                snapshotOverlayRevision = 7L,
                currentOverlayRevision = 7L
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldRetryAfterStaleOverlayResult(
                analysisOk = false,
                snapshotOverlayRevision = 7L,
                currentOverlayRevision = 8L
            )
        )
    }

    @Test
    fun shouldPreserveOnScrollContentChange_keepsTranslatedMasksDuringLayoutBursts() {
        assertTrue(
            MaskOverlayEventPolicy.shouldPreserveOnScrollContentChange(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                isScrollStabilizing = true,
                isLikelySelfContentChange = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveOnScrollContentChange(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                isScrollStabilizing = false,
                isLikelySelfContentChange = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveOnScrollContentChange(
                eventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                hasActiveMasks = true,
                isScrollStabilizing = true,
                isLikelySelfContentChange = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveOnScrollContentChange(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                isScrollStabilizing = true,
                isLikelySelfContentChange = true
            )
        )
    }

    @Test
    fun shouldPreserveOnUnresolvedScrollDelta_keepsMasksUntilScrollPositionIsKnown() {
        assertTrue(
            MaskOverlayEventPolicy.shouldPreserveOnUnresolvedScrollDelta(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                hasResolvedScrollDelta = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveOnUnresolvedScrollDelta(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                hasResolvedScrollDelta = true
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveOnUnresolvedScrollDelta(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                hasResolvedScrollDelta = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldPreserveOnUnresolvedScrollDelta(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = false,
                hasResolvedScrollDelta = false
            )
        )
    }

    @Test
    fun shouldClearOnFailedScrollTranslation_onlyClearsResolvedActiveMaskFailures() {
        assertTrue(
            MaskOverlayEventPolicy.shouldClearOnFailedScrollTranslation(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                hasResolvedScrollDelta = true
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldClearOnFailedScrollTranslation(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                hasResolvedScrollDelta = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldClearOnFailedScrollTranslation(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = false,
                hasResolvedScrollDelta = true
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldClearOnFailedScrollTranslation(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                hasResolvedScrollDelta = true
            )
        )
    }

    @Test
    fun shouldDeferClearForVisualOnlyAnalysis_keepsMasksWhileVisualOcrCanReplaceThem() {
        assertTrue(
            MaskOverlayEventPolicy.shouldDeferClearForVisualOnlyAnalysis(
                hasActiveMasks = true,
                hasRenderableVisualRois = true
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldDeferClearForVisualOnlyAnalysis(
                hasActiveMasks = false,
                hasRenderableVisualRois = true
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldDeferClearForVisualOnlyAnalysis(
                hasActiveMasks = true,
                hasRenderableVisualRois = false
            )
        )
    }

    @Test
    fun shouldClearAfterVisualAnalysisMiss_preservesMasksDuringOverlayStabilization() {
        assertFalse(
            MaskOverlayEventPolicy.shouldClearAfterVisualAnalysisMiss(
                hasActiveMasks = true,
                hasRenderableVisualRois = true,
                isOverlayStabilizing = true,
                hasPreservedRecentVisualMiss = false
            )
        )
        assertTrue(
            MaskOverlayEventPolicy.shouldClearAfterVisualAnalysisMiss(
                hasActiveMasks = true,
                hasRenderableVisualRois = true,
                isOverlayStabilizing = false,
                hasPreservedRecentVisualMiss = true
            )
        )
        assertTrue(
            MaskOverlayEventPolicy.shouldClearAfterVisualAnalysisMiss(
                hasActiveMasks = false,
                hasRenderableVisualRois = true,
                isOverlayStabilizing = true,
                hasPreservedRecentVisualMiss = false
            )
        )
        assertTrue(
            MaskOverlayEventPolicy.shouldClearAfterVisualAnalysisMiss(
                hasActiveMasks = true,
                hasRenderableVisualRois = false,
                isOverlayStabilizing = true,
                hasPreservedRecentVisualMiss = false
            )
        )
    }

    @Test
    fun shouldClearAfterVisualAnalysisMiss_preservesOneTransientMissForRetry() {
        assertFalse(
            MaskOverlayEventPolicy.shouldClearAfterVisualAnalysisMiss(
                hasActiveMasks = true,
                hasRenderableVisualRois = true,
                isOverlayStabilizing = false,
                hasPreservedRecentVisualMiss = false
            )
        )
        assertTrue(
            MaskOverlayEventPolicy.shouldClearAfterVisualAnalysisMiss(
                hasActiveMasks = true,
                hasRenderableVisualRois = true,
                isOverlayStabilizing = false,
                hasPreservedRecentVisualMiss = true
            )
        )
    }

    @Test
    fun shouldClearAfterAnalysisFailure_preservesOneFailureForRenderableVisualRetry() {
        assertFalse(
            MaskOverlayEventPolicy.shouldClearAfterAnalysisFailure(
                hasActiveMasks = true,
                hasRenderableVisualRois = true,
                hasPreservedRecentAnalysisFailure = false
            )
        )
        assertTrue(
            MaskOverlayEventPolicy.shouldClearAfterAnalysisFailure(
                hasActiveMasks = true,
                hasRenderableVisualRois = true,
                hasPreservedRecentAnalysisFailure = true
            )
        )
        assertTrue(
            MaskOverlayEventPolicy.shouldClearAfterAnalysisFailure(
                hasActiveMasks = false,
                hasRenderableVisualRois = true,
                hasPreservedRecentAnalysisFailure = false
            )
        )
        assertTrue(
            MaskOverlayEventPolicy.shouldClearAfterAnalysisFailure(
                hasActiveMasks = true,
                hasRenderableVisualRois = false,
                hasPreservedRecentAnalysisFailure = false
            )
        )
    }

    @Test
    fun isLikelySelfContentChange_keepsFreshOverlayFromClearingItself() {
        assertTrue(
            MaskOverlayEventPolicy.isLikelySelfContentChange(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                overlayUpdatedRecently = true
            )
        )
    }

    @Test
    fun isLikelySelfContentChange_doesNotHideRealContentChanges() {
        assertFalse(
            MaskOverlayEventPolicy.isLikelySelfContentChange(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                overlayUpdatedRecently = false
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.isLikelySelfContentChange(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                overlayUpdatedRecently = true
            )
        )
    }
}
