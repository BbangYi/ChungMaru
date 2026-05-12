package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class VisualTextCandidate(
    val text: String,
    val boundsInScreen: BoundsRect,
    val confidence: Float,
    val source: String = "ocr"
) {
    fun toParsedComment(): ParsedComment {
        return ParsedComment(
            commentText = text,
            boundsInScreen = boundsInScreen,
            authorId = source
        )
    }
}

data class VisualTextAnalysisResult(
    val candidate: VisualTextCandidate,
    val result: AndroidAnalysisResultItem
)

object VisualTextMaskPlanner {
    private const val MIN_CONFIDENCE = 0.55f
    private const val MIN_WIDTH_PX = 18
    private const val MIN_HEIGHT_PX = 12
    private const val MIN_MASK_WIDTH_PX = 22
    private const val MAX_VISUAL_TEXT_LENGTH = 120
    private const val MAX_VISUAL_TEXT_HEIGHT_PX = 96
    private const val MAX_VISUAL_TEXT_AREA_RATIO = 0.06f
    private const val HORIZONTAL_PADDING_PX = 4
    private const val KOREAN_SPAN_CHAR_WIDTH_PX = 28
    private const val LATIN_SPAN_CHAR_WIDTH_PX = 14
    private const val KOREAN_SPAN_MAX_CHAR_WIDTH_PX = 28
    private const val LATIN_SPAN_MAX_CHAR_WIDTH_PX = 14
    private const val KOREAN_SPAN_HEIGHT_WIDTH_RATIO = 0.56f
    private const val LATIN_SPAN_HEIGHT_WIDTH_RATIO = 0.38f
    private const val MAX_COMPACT_KOREAN_SPAN_WIDTH_PX = 112
    private const val MAX_COMPACT_LATIN_SPAN_WIDTH_PX = 84
    private const val MAX_SPAN_MASK_HEIGHT_PX = 32
    private const val COMPACT_SPAN_CODEPOINT_LIMIT = 8
    private const val LEADING_SPAN_PREFIX_TOLERANCE = 2
    private const val MASK_LABEL = "***"

    fun buildPlan(
        analyzedCandidates: List<VisualTextAnalysisResult>,
        screenWidth: Int,
        screenHeight: Int
    ): MaskOverlayPlan {
        if (screenWidth <= 0 || screenHeight <= 0 || analyzedCandidates.isEmpty()) {
            return MaskOverlayPlan(
                specs = emptyList(),
                candidateCount = 0,
                skippedUnstableCount = 0,
                suppressedOverlapCount = 0
            )
        }

        var candidateCount = 0
        var skippedUnstableCount = 0
        val specs = mutableListOf<MaskOverlaySpec>()

        analyzedCandidates.forEach { analyzed ->
            val result = analyzed.result
            if (!result.isOffensive || result.evidenceSpans.isEmpty()) return@forEach

            candidateCount += 1
            val candidateSpecs = toSpecs(analyzed, screenWidth, screenHeight)
            if (candidateSpecs.isEmpty()) {
                skippedUnstableCount += 1
            } else {
                specs += candidateSpecs
            }
        }

        return MaskOverlayPlan(
            specs = specs.distinctBy { "${it.left}|${it.top}|${it.width}|${it.height}|${it.label}" },
            candidateCount = candidateCount,
            skippedUnstableCount = skippedUnstableCount,
            suppressedOverlapCount = 0
        )
    }

    private fun toSpecs(
        analyzed: VisualTextAnalysisResult,
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        val candidate = analyzed.candidate
        val text = analyzed.result.original.ifBlank { candidate.text }
        val textLength = text.codePointCount(0, text.length)
        if (textLength <= 0 || textLength > MAX_VISUAL_TEXT_LENGTH) return emptyList()
        if (candidate.confidence < MIN_CONFIDENCE) return emptyList()

        val fullSpec = toScreenSpec(candidate.boundsInScreen, screenWidth, screenHeight) ?: return emptyList()
        if (!isVisualTextBoundsStable(fullSpec, screenWidth, screenHeight)) return emptyList()

        return analyzed.result.evidenceSpans.mapNotNull { span ->
            toSpanSpec(fullSpec, span, textLength)
        }
    }

    private fun toScreenSpec(bounds: BoundsRect, screenWidth: Int, screenHeight: Int): MaskOverlaySpec? {
        val left = max(0, min(bounds.left, screenWidth))
        val top = max(0, min(bounds.top, screenHeight))
        val right = max(left, min(bounds.right, screenWidth))
        val bottom = max(top, min(bounds.bottom, screenHeight))
        val width = right - left
        val height = bottom - top

        if (width < MIN_WIDTH_PX || height < MIN_HEIGHT_PX) return null

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = MASK_LABEL,
            allowScrollTranslation = false
        )
    }

    private fun isVisualTextBoundsStable(
        spec: MaskOverlaySpec,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (spec.height > MAX_VISUAL_TEXT_HEIGHT_PX) return false

        val screenArea = (screenWidth * screenHeight).coerceAtLeast(1)
        val specArea = spec.width * spec.height
        return specArea.toFloat() / screenArea.toFloat() <= MAX_VISUAL_TEXT_AREA_RATIO
    }

    private fun toSpanSpec(
        fullSpec: MaskOverlaySpec,
        span: EvidenceSpan,
        originalLength: Int
    ): MaskOverlaySpec? {
        val start = span.start.coerceIn(0, originalLength)
        val end = span.end.coerceIn(start, originalLength)
        if (end <= start) return null

        val startRatio = start.toFloat() / originalLength.toFloat()
        val endRatio = end.toFloat() / originalLength.toFloat()
        val rawLeft = fullSpec.left + (fullSpec.width * startRatio).roundToInt()
        val rawRight = fullSpec.left + (fullSpec.width * endRatio).roundToInt()

        val labelText = span.text.ifBlank { MASK_LABEL }
        val minWidth = minOf(
            fullSpec.width,
            maxOf(MIN_MASK_WIDTH_PX, labelText.codePointCount(0, labelText.length) * 16)
        )
        val center = (rawLeft + rawRight) / 2
        var left = rawLeft - HORIZONTAL_PADDING_PX
        var right = rawRight + HORIZONTAL_PADDING_PX

        if (right - left < minWidth) {
            left = center - minWidth / 2
            right = left + minWidth
        }

        if (left < fullSpec.left) {
            right += fullSpec.left - left
            left = fullSpec.left
        }
        if (right > fullSpec.left + fullSpec.width) {
            left -= right - (fullSpec.left + fullSpec.width)
            right = fullSpec.left + fullSpec.width
        }
        left = left.coerceAtLeast(fullSpec.left)
        right = right.coerceAtMost(fullSpec.left + fullSpec.width)

        val maxSpanWidth = estimateMaxSpanMaskWidth(
            spanText = labelText,
            fullSpecWidth = fullSpec.width,
            textHeight = fullSpec.height
        )
        if (right - left > maxSpanWidth) {
            val fullRight = fullSpec.left + fullSpec.width
            left = when {
                start <= LEADING_SPAN_PREFIX_TOLERANCE -> rawLeft - HORIZONTAL_PADDING_PX
                end >= originalLength -> rawRight + HORIZONTAL_PADDING_PX - maxSpanWidth
                else -> center - maxSpanWidth / 2
            }
            right = left + maxSpanWidth

            if (left < fullSpec.left) {
                right += fullSpec.left - left
                left = fullSpec.left
            }
            if (right > fullRight) {
                left -= right - fullRight
                right = fullRight
            }
            left = left.coerceAtLeast(fullSpec.left)
            right = right.coerceAtMost(fullRight)
        }

        val width = right - left
        if (width < MIN_WIDTH_PX) return null

        val height = minOf(fullSpec.height, MAX_SPAN_MASK_HEIGHT_PX).coerceAtLeast(MIN_HEIGHT_PX)
        val top = fullSpec.top + ((fullSpec.height - height) / 2).coerceAtLeast(0)

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = MASK_LABEL,
            allowScrollTranslation = false
        )
    }

    private fun estimateMaxSpanMaskWidth(
        spanText: String,
        fullSpecWidth: Int,
        textHeight: Int
    ): Int {
        val visibleText = spanText.ifBlank { MASK_LABEL }
        val codePointLength = visibleText.codePointCount(0, visibleText.length).coerceAtLeast(1)
        val hasKorean = visibleText.any { it.code in 0xAC00..0xD7A3 }
        val scaledCharWidth = if (hasKorean) {
            max(
                KOREAN_SPAN_CHAR_WIDTH_PX,
                min(KOREAN_SPAN_MAX_CHAR_WIDTH_PX, (textHeight * KOREAN_SPAN_HEIGHT_WIDTH_RATIO).roundToInt())
            )
        } else {
            max(
                LATIN_SPAN_CHAR_WIDTH_PX,
                min(LATIN_SPAN_MAX_CHAR_WIDTH_PX, (textHeight * LATIN_SPAN_HEIGHT_WIDTH_RATIO).roundToInt())
            )
        }
        val estimatedWidth = codePointLength * scaledCharWidth
        val compactCap = if (codePointLength <= COMPACT_SPAN_CODEPOINT_LIMIT) {
            max(
                if (hasKorean) MAX_COMPACT_KOREAN_SPAN_WIDTH_PX else MAX_COMPACT_LATIN_SPAN_WIDTH_PX,
                estimatedWidth + HORIZONTAL_PADDING_PX * 2
            )
        } else {
            fullSpecWidth
        }

        return minOf(
            fullSpecWidth,
            maxOf(MIN_MASK_WIDTH_PX, minOf(estimatedWidth + HORIZONTAL_PADDING_PX * 2, compactCap))
        )
    }
}
