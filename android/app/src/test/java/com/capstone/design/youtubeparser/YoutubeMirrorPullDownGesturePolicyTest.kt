package com.capstone.design.youtubeparser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeMirrorPullDownGesturePolicyTest {

    @Test
    fun downwardVerticalDragAtTopIsCaptured() {
        assertTrue(
            YoutubeMirrorPullDownGesturePolicy.shouldCapture(
                startedAtTop = true,
                deltaX = 4f,
                deltaY = 24f,
                touchSlop = 8f
            )
        )
    }

    @Test
    fun dragDoesNotCaptureWhenMirrorIsScrolled() {
        assertFalse(
            YoutubeMirrorPullDownGesturePolicy.shouldCapture(
                startedAtTop = YoutubeMirrorPullDownGesturePolicy.startsAtTop(20f),
                deltaX = 0f,
                deltaY = 40f,
                touchSlop = 8f
            )
        )
    }

    @Test
    fun horizontalDragDoesNotCapture() {
        assertFalse(
            YoutubeMirrorPullDownGesturePolicy.shouldCapture(
                startedAtTop = true,
                deltaX = 30f,
                deltaY = 20f,
                touchSlop = 8f
            )
        )
    }

    @Test
    fun dismissRequiresFullPullDistance() {
        assertFalse(
            YoutubeMirrorPullDownGesturePolicy.shouldDismiss(
                distanceY = 83f,
                dismissDistance = 84f
            )
        )
        assertTrue(
            YoutubeMirrorPullDownGesturePolicy.shouldDismiss(
                distanceY = 84f,
                dismissDistance = 84f
            )
        )
    }
}
