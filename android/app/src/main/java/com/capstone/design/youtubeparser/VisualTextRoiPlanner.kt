package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min

data class VisualTextRoi(
    val boundsInScreen: BoundsRect,
    val source: String,
    val priority: Int,
    val reason: String
)

data class VisualTextRoiPlan(
    val rois: List<VisualTextRoi>,
    val candidateCount: Int
)

object VisualTextRoiPlanner {
    private const val MAX_ROI_COUNT = 4
    private const val MIN_WIDTH_PX = 120
    private const val MIN_HEIGHT_PX = 60
    private const val SCREEN_EDGE_PADDING_PX = 6
    private const val MAX_SOURCE_TEXT_LENGTH = 260
    private const val MAX_ROI_AREA_RATIO = 0.28f
    private const val MAX_FULL_WIDTH_RATIO = 0.92f
    private const val MAX_VISIBLE_TOP_RATIO = 0.9f
    private const val OVERLAP_SUPPRESSION_RATIO = 0.72f

    fun planFromNodes(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int
    ): List<VisualTextRoi> {
        return buildPlanFromNodes(nodes, screenWidth, screenHeight).rois
    }

    fun buildPlanFromNodes(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int
    ): VisualTextRoiPlan {
        if (screenWidth <= 0 || screenHeight <= 0 || nodes.isEmpty()) {
            return VisualTextRoiPlan(rois = emptyList(), candidateCount = 0)
        }

        val rawCandidates = nodes.mapNotNull { node ->
            toCandidate(node, screenWidth, screenHeight)
        }

        val selected = mutableListOf<VisualTextRoi>()
        rawCandidates
            .sortedWith(
                compareBy<VisualTextRoi> { it.priority }
                    .thenBy { it.boundsInScreen.top }
                    .thenBy { it.boundsInScreen.left }
            )
            .forEach { candidate ->
                if (selected.size >= MAX_ROI_COUNT) return@forEach
                if (selected.none { overlapsTooMuch(it.boundsInScreen, candidate.boundsInScreen) }) {
                    selected += candidate
                }
            }

        return VisualTextRoiPlan(
            rois = selected,
            candidateCount = rawCandidates.size
        )
    }

    private fun toCandidate(
        node: ParsedTextNode,
        screenWidth: Int,
        screenHeight: Int
    ): VisualTextRoi? {
        if (!node.isVisibleToUser) return null

        val sourceText = node.displayText
            ?: node.contentDescription
            ?: node.text
            ?: return null
        val normalized = sourceText.replace(Regex("\\s+"), " ").trim()
        if (!isUsefulSourceText(normalized)) return null

        val contentDescriptionOnly = node.text.isNullOrBlank() && !node.contentDescription.isNullOrBlank()
        val className = node.className.orEmpty()
        val isImageLike = className.contains("Image", ignoreCase = true)
        val isYoutubeComposite = contentDescriptionOnly && isMediaCardDescription(normalized)
        val isGenericVisual = contentDescriptionOnly && (isImageLike || looksLikeVisualCard(className, normalized))
        if (!isYoutubeComposite && !isGenericVisual) return null

        val clamped = clampBounds(
            BoundsRect(node.left, node.top, node.right, node.bottom),
            screenWidth,
            screenHeight
        ) ?: return null
        if (!isNearCurrentViewport(clamped, screenHeight)) return null
        if (looksLikeRootOrSystemRegion(clamped, screenWidth, screenHeight)) return null

        val roiBounds = normalizeRoiBounds(clamped, screenWidth, screenHeight) ?: return null

        return VisualTextRoi(
            boundsInScreen = roiBounds,
            source = if (isYoutubeComposite) "youtube-composite-card" else "generic-visual-region",
            priority = if (isYoutubeComposite) 0 else 1,
            reason = if (contentDescriptionOnly) "content-description-only" else "visual-node"
        )
    }

    private fun isUsefulSourceText(text: String): Boolean {
        if (text.length !in 4..MAX_SOURCE_TEXT_LENGTH) return false
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (lower == "more options" || lower == "action menu") return false
        if (lower == "all" || lower == "shorts" || lower == "videos") return false
        return text.any { it.isLetterOrDigit() || it.code in 0xAC00..0xD7A3 }
    }

    private fun isMediaCardDescription(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("play video") ||
            lower.contains("play short") ||
            lower.contains("views") ||
            lower.contains("조회수") ||
            lower.contains("go to channel") ||
            lower.contains("동영상 재생")
    }

    private fun looksLikeVisualCard(className: String, text: String): Boolean {
        if (className.contains("Button", ignoreCase = true)) return false
        if (className.contains("RecyclerView", ignoreCase = true)) return false
        return text.length >= 12 && text.any { it.isWhitespace() }
    }

    private fun clampBounds(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): BoundsRect? {
        val left = bounds.left.coerceIn(0, screenWidth)
        val top = bounds.top.coerceIn(0, screenHeight)
        val right = bounds.right.coerceIn(left, screenWidth)
        val bottom = bounds.bottom.coerceIn(top, screenHeight)
        if (right - left < MIN_WIDTH_PX || bottom - top < MIN_HEIGHT_PX) return null
        return BoundsRect(left, top, right, bottom)
    }

    private fun isNearCurrentViewport(bounds: BoundsRect, screenHeight: Int): Boolean {
        return bounds.top < (screenHeight * MAX_VISIBLE_TOP_RATIO).toInt()
    }

    private fun looksLikeRootOrSystemRegion(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val widthRatio = width.toFloat() / screenWidth.toFloat()
        val heightRatio = height.toFloat() / screenHeight.toFloat()

        return (widthRatio >= MAX_FULL_WIDTH_RATIO && heightRatio >= 0.55f) ||
            (bounds.top <= SCREEN_EDGE_PADDING_PX && heightRatio >= 0.35f)
    }

    private fun normalizeRoiBounds(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): BoundsRect? {
        val screenArea = max(1, screenWidth * screenHeight)
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val areaRatio = (width * height).toFloat() / screenArea.toFloat()
        if (areaRatio <= MAX_ROI_AREA_RATIO) {
            return padAndClamp(bounds, screenWidth, screenHeight)
        }

        val croppedHeight = max(MIN_HEIGHT_PX, min(height, (screenHeight * 0.32f).toInt()))
        val cropped = BoundsRect(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = min(bounds.bottom, bounds.top + croppedHeight)
        )
        val croppedWidth = cropped.right - cropped.left
        val croppedAreaRatio = (croppedWidth * (cropped.bottom - cropped.top)).toFloat() / screenArea.toFloat()
        if (croppedAreaRatio > MAX_ROI_AREA_RATIO) return null

        return padAndClamp(cropped, screenWidth, screenHeight)
    }

    private fun padAndClamp(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): BoundsRect? {
        val left = max(0, bounds.left - SCREEN_EDGE_PADDING_PX)
        val top = max(0, bounds.top - SCREEN_EDGE_PADDING_PX)
        val right = min(screenWidth, bounds.right + SCREEN_EDGE_PADDING_PX)
        val bottom = min(screenHeight, bounds.bottom + SCREEN_EDGE_PADDING_PX)
        if (right - left < MIN_WIDTH_PX || bottom - top < MIN_HEIGHT_PX) return null
        return BoundsRect(left, top, right, bottom)
    }

    private fun overlapsTooMuch(first: BoundsRect, second: BoundsRect): Boolean {
        val overlapLeft = max(first.left, second.left)
        val overlapTop = max(first.top, second.top)
        val overlapRight = min(first.right, second.right)
        val overlapBottom = min(first.bottom, second.bottom)
        if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) return false

        val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
        val smallerArea = min(
            (first.right - first.left) * (first.bottom - first.top),
            (second.right - second.left) * (second.bottom - second.top)
        ).coerceAtLeast(1)

        return overlapArea.toFloat() / smallerArea.toFloat() >= OVERLAP_SUPPRESSION_RATIO
    }
}
