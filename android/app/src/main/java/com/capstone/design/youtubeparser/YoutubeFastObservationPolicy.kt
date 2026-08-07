package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent

/**
 * Keeps the event-thread work bounded. A translated overlay is already
 * protecting the known comment, so collecting another accessibility subtree
 * can wait until the viewport stops moving.
 */
internal enum class YoutubeFastObservationAction {
    SKIP,
    SNAPSHOT,
    TRANSLATE_AND_DEFER,
    DEFER_UNTIL_STABLE
}

internal object YoutubeFastObservationPolicy {
    fun decide(
        packageName: String,
        eventType: Int,
        overlaySelfContentChange: Boolean,
        hasActiveMasks: Boolean,
        commentPanelGateActive: Boolean,
        isScrollStabilizing: Boolean,
        scrollTranslationStatus: MaskOverlayTranslationStatus?
    ): YoutubeFastObservationAction {
        if (packageName != YOUTUBE_PACKAGE || overlaySelfContentChange) {
            return YoutubeFastObservationAction.SKIP
        }

        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (
                    hasActiveMasks &&
                    scrollTranslationStatus == MaskOverlayTranslationStatus.TRANSLATED
                ) {
                    YoutubeFastObservationAction.TRANSLATE_AND_DEFER
                } else if (commentPanelGateActive) {
                    // The full comment panel remains protected. A fresh tree
                    // walk during the fling only creates duplicate work and
                    // stale row geometry.
                    YoutubeFastObservationAction.DEFER_UNTIL_STABLE
                } else {
                    YoutubeFastObservationAction.SNAPSHOT
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (hasActiveMasks && isScrollStabilizing) {
                    YoutubeFastObservationAction.DEFER_UNTIL_STABLE
                } else {
                    YoutubeFastObservationAction.SNAPSHOT
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> YoutubeFastObservationAction.SNAPSHOT

            else -> YoutubeFastObservationAction.SKIP
        }
    }

    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
}
