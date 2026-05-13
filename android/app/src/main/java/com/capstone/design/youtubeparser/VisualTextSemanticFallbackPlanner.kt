package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object VisualTextSemanticFallbackPlanner {
    private const val HERO_TOP_REGION_RATIO = 0.42f
    private const val HERO_MIN_WIDTH_RATIO = 0.48f
    private const val HERO_MIN_HEIGHT_PX = 180
    private const val HERO_MASK_TOP_RATIO = 0.43f
    private const val HERO_MASK_LEFT_RATIO = 0.05f
    private const val HERO_MASK_HEIGHT_RATIO = 0.15f

    fun selectCandidates(
        visualRoiPlan: VisualTextRoiPlan,
        screenWidth: Int,
        screenHeight: Int
    ): List<ParsedComment> {
        return visualRoiPlan.rois.mapNotNull { roi ->
            semanticTopHeroCandidate(
                roi = roi,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        }
    }

    private fun semanticTopHeroCandidate(
        roi: VisualTextRoi,
        screenWidth: Int,
        screenHeight: Int
    ): ParsedComment? {
        if (!isTopHeroSemanticRoi(roi, screenWidth, screenHeight)) return null
        val range = VisualTextOcrCandidateFilter.findAnalysisRanges(roi.sourceText)
            .firstOrNull { candidateRange ->
                isLikelyLeadingHeroTextHit(roi.sourceText, candidateRange)
            }
            ?: return null

        val bounds = semanticTopHeroMaskBounds(
            roi = roi,
            visualText = range.visualText
        ) ?: return null

        return ParsedComment(
            commentText = range.analysisText,
            boundsInScreen = bounds,
            authorId = VisualTextOcrMetadataCodec.encode(
                source = roi.source,
                roiBoundsInScreen = roi.boundsInScreen,
                visualText = range.visualText
            )
        )
    }

    private fun isTopHeroSemanticRoi(
        roi: VisualTextRoi,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (roi.source != "youtube-composite-card") return false
        if (roi.sourceText.isBlank()) return false

        val bounds = roi.boundsInScreen
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        return bounds.top < (screenHeight * HERO_TOP_REGION_RATIO).roundToInt() &&
            width >= (screenWidth * HERO_MIN_WIDTH_RATIO).roundToInt() &&
            height >= HERO_MIN_HEIGHT_PX
    }

    private fun isLikelyLeadingHeroTextHit(
        sourceText: String,
        range: VisualTextOcrCandidateFilter.CandidateRange
    ): Boolean {
        val before = sourceText.substring(0, range.start.coerceIn(0, sourceText.length))
        val compactPrefix = before
            .lowercase()
            .replace(Regex("""[\s"'`“”‘’.,!?_\-\[\]\(\):|]+"""), "")

        return compactPrefix.length <= 18 ||
            (compactPrefix.contains("playvideo") && compactPrefix.length <= 32) ||
            (compactPrefix.contains("동영상재생") && compactPrefix.length <= 24)
    }

    private fun semanticTopHeroMaskBounds(
        roi: VisualTextRoi,
        visualText: String
    ): BoundsRect? {
        val roiBounds = roi.boundsInScreen
        val roiWidth = roiBounds.right - roiBounds.left
        val roiHeight = roiBounds.bottom - roiBounds.top
        if (roiWidth <= 0 || roiHeight <= 0) return null

        val height = (roiHeight * HERO_MASK_HEIGHT_RATIO)
            .roundToInt()
            .coerceIn(34, 56)
        val left = roiBounds.left + max(24, (roiWidth * HERO_MASK_LEFT_RATIO).roundToInt())
        val top = roiBounds.top + (roiHeight * HERO_MASK_TOP_RATIO).roundToInt()
        val width = estimateSemanticVisualMaskWidth(
            visualText = visualText,
            textHeight = height,
            maxWidth = roiWidth - (left - roiBounds.left)
        )
        val right = min(roiBounds.right, left + width)
        val bottom = min(roiBounds.bottom, top + height)
        if (right - left < 24 || bottom - top < 16) return null

        return BoundsRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    }

    private fun estimateSemanticVisualMaskWidth(
        visualText: String,
        textHeight: Int,
        maxWidth: Int
    ): Int {
        val text = visualText.ifBlank { "tlqkf" }
        val length = text.codePointCount(0, text.length).coerceAtLeast(1)
        val hasKorean = text.any { it.code in 0xAC00..0xD7A3 }
        val charWidth = if (hasKorean) {
            max(28, (textHeight * 0.86f).roundToInt())
        } else {
            max(28, (textHeight * 0.76f).roundToInt())
        }
        return (length * charWidth + 18).coerceIn(96, max(96, maxWidth))
    }
}
