package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent

internal object MaskOverlayEventPolicy {
    fun shouldTranslateOnViewportScroll(
        eventType: Int,
        hasActiveMasks: Boolean,
        deltaX: Int,
        deltaY: Int
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            hasActiveMasks &&
            (deltaX != 0 || deltaY != 0)
    }

    fun shouldPreserveExistingOnEmptyPlan(
        hasActiveMasks: Boolean,
        snapshotOverlayRevision: Long,
        currentOverlayRevision: Long,
        isScrollStabilizing: Boolean
    ): Boolean {
        return hasActiveMasks &&
            snapshotOverlayRevision == currentOverlayRevision &&
            !isScrollStabilizing
    }

    fun isLikelySelfContentChange(
        eventType: Int,
        hasActiveMasks: Boolean,
        overlayUpdatedRecently: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            hasActiveMasks &&
            overlayUpdatedRecently
    }
}
