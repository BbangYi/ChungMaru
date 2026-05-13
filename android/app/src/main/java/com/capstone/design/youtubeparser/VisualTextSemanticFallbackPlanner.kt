package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object VisualTextSemanticFallbackPlanner {
    private const val HERO_TOP_REGION_RATIO = 0.42f
    private const val HERO_MIN_WIDTH_RATIO = 0.48f
    private const val HERO_MIN_HEIGHT_PX = 180
    private const val HERO_MASK_LEFT_RATIO = 0.05f
    private const val HERO_BANNER_MASK_TOP_RATIO = 0.12f
    private const val HERO_BANNER_MASK_HEIGHT_RATIO = 0.14f
    private const val HERO_TITLE_MASK_TOP_RATIO = 0.39f
    private const val HERO_TITLE_MASK_HEIGHT_RATIO = 0.16f
    private const val HERO_TITLE_PREFIX_MAX_CHARS = 110
    private const val VISIBLE_BAND_TOP_REGION_RATIO = 0.36f
    private const val VISIBLE_BAND_MIN_WIDTH_RATIO = 0.70f
    private const val VISIBLE_BAND_MIN_HEIGHT_PX = 220
    private const val VISIBLE_BAND_BANNER_MASK_TOP_RATIO = 0.12f
    private const val VISIBLE_BAND_BANNER_MASK_HEIGHT_RATIO = 0.08f
    private const val VISIBLE_BAND_TITLE_MASK_TOP_RATIO = 0.40f
    private const val VISIBLE_BAND_TITLE_MASK_HEIGHT_RATIO = 0.11f
    private const val VISIBLE_BAND_MASK_MAX_WIDTH_RATIO = 0.34f
    private const val DEFAULT_MASK_MIN_HEIGHT_PX = 34
    private const val DEFAULT_MASK_MAX_HEIGHT_PX = 56
    private const val VISIBLE_BAND_BANNER_MASK_MAX_HEIGHT_PX = 44
    private const val VISIBLE_BAND_TITLE_MASK_MIN_HEIGHT_PX = 48
    private const val VISIBLE_BAND_TITLE_MASK_MAX_HEIGHT_PX = 72
    private const val YOUTUBE_USER_INPUT_AUTHOR_ID = "android-accessibility:youtube_user_input"

    private data class HeroMaskBand(
        val topRatio: Float,
        val heightRatio: Float,
        val minHeightPx: Int = DEFAULT_MASK_MIN_HEIGHT_PX,
        val maxHeightPx: Int = DEFAULT_MASK_MAX_HEIGHT_PX,
        val leftRatio: Float = HERO_MASK_LEFT_RATIO,
        val minLeftPx: Int = 24,
        val maxWidthRatio: Float? = null
    )

    private val heroMaskBands = listOf(
        HeroMaskBand(
            topRatio = HERO_BANNER_MASK_TOP_RATIO,
            heightRatio = HERO_BANNER_MASK_HEIGHT_RATIO
        ),
        HeroMaskBand(
            topRatio = HERO_TITLE_MASK_TOP_RATIO,
            heightRatio = HERO_TITLE_MASK_HEIGHT_RATIO
        )
    )

    private val visibleBandHeroMaskBands = listOf(
        HeroMaskBand(
            topRatio = VISIBLE_BAND_BANNER_MASK_TOP_RATIO,
            heightRatio = VISIBLE_BAND_BANNER_MASK_HEIGHT_RATIO,
            maxHeightPx = VISIBLE_BAND_BANNER_MASK_MAX_HEIGHT_PX,
            maxWidthRatio = VISIBLE_BAND_MASK_MAX_WIDTH_RATIO
        ),
        HeroMaskBand(
            topRatio = VISIBLE_BAND_TITLE_MASK_TOP_RATIO,
            heightRatio = VISIBLE_BAND_TITLE_MASK_HEIGHT_RATIO,
            minHeightPx = VISIBLE_BAND_TITLE_MASK_MIN_HEIGHT_PX,
            maxHeightPx = VISIBLE_BAND_TITLE_MASK_MAX_HEIGHT_PX,
            maxWidthRatio = VISIBLE_BAND_MASK_MAX_WIDTH_RATIO
        )
    )

    fun selectCandidates(
        visualRoiPlan: VisualTextRoiPlan,
        screenWidth: Int,
        screenHeight: Int,
        baseResponse: AndroidAnalysisResponse? = null
    ): List<ParsedComment> {
        val baseHeroRange = topHeroBaseRange(baseResponse)
        val visibleBandRange = baseHeroRange
        return visualRoiPlan.rois.flatMap { roi ->
            semanticTopHeroCandidates(
                roi = roi,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            ) + semanticVisibleBandHeroCandidates(
                roi = roi,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                range = visibleBandRange
            ) + semanticCompositeHeroCandidatesFromBaseTitle(
                roi = roi,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                range = baseHeroRange
            )
        }
    }

    private fun semanticTopHeroCandidates(
        roi: VisualTextRoi,
        screenWidth: Int,
        screenHeight: Int
    ): List<ParsedComment> {
        if (!isTopHeroSemanticRoi(roi, screenWidth, screenHeight)) return emptyList()
        val range = VisualTextOcrCandidateFilter.findAnalysisRanges(roi.sourceText)
            .firstOrNull { candidateRange ->
                isLikelyHeroTitleTextHit(roi.sourceText, candidateRange)
            }
            ?: return emptyList()

        return heroMaskBands.mapNotNull { band ->
            val bounds = semanticTopHeroMaskBounds(
                roi = roi,
                visualText = range.visualText,
                band = band
            ) ?: return@mapNotNull null

            ParsedComment(
                commentText = range.analysisText,
                boundsInScreen = bounds,
                authorId = VisualTextOcrMetadataCodec.encode(
                    source = roi.source,
                    roiBoundsInScreen = roi.boundsInScreen,
                    visualText = range.visualText
                )
            )
        }
    }

    private fun semanticCompositeHeroCandidatesFromBaseTitle(
        roi: VisualTextRoi,
        screenWidth: Int,
        screenHeight: Int,
        range: VisualTextOcrCandidateFilter.CandidateRange?
    ): List<ParsedComment> {
        if (range == null) return emptyList()
        if (!isTopHeroSemanticRoi(roi, screenWidth, screenHeight)) return emptyList()

        return heroMaskBands.mapNotNull { band ->
            val bounds = semanticTopHeroMaskBounds(
                roi = roi,
                visualText = range.visualText,
                band = band
            ) ?: return@mapNotNull null

            ParsedComment(
                commentText = range.analysisText,
                boundsInScreen = bounds,
                authorId = VisualTextOcrMetadataCodec.encode(
                    source = roi.source,
                    roiBoundsInScreen = roi.boundsInScreen,
                    visualText = range.visualText
                )
            )
        }
    }

    private fun semanticVisibleBandHeroCandidates(
        roi: VisualTextRoi,
        screenWidth: Int,
        screenHeight: Int,
        range: VisualTextOcrCandidateFilter.CandidateRange?
    ): List<ParsedComment> {
        if (range == null) return emptyList()
        if (!isTopHeroVisibleBandRoi(roi, screenWidth, screenHeight)) return emptyList()

        return visibleBandHeroMaskBands.mapNotNull { band ->
            val bounds = semanticTopHeroMaskBounds(
                roi = roi,
                visualText = range.visualText,
                band = band
            ) ?: return@mapNotNull null

            ParsedComment(
                commentText = range.analysisText,
                boundsInScreen = bounds,
                authorId = VisualTextOcrMetadataCodec.encode(
                    source = roi.source,
                    roiBoundsInScreen = roi.boundsInScreen,
                    visualText = range.visualText
                )
            )
        }
    }

    private fun isTopHeroSemanticRoi(
        roi: VisualTextRoi,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (roi.source != "youtube-composite-card") return false

        val bounds = roi.boundsInScreen
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        return bounds.top < (screenHeight * HERO_TOP_REGION_RATIO).roundToInt() &&
            width >= (screenWidth * HERO_MIN_WIDTH_RATIO).roundToInt() &&
            height >= HERO_MIN_HEIGHT_PX
    }

    private fun isTopHeroVisibleBandRoi(
        roi: VisualTextRoi,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (roi.source != "youtube-visible-band") return false

        val bounds = roi.boundsInScreen
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        return bounds.top < (screenHeight * VISIBLE_BAND_TOP_REGION_RATIO).roundToInt() &&
            width >= (screenWidth * VISIBLE_BAND_MIN_WIDTH_RATIO).roundToInt() &&
            height >= VISIBLE_BAND_MIN_HEIGHT_PX
    }

    private fun topHeroBaseRange(
        baseResponse: AndroidAnalysisResponse?
    ): VisualTextOcrCandidateFilter.CandidateRange? {
        return baseResponse
            ?.results
            .orEmpty()
            .asSequence()
            .filter { item -> item.isOffensive && item.evidenceSpans.isNotEmpty() }
            .filterNot { item -> item.authorId == YOUTUBE_USER_INPUT_AUTHOR_ID }
            .mapNotNull { item ->
                VisualTextOcrCandidateFilter.findAnalysisRanges(item.original)
                    .firstOrNull { range ->
                        isLikelyHeroTitleTextHit(item.original, range)
                    }
            }
            .firstOrNull()
    }

    private fun isLikelyHeroTitleTextHit(
        sourceText: String,
        range: VisualTextOcrCandidateFilter.CandidateRange
    ): Boolean {
        val before = sourceText.substring(0, range.start.coerceIn(0, sourceText.length))
        val compactPrefix = before
            .lowercase()
            .replace(Regex("""[\s"'`“”‘’.,!?_\-\[\]\(\):|]+"""), "")

        return compactPrefix.length <= 18 ||
            (compactPrefix.contains("playvideo") && compactPrefix.length <= 32) ||
            (compactPrefix.contains("동영상재생") && compactPrefix.length <= 24) ||
            (
                before.codePointCount(0, before.length) <= HERO_TITLE_PREFIX_MAX_CHARS &&
                    !looksLikeMetadataBeforeHeroTitle(before)
                )
    }

    private fun looksLikeMetadataBeforeHeroTitle(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains(" views") ||
            lower.contains(" view") ||
            lower.contains("조회수") ||
            lower.contains("go to channel") ||
            Regex("""\b\d+(?:\.\d+)?\s*(?:k|m|b|thousand|million|billion)\s+views?\b""").containsMatchIn(lower) ||
            Regex("""\b\d+\s+(?:second|seconds|minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)\s+ago\b""").containsMatchIn(lower) ||
            Regex("""\d+\s*(?:초|분|시간|일|주|개월|년)\s*전""").containsMatchIn(text)
    }

    private fun semanticTopHeroMaskBounds(
        roi: VisualTextRoi,
        visualText: String,
        band: HeroMaskBand
    ): BoundsRect? {
        val roiBounds = roi.boundsInScreen
        val roiWidth = roiBounds.right - roiBounds.left
        val roiHeight = roiBounds.bottom - roiBounds.top
        if (roiWidth <= 0 || roiHeight <= 0) return null

        val height = (roiHeight * band.heightRatio)
            .roundToInt()
            .coerceIn(band.minHeightPx, band.maxHeightPx)
        val left = roiBounds.left + max(band.minLeftPx, (roiWidth * band.leftRatio).roundToInt())
        val top = roiBounds.top + (roiHeight * band.topRatio).roundToInt()
        val maxWidth = band.maxWidthRatio
            ?.let { ratio -> (roiWidth * ratio).roundToInt() }
            ?.coerceAtLeast(96)
            ?: (roiWidth - (left - roiBounds.left))
        val width = estimateSemanticVisualMaskWidth(
            visualText = visualText,
            textHeight = height,
            maxWidth = min(maxWidth, roiWidth - (left - roiBounds.left))
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
