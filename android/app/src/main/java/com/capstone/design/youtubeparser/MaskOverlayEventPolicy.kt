package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent

internal data class MaskOverlayScrollDelta(
    val deltaX: Int,
    val deltaY: Int,
    val source: MaskOverlayScrollDeltaSource
)

internal enum class MaskOverlayScrollDeltaSource {
    EXPLICIT_DELTA,
    ABSOLUTE_POSITION
}

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

        val explicitXAvailable = explicitScrollDeltaX != 0
        val explicitYAvailable = explicitScrollDeltaY != 0
        val deltaX = if (explicitXAvailable) {
            -explicitScrollDeltaX
        } else {
            absoluteOverlayDelta(absoluteScrollX, lastAbsoluteScrollX)
        }
        val deltaY = if (explicitYAvailable) {
            -explicitScrollDeltaY
        } else {
            absoluteOverlayDelta(absoluteScrollY, lastAbsoluteScrollY)
        }

        if (deltaX == 0 && deltaY == 0) return null
        val source = if (explicitXAvailable || explicitYAvailable) {
            MaskOverlayScrollDeltaSource.EXPLICIT_DELTA
        } else {
            MaskOverlayScrollDeltaSource.ABSOLUTE_POSITION
        }
        return MaskOverlayScrollDelta(deltaX = deltaX, deltaY = deltaY, source = source)
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

    fun shouldPreserveOnScrollContentChange(
        eventType: Int,
        hasActiveMasks: Boolean,
        isScrollStabilizing: Boolean,
        isLikelySelfContentChange: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            hasActiveMasks &&
            isScrollStabilizing &&
            !isLikelySelfContentChange
    }

    fun shouldPreserveOnUnresolvedScrollDelta(
        eventType: Int,
        hasActiveMasks: Boolean,
        hasResolvedScrollDelta: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            hasActiveMasks &&
            !hasResolvedScrollDelta
    }

    fun shouldClearOnFailedScrollTranslation(
        eventType: Int,
        hasActiveMasks: Boolean,
        hasResolvedScrollDelta: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            hasActiveMasks &&
            hasResolvedScrollDelta
    }

    fun shouldDeferClearForVisualOnlyAnalysis(
        hasActiveMasks: Boolean,
        hasRenderableVisualRois: Boolean
    ): Boolean {
        return hasActiveMasks && hasRenderableVisualRois
    }

    fun shouldClearAfterVisualAnalysisMiss(
        hasActiveMasks: Boolean,
        hasRenderableVisualRois: Boolean,
        isOverlayStabilizing: Boolean
    ): Boolean {
        return !(hasActiveMasks && hasRenderableVisualRois && isOverlayStabilizing)
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
