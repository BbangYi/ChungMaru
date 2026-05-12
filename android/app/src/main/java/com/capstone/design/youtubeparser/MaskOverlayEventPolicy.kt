package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent

internal data class MaskOverlayScrollDelta(
    val deltaX: Int,
    val deltaY: Int
)

internal object MaskOverlayEventPolicy {
    fun resolveScrollTranslationDelta(
        eventType: Int,
        hasActiveMasks: Boolean,
        explicitScrollDeltaX: Int,
        explicitScrollDeltaY: Int,
        absoluteScrollX: Int,
        absoluteScrollY: Int,
        lastAbsoluteScrollX: Int?,
        lastAbsoluteScrollY: Int?
    ): MaskOverlayScrollDelta? {
        if (eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED || !hasActiveMasks) {
            return null
        }

        val deltaX = if (explicitScrollDeltaX != 0) {
            -explicitScrollDeltaX
        } else {
            absoluteOverlayDelta(absoluteScrollX, lastAbsoluteScrollX)
        }
        val deltaY = if (explicitScrollDeltaY != 0) {
            -explicitScrollDeltaY
        } else {
            absoluteOverlayDelta(absoluteScrollY, lastAbsoluteScrollY)
        }

        if (deltaX == 0 && deltaY == 0) return null
        return MaskOverlayScrollDelta(deltaX = deltaX, deltaY = deltaY)
    }

    fun knownAbsoluteScroll(value: Int): Int? {
        return value.takeIf { it >= 0 }
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

    private fun absoluteOverlayDelta(currentAbsoluteScroll: Int, lastAbsoluteScroll: Int?): Int {
        if (currentAbsoluteScroll < 0 || lastAbsoluteScroll == null) return 0
        return lastAbsoluteScroll - currentAbsoluteScroll
    }
}
