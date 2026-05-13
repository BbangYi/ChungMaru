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
        assertTrue(candidates[0].boundsInScreen.top in 205..220)
        assertTrue(candidates[1].boundsInScreen.top in 305..325)
        assertTrue(candidates.all { it.boundsInScreen.left in 24..40 })
        assertTrue(candidates.all { it.authorId.orEmpty().startsWith("ocr:youtube-composite-card:") })
    }

    @Test
    fun selectCandidates_addsTopHeroMasksWhenYoutubePrefixesTitleBeforeHit() {
        val roi = VisualTextRoi(
            boundsInScreen = BoundsRect(0, 184, 656, 562),
            source = "youtube-composite-card",
            priority = 0,
            reason = "content-description-only",
            sourceText = "Play video from semoplaylist \"Tlqkf 또 다시 보여줘야 돼!!!\""
        )

        val candidates = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(roi), candidateCount = 1),
            screenWidth = 656,
            screenHeight = 1454
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.commentText == "tlqkf" })
        assertTrue(candidates.any { it.boundsInScreen.top in 325..340 })
    }

    @Test
    fun selectCandidates_addsCompositeHeroMasksFromBaseTitleWhenSourceTextIsNotTitleLike() {
        val roi = VisualTextRoi(
            boundsInScreen = BoundsRect(0, 309, 1080, 993),
            source = "youtube-composite-card",
            priority = 0,
            reason = "content-description-only",
            sourceText = "semo playlist 917K views 7 months ago"
        )
        val baseResponse = responseOf(
            resultOf(
                original = "🔥\"Tlqkf 또 보여줘야 돼!\" : 식케이 (Sik-K), Lil Moshpit",
                bounds = BoundsRect(96, 596, 608, 664),
                authorId = "android-accessibility:title"
            )
        )

        val candidates = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(roi), candidateCount = 1),
            screenWidth = 1080,
            screenHeight = 2400,
            baseResponse = baseResponse
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates[0].boundsInScreen.top in 385..400)
        assertTrue(candidates[1].boundsInScreen.top in 570..585)
        assertTrue(candidates.all { it.authorId.orEmpty().startsWith("ocr:youtube-composite-card:") })
    }

    @Test
    fun selectCandidates_addsVisibleBandHeroMasksFromBaseTitleWhenCompositeRoiIsMissing() {
        val roi = VisualTextRoi(
            boundsInScreen = BoundsRect(0, 203, 675, 582),
            source = "youtube-visible-band",
            priority = 9,
            reason = "fallback-first-viewport-band"
        )
        val baseResponse = responseOf(
            resultOf(
                original = "tlqkf",
                bounds = BoundsRect(118, 62, 250, 94),
                authorId = "android-accessibility:youtube_user_input"
            ),
            resultOf(
                original = "🔥\"Tlqkf 또 보여줘야 돼!\" : 식케이 (Sik-K), Lil Moshpit",
                bounds = BoundsRect(96, 596, 608, 664),
                authorId = "android-accessibility:title"
            )
        )

        val candidates = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(roi), candidateCount = 1),
            screenWidth = 675,
            screenHeight = 1478,
            baseResponse = baseResponse
        )

        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.commentText == "tlqkf" })
        assertTrue(candidates[0].boundsInScreen.top in 240..255)
        assertTrue(candidates[1].boundsInScreen.top in 360..375)
        assertTrue(candidates.all { it.authorId.orEmpty().startsWith("ocr:youtube-visible-band:") })
    }

    @Test
    fun selectCandidates_doesNotAddVisibleBandHeroMasksFromSearchInputOnly() {
        val roi = VisualTextRoi(
            boundsInScreen = BoundsRect(0, 203, 675, 582),
            source = "youtube-visible-band",
            priority = 9,
            reason = "fallback-first-viewport-band"
        )
        val baseResponse = responseOf(
            resultOf(
                original = "tlqkf",
                bounds = BoundsRect(118, 62, 250, 94),
                authorId = "android-accessibility:youtube_user_input"
            )
        )

        val candidates = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(roi), candidateCount = 1),
            screenWidth = 675,
            screenHeight = 1478,
            baseResponse = baseResponse
        )

        assertTrue(candidates.isEmpty())
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

    private fun responseOf(vararg results: AndroidAnalysisResultItem): AndroidAnalysisResponse {
        return AndroidAnalysisResponse(
            timestamp = 1710000000000,
            filteredCount = 0,
            results = results.toList()
        )
    }

    private fun resultOf(
        original: String,
        bounds: BoundsRect,
        authorId: String
    ): AndroidAnalysisResultItem {
        val range = VisualTextOcrCandidateFilter.findAnalysisRanges(original).first()
        return AndroidAnalysisResultItem(
            original = original,
            boundsInScreen = bounds,
            authorId = authorId,
            isOffensive = true,
            isProfane = true,
            isToxic = false,
            isHate = false,
            scores = HarmScores(profanity = 1.0, toxicity = 0.0, hate = 0.0),
            evidenceSpans = listOf(
                EvidenceSpan(
                    text = range.analysisText,
                    start = range.start,
                    end = range.end,
                    score = 1.0
                )
            )
        )
    }
}
