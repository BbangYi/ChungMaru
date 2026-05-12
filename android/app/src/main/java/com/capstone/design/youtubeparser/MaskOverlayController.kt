package com.capstone.design.youtubeparser

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MaskOverlaySpec(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val label: String,
    val allowScrollTranslation: Boolean = true,
    val debugSource: String = ""
)

data class MaskOverlayPlan(
    val specs: List<MaskOverlaySpec>,
    val candidateCount: Int,
    val skippedUnstableCount: Int,
    val suppressedOverlapCount: Int,
    val renderedSamples: List<String> = emptyList()
)

object AndroidMaskOverlayPlanner {
    private const val MIN_WIDTH_PX = 24
    private const val MIN_HEIGHT_PX = 16
    private const val MIN_SPAN_MASK_WIDTH_PX = 24
    private const val SPAN_HORIZONTAL_PADDING_PX = 4
    private const val MAX_SPAN_MASK_HEIGHT_PX = 32
    private const val KOREAN_SPAN_CHAR_WIDTH_PX = 28
    private const val LATIN_SPAN_CHAR_WIDTH_PX = 14
    private const val KOREAN_SPAN_MAX_CHAR_WIDTH_PX = 28
    private const val LATIN_SPAN_MAX_CHAR_WIDTH_PX = 14
    private const val KOREAN_SPAN_HEIGHT_WIDTH_RATIO = 0.56f
    private const val LATIN_SPAN_HEIGHT_WIDTH_RATIO = 0.38f
    private const val PRECISE_VISUAL_KOREAN_WIDTH_RATIO = 0.86f
    private const val PRECISE_VISUAL_LATIN_WIDTH_RATIO = 0.76f
    private const val PRECISE_VISUAL_WIDTH_PADDING_PX = 10
    private const val MAX_COMPACT_KOREAN_SPAN_WIDTH_PX = 112
    private const val MAX_COMPACT_LATIN_SPAN_WIDTH_PX = 84
    private const val COMPACT_SPAN_CODEPOINT_LIMIT = 8
    private const val LEADING_SPAN_PREFIX_TOLERANCE = 2
    private const val ESTIMATED_LINE_HEIGHT_PX = 34
    private const val MAX_MASK_COUNT = 24
    private const val MAX_SCREEN_WIDTH_RATIO = 0.88f
    private const val MAX_SCREEN_HEIGHT_RATIO = 0.22f
    private const val MAX_HIGH_CONFIDENCE_HEIGHT_PX = 160
    private const val MAX_HIGH_CONFIDENCE_TEXT_LENGTH = 180
    private const val MAX_HIGH_CONFIDENCE_AREA_RATIO = 0.09f
    private const val MAX_ACCESSIBILITY_SOURCE_HEIGHT_PX = 360
    private const val MAX_ACCESSIBILITY_SOURCE_TEXT_LENGTH = 420
    private const val MAX_ACCESSIBILITY_SOURCE_AREA_RATIO = 0.14f
    private const val MAX_ESTIMATED_ACCESSIBILITY_TEXT_LENGTH = 96
    private const val MAX_ESTIMATED_ACCESSIBILITY_HEIGHT_PX = 96
    private const val MAX_ESTIMATED_ACCESSIBILITY_LINE_COUNT = 2
    private const val MAX_ESTIMATED_ACCESSIBILITY_WIDTH_RATIO = 0.78f
    private const val MAX_ACCESSIBILITY_RANGE_WIDTH_PX = 180
    private const val MAX_ACCESSIBILITY_RANGE_HEIGHT_PX = 64
    private const val MAX_UNSOURCED_LONG_TEXT_LENGTH = 70
    private const val MAX_UNSOURCED_LONG_TEXT_HEIGHT_PX = 72
    private const val MAX_VISUAL_SOURCE_HEIGHT_PX = 110
    private const val MAX_VISUAL_SOURCE_AREA_RATIO = 0.08f
    private const val MAX_COMPOSITE_SOURCE_HEIGHT_PX = 132
    private const val MAX_COMPOSITE_SOURCE_AREA_RATIO = 0.06f
    private const val NEAR_DUPLICATE_OVERLAP_RATIO = 0.25f
    private const val MIN_SCROLL_TRANSLATION_DELTA_PX = 96
    private const val MAX_SCROLL_TRANSLATION_AXIS_RATIO = 0.25f
    private const val TOP_CONTROL_REGION_RATIO = 0.14f
    private const val TOP_CONTROL_REGION_MAX_PX = 220
    private const val TOP_GENERIC_VISUAL_CONTROL_REGION_RATIO = 0.26f
    private const val TOP_GENERIC_VISUAL_CONTROL_REGION_MAX_PX = 360
    private const val TOP_USER_INPUT_REGION_RATIO = 0.24f
    private const val TOP_USER_INPUT_REGION_MAX_PX = 360

    fun buildSpecs(
        response: AndroidAnalysisResponse?,
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        return buildPlan(response, screenWidth, screenHeight).specs
    }

    fun buildPlan(
        response: AndroidAnalysisResponse?,
        screenWidth: Int,
        screenHeight: Int
    ): MaskOverlayPlan {
        if (response == null || screenWidth <= 0 || screenHeight <= 0) {
            return MaskOverlayPlan(
                specs = emptyList(),
                candidateCount = 0,
                skippedUnstableCount = 0,
                suppressedOverlapCount = 0
            )
        }

        var candidateCount = 0
        var skippedUnstableCount = 0
        val rawSpecs = mutableListOf<MaskOverlaySpec>()

        response.results
            .asSequence()
            .filter { it.isOffensive && it.evidenceSpans.isNotEmpty() }
            .forEach { item ->
                candidateCount += 1
                val specs = toSpecs(item, screenWidth, screenHeight)
                if (specs.isEmpty()) {
                    skippedUnstableCount += 1
                } else {
                    rawSpecs += specs
                }
            }

        val suppressedSpecs = suppressOverlappingSpecs(rawSpecs)
        val finalSpecs = suppressedSpecs.take(MAX_MASK_COUNT)

        return MaskOverlayPlan(
            specs = finalSpecs,
            candidateCount = candidateCount,
            skippedUnstableCount = skippedUnstableCount,
            suppressedOverlapCount = (rawSpecs.size - finalSpecs.size).coerceAtLeast(0),
            renderedSamples = finalSpecs.mapNotNull { spec ->
                spec.debugSource.takeIf { it.isNotBlank() }
            }.take(6)
        )
    }

    fun signature(specs: List<MaskOverlaySpec>): String {
        return specs.joinToString("|") {
            "${it.left},${it.top},${it.width},${it.height},${it.label},${it.allowScrollTranslation}"
        }
    }

    fun translateSpecs(
        specs: List<MaskOverlaySpec>,
        deltaX: Int,
        deltaY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        if (specs.isEmpty() || screenWidth <= 0 || screenHeight <= 0) return emptyList()
        if (deltaX == 0 && deltaY == 0) return specs
        val maxDeltaX = maxScrollTranslationDeltaPx(screenWidth)
        val maxDeltaY = maxScrollTranslationDeltaPx(screenHeight)
        if (abs(deltaX) > maxDeltaX || abs(deltaY) > maxDeltaY) {
            return emptyList()
        }

        return specs.mapNotNull { spec ->
            if (!spec.allowScrollTranslation) {
                return@mapNotNull null
            }

            val nextLeft = spec.left + deltaX
            val nextTop = spec.top + deltaY
            val nextRight = nextLeft + spec.width
            val nextBottom = nextTop + spec.height

            if (
                nextRight <= 0 ||
                nextBottom <= 0 ||
                nextLeft >= screenWidth ||
                nextTop >= screenHeight
            ) {
                null
            } else {
                spec.copy(left = nextLeft, top = nextTop)
            }
        }
    }

    private fun toSpecs(
        item: AndroidAnalysisResultItem,
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        val fullSpec = toSpec(item.boundsInScreen, screenWidth, screenHeight) ?: return emptyList()
        val originalLength = item.original.codePointCount(0, item.original.length)
        if (originalLength <= 0) return emptyList()
        if (!hasHighConfidenceTextBounds(
                spec = fullSpec,
                originalLength = originalLength,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                authorId = item.authorId
            )
        ) {
            return emptyList()
        }

        val allowScrollTranslation = shouldAllowScrollTranslation(item.authorId)
        val preciseVisualBounds = isPreciseVisualAuthor(item.authorId)
        val visualTextForSizing = if (preciseVisualBounds) {
            VisualTextOcrMetadataCodec.decode(item.authorId)?.visualText
        } else {
            null
        }
        val debugSource = buildDebugSource(item)
        val spanSpecs = item.evidenceSpans.mapNotNull { span ->
            toSpanSpec(
                fullSpec = fullSpec,
                span = span,
                original = item.original,
                originalLength = originalLength,
                allowScrollTranslation = allowScrollTranslation,
                preciseVisualBounds = preciseVisualBounds,
                visualTextForSizing = visualTextForSizing,
                debugSource = debugSource
            )
        }

        return spanSpecs
    }

    private fun shouldAllowScrollTranslation(authorId: String?): Boolean {
        // Only explicit input fields and exact OCR boxes are stable enough to
        // translate. Coarse accessibility rows still get dropped and reanalyzed.
        val value = authorId ?: return false
        return value == "android-accessibility:user_input" ||
            isPreciseVisualAuthor(value)
    }

    private fun hasHighConfidenceTextBounds(
        spec: MaskOverlaySpec,
        originalLength: Int,
        screenWidth: Int,
        screenHeight: Int,
        authorId: String?
    ): Boolean {
        val accessibilityAuthor = isAccessibilityAuthor(authorId)
        if (originalLength > MAX_HIGH_CONFIDENCE_TEXT_LENGTH && !accessibilityAuthor) return false
        if (spec.height > MAX_HIGH_CONFIDENCE_HEIGHT_PX && !accessibilityAuthor) return false

        val screenArea = (screenWidth * screenHeight).coerceAtLeast(1)
        val specArea = spec.width * spec.height
        val areaRatio = specArea.toFloat() / screenArea.toFloat()

        if (!accessibilityAuthor && areaRatio > MAX_HIGH_CONFIDENCE_AREA_RATIO) {
            return false
        }

        if (isFallbackVisualAuthor(authorId)) {
            return false
        }

        if (isTopControlMask(spec, screenWidth, screenHeight, authorId)) {
            return false
        }

        if (isPreciseVisualAuthor(authorId)) {
            return spec.height <= MAX_VISUAL_SOURCE_HEIGHT_PX &&
                areaRatio <= MAX_VISUAL_SOURCE_AREA_RATIO
        }

        if (isCompositeYoutubeAuthor(authorId)) {
            return spec.height <= MAX_COMPOSITE_SOURCE_HEIGHT_PX &&
                areaRatio <= MAX_COMPOSITE_SOURCE_AREA_RATIO
        }

        if (accessibilityAuthor) {
            if (!hasStableAccessibilityGeometry(spec, originalLength, screenWidth, authorId)) {
                return false
            }
            return originalLength <= MAX_ACCESSIBILITY_SOURCE_TEXT_LENGTH &&
                spec.height <= MAX_ACCESSIBILITY_SOURCE_HEIGHT_PX &&
                areaRatio <= MAX_ACCESSIBILITY_SOURCE_AREA_RATIO
        }

        if (originalLength > MAX_UNSOURCED_LONG_TEXT_LENGTH &&
            spec.height > MAX_UNSOURCED_LONG_TEXT_HEIGHT_PX
        ) {
            return false
        }

        return true
    }

    private fun hasStableAccessibilityGeometry(
        spec: MaskOverlaySpec,
        originalLength: Int,
        screenWidth: Int,
        authorId: String?
    ): Boolean {
        if (authorId == "android-accessibility:user_input") {
            return true
        }
        if (isAccessibilityRangeAuthor(authorId)) {
            return spec.width <= MAX_ACCESSIBILITY_RANGE_WIDTH_PX &&
                spec.height <= MAX_ACCESSIBILITY_RANGE_HEIGHT_PX
        }
        if (isBrowserAccessibilityAuthor(authorId)) {
            // Browser accessibility nodes are reliable context, not reliable word geometry.
            // Chrome/Firefox often expose row, snippet, or card bounds as a short text node,
            // which caused floating masks on scroll. Keep these candidates analysis-only until
            // OCR/range projection can provide an exact visual box.
            return false
        }
        if (isGenericScreenAccessibilityAuthor(authorId)) {
            // ScreenTextCandidateExtractor emits this generic source for cross-app
            // context collection. The bounds can be a row/card/container instead
            // of a word box, so rendering it directly creates detached floating
            // masks. Keep it analysis-only unless a precise range/OCR source exists.
            return false
        }

        val estimatedLineCount = estimateLineCount(spec.height, originalLength)
        if (estimatedLineCount > MAX_ESTIMATED_ACCESSIBILITY_LINE_COUNT) {
            return false
        }
        if (
            originalLength > MAX_ESTIMATED_ACCESSIBILITY_TEXT_LENGTH &&
            spec.height > MAX_ESTIMATED_ACCESSIBILITY_HEIGHT_PX
        ) {
            return false
        }
        if (
            spec.width > (screenWidth * MAX_ESTIMATED_ACCESSIBILITY_WIDTH_RATIO).roundToInt() &&
            spec.height > MAX_SPAN_MASK_HEIGHT_PX &&
            originalLength > MAX_UNSOURCED_LONG_TEXT_LENGTH
        ) {
            return false
        }

        return true
    }

    private fun isPreciseVisualAuthor(authorId: String?): Boolean {
        val value = authorId ?: return false
        return value.startsWith("ocr:youtube-composite-card:") ||
            value.startsWith("ocr:youtube-visible-band:")
    }

    private fun isFallbackVisualAuthor(authorId: String?): Boolean {
        val value = authorId ?: return false
        return (value.startsWith("ocr:") && !isPreciseVisualAuthor(value)) ||
            value.startsWith("youtube-visual-range:")
    }

    private fun isGenericVisualAuthor(authorId: String?): Boolean {
        return authorId?.startsWith("ocr:generic-visual-region:") == true
    }

    private fun isCompositeYoutubeAuthor(authorId: String?): Boolean {
        return authorId == "youtube-composite-description"
    }

    private fun isAccessibilityAuthor(authorId: String?): Boolean {
        val value = authorId ?: return false
        return value.startsWith("android-accessibility:") ||
            value.startsWith("android-accessibility-range:") ||
            value.startsWith("android-accessibility-browser:") ||
            value.startsWith("screen:accessibility_text:")
    }

    private fun isAccessibilityRangeAuthor(authorId: String?): Boolean {
        return authorId?.startsWith("android-accessibility-range:") == true
    }

    private fun isBrowserAccessibilityAuthor(authorId: String?): Boolean {
        return authorId?.startsWith("android-accessibility-browser:") == true
    }

    private fun isGenericScreenAccessibilityAuthor(authorId: String?): Boolean {
        return authorId?.startsWith("screen:accessibility_text:") == true
    }

    private fun isTopControlMask(
        spec: MaskOverlaySpec,
        screenWidth: Int,
        screenHeight: Int,
        authorId: String?
    ): Boolean {
        val value = authorId ?: return false

        val isEstimatedMask = isAccessibilityAuthor(value) ||
            isPreciseVisualAuthor(value)
        if (!isEstimatedMask) return false

        if (
            isPreciseVisualAuthor(value) &&
            VisualTextGeometryPolicy.isTopHeroYoutubeComposite(value, screenWidth)
        ) {
            return false
        }

        if (value == "android-accessibility:user_input") {
            val inputCutoff = min(
                TOP_USER_INPUT_REGION_MAX_PX,
                (screenHeight * TOP_USER_INPUT_REGION_RATIO).roundToInt()
            )
            return spec.top < inputCutoff
        }

        if (isGenericVisualAuthor(value)) {
            val genericVisualCutoff = min(
                TOP_GENERIC_VISUAL_CONTROL_REGION_MAX_PX,
                (screenHeight * TOP_GENERIC_VISUAL_CONTROL_REGION_RATIO).roundToInt()
            )
            return spec.top < genericVisualCutoff
        }

        val cutoff = min(TOP_CONTROL_REGION_MAX_PX, (screenHeight * TOP_CONTROL_REGION_RATIO).roundToInt())
        return spec.top < cutoff
    }

    private fun toSpec(bounds: BoundsRect, screenWidth: Int, screenHeight: Int): MaskOverlaySpec? {
        val left = max(0, min(bounds.left, screenWidth))
        val top = max(0, min(bounds.top, screenHeight))
        val right = max(left, min(bounds.right, screenWidth))
        val bottom = max(top, min(bounds.bottom, screenHeight))
        val width = right - left
        val height = bottom - top

        if (width < MIN_WIDTH_PX || height < MIN_HEIGHT_PX) {
            return null
        }
        if (
            width >= (screenWidth * MAX_SCREEN_WIDTH_RATIO).toInt() &&
            height >= (screenHeight * MAX_SCREEN_HEIGHT_RATIO).toInt()
        ) {
            return null
        }

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = MASK_LABEL
        )
    }

    private fun toSpanSpec(
        fullSpec: MaskOverlaySpec,
        span: EvidenceSpan,
        original: String,
        originalLength: Int,
        allowScrollTranslation: Boolean,
        preciseVisualBounds: Boolean,
        visualTextForSizing: String?,
        debugSource: String
    ): MaskOverlaySpec? {
        val resolvedRange = resolveSpanRange(
            original = original,
            span = span,
            originalLength = originalLength
        ) ?: return null
        val start = resolvedRange.first
        val end = resolvedRange.second
        if (end <= start) return null

        val lineCount = estimateLineCount(fullSpec.height, originalLength)
        val lineHeight = (fullSpec.height / lineCount).coerceAtLeast(MIN_HEIGHT_PX)

        if (preciseVisualBounds && isWholeTextSpan(start, end, originalLength)) {
            return toPreciseVisualSpanSpec(
                fullSpec = fullSpec,
                spanText = visualTextSizingOverride(
                    spanText = span.text,
                    visualText = visualTextForSizing
                ),
                lineHeight = lineHeight,
                allowScrollTranslation = allowScrollTranslation,
                debugSource = debugSource
            )
        }

        val charsPerLine = ((originalLength + lineCount - 1) / lineCount).coerceAtLeast(1)
        val lineIndex = (start / charsPerLine).coerceIn(0, lineCount - 1)
        val lineStart = lineIndex * charsPerLine
        val lineEnd = min(originalLength, lineStart + charsPerLine).coerceAtLeast(lineStart + 1)
        val lineLength = (lineEnd - lineStart).coerceAtLeast(1)
        val localStart = (start - lineStart).coerceIn(0, lineLength)
        val localEnd = (end - lineStart).coerceIn(localStart + 1, lineLength)

        val startRatio = localStart.toFloat() / lineLength.toFloat()
        val endRatio = localEnd.toFloat() / lineLength.toFloat()
        val rawLeft = fullSpec.left + (fullSpec.width * startRatio).roundToInt()
        val rawRight = fullSpec.left + (fullSpec.width * endRatio).roundToInt()

        val minWidth = minOf(
            fullSpec.width,
            maxOf(
                MIN_SPAN_MASK_WIDTH_PX,
                span.text.ifBlank { MASK_LABEL }.length * 18
            )
        )
        val center = (rawLeft + rawRight) / 2
        var left = rawLeft - SPAN_HORIZONTAL_PADDING_PX
        var right = rawRight + SPAN_HORIZONTAL_PADDING_PX

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
            spanText = span.text,
            fullSpecWidth = fullSpec.width,
            lineHeight = lineHeight
        )
        if (right - left > maxSpanWidth) {
            val anchored = anchorCompactSpanBounds(
                fullSpec = fullSpec,
                rawLeft = rawLeft,
                rawRight = rawRight,
                start = start,
                end = end,
                originalLength = originalLength,
                maxSpanWidth = maxSpanWidth
            )
            left = anchored.first
            right = anchored.second
        }

        val width = right - left
        if (width < MIN_WIDTH_PX) return null

        val height = minOf(lineHeight, MAX_SPAN_MASK_HEIGHT_PX).coerceAtLeast(MIN_HEIGHT_PX)
        val top = fullSpec.top + (lineIndex * lineHeight) + ((lineHeight - height) / 2).coerceAtLeast(0)

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = MASK_LABEL,
            allowScrollTranslation = allowScrollTranslation,
            debugSource = debugSource
        )
    }

    private fun resolveSpanRange(
        original: String,
        span: EvidenceSpan,
        originalLength: Int
    ): Pair<Int, Int>? {
        val clampedStart = span.start.coerceIn(0, originalLength)
        val clampedEnd = span.end.coerceIn(clampedStart, originalLength)
        val spanText = span.text.trim()
        if (spanText.isNotBlank()) {
            val clampedText = codePointSubstring(original, clampedStart, clampedEnd)
            val spanCodePointLength = spanText.codePointCount(0, spanText.length).coerceAtLeast(1)
            val shouldRepair =
                clampedEnd - clampedStart < spanCodePointLength ||
                    !clampedText.equals(spanText, ignoreCase = true)
            if (shouldRepair) {
                val matchedStart = codePointIndexOf(original, spanText)
                if (matchedStart >= 0) {
                    return matchedStart to min(originalLength, matchedStart + spanCodePointLength)
                }
            }
        }

        return if (clampedEnd > clampedStart) {
            clampedStart to clampedEnd
        } else {
            null
        }
    }

    private fun codePointIndexOf(value: String, query: String): Int {
        val charIndex = value.indexOf(query, ignoreCase = true)
        if (charIndex < 0) return -1
        return value.codePointCount(0, charIndex)
    }

    private fun codePointSubstring(value: String, start: Int, end: Int): String {
        val total = value.codePointCount(0, value.length)
        val safeStart = start.coerceIn(0, total)
        val safeEnd = end.coerceIn(safeStart, total)
        val startCharIndex = value.offsetByCodePoints(0, safeStart)
        val endCharIndex = value.offsetByCodePoints(0, safeEnd)
        return value.substring(startCharIndex, endCharIndex)
    }

    private fun isWholeTextSpan(start: Int, end: Int, originalLength: Int): Boolean {
        return start <= LEADING_SPAN_PREFIX_TOLERANCE &&
            end >= originalLength - LEADING_SPAN_PREFIX_TOLERANCE
    }

    private fun toPreciseVisualSpanSpec(
        fullSpec: MaskOverlaySpec,
        spanText: String,
        lineHeight: Int,
        allowScrollTranslation: Boolean,
        debugSource: String
    ): MaskOverlaySpec? {
        val maxWidth = estimatePreciseVisualSpanMaxWidth(
            spanText = spanText,
            fullSpecWidth = fullSpec.width,
            lineHeight = lineHeight
        )
        val width = minOf(fullSpec.width, maxWidth).coerceAtLeast(MIN_WIDTH_PX)
        if (width < MIN_WIDTH_PX) return null

        val left = if (width >= fullSpec.width) {
            fullSpec.left
        } else {
            fullSpec.left + ((fullSpec.width - width) / 2).coerceAtLeast(0)
        }
        val height = minOf(lineHeight, MAX_SPAN_MASK_HEIGHT_PX).coerceAtLeast(MIN_HEIGHT_PX)
        val top = fullSpec.top + ((fullSpec.height - height) / 2).coerceAtLeast(0)

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = MASK_LABEL,
            allowScrollTranslation = allowScrollTranslation,
            debugSource = debugSource
        )
    }

    private fun visualTextSizingOverride(spanText: String, visualText: String?): String {
        val cleanVisualText = visualText?.trim()?.takeIf { it.isNotBlank() } ?: return spanText
        val spanKey = visualSizingKey(spanText)
        if (spanKey.isBlank()) return spanText

        return if (visualSizingKey(cleanVisualText) == spanKey) {
            cleanVisualText
        } else {
            spanText
        }
    }

    private fun visualSizingKey(text: String): String {
        return text
            .lowercase()
            .replace(Regex("""[\s"'`.,!?_\-]+"""), "")
            .map { char ->
                when (char) {
                    '|', '!', '1', 'i' -> 'l'
                    'a', 'g' -> 'q'
                    else -> char
                }
            }
            .joinToString("")
    }

    private fun buildDebugSource(item: AndroidAnalysisResultItem): String {
        val bounds = item.boundsInScreen
        val spanText = item.evidenceSpans.firstOrNull()?.text.orEmpty()
        val source = item.authorId.orEmpty().ifBlank { "unsourced" }
        val originalSample = item.original
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(36)
        return "$source span=${spanText.take(16)} rect=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom} text=$originalSample"
    }

    private fun estimatePreciseVisualSpanMaxWidth(
        spanText: String,
        fullSpecWidth: Int,
        lineHeight: Int
    ): Int {
        val visibleText = spanText.ifBlank { MASK_LABEL }
        val codePointLength = visibleText.codePointCount(0, visibleText.length).coerceAtLeast(1)
        val hasKorean = visibleText.any { it.code in 0xAC00..0xD7A3 }
        val charWidth = if (hasKorean) {
            max(KOREAN_SPAN_CHAR_WIDTH_PX, (lineHeight * PRECISE_VISUAL_KOREAN_WIDTH_RATIO).roundToInt())
        } else {
            max(LATIN_SPAN_CHAR_WIDTH_PX, (lineHeight * PRECISE_VISUAL_LATIN_WIDTH_RATIO).roundToInt())
        }

        return minOf(
            fullSpecWidth,
            maxOf(
                MIN_SPAN_MASK_WIDTH_PX,
                codePointLength * charWidth + PRECISE_VISUAL_WIDTH_PADDING_PX
            )
        )
    }

    private fun estimateMaxSpanMaskWidth(
        spanText: String,
        fullSpecWidth: Int,
        lineHeight: Int
    ): Int {
        val visibleText = spanText.ifBlank { MASK_LABEL }
        val codePointLength = visibleText.codePointCount(0, visibleText.length).coerceAtLeast(1)
        val hasKorean = visibleText.any { it.code in 0xAC00..0xD7A3 }
        val scaledCharWidth = if (hasKorean) {
            max(
                KOREAN_SPAN_CHAR_WIDTH_PX,
                min(KOREAN_SPAN_MAX_CHAR_WIDTH_PX, (lineHeight * KOREAN_SPAN_HEIGHT_WIDTH_RATIO).roundToInt())
            )
        } else {
            max(
                LATIN_SPAN_CHAR_WIDTH_PX,
                min(LATIN_SPAN_MAX_CHAR_WIDTH_PX, (lineHeight * LATIN_SPAN_HEIGHT_WIDTH_RATIO).roundToInt())
            )
        }
        val estimatedWidth = codePointLength * scaledCharWidth
        val paddedWidth = estimatedWidth + SPAN_HORIZONTAL_PADDING_PX * 2
        val compactCap = if (codePointLength <= COMPACT_SPAN_CODEPOINT_LIMIT) {
            max(
                if (hasKorean) MAX_COMPACT_KOREAN_SPAN_WIDTH_PX else MAX_COMPACT_LATIN_SPAN_WIDTH_PX,
                paddedWidth
            )
        } else {
            fullSpecWidth
        }

        return minOf(
            fullSpecWidth,
            maxOf(MIN_SPAN_MASK_WIDTH_PX, minOf(paddedWidth, compactCap))
        )
    }

    private fun anchorCompactSpanBounds(
        fullSpec: MaskOverlaySpec,
        rawLeft: Int,
        rawRight: Int,
        start: Int,
        end: Int,
        originalLength: Int,
        maxSpanWidth: Int
    ): Pair<Int, Int> {
        val fullRight = fullSpec.left + fullSpec.width
        var left: Int
        var right: Int

        when {
            start <= LEADING_SPAN_PREFIX_TOLERANCE -> {
                left = (rawLeft - SPAN_HORIZONTAL_PADDING_PX).coerceAtLeast(fullSpec.left)
                right = left + maxSpanWidth
            }
            end >= originalLength -> {
                right = (rawRight + SPAN_HORIZONTAL_PADDING_PX).coerceAtMost(fullRight)
                left = right - maxSpanWidth
            }
            else -> {
                val center = (rawLeft + rawRight) / 2
                left = center - maxSpanWidth / 2
                right = left + maxSpanWidth
            }
        }

        if (right > fullRight) {
            left -= right - fullRight
            right = fullRight
        }
        if (left < fullSpec.left) {
            right += fullSpec.left - left
            left = fullSpec.left
        }

        return left.coerceAtLeast(fullSpec.left) to right.coerceAtMost(fullRight)
    }

    private fun estimateLineCount(height: Int, originalLength: Int): Int {
        if (height <= MAX_SPAN_MASK_HEIGHT_PX || originalLength <= 20) {
            return 1
        }

        return (height / ESTIMATED_LINE_HEIGHT_PX)
            .coerceAtLeast(1)
            .coerceAtMost(8)
    }

    private fun maxScrollTranslationDeltaPx(axisSize: Int): Int {
        return max(
            MIN_SCROLL_TRANSLATION_DELTA_PX,
            (axisSize * MAX_SCROLL_TRANSLATION_AXIS_RATIO).roundToInt()
        )
    }

    private fun suppressOverlappingSpecs(specs: List<MaskOverlaySpec>): List<MaskOverlaySpec> {
        val kept = mutableListOf<MaskOverlaySpec>()
        specs
            .distinctBy { "${it.left}|${it.top}|${it.width}|${it.height}" }
            .sortedWith(
                compareBy<MaskOverlaySpec> { it.top / MAX_SPAN_MASK_HEIGHT_PX }
                    .thenBy { it.width * it.height }
                    .thenBy { it.left }
                    .thenBy { it.top }
            )
            .forEach { spec ->
                val overlapsExisting = kept.any { existing ->
                    isNearDuplicateMask(spec, existing)
                }
                if (!overlapsExisting) {
                    kept += spec
                }
            }
        return kept
    }

    private fun isNearDuplicateMask(left: MaskOverlaySpec, right: MaskOverlaySpec): Boolean {
        if (overlapRatio(left, right) >= NEAR_DUPLICATE_OVERLAP_RATIO) return true

        val horizontalOverlap = min(left.left + left.width, right.left + right.width) -
            max(left.left, right.left)
        if (horizontalOverlap <= 0) return false

        val verticalOverlap = min(left.top + left.height, right.top + right.height) -
            max(left.top, right.top)
        if (verticalOverlap <= 0) return false

        val smallerHeight = min(left.height, right.height).coerceAtLeast(1)
        val smallerWidth = min(left.width, right.width).coerceAtLeast(1)
        val verticalOverlapRatio = verticalOverlap.toFloat() / smallerHeight.toFloat()
        val horizontalOverlapRatio = horizontalOverlap.toFloat() / smallerWidth.toFloat()

        return verticalOverlapRatio >= SAME_LINE_VERTICAL_OVERLAP_RATIO &&
            horizontalOverlapRatio >= SAME_LINE_HORIZONTAL_OVERLAP_RATIO
    }

    private fun overlapRatio(left: MaskOverlaySpec, right: MaskOverlaySpec): Float {
        val overlapLeft = max(left.left, right.left)
        val overlapTop = max(left.top, right.top)
        val overlapRight = min(left.left + left.width, right.left + right.width)
        val overlapBottom = min(left.top + left.height, right.top + right.height)
        val overlapWidth = overlapRight - overlapLeft
        val overlapHeight = overlapBottom - overlapTop
        if (overlapWidth <= 0 || overlapHeight <= 0) return 0f

        val overlapArea = overlapWidth * overlapHeight
        val smallerArea = min(left.width * left.height, right.width * right.height).coerceAtLeast(1)
        return overlapArea.toFloat() / smallerArea.toFloat()
    }

    private const val MASK_LABEL = "***"
    private const val SAME_LINE_VERTICAL_OVERLAP_RATIO = 0.55f
    private const val SAME_LINE_HORIZONTAL_OVERLAP_RATIO = 0.03f
}

class MaskOverlayController(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "MaskOverlayController"
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeViews = mutableListOf<TextView>()
    private val activeSpecs = mutableListOf<MaskOverlaySpec>()
    private var lastSignature: String = ""
    private var lastOverlayUpdateAtMs: Long = 0L

    fun render(
        response: AndroidAnalysisResponse?,
        preserveExistingIfEmpty: Boolean = false
    ) {
        val metrics = service.resources.displayMetrics
        val plan = AndroidMaskOverlayPlanner.buildPlan(
            response = response,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        val specs = plan.specs

        if (specs.isEmpty()) {
            if (preserveExistingIfEmpty && activeViews.isNotEmpty()) {
                Log.d(
                    TAG,
                    "render empty plan preserved existing masks candidates=${plan.candidateCount} " +
                        "unstable=${plan.skippedUnstableCount} suppressed=${plan.suppressedOverlapCount}"
                )
                return
            }
            clear()
            Log.d(
                TAG,
                "render skipped candidates=${plan.candidateCount} unstable=${plan.skippedUnstableCount} " +
                    "suppressed=${plan.suppressedOverlapCount}"
            )
            return
        }

        val signature = AndroidMaskOverlayPlanner.signature(specs)
        if (signature == lastSignature && activeViews.isNotEmpty()) {
            return
        }

        try {
            specs.forEachIndexed { index, spec ->
                val existing = activeViews.getOrNull(index)
                if (existing == null) {
                    val maskView = createMaskView(spec)
                    windowManager.addView(maskView, createMaskLayoutParams(spec))
                    activeViews += maskView
                    activeSpecs += spec
                } else {
                    existing.text = MASK_RENDER_TEXT
                    windowManager.updateViewLayout(existing, createMaskLayoutParams(spec))
                    activeSpecs[index] = spec
                }
            }

            while (activeViews.size > specs.size) {
                val view = activeViews.removeAt(activeViews.lastIndex)
                activeSpecs.removeAt(activeSpecs.lastIndex)
                try {
                    windowManager.removeView(view)
                } catch (_: IllegalArgumentException) {
                    // The view may already be detached after a fast window transition.
                }
            }

            Log.d(
                TAG,
                "render maskCount=${specs.size} signature=$signature sources=${
                    specs.mapNotNull { spec -> spec.debugSource.takeIf { it.isNotBlank() } }.take(3)
                }"
            )
            lastSignature = signature
            lastOverlayUpdateAtMs = SystemClock.uptimeMillis()
        } catch (error: RuntimeException) {
            clearViews()
            Log.w(TAG, "render mask overlay failed", error)
        }
    }

    fun translateBy(deltaX: Int = 0, deltaY: Int = 0): Boolean {
        if (activeViews.isEmpty() || activeSpecs.isEmpty()) return false
        if (deltaX == 0 && deltaY == 0) return false

        val metrics = service.resources.displayMetrics
        val translatedSpecs = AndroidMaskOverlayPlanner.translateSpecs(
            specs = activeSpecs,
            deltaX = deltaX,
            deltaY = deltaY,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )

        if (translatedSpecs.isEmpty()) {
            clear()
            return false
        }

        return try {
            translatedSpecs.forEachIndexed { index, spec ->
                val existing = activeViews.getOrNull(index)
                if (existing == null) {
                    val maskView = createMaskView(spec)
                    windowManager.addView(maskView, createMaskLayoutParams(spec))
                    activeViews += maskView
                } else {
                    windowManager.updateViewLayout(existing, createMaskLayoutParams(spec))
                }
            }

            while (activeViews.size > translatedSpecs.size) {
                val view = activeViews.removeAt(activeViews.lastIndex)
                try {
                    windowManager.removeView(view)
                } catch (_: IllegalArgumentException) {
                    // The view may already be detached after a fast window transition.
                }
            }

            activeSpecs.clear()
            activeSpecs += translatedSpecs
            lastSignature = AndroidMaskOverlayPlanner.signature(translatedSpecs)
            lastOverlayUpdateAtMs = SystemClock.uptimeMillis()
            true
        } catch (error: RuntimeException) {
            clearViews()
            Log.w(TAG, "translate mask overlay failed", error)
            false
        }
    }

    fun clear() {
        clearViews()
        lastSignature = ""
        lastOverlayUpdateAtMs = 0L
    }

    fun hasActiveMasks(): Boolean {
        return activeViews.isNotEmpty()
    }

    fun wasUpdatedWithin(windowMs: Long, nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        if (windowMs <= 0L || lastOverlayUpdateAtMs <= 0L) return false

        val elapsedMs = nowMs - lastOverlayUpdateAtMs
        return elapsedMs in 0..windowMs
    }

    private fun clearViews() {
        if (activeViews.isEmpty()) {
            activeSpecs.clear()
            lastSignature = ""
            return
        }

        val viewsToRemove = activeViews.toList()
        activeViews.clear()
        activeSpecs.clear()
        lastSignature = ""

        viewsToRemove.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The view may already be detached during service shutdown.
            }
        }
    }

    private fun createMaskView(spec: MaskOverlaySpec): TextView {
        return TextView(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            text = MASK_RENDER_TEXT
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.TRANSPARENT)
            textSize = if (spec.height <= 90) 13f else 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 8f * service.resources.displayMetrics.density
                setColor(Color.argb(245, 12, 12, 12))
            }
        }
    }

    private fun createMaskLayoutParams(spec: MaskOverlaySpec): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            spec.width,
            spec.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = spec.left
            y = spec.top
        }
    }
}

private const val MASK_RENDER_TEXT = ""
