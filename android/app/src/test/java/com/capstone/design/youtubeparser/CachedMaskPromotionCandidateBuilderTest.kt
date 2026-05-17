package com.capstone.design.youtubeparser

import org.junit.Assert.assertTrue
import org.junit.Test

class CachedMaskPromotionCandidateBuilderTest {

    @Test
    fun buildYoutubeComments_includesSemanticVisualFallbackForCachePromotion() {
        val roi = VisualTextRoi(
            boundsInScreen = BoundsRect(0, 972, 1080, 1656),
            source = "youtube-composite-card",
            priority = 0,
            reason = "content-description-only",
            sourceText = "What is 'Tlqkf'?_Contemporary Korean Slang - 46 seconds - " +
                "Go to channel Contemporary Korean Slang - 40 thousand views - 5 years ago - play video"
        )
        val nodes = listOf(
            contentDescriptionNode(
                displayText = roi.sourceText,
                left = roi.boundsInScreen.left,
                top = roi.boundsInScreen.top,
                right = roi.boundsInScreen.right,
                bottom = roi.boundsInScreen.bottom
            )
        )

        val comments = CachedMaskPromotionCandidateBuilder.buildYoutubeComments(
            nodes = nodes,
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(roi), candidateCount = 1),
            screenWidth = 1080,
            screenHeight = 2400,
            sceneRevision = 17L
        )

        assertTrue(comments.any {
            it.commentText == "tlqkf" &&
                it.authorId.orEmpty().startsWith("youtube-visual-range:")
        })
        assertTrue(comments.any {
            it.commentText == "tlqkf" &&
                it.authorId.orEmpty().startsWith("ocr:youtube-semantic-card:")
        })
    }

    @Test
    fun buildYoutubeComments_doesNotInventSemanticFallbackForSmallShortsCards() {
        val roi = VisualTextRoi(
            boundsInScreen = BoundsRect(20, 790, 330, 1255),
            source = "youtube-composite-card",
            priority = 0,
            reason = "content-description-only",
            sourceText = "Tlqkf 공부법"
        )

        val comments = CachedMaskPromotionCandidateBuilder.buildYoutubeComments(
            nodes = emptyList(),
            visualRoiPlan = VisualTextRoiPlan(rois = listOf(roi), candidateCount = 1),
            screenWidth = 656,
            screenHeight = 1454,
            sceneRevision = 17L
        )

        assertTrue(comments.isEmpty())
    }

    private fun contentDescriptionNode(
        displayText: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = "com.google.android.youtube",
            text = null,
            contentDescription = displayText,
            displayText = displayText,
            className = "android.view.View",
            viewIdResourceName = null,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true
        )
    }
}
