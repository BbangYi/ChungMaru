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
