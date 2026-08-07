package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class YoutubeSemanticFastMaskerTest {

    @Test
    fun buildFromNodes_masksHarmfulCommentFromSharedSnapshot() {
        val result = YoutubeSemanticFastMasker.buildFromNodes(
            nodes = listOf(
                node("댓글 2", 40, 650, 180, 704),
                node("오늘 영상 너무 좋네요", 132, 810, 780, 858),
                node("하...씨발..또 다시 보여줘야돼?", 132, 990, 920, 1084)
            ),
            source = "shared-event-source",
            visitedNodeCount = 3,
            screenWidth = 1080,
            screenHeight = 2400,
            timestamp = 123L
        )

        assertEquals("shared-event-source", result.source)
        assertEquals(3, result.visitedNodeCount)
        assertNotNull(result.response)
        assertEquals(1, result.response!!.results.size)
        assertEquals("씨발", result.response!!.results.single().evidenceSpans.single().text)
    }

    @Test
    fun buildFromNodes_masksTheWholeCommentAndAllowsScrollTranslation() {
        val result = YoutubeSemanticFastMasker.buildFromNodes(
            nodes = listOf(
                node("댓글 1", 40, 650, 180, 704),
                node("하...씨발..또 다시 보여줘야돼?", 132, 990, 920, 1084)
            ),
            source = "shared-event-source",
            visitedNodeCount = 2,
            screenWidth = 1080,
            screenHeight = 2400,
            timestamp = 123L
        )

        val specs = YoutubeCommentGateOverlayPlanner.buildBlockedCommentSpecs(
            results = result.response!!.results,
            screenWidth = 1080,
            screenHeight = 2400
        )
        assertEquals(1, specs.size)
        assertEquals("차단된 댓글", specs.single().label)
        assertEquals(132, specs.single().left)
        assertEquals(990, specs.single().top)
        assertEquals(788, specs.single().width)
        assertEquals(94, specs.single().height)

        val translated = AndroidMaskOverlayPlanner.translatePlan(
            specs = specs,
            deltaX = 0,
            deltaY = -120,
            screenWidth = 1080,
            screenHeight = 2400
        )
        assertEquals(MaskOverlayTranslationStatus.TRANSLATED, translated.status)
        assertEquals(870, translated.specs.single().top)
    }

    private fun node(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = "com.google.android.youtube",
            text = text,
            contentDescription = null,
            displayText = text,
            className = "android.widget.TextView",
            viewIdResourceName = null,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true
        )
    }
}
