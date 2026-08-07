package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDecisionPolicyTest {

    @Test
    fun decideFastCandidate_allowsSmallExactBoundsForDirectOverlay() {
        val decision = AndroidDecisionPolicy.decideFastCandidate(
            rawText = "병신아 뭐해",
            bounds = BoundsRect(120, 640, 360, 696),
            screenWidth = 1080,
            screenHeight = 2400,
            source = AndroidFastCandidateSource.EVENT_SOURCE
        )

        assertEquals(AndroidRouteAction.DIRECT_OVERLAY, decision.action)
        assertEquals(AndroidBoundsTrust.EXACT_SMALL, decision.boundsTrust)
        assertTrue(decision.overlayAllowed)
        assertFalse(decision.ocrRequired)
        assertEquals(listOf("병신"), decision.ranges.map { it.analysisText })
    }

    @Test
    fun decideFastCandidate_holdsWideAccessibilityBoundsForSlowAnalysisOnly() {
        val decision = AndroidDecisionPolicy.decideFastCandidate(
            rawText = "병신아 뭐해",
            bounds = BoundsRect(0, 420, 1080, 820),
            screenWidth = 1080,
            screenHeight = 2400,
            source = AndroidFastCandidateSource.EVENT_SOURCE
        )

        assertEquals(AndroidRouteAction.HOLD_ANALYSIS_ONLY, decision.action)
        assertEquals(AndroidBoundsTrust.EXACT_WIDE, decision.boundsTrust)
        assertFalse(decision.overlayAllowed)
        assertFalse(decision.ocrRequired)
    }

    @Test
    fun decideFastCandidate_allowsTallYoutubeCommentBoundsForDirectOverlay() {
        val decision = AndroidDecisionPolicy.decideFastCandidate(
            rawText = "하...씨발..또 다시 보여줘야돼? 이게 존나 야마있네",
            bounds = BoundsRect(127, 1363, 1006, 1677),
            screenWidth = 1080,
            screenHeight = 2400,
            source = AndroidFastCandidateSource.YOUTUBE_SEMANTIC
        )

        assertEquals(AndroidRouteAction.DIRECT_OVERLAY, decision.action)
        assertEquals(AndroidBoundsTrust.EXACT_COMPACT, decision.boundsTrust)
        assertTrue(decision.overlayAllowed)
        assertFalse(decision.ocrRequired)
        assertEquals(listOf("씨발", "존나"), decision.ranges.map { it.analysisText })
    }

    @Test
    fun decideFastCandidate_sendsEstimatedVisualGeometryToOcrRequired() {
        val decision = AndroidDecisionPolicy.decideFastCandidate(
            rawText = "tlqkf",
            bounds = BoundsRect(80, 360, 360, 420),
            screenWidth = 1080,
            screenHeight = 2400,
            source = AndroidFastCandidateSource.VISUAL_ESTIMATED
        )

        assertEquals(AndroidRouteAction.OCR_REQUIRED, decision.action)
        assertEquals(AndroidBoundsTrust.ESTIMATED, decision.boundsTrust)
        assertFalse(decision.overlayAllowed)
        assertTrue(decision.ocrRequired)
    }

    @Test
    fun decideFastCandidate_dropsSafeTextWithoutCheapSignal() {
        val decision = AndroidDecisionPolicy.decideFastCandidate(
            rawText = "오늘 날씨가 좋다",
            bounds = BoundsRect(80, 360, 360, 420),
            screenWidth = 1080,
            screenHeight = 2400,
            source = AndroidFastCandidateSource.EVENT_SOURCE
        )

        assertEquals(AndroidRouteAction.DROP, decision.action)
        assertEquals("no-cheap-harmful-signal", decision.dropReason)
        assertFalse(decision.overlayAllowed)
    }

    @Test
    fun decideFastCandidate_keepsRecentFingerprintAsCacheOnly() {
        val decision = AndroidDecisionPolicy.decideFastCandidate(
            rawText = "병신아 뭐해",
            bounds = BoundsRect(120, 640, 360, 696),
            screenWidth = 1080,
            screenHeight = 2400,
            source = AndroidFastCandidateSource.EVENT_SOURCE,
            recentFingerprintMatched = true
        )

        assertEquals(AndroidRouteAction.CACHE_ONLY, decision.action)
        assertEquals("recent-fingerprint", decision.dropReason)
        assertFalse(decision.overlayAllowed)

        val sample = decision.toSample("android_decision/event_source")
        assertTrue(sample.contains("route_action=cache_only"))
        assertTrue(sample.contains("drop_reason=recent-fingerprint"))
        assertTrue(sample.contains("backend_sent=false"))
        assertTrue(sample.contains("ocr_required=false"))
        assertTrue(sample.contains("overlay_allowed=false"))
    }
}
