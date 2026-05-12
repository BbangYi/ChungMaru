package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskOverlayEventPolicyTest {

    @Test
    fun shouldTranslateOnViewportScroll_requiresScrollEventActiveMasksAndDelta() {
        assertTrue(
            MaskOverlayEventPolicy.shouldTranslateOnViewportScroll(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                deltaX = 0,
                deltaY = -12
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldTranslateOnViewportScroll(
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                hasActiveMasks = true,
                deltaX = 0,
                deltaY = -12
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldTranslateOnViewportScroll(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = false,
                deltaX = 0,
                deltaY = -12
            )
        )
        assertFalse(
            MaskOverlayEventPolicy.shouldTranslateOnViewportScroll(
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                hasActiveMasks = true,
                deltaX = 0,
                deltaY = 0
            )
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
