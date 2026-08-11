package com.capstone.design.youtubeparser

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class InstagramCommentSurfaceKind {
    REELS_BOTTOM_SHEET
}

data class InstagramCommentSurface(
    val boundsInScreen: BoundsRect,
    val commentCount: Int,
    val confidence: Int,
    val signature: String,
    val kind: InstagramCommentSurfaceKind = InstagramCommentSurfaceKind.REELS_BOTTOM_SHEET
)

object InstagramCommentSurfaceDetector {
    private const val MIN_REELS_CONFIDENCE = 11
    private const val MIN_PANEL_HEIGHT_PX = 360
    private const val MIN_REELS_WIDTH_RATIO = 0.55f
    private const val MAX_REELS_WIDTH_RATIO = 0.90f
    private const val MIN_REELS_TOP_RATIO = 0.25f
    private const val MAX_REELS_TOP_RATIO = 0.72f
    private const val MAX_CENTER_OFFSET_RATIO = 0.12f

    private const val BOTTOM_SHEET_ID = "bottom_sheet_container"
    private const val MAIN_LIST_ID = "main_list_view"
    private const val STICKY_LIST_ID = "sticky_header_list"
    private const val ABOVE_COMPOSER_ID = "above_composer_views"
    private const val COMPOSER_PARENT_ID = "comment_composer_parent_updated"
    private const val COMPOSER_INPUT_ID = "layout_comment_thread_edittext_multiline"

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

        val bottomSheet = boundedNodes.firstOrNull { node ->
            node.viewIdResourceName.hasId(BOTTOM_SHEET_ID)
        } ?: return null
        val listNode = visibleNodes
            .filter { node ->
                node.viewIdResourceName.hasId(MAIN_LIST_ID) ||
                    node.viewIdResourceName.hasId(STICKY_LIST_ID)
            }
            .filter { node ->
                val widthRatio = node.width().toFloat() / screenWidth
                val topRatio = node.top.toFloat() / screenHeight
                widthRatio in MIN_REELS_WIDTH_RATIO..MAX_REELS_WIDTH_RATIO &&
                    topRatio in MIN_REELS_TOP_RATIO..MAX_REELS_TOP_RATIO &&
                    node.bottom - node.top >= MIN_PANEL_HEIGHT_PX
            }
            .maxByOrNull { node -> node.width().toLong() * node.height() }
            ?: return null

        val leftMargin = listNode.left
        val rightMargin = screenWidth - listNode.right
        val centered = abs(leftMargin - rightMargin) <= screenWidth * MAX_CENTER_OFFSET_RATIO
        if (!centered) return null

        val composerNodes = visibleNodes.filter { node ->
            val id = node.viewIdResourceName
            id.hasId(ABOVE_COMPOSER_ID) ||
                id.hasId(COMPOSER_PARENT_ID) ||
                id.hasId(COMPOSER_INPUT_ID)
        }
        val alignedComposerNodes = composerNodes.filter { node ->
            node.right >= listNode.left + listNode.width() / 2 &&
                node.left <= listNode.right - listNode.width() / 2 &&
                node.top >= listNode.top
        }
        if (alignedComposerNodes.isEmpty()) return null

        val parsedComments = InstagramCommentExtractor.extractComments(visibleNodes)
        val announcementCount = visibleNodes.count { node ->
            InstagramCommentExtractor.isAccessibilityCommentAnnouncement(
                node.displayText.orEmpty()
            )
        }

        val horizontalNodes = listOf(listNode) + alignedComposerNodes.filter { node ->
            node.width() >= listNode.width() * 0.72f
        }
        val left = horizontalNodes.minOf { node -> node.left }.coerceIn(0, screenWidth)
        val right = horizontalNodes.maxOf { node -> node.right }.coerceIn(left, screenWidth)
        val top = (listNode.top - dp(26, density)).coerceIn(0, screenHeight)
        val composerBottom = alignedComposerNodes.maxOf { node -> node.bottom }
        val bottom = max(listNode.bottom, composerBottom).coerceIn(top, screenHeight)

        if (right - left < screenWidth * MIN_REELS_WIDTH_RATIO) return null
        if (bottom - top < MIN_PANEL_HEIGHT_PX) return null
        if (bottom < screenHeight * 0.70f) return null
        if (bottomSheet.bottom < bottom) return null

        val confidence =
            4 +
                5 +
                3 +
                min(parsedComments.size, 3) +
                min(announcementCount, 2) +
                2
        if (confidence < MIN_REELS_CONFIDENCE) return null

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
            signature = "reels|${bounds.left}|${bounds.top}|${bounds.right}|" +
                "${bounds.bottom}|${parsedComments.size}",
            kind = InstagramCommentSurfaceKind.REELS_BOTTOM_SHEET
        )
    }

    fun isCommentStructureViewId(viewIdResourceName: String?): Boolean {
        val id = viewIdResourceName.orEmpty().lowercase()
        if (id.isBlank()) return false
        if (
            id.hasId(BOTTOM_SHEET_ID) ||
            id.hasId(MAIN_LIST_ID) ||
            id.hasId(STICKY_LIST_ID) ||
            id.hasId(ABOVE_COMPOSER_ID) ||
            id.hasId(COMPOSER_PARENT_ID) ||
            id.hasId(COMPOSER_INPUT_ID)
        ) {
            return true
        }
        if (!id.contains("comment")) return false
        return id.contains("recycler") ||
            id.contains("list") ||
            id.contains("sheet") ||
            id.contains("panel") ||
            id.contains("container") ||
            id.contains("thread") ||
            id.contains("composer")
    }

    private fun ParsedTextNode.width(): Int = right - left

    private fun ParsedTextNode.height(): Int = bottom - top

    private fun String?.hasId(name: String): Boolean {
        val value = this.orEmpty().lowercase()
        return value == name || value.endsWith(":id/$name") || value.endsWith("/$name")
    }

    private fun dp(value: Int, density: Float): Int {
        return (value * density.coerceAtLeast(1f)).toInt()
    }
}
