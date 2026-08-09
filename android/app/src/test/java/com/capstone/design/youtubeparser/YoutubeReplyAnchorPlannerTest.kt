package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeReplyAnchorPlannerTest {

    private val row = BoundsRect(36, 1190, 1344, 1564)
    private val reply = BoundsRect(414, 1420, 561, 1564)
    private val sourceSpec = MaskOverlaySpec(
        left = 600,
        top = 1300,
        width = 140,
        height = 48,
        label = "comment-blocked",
        debugSource = "youtube-comment-blocked-model:0",
        style = MaskOverlayStyle.BLOCKED
    )

    @Test
    fun anchor_coversStableCommentBodyBandAboveReply() {
        val anchored = requireNotNull(
            YoutubeReplyAnchorPlanner.anchor(
                spec = sourceSpec,
                rowBounds = row,
                replyBounds = reply,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )

        val rendered = requireNotNull(
            anchored.specAt(
                replyTop = reply.top,
                visibleRowBounds = row,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )

        assertEquals(132, rendered.left)
        assertEquals(1244, rendered.top)
        assertEquals(1140, rendered.width)
        assertEquals(168, rendered.height)
        assertEquals(MaskOverlayStyle.BLOCKED, rendered.style)
        assertTrue(rendered.debugSource.endsWith(":reply-anchor"))
    }

    @Test
    fun specAt_movesExactlyWithReplyWithoutAccumulatedDrift() {
        val anchored = requireNotNull(
            YoutubeReplyAnchorPlanner.anchor(
                spec = sourceSpec,
                rowBounds = row,
                replyBounds = reply,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )
        val movedRow = BoundsRect(row.left, row.top - 400, row.right, row.bottom - 400)
        val moved = requireNotNull(
            anchored.specAt(
                replyTop = reply.top - 400,
                visibleRowBounds = movedRow,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )
        val returned = requireNotNull(
            anchored.specAt(
                replyTop = reply.top,
                visibleRowBounds = row,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )

        assertEquals(844, moved.top)
        assertEquals(1244, returned.top)
        assertEquals(168, moved.height)
        assertEquals(168, returned.height)
    }

    @Test
    fun specAt_clipsAtViewportAndDisappearsWhenBodyIsNoLongerVisible() {
        val anchored = requireNotNull(
            YoutubeReplyAnchorPlanner.anchor(
                spec = sourceSpec,
                rowBounds = row,
                replyBounds = reply,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )

        val clipped = requireNotNull(
            anchored.specAt(
                replyTop = 176,
                visibleRowBounds = BoundsRect(36, 0, 1344, 180),
                screenWidth = 1344,
                screenHeight = 2992
            )
        )
        val offscreen = anchored.specAt(
            replyTop = 140,
            visibleRowBounds = BoundsRect(36, 0, 1344, 10),
            screenWidth = 1344,
            screenHeight = 2992
        )

        assertEquals(0, clipped.top)
        assertEquals(168, clipped.height)
        assertNull(offscreen)
    }

    @Test
    fun anchor_capsLongCommentBodyWithoutCrossingReply() {
        val longRow = BoundsRect(36, 800, 1344, 2200)
        val longReply = BoundsRect(414, 2000, 561, 2080)

        val anchored = requireNotNull(
            YoutubeReplyAnchorPlanner.anchor(
                spec = sourceSpec,
                rowBounds = longRow,
                replyBounds = longReply,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )
        val rendered = requireNotNull(
            anchored.specAt(
                replyTop = longReply.top,
                visibleRowBounds = longRow,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )

        assertEquals(1752, rendered.top)
        assertEquals(240, rendered.height)
        assertEquals(1992, rendered.top + rendered.height)
    }

    @Test
    fun anchor_rejectsReplyThatDoesNotSitBelowCommentBody() {
        val invalidReply = BoundsRect(414, 1230, 561, 1300)

        assertNull(
            YoutubeReplyAnchorPlanner.anchor(
                spec = sourceSpec,
                rowBounds = row,
                replyBounds = invalidReply,
                screenWidth = 1344,
                screenHeight = 2992
            )
        )
    }
}
