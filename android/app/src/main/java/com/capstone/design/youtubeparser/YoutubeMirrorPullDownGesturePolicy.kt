package com.capstone.design.youtubeparser

import kotlin.math.abs

internal object YoutubeMirrorPullDownGesturePolicy {
    private const val TOP_TOLERANCE_PX = 1f
    private const val VERTICAL_DOMINANCE_RATIO = 1.15f

    fun startsAtTop(scrollOffset: Float): Boolean = scrollOffset <= TOP_TOLERANCE_PX

    fun shouldCapture(
        startedAtTop: Boolean,
        deltaX: Float,
        deltaY: Float,
        touchSlop: Float
    ): Boolean {
        if (!startedAtTop || deltaY <= touchSlop) return false
        return deltaY > abs(deltaX) * VERTICAL_DOMINANCE_RATIO
    }

    fun isDirectionResolved(deltaX: Float, deltaY: Float, touchSlop: Float): Boolean {
        return abs(deltaX) > touchSlop || abs(deltaY) > touchSlop
    }

    fun shouldDismiss(distanceY: Float, dismissDistance: Float): Boolean {
        return distanceY >= dismissDistance
    }
}
