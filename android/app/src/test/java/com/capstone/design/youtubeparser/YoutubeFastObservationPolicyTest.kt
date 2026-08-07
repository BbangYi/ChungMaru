package com.capstone.design.youtubeparser

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeFastObservationPolicyTest {

    @Test
    fun translatedYoutubeScroll_defersSnapshotUntilViewportSettles() {
        assertEquals(
            YoutubeFastObservationAction.TRANSLATE_AND_DEFER,
            YoutubeFastObservationPolicy.decide(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                overlaySelfContentChange = false,
                hasActiveMasks = true,
                commentPanelGateActive = true,
                isScrollStabilizing = true,
                scrollTranslationStatus = MaskOverlayTranslationStatus.TRANSLATED
            )
        )
    }

    @Test
    fun unresolvedYoutubeScroll_keepsCommentGateUntilViewportSettles() {
        assertEquals(
            YoutubeFastObservationAction.DEFER_UNTIL_STABLE,
            YoutubeFastObservationPolicy.decide(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                overlaySelfContentChange = false,
                hasActiveMasks = true,
                commentPanelGateActive = true,
                isScrollStabilizing = true,
                scrollTranslationStatus = MaskOverlayTranslationStatus.REJECTED_DELTA
            )
        )
    }

    @Test
    fun unresolvedYoutubeScroll_withoutCommentGateCapturesNewViewport() {
        assertEquals(
            YoutubeFastObservationAction.SNAPSHOT,
            YoutubeFastObservationPolicy.decide(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                overlaySelfContentChange = false,
                hasActiveMasks = true,
                commentPanelGateActive = false,
                isScrollStabilizing = true,
                scrollTranslationStatus = MaskOverlayTranslationStatus.REJECTED_DELTA
            )
        )
    }

    @Test
    fun contentBurstDuringTranslatedScroll_waitsForStableViewport() {
        assertEquals(
            YoutubeFastObservationAction.DEFER_UNTIL_STABLE,
            YoutubeFastObservationPolicy.decide(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                overlaySelfContentChange = false,
                hasActiveMasks = true,
                commentPanelGateActive = true,
                isScrollStabilizing = true,
                scrollTranslationStatus = null
            )
        )
    }

    @Test
    fun unrelatedOrSelfEvents_areSkipped() {
        assertEquals(
            YoutubeFastObservationAction.SKIP,
            YoutubeFastObservationPolicy.decide(
                packageName = "com.android.chrome",
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                overlaySelfContentChange = false,
                hasActiveMasks = true,
                commentPanelGateActive = false,
                isScrollStabilizing = false,
                scrollTranslationStatus = null
            )
        )
        assertEquals(
            YoutubeFastObservationAction.SKIP,
            YoutubeFastObservationPolicy.decide(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                overlaySelfContentChange = true,
                hasActiveMasks = true,
                commentPanelGateActive = false,
                isScrollStabilizing = false,
                scrollTranslationStatus = null
            )
        )
    }
}
