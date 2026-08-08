package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min

internal data class YoutubeReplyAnchoredMask(
    val left: Int,
    val right: Int,
    val topOffsetFromReply: Int,
    val bottomOffsetFromReply: Int,
    val label: String,
    val debugSource: String,
    val style: MaskOverlayStyle
) {
    fun specAt(
        replyTop: Int,
        visibleRowBounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): MaskOverlaySpec? {
        val top = max(replyTop + topOffsetFromReply, visibleRowBounds.top).coerceIn(0, screenHeight)
        val bottom = min(
            min(replyTop + bottomOffsetFromReply, replyTop - 4),
            visibleRowBounds.bottom
        ).coerceIn(0, screenHeight)
        val safeLeft = left.coerceIn(0, screenWidth)
        val safeRight = right.coerceIn(safeLeft, screenWidth)
        if (safeRight - safeLeft < 40 || bottom - top < 16) return null

        return MaskOverlaySpec(
            left = safeLeft,
            top = top,
            width = safeRight - safeLeft,
            height = bottom - top,
            label = label,
            allowScrollTranslation = true,
            debugSource = debugSource,
            style = style
        )
    }
}

internal object YoutubeReplyAnchorPlanner {
    private const val ROW_AUTHOR_BAND_PX = 54
    private const val ACTION_GAP_PX = 8
    private const val MIN_MASK_HEIGHT_PX = 32
    private const val MAX_MASK_HEIGHT_PX = 240
    private const val BODY_START_INSET_PX = 96
    private const val BODY_END_INSET_PX = 72

    fun anchor(
        spec: MaskOverlaySpec,
        rowBounds: BoundsRect,
        replyBounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): YoutubeReplyAnchoredMask? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        if (replyBounds.bottom <= replyBounds.top || rowBounds.bottom <= rowBounds.top) return null
        if (replyBounds.top <= rowBounds.top + ROW_AUTHOR_BAND_PX) return null

        val bodyLeft = max(rowBounds.left + BODY_START_INSET_PX, 0)
        val bodyRight = min(rowBounds.right - BODY_END_INSET_PX, screenWidth)
        val left = bodyLeft.coerceIn(0, screenWidth)
        val right = bodyRight.coerceIn(left, screenWidth)

        val maxBottom = min(replyBounds.top - ACTION_GAP_PX, rowBounds.bottom)
        val minTop = (rowBounds.top + ROW_AUTHOR_BAND_PX).coerceAtMost(maxBottom)
        var top = max(minTop, maxBottom - MAX_MASK_HEIGHT_PX)
        var bottom = maxBottom
        if (bottom - top < MIN_MASK_HEIGHT_PX) {
            bottom = maxBottom
            top = (bottom - MIN_MASK_HEIGHT_PX).coerceAtLeast(minTop)
        }
        if (right - left < 40 || bottom - top < 16) return null

        return YoutubeReplyAnchoredMask(
            left = left,
            right = right,
            topOffsetFromReply = top - replyBounds.top,
            bottomOffsetFromReply = bottom - replyBounds.top,
            label = spec.label,
            debugSource = "${spec.debugSource}:reply-anchor",
            style = spec.style
        )
    }
}
