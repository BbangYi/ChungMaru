package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min

data class InstagramCommentSurface(
    val boundsInScreen: BoundsRect,
    val commentCount: Int,
    val confidence: Int,
    val signature: String
)

object InstagramCommentSurfaceDetector {
    private const val MIN_CONFIDENCE = 6
    private const val MIN_PANEL_HEIGHT_PX = 180
    private const val MIN_CONTAINER_WIDTH_RATIO = 0.32f
    private const val SIDE_PANEL_START_RATIO = 0.42f

    private const val KOREAN_COMMENTS = "\uB313\uAE00"
    private const val KOREAN_REPLY = "\uB2F5\uAE00"
    private const val KOREAN_COUNT_MAGNITUDES = "\uB9CC\uCC9C"
    private const val KOREAN_COUNT_SUFFIX = "\uAC1C"
    fun detect(
        nodes: List<ParsedTextNode>,
        screenWidth: Int,
        screenHeight: Int,
        density: Float = 1f
    ): InstagramCommentSurface? {
        if (nodes.isEmpty() || screenWidth <= 0 || screenHeight <= 0) return null

        val boundedNodes = nodes.filter { node ->
            node.right > 0 &&
                node.bottom > 0 &&
                node.left < screenWidth &&
                node.top < screenHeight &&
                node.right > node.left &&
                node.bottom > node.top
        }
        val visibleNodes = boundedNodes.filter { node -> node.isVisibleToUser }
        if (visibleNodes.isEmpty()) return null

        // Instagram can mark the header text itself invisible while its panel children remain visible.
        // Exact, on-screen title geometry is still reliable evidence for the open comment surface.
        val titleNode = boundedNodes
            .filter { node -> isCommentTitle(node.displayText.orEmpty()) }
            .minByOrNull { node -> node.top }
        val composerNodes = visibleNodes.filter(::isComposerNode)
        val containerNodes = visibleNodes.filter(::isCommentContainerNode)
        val parsedComments = InstagramCommentExtractor.extractComments(visibleNodes)
        val metadataCount = visibleNodes.count { node ->
            isCommentMetadata(node.displayText.orEmpty())
        }

        val credibleContainer = containerNodes
            .filter { node ->
                node.right - node.left >= screenWidth * MIN_CONTAINER_WIDTH_RATIO &&
                    node.bottom - node.top >= MIN_PANEL_HEIGHT_PX
            }
            .maxByOrNull { node ->
                (node.right - node.left).toLong() * (node.bottom - node.top)
            }

        val hasStrongPanelEvidence =
            titleNode != null ||
                (
                    credibleContainer != null &&
                        (
                            composerNodes.isNotEmpty() ||
                                parsedComments.size >= 2 ||
                                metadataCount >= 2
                            )
                    )
        if (!hasStrongPanelEvidence) return null

        val confidence =
            (if (titleNode != null) 6 else 0) +
                (if (credibleContainer != null) 5 else 0) +
                (if (composerNodes.isNotEmpty()) 2 else 0) +
                min(parsedComments.size, 3) +
                (if (metadataCount >= 2) 1 else 0)
        if (confidence < MIN_CONFIDENCE) return null

        val commentUnion = unionBounds(parsedComments.map { comment -> comment.boundsInScreen })
        val containerBounds = credibleContainer?.toBoundsRect()
        val titleBottom = titleNode?.bottom
        val top = when {
            titleBottom != null -> {
                val afterHeader = titleBottom + dp(8, density)
                max(afterHeader, containerBounds?.top?.takeIf { it >= titleBottom } ?: afterHeader)
            }
            containerBounds != null -> containerBounds.top
            commentUnion != null -> commentUnion.top - dp(56, density)
            else -> (screenHeight * 0.18f).toInt()
        }.coerceIn(0, screenHeight)

        val composerTop = composerNodes
            .asSequence()
            .map { node -> node.top }
            .filter { value -> value >= top + MIN_PANEL_HEIGHT_PX }
            .minOrNull()
        val preferredBottom = when {
            composerTop != null -> composerTop
            containerBounds != null && containerBounds.bottom >= top + MIN_PANEL_HEIGHT_PX ->
                containerBounds.bottom
            else -> screenHeight - dp(20, density)
        }
        var bottom = preferredBottom.coerceIn(top, screenHeight)
        if (bottom - top < MIN_PANEL_HEIGHT_PX) {
            bottom = screenHeight
        }
        if (bottom - top < MIN_PANEL_HEIGHT_PX) return null

        val horizontalBounds = containerBounds ?: commentUnion
        val left: Int
        val right: Int
        if (horizontalBounds == null) {
            left = 0
            right = screenWidth
        } else if (horizontalBounds.left > screenWidth * SIDE_PANEL_START_RATIO) {
            left = (horizontalBounds.left - dp(88, density)).coerceIn(0, screenWidth)
            right = screenWidth
        } else {
            val horizontalWidth = horizontalBounds.right - horizontalBounds.left
            if (horizontalWidth >= screenWidth * 0.72f) {
                left = 0
                right = screenWidth
            } else {
                left = (horizontalBounds.left - dp(72, density)).coerceIn(0, screenWidth)
                right = (horizontalBounds.right + dp(72, density)).coerceIn(left, screenWidth)
            }
        }
        if (right - left < 160) return null

        val bounds = BoundsRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
        return InstagramCommentSurface(
            boundsInScreen = bounds,
            commentCount = parsedComments.size,
            confidence = confidence,
            signature = "${bounds.left}|${bounds.top}|${bounds.right}|" +
                "${bounds.bottom}|${parsedComments.size}"
        )
    }

    fun isCommentStructureViewId(viewIdResourceName: String?): Boolean {
        val id = viewIdResourceName.orEmpty().lowercase()
        if (!id.contains("comment")) return false
        return id.contains("recycler") ||
            id.contains("list") ||
            id.contains("sheet") ||
            id.contains("panel") ||
            id.contains("container") ||
            id.contains("thread")
    }

    private fun isCommentContainerNode(node: ParsedTextNode): Boolean {
        return isCommentStructureViewId(node.viewIdResourceName)
    }

    private fun isComposerNode(node: ParsedTextNode): Boolean {
        val id = node.viewIdResourceName.orEmpty().lowercase()
        val text = node.displayText.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
        val composerId =
            id.contains("comment") &&
                (
                    id.contains("composer") ||
                        id.contains("input") ||
                        id.contains("field")
                    )
        return composerId ||
            text == "\uB313\uAE00 \uB2EC\uAE30" ||
            text == "\uB313\uAE00 \uB2EC\uAE30..." ||
            text == "\uB313\uAE00 \uCD94\uAC00" ||
            text == "\uB313\uAE00 \uCD94\uAC00..." ||
            text == "add a comment" ||
            text == "add a comment..."
    }

    private fun isCommentTitle(value: String): Boolean {
        val text = value.replace(Regex("\\s+"), " ").trim().lowercase()
        return text == "comments" ||
            text == KOREAN_COMMENTS ||
            Regex("^comments\\s+[\\d,.kmb]+$").matches(text) ||
            Regex("^$KOREAN_COMMENTS\\s*[\\d,.$KOREAN_COUNT_MAGNITUDES]+\\s*$KOREAN_COUNT_SUFFIX?$").matches(text)
    }

    private fun isCommentMetadata(value: String): Boolean {
        val text = value.replace(Regex("\\s+"), " ").trim().lowercase()
        return text == KOREAN_REPLY ||
            text == "reply" ||
            text.endsWith("\uCD08 \uC804") ||
            text.endsWith("\uBD84 \uC804") ||
            text.endsWith("\uC2DC\uAC04 \uC804") ||
            text.endsWith("\uC77C \uC804") ||
            text.endsWith("\uC8FC \uC804") ||
            text.endsWith("s ago") ||
            text.endsWith("m ago") ||
            text.endsWith("h ago") ||
            text.endsWith("d ago") ||
            text.endsWith("w ago")
    }

    private fun ParsedTextNode.toBoundsRect(): BoundsRect {
        return BoundsRect(left = left, top = top, right = right, bottom = bottom)
    }

    private fun unionBounds(bounds: List<BoundsRect>): BoundsRect? {
        if (bounds.isEmpty()) return null
        return BoundsRect(
            left = bounds.minOf { rect -> rect.left },
            top = bounds.minOf { rect -> rect.top },
            right = bounds.maxOf { rect -> rect.right },
            bottom = bounds.maxOf { rect -> rect.bottom }
        )
    }

    private fun dp(value: Int, density: Float): Int {
        return (value * density.coerceAtLeast(1f)).toInt()
    }
}
