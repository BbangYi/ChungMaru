package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent

internal data class MaskOverlayScrollDelta(
    val deltaX: Int,
    val deltaY: Int,
    val source: MaskOverlayScrollDeltaSource
)

internal enum class MaskOverlayScrollDeltaSource {
    YOUTUBE_COMMENT_ANCHOR,
    EXPLICIT_DELTA,
    ABSOLUTE_POSITION
}

internal object MaskOverlayEventPolicy {
    private const val TAKE_SCREENSHOT_INTERVAL_TOO_SHORT_ERROR_CODE = 3
    private const val MIN_SCREENSHOT_REQUEST_INTERVAL_MS = 380L
    private const val SCREENSHOT_RETRY_GRACE_MS = 64L
    private const val VISUAL_CONTENT_CHANGE_INVALIDATION_GRACE_MS = 4_200L
    private const val VISUAL_SCROLL_INVALIDATION_GRACE_MS = 4_200L
    private const val VISUAL_REFRESH_COOLDOWN_MS = 1200L

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
        isScrollStabilizing: Boolean,
        hasProvisionalMasks: Boolean = false,
        isProvisionalPlan: Boolean = false
    ): Boolean {
        return hasActiveMasks &&
            snapshotOverlayRevision == currentOverlayRevision &&
            !isScrollStabilizing &&
            !hasProvisionalMasks &&
            !isProvisionalPlan
    }

    fun shouldRetryAfterStaleOverlayResult(
        analysisOk: Boolean,
        snapshotOverlayRevision: Long,
        currentOverlayRevision: Long
    ): Boolean {
        return analysisOk && snapshotOverlayRevision != currentOverlayRevision
    }

    fun shouldPrimeYoutubeLoadingForPotentialScroll(
        eventType: Int,
        isYoutubePackage: Boolean,
        isLikelySelfContentChange: Boolean,
        hasConfirmedCommentPanel: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            isYoutubePackage &&
            !isLikelySelfContentChange &&
            hasConfirmedCommentPanel
    }

    fun shouldRestoreYoutubeLoadingOnForeground(
        eventType: Int,
        isYoutubePackage: Boolean,
        wasYoutubeObserved: Boolean,
        hasCachedCommentPanel: Boolean,
        isCacheFresh: Boolean,
        windowClassMatches: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            isYoutubePackage &&
            !wasYoutubeObserved &&
            hasCachedCommentPanel &&
            isCacheFresh &&
            windowClassMatches
    }

    fun shouldPrimeYoutubeLoadingForLaunchClick(
        eventType: Int,
        isTrustedLauncherPackage: Boolean,
        hasCachedCommentPanel: Boolean,
        isCacheFresh: Boolean,
        isYoutubeLaunchTarget: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            isTrustedLauncherPackage &&
            hasCachedCommentPanel &&
            isCacheFresh &&
            isYoutubeLaunchTarget
    }

    fun shouldPrimeYoutubeLoadingForCommentButtonClick(
        eventType: Int,
        isYoutubePackage: Boolean,
        hasCachedCommentPanel: Boolean,
        isCacheFresh: Boolean,
        labelLooksLikeComments: Boolean,
        isCompactTrailingAction: Boolean
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            isYoutubePackage &&
            hasCachedCommentPanel &&
            isCacheFresh &&
            labelLooksLikeComments &&
            isCompactTrailingAction
    }
    fun shouldClearOverlayForExitPackage(
        eventType: Int,
        isExitPackage: Boolean
    ): Boolean {
        return isExitPackage &&
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
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

    fun shouldHideOnUnresolvedScrollDelta(
        eventType: Int,
        hasActiveMasks: Boolean,
        hasResolvedScrollDelta: Boolean,
        overlayUpdatedRecently: Boolean = false
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            hasActiveMasks &&
            !hasResolvedScrollDelta &&
            !overlayUpdatedRecently
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
        isOverlayStabilizing: Boolean,
        hasPreservedRecentVisualMiss: Boolean
    ): Boolean {
        return !(
            hasActiveMasks &&
                hasRenderableVisualRois &&
                (isOverlayStabilizing || !hasPreservedRecentVisualMiss)
        )
    }

    fun shouldClearAfterAnalysisFailure(
        hasActiveMasks: Boolean,
        hasRenderableVisualRois: Boolean,
        hasProvisionalMasks: Boolean,
        visualAnalysisInFlight: Boolean
    ): Boolean {
        return !(
            hasActiveMasks && (hasRenderableVisualRois || hasProvisionalMasks) ||
                visualAnalysisInFlight && hasRenderableVisualRois
            )
    }

    fun shouldDeferVisualInvalidationForContentChange(
        eventType: Int,
        visualAnalysisInFlight: Boolean,
        elapsedSinceVisualAnalysisStartMs: Long
    ): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            visualAnalysisInFlight &&
            elapsedSinceVisualAnalysisStartMs in 0..VISUAL_CONTENT_CHANGE_INVALIDATION_GRACE_MS
    }

    fun shouldDeferVisualInvalidationForFreshCapture(
        eventType: Int,
        visualAnalysisInFlight: Boolean,
        elapsedSinceVisualAnalysisStartMs: Long
    ): Boolean {
        if (!visualAnalysisInFlight || elapsedSinceVisualAnalysisStartMs < 0L) {
            return false
        }

        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                elapsedSinceVisualAnalysisStartMs <= VISUAL_CONTENT_CHANGE_INVALIDATION_GRACE_MS
            AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                elapsedSinceVisualAnalysisStartMs <= VISUAL_SCROLL_INVALIDATION_GRACE_MS
            else -> false
        }
    }

    fun shouldRunVisualRefreshForDuplicateSnapshot(
        hasRenderableVisualRois: Boolean,
        visualAnalysisInFlight: Boolean,
        hasReusableVisualSupplement: Boolean
    ): Boolean {
        return hasRenderableVisualRois &&
            !visualAnalysisInFlight &&
            !hasReusableVisualSupplement
    }

    fun shouldThrottleRecentVisualRefresh(
        hasActiveMasks: Boolean,
        currentVisualSignature: String,
        lastVisualSignature: String?,
        elapsedSinceLastRefreshMs: Long
    ): Boolean {
        return hasActiveMasks &&
            currentVisualSignature.isNotBlank() &&
            currentVisualSignature == lastVisualSignature &&
            elapsedSinceLastRefreshMs in 0..VISUAL_REFRESH_COOLDOWN_MS
    }

    fun isLikelySelfContentChange(
        eventType: Int,
        hasActiveMasks: Boolean,
        overlayUpdatedRecently: Boolean
    ): Boolean {
        val canComeFromOverlayWindow =
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        return canComeFromOverlayWindow &&
            hasActiveMasks &&
            overlayUpdatedRecently
    }

    fun shouldRemoveYoutubeMirrorAfterPanelMiss(
        mirrorReady: Boolean,
        panelPresent: Boolean,
        panelTransitionActive: Boolean,
        missingForMs: Long,
        missingGraceMs: Long
    ): Boolean {
        // A ready accessibility overlay can make YouTube's underlying panel
        // disappear from the observable window tree. Only explicit close/back
        // signals may dismiss the mirror after that handoff.
        if (mirrorReady || panelPresent || panelTransitionActive) return false
        return missingForMs >= missingGraceMs.coerceAtLeast(0L)
    }

    fun screenshotRequestThrottleDelay(elapsedSinceLastRequestMs: Long): Long {
        if (elapsedSinceLastRequestMs < 0L) return MIN_SCREENSHOT_REQUEST_INTERVAL_MS
        return (MIN_SCREENSHOT_REQUEST_INTERVAL_MS - elapsedSinceLastRequestMs).coerceAtLeast(0L)
    }

    fun screenshotFailureRetryDelay(
        errorCode: Int,
        elapsedSinceLastRequestMs: Long
    ): Long? {
        if (errorCode != TAKE_SCREENSHOT_INTERVAL_TOO_SHORT_ERROR_CODE) return null
        return screenshotRequestThrottleDelay(elapsedSinceLastRequestMs) + SCREENSHOT_RETRY_GRACE_MS
    }

    private fun absoluteOverlayDelta(currentAbsoluteScroll: Int, lastAbsoluteScroll: Int?): Int {
        if (currentAbsoluteScroll < 0 || lastAbsoluteScroll == null) return 0
        return lastAbsoluteScroll - currentAbsoluteScroll
    }
}
