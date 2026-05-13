package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualTextSemanticFallbackPlannerTest {
    @Test
    fun selectCandidates_addsTopHeroBannerAndTitleMasksForLeadingYoutubeCompositeHit() {
        val roi = VisualTextRoi(
            boundsInScreen = BoundsRect(0, 166, 656, 545),
            source = "youtube-composite-card",
            priority = 0,
            reason = "content-description-only",
            sourceText = "Play video \"Tlqkf 또 다시 보여줘야 돼!!!\""
        )

        val candidates = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(roi), candidateCount = 1),
            screenWidth = 656,
            screenHeight = 1454
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.commentText == "tlqkf" })
        assertTrue(candidates[0].boundsInScreen.top in 195..215)
        assertTrue(candidates[1].boundsInScreen.top in 320..340)
        assertTrue(candidates.all { it.boundsInScreen.left in 24..40 })
        assertTrue(candidates.all { it.authorId.orEmpty().startsWith("ocr:youtube-composite-card:") })
    }

    @Test
    fun selectCandidates_skipsSmallOrLateCompositeHits() {
        val smallCard = VisualTextRoi(
            boundsInScreen = BoundsRect(20, 790, 330, 1255),
            source = "youtube-composite-card",
            priority = 0,
            reason = "content-description-only",
            sourceText = "Tlqkf 공부법"
        )
        val lateHit = VisualTextRoi(
            boundsInScreen = BoundsRect(0, 166, 656, 545),
            source = "youtube-composite-card",
            priority = 0,
            reason = "content-description-only",
            sourceText = "semo playlist 917K views 7 months ago Tlqkf"
        )

        val candidates = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(smallCard, lateHit), candidateCount = 2),
            screenWidth = 656,
            screenHeight = 1454
        )

        assertTrue(candidates.isEmpty())
    }
}
