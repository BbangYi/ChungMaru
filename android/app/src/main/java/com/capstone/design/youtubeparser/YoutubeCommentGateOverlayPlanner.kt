package com.capstone.design.youtubeparser

internal object YoutubeCommentGateOverlayPlanner {
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val COMMENT_PANEL_SOURCE = "youtube-comment-panel"
    private const val GATE_LABEL = "댓글 검사 중"
    private const val MAX_GATE_COUNT = 4
    private const val MIN_GATE_WIDTH_PX = 120
    private const val MIN_GATE_HEIGHT_PX = 48
    private const val PANEL_CONTENT_TOP_PADDING_PX = 16
    private const val PANEL_BOTTOM_GUARD_PX = 56
    private const val PANEL_MARKER_MIN_TOP_RATIO = 0.16f
    private const val PANEL_MARKER_MAX_TOP_RATIO = 0.92f
    private const val PANEL_INPUT_MIN_TOP_RATIO = 0.35f
    private const val PANEL_SORT_SEARCH_HEIGHT_PX = 220
    private val whitespacePattern = Regex("\\s+")

    fun buildSpecs(
        visualRoiPlan: VisualTextRoiPlan,
        screenCandidates: List<ScreenTextCandidate> = emptyList(),
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()

        val candidateSpecs = buildCandidateSpecs(screenCandidates, screenWidth, screenHeight)
        if (candidateSpecs.isNotEmpty()) return candidateSpecs

        return visualRoiPlan.rois
            .asSequence()
            .filter { roi -> roi.source == COMMENT_PANEL_SOURCE }
            .take(MAX_GATE_COUNT)
            .mapIndexedNotNull { index, roi ->
                toGateSpec(
                    bounds = roi.boundsInScreen,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    debugSource = "youtube-comment-panel-gate:$index:${roi.reason}"
                )
            }
            .toList()
    }

    fun buildSpecsFromNodes(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        if (nodes.isEmpty() || screenWidth <= 0 || screenHeight <= 0) return emptyList()
        if (nodes.none { node -> node.packageName == YOUTUBE_PACKAGE }) return emptyList()

        // This runs on the Accessibility callback path. It must not invoke the
        // general ROI/candidate planners: the visible YouTube comment sheet can
        // be protected from its header and composer bounds alone.
        val marker = nodes
            .asSequence()
            .filter { node ->
                node.packageName == YOUTUBE_PACKAGE &&
                    node.isVisibleToUser &&
                    node.top in
                        (screenHeight * PANEL_MARKER_MIN_TOP_RATIO).toInt()..
                        (screenHeight * PANEL_MARKER_MAX_TOP_RATIO).toInt() &&
                    isCommentPanelMarker(node.displayText.orEmpty())
            }
            .maxByOrNull { node -> node.bottom }
            ?: return emptyList()

        val sortBottom = nodes
            .asSequence()
            .filter { node ->
                node.packageName == YOUTUBE_PACKAGE &&
                    node.isVisibleToUser &&
                    node.top >= marker.bottom &&
                    node.top <= marker.bottom + PANEL_SORT_SEARCH_HEIGHT_PX &&
                    isCommentSortControl(node.displayText.orEmpty())
            }
            .map { node -> node.bottom }
            .maxOrNull()
            ?: marker.bottom
        val panelTop = (sortBottom + PANEL_CONTENT_TOP_PADDING_PX).coerceAtMost(screenHeight)
        val panelBottom = nodes
            .asSequence()
            .filter { node ->
                node.packageName == YOUTUBE_PACKAGE &&
                    node.isVisibleToUser &&
                    node.top > (screenHeight * PANEL_INPUT_MIN_TOP_RATIO).toInt() &&
                    isCommentInput(node.displayText.orEmpty())
            }
            .map { node -> node.top }
            .minOrNull()
            ?.coerceAtMost(screenHeight)
            ?: (screenHeight - PANEL_BOTTOM_GUARD_PX)

        return toGateSpec(
            bounds = BoundsRect(0, panelTop, screenWidth, panelBottom),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            debugSource = "youtube-comment-panel-fast-gate:${marker.top}"
        )?.let(::listOf).orEmpty()
    }

    /**
     * The local fast decision has already validated that this is a harmful
     * YouTube comment with an exact Accessibility bounds. Keep that geometry
     * intact instead of passing it through span-level overlay planning.
     */
    fun buildBlockedCommentSpecs(
        results: List<AndroidAnalysisResultItem>,
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()

        val wholeCommentResults = results
            .asSequence()
            .filter { result -> result.isOffensive }
            .distinctBy { result ->
                val bounds = result.boundsInScreen
                "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}"
            }
            .sortedByDescending { result ->
                val bounds = result.boundsInScreen
                (bounds.right - bounds.left) * (bounds.bottom - bounds.top)
            }
            .fold(mutableListOf<AndroidAnalysisResultItem>()) { selected, result ->
                if (selected.none { existing -> contains(existing.boundsInScreen, result.boundsInScreen) }) {
                    selected += result
                }
                selected
            }
            .sortedWith(
                compareBy<AndroidAnalysisResultItem> { result -> result.boundsInScreen.top }
                    .thenBy { result -> result.boundsInScreen.left }
            )
            .asSequence()
            .take(MAX_GATE_COUNT)
            .mapIndexedNotNull { index, result ->
                toSpec(
                    bounds = result.boundsInScreen,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    label = "차단된 댓글",
                    debugSource = "youtube-comment-row-mask:$index:${result.authorId.orEmpty()}"
                )
            }
            .toList()

        return wholeCommentResults
    }

    private fun contains(container: BoundsRect, candidate: BoundsRect): Boolean {
        return container.left <= candidate.left &&
            container.top <= candidate.top &&
            container.right >= candidate.right &&
            container.bottom >= candidate.bottom
    }

    private fun isCommentPanelMarker(text: String): Boolean {
        val normalized = text.replace(whitespacePattern, " ").trim()
        val lower = normalized.lowercase()
        return lower == "comments" ||
            lower == "replies" ||
            lower == "reply" ||
            lower.matches(Regex("""^comments?\s+\d+.*""")) ||
            lower.matches(Regex("""^\d+\s+repl(?:y|ies)\b.*""")) ||
            normalized == "댓글" ||
            normalized.startsWith("댓글 ") ||
            normalized.endsWith("개의 답글")
    }

    private fun isCommentSortControl(text: String): Boolean {
        val normalized = text.replace(whitespacePattern, " ").trim()
        val lower = normalized.lowercase()
        return lower == "top" || lower == "newest" ||
            normalized == "인기순" || normalized == "최신순"
    }

    private fun isCommentInput(text: String): Boolean {
        val normalized = text.replace(whitespacePattern, " ").trim()
        val lower = normalized.lowercase()
        return lower.startsWith("reply") ||
            lower.startsWith("add a comment") ||
            lower.startsWith("share your thoughts") ||
            normalized.startsWith("댓글을 입력") ||
            normalized.startsWith("답글")
    }

    private fun buildCandidateSpecs(
        candidates: List<ScreenTextCandidate>,
        screenWidth: Int,
        screenHeight: Int
    ): List<MaskOverlaySpec> {
        return candidates
            .asSequence()
            .filter { candidate ->
                candidate.packageName == YOUTUBE_PACKAGE &&
                    candidate.route.surface == CandidateSurface.YOUTUBE_COMMENT &&
                    candidate.route.geometryPolicy == CandidateGeometryPolicy.ACCESSIBILITY_EXACT &&
                    candidate.route.renderPolicy == CandidateRenderPolicy.DIRECT_OVERLAY &&
                    candidate.rawText.isNotBlank()
            }
            .distinctBy { candidate ->
                val bounds = candidate.screenRect
                "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}:${candidate.rawText}"
            }
            .sortedWith(
                compareBy<ScreenTextCandidate> { it.screenRect.top }
                    .thenBy { it.screenRect.left }
            )
            .take(MAX_GATE_COUNT)
            .mapIndexedNotNull { index, candidate ->
                toGateSpec(
                    bounds = candidate.screenRect,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    debugSource = "youtube-comment-candidate-gate:$index:${candidate.backendSourceId.orEmpty()}"
                )
            }
            .toList()
    }

    private fun toGateSpec(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int,
        debugSource: String
    ): MaskOverlaySpec? = toSpec(
        bounds = bounds,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        label = GATE_LABEL,
        debugSource = debugSource
    )

    private fun toSpec(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int,
        label: String,
        debugSource: String
    ): MaskOverlaySpec? {
        val left = bounds.left.coerceIn(0, screenWidth - 1)
        val top = bounds.top.coerceIn(0, screenHeight - 1)
        val right = bounds.right.coerceIn(left + 1, screenWidth)
        val bottom = bounds.bottom.coerceIn(top + 1, screenHeight)
        val width = right - left
        val height = bottom - top
        if (width < MIN_GATE_WIDTH_PX || height < MIN_GATE_HEIGHT_PX) return null

        return MaskOverlaySpec(
            left = left,
            top = top,
            width = width,
            height = height,
            label = label,
            allowScrollTranslation = true,
            debugSource = debugSource
        )
    }
}
