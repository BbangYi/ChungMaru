package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class YoutubeSkeletonMaskPlan(
    val specs: List<MaskOverlaySpec>,
    val cachedHarmfulSpecs: List<MaskOverlaySpec>,
    val unknownLoadingSpecs: List<MaskOverlaySpec>,
    val unknownCount: Int,
    val safeCacheHitCount: Int,
    val harmfulCacheHitCount: Int,
    val skippedCount: Int,
    val cacheSamples: List<String>
)

internal object YoutubeSkeletonMaskBuilder {
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val YOUTUBE_COMMENT_SOURCE_PREFIX = "android-accessibility-comment:youtube"
    private const val ACCESSIBILITY_LOOKAHEAD_PREFIX = "android-accessibility-lookahead:"
    private const val MIN_SOURCE_WIDTH_PX = 40
    private const val MIN_SOURCE_HEIGHT_PX = 14
    private const val MAX_SOURCE_HEIGHT_PX = 420
    private const val MIN_PANE_WIDTH_PX = 120
    private const val MIN_PANE_HEIGHT_PX = 160
    private const val PANE_TOP_PADDING_PX = 112
    private const val PANE_SIDE_PADDING_PX = 40
    private const val RIGHT_PANE_LEFT_RATIO = 0.35f
    private const val RIGHT_PANE_MAX_WIDTH_RATIO = 0.75f
    private const val MIN_COMMENT_PANE_TOP_RATIO = 0.20f
    private const val COMMENT_MASK_HORIZONTAL_PADDING_PX = 12
    private const val COMMENT_MASK_VERTICAL_PADDING_PX = 8
    private const val MIN_COMMENT_MASK_WIDTH_PX = 80
    private const val MIN_COMMENT_MASK_HEIGHT_PX = 32
    private const val MAX_COMMENT_MASK_HEIGHT_RATIO = 0.28f
    private const val MAX_COMMENT_MASK_AREA_RATIO = 0.22f
    private const val COMMENT_PREVIEW_VERTICAL_PADDING_RATIO = 0.08f
    private const val MIN_COMMENT_PREVIEW_VERTICAL_PADDING_PX = 48
    private const val MAX_COMMENT_PREVIEW_VERTICAL_PADDING_PX = 160
    private const val MAX_COMMENT_PREVIEW_HEIGHT_RATIO = 0.24f
    private const val PANEL_OVERLAP_SLOP_PX = 36
    private const val MAX_COMMENT_MASK_COUNT = 6
    private const val MIN_NATIVE_PANE_WIDTH_RATIO = 0.28f
    private const val MIN_NATIVE_PANE_HEIGHT_RATIO = 0.18f
    private const val MIN_NATIVE_PANE_TOP_RATIO = 0.12f
    private const val MAX_NATIVE_PANE_TOP_RATIO = 0.82f
    private const val MIN_NATIVE_PANE_BOTTOM_RATIO = 0.62f
    private const val MAX_NATIVE_MARKER_GAP_PX = 160

    fun build(
        candidates: List<ScreenTextCandidate>,
        cachedResults: List<AndroidAnalysisResultItem?>,
        screenWidth: Int,
        screenHeight: Int,
        timestamp: Long,
        commentPanelBounds: List<BoundsRect> = emptyList()
    ): YoutubeSkeletonMaskPlan {
        val cacheSamples = mutableListOf<String>()
        val unknownCandidates = mutableListOf<ScreenTextCandidate>()
        val harmfulCachedBounds = mutableListOf<BoundsRect>()
        var safeCacheHitCount = 0
        var harmfulCacheHitCount = 0
        var unknownCount = 0
        var skippedCount = 0

        candidates.forEachIndexed { index, candidate ->
            if (!candidate.isYoutubeCommentOverlayCandidate()) {
                skippedCount += 1
                return@forEachIndexed
            }

            val hash = stableTextHash(candidate.rawText)
            val cached = cachedResults.getOrNull(index)
            when {
                cached == null -> {
                    unknownCount += 1
                    unknownCandidates += candidate
                    cacheSamples += "miss:$hash:${candidate.route.surface.name.lowercase()}"
                }
                cached.isHarmful() -> {
                    harmfulCacheHitCount += 1
                    harmfulCachedBounds += candidate.screenRect
                    cacheSamples += "harmful-hit:$hash"
                }
                else -> {
                    safeCacheHitCount += 1
                    cacheSamples += "safe-hit:$hash"
                }
            }
        }

        val blockedCommentSpecs = buildCommentContentSpecsFromBounds(
            bounds = harmfulCachedBounds,
            commentPanelBounds = emptyList(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            style = MaskOverlayStyle.BLOCKED,
            label = "comment-blocked",
            debugSource = "youtube-comment-blocked-cache"
        )

        val unknownLoadingSpecs = buildCommentContentSpecsFromBounds(
            bounds = unknownCandidates.map { it.screenRect },
            commentPanelBounds = emptyList(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            style = MaskOverlayStyle.LOADING,
            label = "comment-loading",
            debugSource = "youtube-comment-row-loading"
        )

        val loadingPaneSpec = buildCommentPaneSpecFromBounds(
            bounds = unknownCandidates.map { it.screenRect },
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            style = MaskOverlayStyle.LOADING,
            label = "comments-loading",
            debugSource = "youtube-comment-pane-loading"
        )
        if (unknownCount > 0 && loadingPaneSpec == null) {
            skippedCount += unknownCount
        }

        val preferredSpecs = if (loadingPaneSpec != null) {
            listOf(loadingPaneSpec)
        } else {
            blockedCommentSpecs
        }
        return YoutubeSkeletonMaskPlan(
            specs = preferredSpecs,
            cachedHarmfulSpecs = blockedCommentSpecs,
            unknownLoadingSpecs = unknownLoadingSpecs,
            unknownCount = unknownCount,
            safeCacheHitCount = safeCacheHitCount,
            harmfulCacheHitCount = harmfulCacheHitCount,
            skippedCount = skippedCount,
            cacheSamples = cacheSamples.take(8)
        )
    }

    fun buildAttachedViewportSpecs(plan: YoutubeSkeletonMaskPlan): List<MaskOverlaySpec> {
        return (plan.cachedHarmfulSpecs + plan.unknownLoadingSpecs)
            .distinctBy { spec ->
                "${spec.left}|${spec.top}|${spec.width}|${spec.height}|${spec.style}"
            }
            .sortedWith(compareBy<MaskOverlaySpec> { it.top }.thenBy { it.left })
            .take(MAX_COMMENT_MASK_COUNT)
    }

    fun buildCommentPaneSpecFromBounds(
        bounds: List<BoundsRect>,
        screenWidth: Int,
        screenHeight: Int,
        style: MaskOverlayStyle,
        label: String,
        debugSource: String
    ): MaskOverlaySpec? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val usableBounds = bounds
            .mapNotNull { bound -> bound.clampedToScreen(screenWidth, screenHeight) }
            .filter { bound -> bound.isUsablePaneSource() }
        if (usableBounds.isEmpty()) return null

        val minLeft = usableBounds.minOf { it.left }
        val maxRight = usableBounds.maxOf { it.right }
        val minTop = usableBounds.minOf { it.top }
        val minPaneTop = (screenHeight * MIN_COMMENT_PANE_TOP_RATIO).roundToInt()
        if (minTop < minPaneTop) return null
        val contentWidth = maxRight - minLeft
        val rightPaneLeftThreshold = (screenWidth * RIGHT_PANE_LEFT_RATIO).roundToInt()
        val rightPaneMaxWidth = (screenWidth * RIGHT_PANE_MAX_WIDTH_RATIO).roundToInt()
        val looksLikeRightSidePane = minLeft >= rightPaneLeftThreshold && contentWidth <= rightPaneMaxWidth

        val left = if (looksLikeRightSidePane) {
            max(0, minLeft - PANE_SIDE_PADDING_PX)
        } else {
            0
        }
        val right = if (looksLikeRightSidePane) {
            min(screenWidth, maxRight + PANE_SIDE_PADDING_PX)
        } else {
            screenWidth
        }
        val top = max(0, minTop - PANE_TOP_PADDING_PX)
        val bottom = screenHeight
        val width = right - left
        val height = bottom - top
        if (width < MIN_PANE_WIDTH_PX || height < MIN_PANE_HEIGHT_PX) return null

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = label,
            allowScrollTranslation = false,
            debugSource = debugSource,
            style = style
        )
    }

    fun buildNativeCommentPaneSpec(
        contentBounds: BoundsRect,
        commentMarkerBounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int,
        label: String = "comments-loading",
        debugSource: String = "youtube-comment-pane-loading-native"
    ): MaskOverlaySpec? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val content = contentBounds.clampedToScreen(screenWidth, screenHeight) ?: return null
        val marker = commentMarkerBounds.clampedToScreen(screenWidth, screenHeight) ?: return null
        val width = content.right - content.left
        val height = content.bottom - content.top
        val minWidth = (screenWidth * MIN_NATIVE_PANE_WIDTH_RATIO).roundToInt()
        val minHeight = (screenHeight * MIN_NATIVE_PANE_HEIGHT_RATIO).roundToInt()
        val minTop = (screenHeight * MIN_NATIVE_PANE_TOP_RATIO).roundToInt()
        val maxTop = (screenHeight * MAX_NATIVE_PANE_TOP_RATIO).roundToInt()
        val minBottom = (screenHeight * MIN_NATIVE_PANE_BOTTOM_RATIO).roundToInt()
        val markerOverlapsHorizontally = marker.left < content.right && marker.right > content.left
        val markerGap = content.top - marker.bottom

        if (
            width < max(MIN_PANE_WIDTH_PX, minWidth) ||
            height < max(MIN_PANE_HEIGHT_PX, minHeight) ||
            content.top !in minTop..maxTop ||
            content.bottom < minBottom ||
            !markerOverlapsHorizontally ||
            marker.top >= content.top ||
            markerGap !in 0..MAX_NATIVE_MARKER_GAP_PX
        ) {
            return null
        }

        return MaskOverlaySpec(
            left = content.left,
            top = content.top,
            width = width,
            height = height,
            label = label,
            allowScrollTranslation = false,
            debugSource = debugSource,
            style = MaskOverlayStyle.LOADING
        )
    }

    fun buildCommentPreviewLoadingSpec(
        actionBounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): MaskOverlaySpec? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val action = actionBounds.clampedToScreen(screenWidth, screenHeight) ?: return null
        val padding = (screenHeight * COMMENT_PREVIEW_VERTICAL_PADDING_RATIO)
            .roundToInt()
            .coerceIn(
                MIN_COMMENT_PREVIEW_VERTICAL_PADDING_PX,
                MAX_COMMENT_PREVIEW_VERTICAL_PADDING_PX
            )
        val maxHeight = (screenHeight * MAX_COMMENT_PREVIEW_HEIGHT_RATIO)
            .roundToInt()
            .coerceAtLeast(MIN_COMMENT_MASK_HEIGHT_PX)
        val desiredTop = max(0, action.top - padding)
        val desiredBottom = min(screenHeight, action.bottom + padding)
        val desiredHeight = desiredBottom - desiredTop
        val centerY = (action.top + action.bottom) / 2
        val top = if (desiredHeight <= maxHeight) {
            desiredTop
        } else {
            (centerY - maxHeight / 2).coerceIn(0, screenHeight - maxHeight)
        }
        val bottom = if (desiredHeight <= maxHeight) {
            desiredBottom
        } else {
            top + maxHeight
        }
        if (bottom - top < MIN_COMMENT_MASK_HEIGHT_PX) return null

        return MaskOverlaySpec(
            left = 0,
            top = top,
            width = screenWidth,
            height = bottom - top,
            label = "comment-preview-loading",
            allowScrollTranslation = false,
            debugSource = "youtube-comment-preview-loading",
            style = MaskOverlayStyle.LOADING
        )
    }

    fun buildCommentContentSpecsFromResults(
        results: List<AndroidAnalysisResultItem>,
        commentPanelBounds: List<BoundsRect>,
        screenWidth: Int,
        screenHeight: Int,
        style: MaskOverlayStyle,
        label: String,
        debugSource: String
    ): List<MaskOverlaySpec> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()
        val clampedPanelBounds = commentPanelBounds
            .mapNotNull { bound -> bound.clampedToScreen(screenWidth, screenHeight) }

        return results
            .filter { result -> result.isOffensive }
            .mapIndexedNotNull { index, result ->
                val hasTrustedCommentSource = result.hasTrustedYoutubeCommentSource()
                if (!hasTrustedCommentSource && clampedPanelBounds.isEmpty()) return@mapIndexedNotNull null
                val requiredPanelBounds = if (hasTrustedCommentSource) {
                    emptyList()
                } else {
                    clampedPanelBounds
                }
                buildCommentContentSpecFromBound(
                    bound = result.boundsInScreen,
                    commentPanelBounds = requiredPanelBounds,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    style = style,
                    label = label,
                    debugSource = "$debugSource:$index"
                )
            }
            .distinctBy { spec -> "${spec.left}|${spec.top}|${spec.width}|${spec.height}|${spec.style}" }
            .take(MAX_COMMENT_MASK_COUNT)
    }

    fun buildCommentContentSpecsFromBounds(
        bounds: List<BoundsRect>,
        commentPanelBounds: List<BoundsRect>,
        screenWidth: Int,
        screenHeight: Int,
        style: MaskOverlayStyle,
        label: String,
        debugSource: String
    ): List<MaskOverlaySpec> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()
        val clampedPanelBounds = commentPanelBounds
            .mapNotNull { bound -> bound.clampedToScreen(screenWidth, screenHeight) }
        return bounds
            .mapIndexedNotNull { index, bound ->
                buildCommentContentSpecFromBound(
                    bound = bound,
                    commentPanelBounds = clampedPanelBounds,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    style = style,
                    label = label,
                    debugSource = "$debugSource:$index"
                )
            }
            .distinctBy { spec -> "${spec.left}|${spec.top}|${spec.width}|${spec.height}|${spec.style}" }
            .take(MAX_COMMENT_MASK_COUNT)
    }

    fun stabilizeLoadingPaneSpec(
        previousSpec: MaskOverlaySpec?,
        currentSpec: MaskOverlaySpec,
        screenWidth: Int,
        screenHeight: Int
    ): MaskOverlaySpec {
        if (
            previousSpec == null ||
            previousSpec.style != MaskOverlayStyle.LOADING ||
            currentSpec.style != MaskOverlayStyle.LOADING ||
            screenWidth <= 0 ||
            screenHeight <= 0
        ) {
            return currentSpec
        }

        val left = min(previousSpec.left, currentSpec.left).coerceIn(0, screenWidth)
        val top = min(previousSpec.top, currentSpec.top).coerceIn(0, screenHeight)
        val right = max(
            previousSpec.left + previousSpec.width,
            currentSpec.left + currentSpec.width
        ).coerceIn(left, screenWidth)
        val bottom = max(
            previousSpec.top + previousSpec.height,
            currentSpec.top + currentSpec.height
        ).coerceIn(top, screenHeight)
        return currentSpec.copy(
            left = left,
            top = top,
            width = right - left,
            height = bottom - top,
            allowScrollTranslation = false
        )
    }
    fun mergeCommentSpecs(
        primarySpecs: List<MaskOverlaySpec>,
        supplementalSpecs: List<MaskOverlaySpec>
    ): List<MaskOverlaySpec> {
        val primary = compactOverlappingSpecs(primarySpecs)
        val supplemental = compactOverlappingSpecs(supplementalSpecs)
            .filterNot { candidate ->
                primary.any { exact -> exact.overlapRatioOfSmaller(candidate) >= 0.85f }
            }

        return (primary + supplemental)
            .distinctBy { spec -> "${spec.left}|${spec.top}|${spec.width}|${spec.height}|${spec.style}" }
            .sortedWith(compareBy<MaskOverlaySpec> { it.top }.thenBy { it.left })
            .take(MAX_COMMENT_MASK_COUNT)
    }

    private fun compactOverlappingSpecs(specs: List<MaskOverlaySpec>): List<MaskOverlaySpec> {
        val kept = mutableListOf<MaskOverlaySpec>()
        specs
            .distinctBy { spec -> "${spec.left}|${spec.top}|${spec.width}|${spec.height}|${spec.style}" }
            .sortedByDescending { spec -> spec.width.toLong() * spec.height.toLong() }
            .forEach { candidate ->
                if (kept.none { existing ->
                        existing.style == candidate.style &&
                            existing.overlapRatioOfSmaller(candidate) >= 0.85f
                    }
                ) {
                    kept += candidate
                }
            }
        return kept
    }

    private fun MaskOverlaySpec.overlapRatioOfSmaller(other: MaskOverlaySpec): Float {
        val intersectionWidth = (min(left + width, other.left + other.width) -
            max(left, other.left)).coerceAtLeast(0)
        val intersectionHeight = (min(top + height, other.top + other.height) -
            max(top, other.top)).coerceAtLeast(0)
        val intersectionArea = intersectionWidth.toLong() * intersectionHeight.toLong()
        val smallerArea = min(
            width.toLong() * height.toLong(),
            other.width.toLong() * other.height.toLong()
        ).coerceAtLeast(1L)
        return intersectionArea.toFloat() / smallerArea.toFloat()
    }
    private fun buildCommentContentSpecFromBound(
        bound: BoundsRect,
        commentPanelBounds: List<BoundsRect>,
        screenWidth: Int,
        screenHeight: Int,
        style: MaskOverlayStyle,
        label: String,
        debugSource: String
    ): MaskOverlaySpec? {
        val clamped = bound.clampedToScreen(screenWidth, screenHeight) ?: return null
        if (!clamped.isUsableCommentMaskSource(screenWidth, screenHeight)) return null
        if (commentPanelBounds.isNotEmpty() && !clamped.overlapsAnyCommentPanel(commentPanelBounds)) {
            return null
        }

        var left = max(0, clamped.left - COMMENT_MASK_HORIZONTAL_PADDING_PX)
        var top = max(0, clamped.top - COMMENT_MASK_VERTICAL_PADDING_PX)
        var right = min(screenWidth, clamped.right + COMMENT_MASK_HORIZONTAL_PADDING_PX)
        var bottom = min(screenHeight, clamped.bottom + COMMENT_MASK_VERTICAL_PADDING_PX)

        if (right - left < MIN_COMMENT_MASK_WIDTH_PX) {
            val center = (left + right) / 2
            left = (center - MIN_COMMENT_MASK_WIDTH_PX / 2).coerceIn(0, screenWidth)
            right = (left + MIN_COMMENT_MASK_WIDTH_PX).coerceAtMost(screenWidth)
            left = (right - MIN_COMMENT_MASK_WIDTH_PX).coerceAtLeast(0)
        }
        if (bottom - top < MIN_COMMENT_MASK_HEIGHT_PX) {
            val center = (top + bottom) / 2
            top = (center - MIN_COMMENT_MASK_HEIGHT_PX / 2).coerceIn(0, screenHeight)
            bottom = (top + MIN_COMMENT_MASK_HEIGHT_PX).coerceAtMost(screenHeight)
            top = (bottom - MIN_COMMENT_MASK_HEIGHT_PX).coerceAtLeast(0)
        }

        val width = right - left
        val height = bottom - top
        if (width < MIN_COMMENT_MASK_WIDTH_PX || height < MIN_COMMENT_MASK_HEIGHT_PX) return null

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = label,
            allowScrollTranslation = true,
            debugSource = debugSource,
            style = style
        )
    }

    private fun AndroidAnalysisResultItem.hasTrustedYoutubeCommentSource(): Boolean {
        val source = authorId.orEmpty().removePrefix(ACCESSIBILITY_LOOKAHEAD_PREFIX)
        return source.startsWith(YOUTUBE_COMMENT_SOURCE_PREFIX)
    }

    private fun ScreenTextCandidate.isYoutubeCommentOverlayCandidate(): Boolean {
        return packageName == YOUTUBE_PACKAGE &&
            route.surface == CandidateSurface.YOUTUBE_COMMENT &&
            route.renderPolicy == CandidateRenderPolicy.DIRECT_OVERLAY &&
            route.geometryPolicy == CandidateGeometryPolicy.ACCESSIBILITY_EXACT
    }

    private fun BoundsRect.clampedToScreen(screenWidth: Int, screenHeight: Int): BoundsRect? {
        val clampedLeft = left.coerceIn(0, screenWidth)
        val clampedTop = top.coerceIn(0, screenHeight)
        val clampedRight = right.coerceIn(0, screenWidth)
        val clampedBottom = bottom.coerceIn(0, screenHeight)
        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return null
        return BoundsRect(
            left = clampedLeft,
            top = clampedTop,
            right = clampedRight,
            bottom = clampedBottom
        )
    }

    private fun BoundsRect.isUsablePaneSource(): Boolean {
        val width = right - left
        val height = bottom - top
        return width >= MIN_SOURCE_WIDTH_PX &&
            height >= MIN_SOURCE_HEIGHT_PX &&
            height <= MAX_SOURCE_HEIGHT_PX
    }

    private fun BoundsRect.isUsableCommentMaskSource(screenWidth: Int, screenHeight: Int): Boolean {
        val width = right - left
        val height = bottom - top
        val screenArea = (screenWidth * screenHeight).coerceAtLeast(1)
        val areaRatio = (width * height).toFloat() / screenArea.toFloat()
        val minCommentTop = (screenHeight * MIN_COMMENT_PANE_TOP_RATIO).roundToInt()
        return top >= minCommentTop &&
            width >= MIN_SOURCE_WIDTH_PX &&
            height >= MIN_SOURCE_HEIGHT_PX &&
            height <= max(MAX_SOURCE_HEIGHT_PX, (screenHeight * MAX_COMMENT_MASK_HEIGHT_RATIO).roundToInt()) &&
            areaRatio <= MAX_COMMENT_MASK_AREA_RATIO
    }

    private fun BoundsRect.overlapsAnyCommentPanel(commentPanelBounds: List<BoundsRect>): Boolean {
        return commentPanelBounds.any { panel ->
            val expandedPanel = panel.expand(PANEL_OVERLAP_SLOP_PX)
            intersects(expandedPanel) || centerInside(expandedPanel)
        }
    }

    private fun BoundsRect.expand(padding: Int): BoundsRect {
        return BoundsRect(
            left = left - padding,
            top = top - padding,
            right = right + padding,
            bottom = bottom + padding
        )
    }

    private fun BoundsRect.intersects(other: BoundsRect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    private fun BoundsRect.centerInside(other: BoundsRect): Boolean {
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        return centerX in other.left..other.right && centerY in other.top..other.bottom
    }

    private fun AndroidAnalysisResultItem.isHarmful(): Boolean {
        return isOffensive
    }

    private fun stableTextHash(text: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim().lowercase()
        return Integer.toHexString(normalized.hashCode())
    }
}