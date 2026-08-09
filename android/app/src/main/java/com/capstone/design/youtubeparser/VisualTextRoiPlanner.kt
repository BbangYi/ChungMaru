package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min

data class VisualTextRoi(
    val boundsInScreen: BoundsRect,
    val source: String,
    val priority: Int,
    val reason: String,
    val sourceText: String = ""
)

data class VisualTextRoiPlan(
    val rois: List<VisualTextRoi>,
    val candidateCount: Int
)

object VisualTextRoiPlanner {
    private const val MAX_ROI_COUNT = 6
    private const val MIN_WIDTH_PX = 120
    private const val MIN_HEIGHT_PX = 60
    private const val SCREEN_EDGE_PADDING_PX = 6
    private const val MAX_SOURCE_TEXT_LENGTH = 260
    private const val MAX_ROI_AREA_RATIO = 0.28f
    private const val MAX_FULL_WIDTH_RATIO = 0.92f
    private const val MAX_VISIBLE_TOP_RATIO = 0.9f
    private const val OVERLAP_SUPPRESSION_RATIO = 0.72f
    private const val FALLBACK_BAND_HEIGHT_RATIO = 0.26f
    private const val FALLBACK_BAND_OVERLAP_PX = 32
    private const val MAX_FALLBACK_BAND_COUNT = 3
    private const val TOP_CONTROL_REGION_RATIO = 0.14f
    private const val TOP_CONTROL_REGION_MAX_PX = 230
    private const val TOP_HERO_MEDIA_MIN_HEIGHT_PX = 180
    private const val TOP_HERO_MEDIA_MIN_WIDTH_RATIO = 0.48f
    private const val TOP_SHORTS_CARD_MIN_WIDTH_RATIO = 0.34f
    private const val TOP_SHORTS_CARD_MIN_HEIGHT_PX = 180
    private const val CLIPPED_TOP_COMPOSITE_MAX_HEIGHT_PX = 59
    private const val SHORT_COMPOSITE_EXPAND_MAX_HEIGHT_PX = 260
    private const val SHORT_COMPOSITE_TITLE_GAP_MAX_PX = 180
    private const val SHORT_COMPOSITE_EXPANDED_MAX_HEIGHT_PX = 420
    private const val SHORT_COMPOSITE_TITLE_OVERLAP_RATIO = 0.55f
    private const val SHORTS_THUMBNAIL_CARD_MIN_WIDTH_RATIO = 0.34f
    private const val SHORTS_THUMBNAIL_CARD_MAX_WIDTH_RATIO = 0.55f
    private const val SHORTS_THUMBNAIL_CARD_MIN_HEIGHT_PX = 360
    private const val SHORTS_THUMBNAIL_HEIGHT_RATIO = 0.82f
    private const val COMMENT_PANEL_AUTHOR_MIN_TOP_RATIO = 0.18f
    private const val COMMENT_PANEL_ROW_MAX_HEIGHT_PX = 260
    private const val COMMENT_PANEL_ROW_MIN_HEIGHT_PX = 64
    private const val COMMENT_PANEL_BODY_TOP_GAP_PX = 4
    private const val COMMENT_PANEL_ROW_BOTTOM_GAP_PX = 18
    private const val COMMENT_PANEL_LEFT_PADDING_PX = 8
    private const val COMMENT_PANEL_RIGHT_PADDING_PX = 24
    private const val COMMENT_PANEL_BOTTOM_GUARD_PX = 72
    private const val MAX_COMMENT_PANEL_ROI_COUNT = 4
    private const val COMMENT_PANEL_AUTHOR_AVATAR_MAX_WIDTH_PX = 96
    private const val COMMENT_PANEL_AUTHOR_LABEL_MIN_WIDTH_PX = 96
    private const val BROWSER_TEXT_NODE_SOURCE = "browser-text-node"
    private const val BROWSER_VISUAL_NODE_SOURCE = "browser-visual-region"
    private const val BROWSER_TEXT_ROI_HORIZONTAL_PADDING_PX = 12
    private const val BROWSER_TEXT_ROI_VERTICAL_PADDING_PX = 10
    private const val BROWSER_TEXT_ROI_MIN_WIDTH_PX = 80
    private const val BROWSER_TEXT_NODE_MIN_HEIGHT_PX = 16
    private const val BROWSER_TEXT_ROI_MIN_HEIGHT_PX = 40
    private const val BROWSER_TEXT_ROI_MAX_HEIGHT_PX = 260
    private const val BROWSER_TEXT_ROI_MAX_COUNT = 4
    private const val BROWSER_VISUAL_ROI_MIN_WIDTH_PX = 220
    private const val BROWSER_VISUAL_ROI_MIN_HEIGHT_PX = 140
    private const val BROWSER_VISUAL_ROI_MAX_COUNT = 2

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
        val browserTextNodeRois = buildBrowserTextNodeRois(nodes, screenWidth, screenHeight)
        val browserVisualNodeRois = buildBrowserVisualNodeRois(nodes, screenWidth, screenHeight)
        val commentPanelRois = buildYoutubeCommentPanelRois(nodes, screenWidth, screenHeight)
        if (commentPanelRois.isNotEmpty()) {
            return VisualTextRoiPlan(
                rois = commentPanelRois.take(MAX_ROI_COUNT),
                candidateCount = rawCandidates.size + commentPanelRois.size
            )
        }

        val fallbackCandidates =
            browserTextNodeRois +
                browserVisualNodeRois +
                commentPanelRois +
                buildYoutubeExpandedShortCompositeRois(nodes, screenWidth, screenHeight, rawCandidates) +
                buildYoutubeShortCardThumbnailRois(rawCandidates, screenWidth, screenHeight) +
                buildYoutubeClippedTopCompositeRois(nodes, screenWidth, screenHeight, rawCandidates) +
                if (commentPanelRois.isEmpty()) {
                    buildYoutubeFallbackRois(nodes, screenWidth, screenHeight, rawCandidates)
                } else {
                    emptyList()
                }
        val selectableRawCandidates = if (fallbackCandidates.isNotEmpty()) {
            rawCandidates.filterNot { candidate -> candidate.source == "generic-visual-region" }
        } else {
            rawCandidates
        }

        val selected = mutableListOf<VisualTextRoi>()
        (selectableRawCandidates + fallbackCandidates)
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
            candidateCount = rawCandidates.size + fallbackCandidates.size
        )
    }

    private fun buildBrowserTextNodeRois(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int
    ): List<VisualTextRoi> {
        return nodes
            .asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.packageName in ACCESSIBILITY_FIRST_PACKAGES &&
                    !node.text.isNullOrBlank()
            }
            .mapNotNull { node ->
                val text = node.text
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?: return@mapNotNull null
                val ranges = VisualTextOcrCandidateFilter.findAnalysisRanges(text)
                if (ranges.isEmpty()) return@mapNotNull null
                if (hasExactCharBoxCoverage(text, node.charBoxes, ranges)) return@mapNotNull null
                if (looksLikeBrowserControlTextNode(node, text)) return@mapNotNull null

                val clamped = clampBrowserTextBounds(
                    BoundsRect(node.left, node.top, node.right, node.bottom),
                    screenWidth,
                    screenHeight
                ) ?: return@mapNotNull null
                val padded = padBrowserTextBounds(clamped, screenWidth, screenHeight) ?: return@mapNotNull null

                VisualTextRoi(
                    boundsInScreen = padded,
                    source = BROWSER_TEXT_NODE_SOURCE,
                    priority = -4,
                    reason = "browser-accessibility-text-hit",
                    sourceText = text.take(MAX_SOURCE_TEXT_LENGTH)
                )
            }
            .sortedWith(
                compareBy<VisualTextRoi> { it.boundsInScreen.top }
                    .thenBy { it.boundsInScreen.left }
            )
            .take(BROWSER_TEXT_ROI_MAX_COUNT)
            .toList()
    }

    private fun buildBrowserVisualNodeRois(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int
    ): List<VisualTextRoi> {
        return nodes
            .asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.packageName in ACCESSIBILITY_FIRST_PACKAGES
            }
            .mapNotNull { node ->
                val text = node.displayText
                    ?: node.contentDescription
                    ?: node.text
                    ?: return@mapNotNull null
                val normalized = text.replace(Regex("\\s+"), " ").trim()
                if (!isUsefulSourceText(normalized)) return@mapNotNull null
                if (!hasBrowserTextBearingVisualCue(normalized)) return@mapNotNull null
                if (looksLikeBrowserControlTextNode(node, normalized)) return@mapNotNull null

                val clamped = clampBounds(
                    BoundsRect(node.left, node.top, node.right, node.bottom),
                    screenWidth,
                    screenHeight
                ) ?: return@mapNotNull null
                val width = clamped.right - clamped.left
                val height = clamped.bottom - clamped.top
                if (width < BROWSER_VISUAL_ROI_MIN_WIDTH_PX || height < BROWSER_VISUAL_ROI_MIN_HEIGHT_PX) {
                    return@mapNotNull null
                }
                if (!isNearCurrentViewport(clamped, screenHeight)) return@mapNotNull null
                if (looksLikeRootOrSystemRegion(clamped, screenWidth, screenHeight)) return@mapNotNull null
                if (looksLikeTopControlRegion(clamped, screenHeight)) return@mapNotNull null

                val roiBounds = normalizeRoiBounds(clamped, screenWidth, screenHeight) ?: return@mapNotNull null
                VisualTextRoi(
                    boundsInScreen = roiBounds,
                    source = BROWSER_VISUAL_NODE_SOURCE,
                    priority = -2,
                    reason = "browser-text-bearing-visual-node",
                    sourceText = normalized.take(MAX_SOURCE_TEXT_LENGTH)
                )
            }
            .sortedWith(
                compareBy<VisualTextRoi> { it.boundsInScreen.top }
                    .thenBy { it.boundsInScreen.left }
            )
            .take(BROWSER_VISUAL_ROI_MAX_COUNT)
            .toList()
    }

    private fun hasExactCharBoxCoverage(
        text: String,
        charBoxes: List<CharBox>,
        ranges: List<VisualTextOcrCandidateFilter.CandidateRange>
    ): Boolean {
        if (charBoxes.isEmpty() || ranges.isEmpty()) return false

        return ranges.any { range ->
            val startCodePoint = text.codePointCount(0, range.start.coerceIn(0, text.length))
            val endCodePoint = text.codePointCount(0, range.end.coerceIn(range.start, text.length))
            if (endCodePoint <= startCodePoint) return@any false

            charBoxes.any { box -> box.start <= startCodePoint && box.end > startCodePoint } &&
                charBoxes.any { box -> box.start < endCodePoint && box.end >= endCodePoint }
        }
    }

    private fun looksLikeBrowserControlTextNode(node: ParsedTextNode, text: String): Boolean {
        val className = node.className.orEmpty()
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val lower = text.lowercase()

        if (className.contains("EditText", ignoreCase = true)) return true
        if (className.contains("Button", ignoreCase = true)) return true
        if (viewId.contains("url") || viewId.contains("omnibox") || viewId.contains("search_box")) return true
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true
        if (lower.contains("/search?q=") || lower.contains("?q=") || lower.contains("&q=")) return true

        return false
    }

    private fun clampBrowserTextBounds(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): BoundsRect? {
        val left = bounds.left.coerceIn(0, screenWidth)
        val top = bounds.top.coerceIn(0, screenHeight)
        val right = bounds.right.coerceIn(left, screenWidth)
        val bottom = bounds.bottom.coerceIn(top, screenHeight)
        if (right - left < BROWSER_TEXT_ROI_MIN_WIDTH_PX) return null
        if (bottom - top < BROWSER_TEXT_NODE_MIN_HEIGHT_PX) return null
        if (bottom - top > BROWSER_TEXT_ROI_MAX_HEIGHT_PX) return null
        return BoundsRect(left, top, right, bottom)
    }

    private fun padBrowserTextBounds(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): BoundsRect? {
        val left = max(0, bounds.left - BROWSER_TEXT_ROI_HORIZONTAL_PADDING_PX)
        val top = max(0, bounds.top - BROWSER_TEXT_ROI_VERTICAL_PADDING_PX)
        val right = min(screenWidth, bounds.right + BROWSER_TEXT_ROI_HORIZONTAL_PADDING_PX)
        val bottom = min(screenHeight, bounds.bottom + BROWSER_TEXT_ROI_VERTICAL_PADDING_PX)
        if (right - left < BROWSER_TEXT_ROI_MIN_WIDTH_PX) return null
        if (bottom - top < BROWSER_TEXT_ROI_MIN_HEIGHT_PX) return null
        return BoundsRect(left, top, right, bottom)
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
        val browserVisualNode = node.packageName in ACCESSIBILITY_FIRST_PACKAGES &&
            isBrowserVisualNodeCandidate(contentDescriptionOnly, className, normalized)
        if (node.packageName in ACCESSIBILITY_FIRST_PACKAGES && !browserVisualNode) return null

        val clamped = clampBounds(
            BoundsRect(node.left, node.top, node.right, node.bottom),
            screenWidth,
            screenHeight
        ) ?: return null
        val isImageLike = className.contains("Image", ignoreCase = true)
        val isYoutubeComposite = contentDescriptionOnly &&
            (isMediaCardDescription(normalized) || isLargeAnalyzableVisualCard(normalized, clamped))
        val isGenericVisual = contentDescriptionOnly &&
            if (browserVisualNode) {
                true
            } else {
                isImageLike || looksLikeVisualCard(className, normalized)
            }
        if (!isYoutubeComposite && !isGenericVisual) return null

        if (!isNearCurrentViewport(clamped, screenHeight)) return null
        if (looksLikeRootOrSystemRegion(clamped, screenWidth, screenHeight)) return null
        if (
            looksLikeTopControlRegion(clamped, screenHeight) &&
            !isTopVisibleMediaRegion(isYoutubeComposite, clamped, screenWidth)
        ) {
            return null
        }

        val roiBounds = normalizeRoiBounds(clamped, screenWidth, screenHeight) ?: return null

        return VisualTextRoi(
            boundsInScreen = roiBounds,
            source = when {
                isYoutubeComposite -> "youtube-composite-card"
                browserVisualNode -> BROWSER_VISUAL_NODE_SOURCE
                else -> "generic-visual-region"
            },
            priority = when {
                isYoutubeComposite -> 0
                browserVisualNode -> 2
                else -> 1
            },
            reason = if (contentDescriptionOnly) "content-description-only" else "visual-node",
            sourceText = normalized
        )
    }

    private fun isBrowserVisualNodeCandidate(
        contentDescriptionOnly: Boolean,
        className: String,
        text: String
    ): Boolean {
        if (!contentDescriptionOnly) return false
        if (className.contains("Button", ignoreCase = true)) return false
        if (className.contains("EditText", ignoreCase = true)) return false
        if (className.contains("RecyclerView", ignoreCase = true)) return false

        if (!hasBrowserTextBearingVisualCue(text)) return false

        return className.contains("View", ignoreCase = true) ||
            className.contains("Image", ignoreCase = true) ||
            looksLikeVisualCard(className, text)
    }

    private fun hasBrowserTextBearingVisualCue(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("ocr") ||
            lower.contains("canvas") ||
            lower.contains("캔버스") ||
            lower.contains("image text") ||
            lower.contains("text in image") ||
            lower.contains("텍스트 이미지") ||
            (lower.contains("이미지") && (lower.contains("텍스트") || lower.contains("문자")))
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

    private fun isLargeAnalyzableVisualCard(text: String, bounds: BoundsRect): Boolean {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        return width >= 320 &&
            height >= 180 &&
            VisualTextOcrCandidateFilter.shouldAnalyze(text)
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

    private fun looksLikeTopControlRegion(bounds: BoundsRect, screenHeight: Int): Boolean {
        val cutoff = min(TOP_CONTROL_REGION_MAX_PX, (screenHeight * TOP_CONTROL_REGION_RATIO).toInt())
        return bounds.top < cutoff
    }

    private fun isTopVisibleMediaRegion(
        isYoutubeComposite: Boolean,
        bounds: BoundsRect,
        screenWidth: Int
    ): Boolean {
        if (!isYoutubeComposite) return false

        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val isHero = height >= TOP_HERO_MEDIA_MIN_HEIGHT_PX &&
            width >= (screenWidth * TOP_HERO_MEDIA_MIN_WIDTH_RATIO).toInt()
        val isShortsGridCard = height >= TOP_SHORTS_CARD_MIN_HEIGHT_PX &&
            width >= (screenWidth * TOP_SHORTS_CARD_MIN_WIDTH_RATIO).toInt()

        return isHero || isShortsGridCard
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

        val maxArea = (screenArea * MAX_ROI_AREA_RATIO).toInt().coerceAtLeast(MIN_WIDTH_PX * MIN_HEIGHT_PX)
        val maxHeightForWidth = (maxArea / max(1, width)).coerceAtLeast(MIN_HEIGHT_PX)
        val croppedHeight = max(
            MIN_HEIGHT_PX,
            min(height, min((screenHeight * 0.32f).toInt(), maxHeightForWidth))
        )
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

    private fun buildYoutubeCommentPanelRois(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int
    ): List<VisualTextRoi> {
        if (nodes.none { it.packageName == YOUTUBE_PACKAGE }) return emptyList()
        buildYoutubeNativeCommentPanelRoi(nodes, screenWidth, screenHeight)?.let { nativeRoi ->
            return listOf(nativeRoi)
        }

        val panelContentTop = commentPanelContentTop(nodes, screenHeight) ?: return emptyList()
        val inputTop = commentPanelInputTop(nodes, screenHeight)
        val panelBottom = min(inputTop, screenHeight - COMMENT_PANEL_BOTTOM_GUARD_PX)
            .coerceAtLeast(panelContentTop)
        val minAuthorTop = max(panelContentTop, (screenHeight * COMMENT_PANEL_AUTHOR_MIN_TOP_RATIO).toInt())

        val authors = nodes
            .asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.packageName == YOUTUBE_PACKAGE &&
                    node.top >= minAuthorTop &&
                    node.top < panelBottom &&
                    looksLikeYoutubeCommentAuthorNode(node)
            }
            .sortedWith(compareBy<ParsedTextNode> { it.top }.thenBy { it.left })
            .take(MAX_COMMENT_PANEL_ROI_COUNT + 1)
            .toList()

        if (authors.isEmpty()) return emptyList()

        return authors
            .take(MAX_COMMENT_PANEL_ROI_COUNT)
            .mapIndexedNotNull { index, author ->
                val nextAuthorTop = authors.getOrNull(index + 1)?.top ?: panelBottom
                val top = max(panelContentTop, author.bottom + COMMENT_PANEL_BODY_TOP_GAP_PX)
                val rowLimit = min(
                    min(nextAuthorTop - COMMENT_PANEL_ROW_BOTTOM_GAP_PX, panelBottom),
                    top + COMMENT_PANEL_ROW_MAX_HEIGHT_PX
                )
                if (rowLimit - top < COMMENT_PANEL_ROW_MIN_HEIGHT_PX) return@mapIndexedNotNull null

                val left = max(0, author.left - COMMENT_PANEL_LEFT_PADDING_PX)
                val right = min(screenWidth, screenWidth - COMMENT_PANEL_RIGHT_PADDING_PX)
                if (right - left < MIN_WIDTH_PX) return@mapIndexedNotNull null

                VisualTextRoi(
                    boundsInScreen = BoundsRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = rowLimit
                    ),
                    source = YOUTUBE_COMMENT_PANEL_SOURCE,
                    priority = -3,
                    reason = "comment-panel-author-body-band",
                    sourceText = author.displayText.orEmpty()
                )
            }
    }

    private fun buildYoutubeNativeCommentPanelRoi(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int
    ): VisualTextRoi? {
        val contentNode = nodes
            .asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.packageName == YOUTUBE_PACKAGE &&
                    node.viewIdResourceName.orEmpty() in YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS
            }
            .filter { node -> node.right > node.left && node.bottom > node.top }
            .maxByOrNull { node ->
                (node.right - node.left) * (node.bottom - node.top)
            }
            ?: return null

        val left = contentNode.left.coerceIn(0, screenWidth)
        val right = contentNode.right.coerceIn(left, screenWidth)
        val top = contentNode.top.coerceIn(0, screenHeight)
        val contentBottom = contentNode.bottom.coerceIn(top, screenHeight)
        val inputTop = commentPanelInputTop(nodes, screenHeight)
        val bottom = if (inputTop in (top + MIN_HEIGHT_PX) until contentBottom) {
            inputTop
        } else {
            contentBottom
        }
        if (right - left < MIN_WIDTH_PX || bottom - top < MIN_HEIGHT_PX) return null

        return VisualTextRoi(
            boundsInScreen = BoundsRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom
            ),
            source = YOUTUBE_COMMENT_PANEL_SOURCE,
            priority = -5,
            reason = "comment-panel-native-content",
            sourceText = contentNode.viewIdResourceName.orEmpty()
        )
    }

    private fun commentPanelContentTop(nodes: List<ParsedTextNode>, screenHeight: Int): Int? {
        val panelMarkers = nodes
            .asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.packageName == YOUTUBE_PACKAGE &&
                    node.top in (screenHeight * 0.16f).toInt()..(screenHeight * 0.92f).toInt() &&
                    isYoutubeCommentPanelMarker(node.displayText.orEmpty())
            }
            .toList()

        if (panelMarkers.isEmpty()) {
            return inferCommentPanelContentTopFromCommentRows(nodes, screenHeight)
        }

        val headerBottom = panelMarkers.maxOf { it.bottom }
        val sortBottom = nodes
            .asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.packageName == YOUTUBE_PACKAGE &&
                    node.top >= headerBottom - SCREEN_EDGE_PADDING_PX &&
                    node.top <= headerBottom + TOP_CONTROL_REGION_MAX_PX &&
                    isYoutubeCommentSortControl(node.displayText.orEmpty())
            }
            .map { it.bottom }
            .maxOrNull()

        return max(headerBottom, sortBottom ?: headerBottom) + SCREEN_EDGE_PADDING_PX
    }

    private fun inferCommentPanelContentTopFromCommentRows(
        nodes: List<ParsedTextNode>,
        screenHeight: Int
    ): Int? {
        val minTop = (screenHeight * COMMENT_PANEL_AUTHOR_MIN_TOP_RATIO).toInt()
        val hasCommentActions = nodes.any { node ->
            node.isVisibleToUser &&
                node.packageName == YOUTUBE_PACKAGE &&
                node.top >= minTop &&
                isYoutubeCommentActionControl(node.displayText.orEmpty())
        }
        if (!hasCommentActions) return null

        val authors = nodes
            .asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.packageName == YOUTUBE_PACKAGE &&
                    node.top >= minTop &&
                    looksLikeYoutubeCommentAuthorNode(node)
            }
            .sortedWith(compareBy<ParsedTextNode> { it.top }.thenBy { it.left })
            .toList()
        val firstAuthor = authors.firstOrNull() ?: return null

        val hasBodyBelowAuthor = nodes.any { node ->
            node.isVisibleToUser &&
                node.packageName == YOUTUBE_PACKAGE &&
                node.top >= firstAuthor.bottom &&
                node.top <= firstAuthor.bottom + COMMENT_PANEL_ROW_MAX_HEIGHT_PX &&
                node.left >= firstAuthor.left - COMMENT_PANEL_LEFT_PADDING_PX &&
                !looksLikeYoutubeCommentAuthorNode(node) &&
                !isYoutubeCommentActionControl(node.displayText.orEmpty()) &&
                !isYoutubeCommentInput(node.displayText.orEmpty())
        }
        if (!hasBodyBelowAuthor) return null

        return firstAuthor.top
    }
    private fun commentPanelInputTop(nodes: List<ParsedTextNode>, screenHeight: Int): Int {
        return nodes
            .asSequence()
            .filter { node ->
                val displayText = node.displayText.orEmpty()
                val isInputWidget = node.className.orEmpty().contains("EditText", ignoreCase = true)
                val isInputLabel = isYoutubeCommentInput(displayText) &&
                    !isYoutubeCommentActionControl(displayText)
                node.isVisibleToUser &&
                    node.packageName == YOUTUBE_PACKAGE &&
                    node.top > (screenHeight * 0.55f).toInt() &&
                    (isInputWidget || isInputLabel)
            }
            .map { it.top }
            .minOrNull()
            ?: screenHeight
    }

    private fun isYoutubeCommentPanelMarker(text: String): Boolean {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val lower = normalized.lowercase()
        val marker = lower.trimEnd('.', ':')
        return marker == "comments" ||
            marker == "replies" ||
            marker.matches(Regex("""^\d+\s+repl(?:y|ies)\b.*""")) ||
            normalized == "댓글" ||
            normalized.endsWith("개의 답글")
    }
    private fun isYoutubeCommentSortControl(text: String): Boolean {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val lower = normalized.lowercase()
        return lower == "top" ||
            lower == "newest" ||
            normalized == "인기순" ||
            normalized == "최신순"
    }

    private fun isYoutubeCommentInput(text: String): Boolean {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val lower = normalized.lowercase()
        return lower.startsWith("reply") ||
            lower.startsWith("share your thoughts") ||
            lower.startsWith("reminds me of") ||
            lower.startsWith("describe the vibe") ||
            normalized.startsWith("답글")
    }

    private fun isYoutubeCommentActionControl(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower.startsWith("like this comment") ||
            lower == "dislike this comment" ||
            lower == "reply" ||
            lower == "답글" ||
            lower == "답글 달기"
    }
    private fun looksLikeYoutubeCommentAuthorNode(node: ParsedTextNode): Boolean {
        val normalized = node.displayText.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (!looksLikeYoutubeCommentAuthor(normalized)) return false

        val width = node.right - node.left
        val className = node.className.orEmpty()
        val height = node.bottom - node.top
        val isAvatarClass = className.contains("Button", ignoreCase = true) ||
            className.contains("Image", ignoreCase = true)
        if (isAvatarClass && width <= COMMENT_PANEL_AUTHOR_AVATAR_MAX_WIDTH_PX && height <= COMMENT_PANEL_AUTHOR_AVATAR_MAX_WIDTH_PX) {
            return false
        }
        if (width < COMMENT_PANEL_AUTHOR_LABEL_MIN_WIDTH_PX && !normalized.lowercase().contains(" ago")) {
            return false
        }

        return true
    }

    private fun looksLikeYoutubeCommentAuthor(text: String): Boolean {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (!normalized.startsWith("@")) return false
        if (normalized.length !in 2..96) return false

        val handleToken = normalized.substringBefore(" ")
        if (handleToken.length < 3) return false
        if (handleToken.any { it == '/' || it == '\\' }) return false

        val lower = normalized.lowercase()
        val hasTimelineOrBadge = lower.contains(" ago") ||
            lower.contains("edited") ||
            lower.contains("verified user") ||
            lower.contains("개월 전") ||
            lower.contains("년 전") ||
            lower.contains("일 전") ||
            lower.contains("시간 전") ||
            lower.contains("분 전") ||
            lower.contains("초 전") ||
            lower.contains("수정됨")

        return hasTimelineOrBadge || normalized.count { it.isWhitespace() } <= 2
    }

    private fun buildYoutubeFallbackRois(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int,
        rawCandidates: List<VisualTextRoi>
    ): List<VisualTextRoi> {
        if (nodes.none { it.packageName == YOUTUBE_PACKAGE }) return emptyList()
        if (rawCandidates.any { it.source == "youtube-composite-card" }) return emptyList()

        val topControlBottom = nodes
            .asSequence()
            .filter { node ->
                val height = node.bottom - node.top
                node.top in 0..(screenHeight * TOP_CONTROL_REGION_RATIO).toInt() &&
                    height in MIN_HEIGHT_PX..TOP_CONTROL_REGION_MAX_PX
            }
            .map { it.bottom }
            .maxOrNull()

        val filterBottom = nodes
            .asSequence()
            .filter { node ->
                val text = node.displayText.orEmpty().trim().lowercase()
                text in YOUTUBE_FILTER_LABELS && node.top in 0..(screenHeight * 0.28f).toInt()
            }
            .map { it.bottom }
            .maxOrNull()
            ?: topControlBottom
            ?: return emptyList()

        val firstTop = filterBottom + SCREEN_EDGE_PADDING_PX
        val bandHeight = (screenHeight * FALLBACK_BAND_HEIGHT_RATIO).toInt().coerceAtLeast(MIN_HEIGHT_PX)
        val maxVisibleTop = (screenHeight * MAX_VISIBLE_TOP_RATIO).toInt()
        val bandStep = (bandHeight - FALLBACK_BAND_OVERLAP_PX).coerceAtLeast(MIN_HEIGHT_PX)
        val fallbackRois = mutableListOf<VisualTextRoi>()
        var top = firstTop

        while (fallbackRois.size < MAX_FALLBACK_BAND_COUNT && top < maxVisibleTop) {
            val bottom = min(screenHeight, top + bandHeight)
            if (bottom - top < MIN_HEIGHT_PX) break
            fallbackRois += VisualTextRoi(
                boundsInScreen = BoundsRect(
                    left = 0,
                    top = top,
                    right = screenWidth,
                    bottom = bottom
                ),
                source = "youtube-visible-band",
                priority = 9,
                reason = "fallback-first-viewport-band"
            )
            top += bandStep
        }

        return fallbackRois
    }

    private fun buildYoutubeClippedTopCompositeRois(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int,
        rawCandidates: List<VisualTextRoi>
    ): List<VisualTextRoi> {
        if (nodes.none { it.packageName == YOUTUBE_PACKAGE }) return emptyList()
        val firstCompositeTop = rawCandidates
            .asSequence()
            .filter { it.source == "youtube-composite-card" }
            .map { it.boundsInScreen.top }
            .minOrNull()
            ?: return emptyList()

        val filterBottom = nodes
            .asSequence()
            .filter { node ->
                val text = node.displayText.orEmpty().trim().lowercase()
                text in YOUTUBE_FILTER_LABELS && node.top in 0..(screenHeight * 0.28f).toInt()
            }
            .map { it.bottom }
            .maxOrNull()
            ?: return emptyList()

        val clippedTopNode = nodes
            .asSequence()
            .filter { node ->
                if (!node.isVisibleToUser) return@filter false
                val text = node.displayText.orEmpty().replace(Regex("\\s+"), " ").trim()
                val width = node.right - node.left
                val height = node.bottom - node.top
                val contentDescriptionOnly = node.text.isNullOrBlank() && !node.contentDescription.isNullOrBlank()

                contentDescriptionOnly &&
                    node.top >= filterBottom &&
                    node.top < firstCompositeTop &&
                    width >= (screenWidth * MAX_FULL_WIDTH_RATIO).toInt() &&
                    height in MIN_HEIGHT_PX / 2..CLIPPED_TOP_COMPOSITE_MAX_HEIGHT_PX &&
                    isUsefulSourceText(text) &&
                    isMediaCardDescription(text)
            }
            .minByOrNull { it.top }
            ?: return emptyList()

        val top = clippedTopNode.top.coerceIn(0, screenHeight)
        val bottom = min(
            screenHeight,
            max(
                firstCompositeTop + FALLBACK_BAND_OVERLAP_PX,
                top + TOP_HERO_MEDIA_MIN_HEIGHT_PX
            )
        )
        if (bottom - top < MIN_HEIGHT_PX) return emptyList()

        return listOf(
            VisualTextRoi(
                boundsInScreen = BoundsRect(
                    left = 0,
                    top = top,
                    right = screenWidth,
                    bottom = bottom
                ),
                source = "youtube-visible-band",
                priority = -1,
                reason = "fallback-clipped-top-composite"
            )
        )
    }

    private fun buildYoutubeExpandedShortCompositeRois(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int,
        rawCandidates: List<VisualTextRoi>
    ): List<VisualTextRoi> {
        if (nodes.none { it.packageName == YOUTUBE_PACKAGE }) return emptyList()

        return rawCandidates
            .asSequence()
            .filter { candidate ->
                candidate.source == "youtube-composite-card" &&
                    candidate.boundsInScreen.bottom - candidate.boundsInScreen.top <= SHORT_COMPOSITE_EXPAND_MAX_HEIGHT_PX
            }
            .mapNotNull { candidate ->
                val titleNode = findVisibleTitleNodeBelowComposite(candidate, nodes) ?: return@mapNotNull null
                val expandedBottom = min(
                    screenHeight,
                    max(candidate.boundsInScreen.bottom, titleNode.bottom + SCREEN_EDGE_PADDING_PX)
                )
                val expandedHeight = expandedBottom - candidate.boundsInScreen.top
                if (expandedHeight > SHORT_COMPOSITE_EXPANDED_MAX_HEIGHT_PX) return@mapNotNull null

                VisualTextRoi(
                    boundsInScreen = BoundsRect(
                        left = candidate.boundsInScreen.left,
                        top = candidate.boundsInScreen.top,
                        right = candidate.boundsInScreen.right,
                        bottom = expandedBottom
                    ),
                    source = candidate.source,
                    priority = -1,
                    reason = "expanded-short-composite-title",
                    sourceText = candidate.sourceText
                )
            }
            .toList()
    }

    private fun buildYoutubeShortCardThumbnailRois(
        rawCandidates: List<VisualTextRoi>,
        screenWidth: Int,
        screenHeight: Int
    ): List<VisualTextRoi> {
        return rawCandidates
            .asSequence()
            .filter { candidate ->
                candidate.source == "youtube-composite-card" &&
                    candidate.sourceText.lowercase().contains("play short")
            }
            .mapNotNull { candidate ->
                val bounds = candidate.boundsInScreen
                val width = bounds.right - bounds.left
                val height = bounds.bottom - bounds.top
                val widthRatio = width.toFloat() / screenWidth.toFloat()

                if (
                    widthRatio < SHORTS_THUMBNAIL_CARD_MIN_WIDTH_RATIO ||
                    widthRatio > SHORTS_THUMBNAIL_CARD_MAX_WIDTH_RATIO ||
                    height < SHORTS_THUMBNAIL_CARD_MIN_HEIGHT_PX
                ) {
                    return@mapNotNull null
                }

                val thumbnailBottom = min(
                    screenHeight,
                    bounds.top + (height * SHORTS_THUMBNAIL_HEIGHT_RATIO).toInt()
                )
                if (thumbnailBottom - bounds.top < MIN_HEIGHT_PX) return@mapNotNull null

                val roiBounds = padAndClamp(
                    BoundsRect(
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = thumbnailBottom
                    ),
                    screenWidth,
                    screenHeight
                ) ?: return@mapNotNull null

                VisualTextRoi(
                    boundsInScreen = roiBounds,
                    source = candidate.source,
                    priority = -2,
                    reason = "short-card-thumbnail-segment",
                    sourceText = candidate.sourceText
                )
            }
            .toList()
    }

    private fun findVisibleTitleNodeBelowComposite(
        candidate: VisualTextRoi,
        nodes: List<ParsedTextNode>
    ): ParsedTextNode? {
        val candidateBounds = candidate.boundsInScreen
        val candidateSourceKey = compactCardText(candidate.sourceText)
        if (candidateSourceKey.isBlank()) return null

        return nodes
            .asSequence()
            .filter { node ->
                if (!node.isVisibleToUser || node.packageName != YOUTUBE_PACKAGE) return@filter false
                val text = node.displayText.orEmpty().replace(Regex("\\s+"), " ").trim()
                val textKey = compactCardText(text)
                val width = node.right - node.left
                val height = node.bottom - node.top
                val verticalGap = node.top - candidateBounds.bottom
                val contentDescriptionOnly = node.text.isNullOrBlank() && !node.contentDescription.isNullOrBlank()

                !contentDescriptionOnly &&
                    textKey.isNotBlank() &&
                    textKey.length >= 4 &&
                    candidateSourceKey.contains(textKey) &&
                    width >= MIN_WIDTH_PX &&
                    height in MIN_HEIGHT_PX / 2..TOP_CONTROL_REGION_MAX_PX &&
                    verticalGap in -SCREEN_EDGE_PADDING_PX..SHORT_COMPOSITE_TITLE_GAP_MAX_PX &&
                    horizontalOverlapRatio(candidateBounds, BoundsRect(node.left, node.top, node.right, node.bottom)) >=
                    SHORT_COMPOSITE_TITLE_OVERLAP_RATIO
            }
            .minByOrNull { it.top }
    }

    private fun compactCardText(text: String): String {
        return text
            .lowercase()
            .replace(Regex("""[\s"'`“”‘’.,!?_\-\[\]\(\):|#]+"""), "")
    }

    private fun horizontalOverlapRatio(first: BoundsRect, second: BoundsRect): Float {
        val overlapLeft = max(first.left, second.left)
        val overlapRight = min(first.right, second.right)
        if (overlapRight <= overlapLeft) return 0f

        val smallerWidth = min(first.right - first.left, second.right - second.left).coerceAtLeast(1)
        return (overlapRight - overlapLeft).toFloat() / smallerWidth.toFloat()
    }

    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val YOUTUBE_COMMENT_PANEL_SOURCE = "youtube-comment-panel"
    private val YOUTUBE_COMMENT_PANEL_CONTENT_VIEW_IDS = setOf(
        "com.google.android.youtube:id/panel_content_touch_wrapper",
        "com.google.android.youtube:id/panel_content",
        "com.google.android.youtube:id/engagement_panel_content"
    )

    private val ACCESSIBILITY_FIRST_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.searchlite",
        "com.android.browser",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser"
    )

    private val YOUTUBE_FILTER_LABELS = setOf(
        "all",
        "shorts",
        "unwatched",
        "watched",
        "videos",
        "전체",
        "쇼츠",
        "동영상"
    )
}
