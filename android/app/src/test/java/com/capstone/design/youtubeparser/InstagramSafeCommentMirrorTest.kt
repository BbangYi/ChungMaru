package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramSafeCommentMirrorTest {
    @Test
    fun extractor_preservesAuthorForCombinedAndPairedRows() {
        val comments = InstagramCommentExtractor.extractComments(
            listOf(
                node("combined.user This is a combined comment", 300, 360),
                node("@paired.user", 500, 540),
                node("This is a separately exposed comment", 548, 610)
            )
        )

        assertEquals(
            listOf("combined.user", "paired.user"),
            comments.mapNotNull { comment -> comment.authorId }.distinct()
        )
    }

    @Test
    fun analysisAdapter_addsInstagramSourceWithoutReparsing() {
        val adapted = InstagramCommentAnalysisAdapter.adapt(
            listOf(
                ParsedComment(
                    commentText = "A safe comment body",
                    boundsInScreen = BoundsRect(80, 300, 900, 360),
                    authorId = "sample.user"
                )
            )
        )

        assertEquals(
            "android-accessibility-comment:instagram:sample.user",
            adapted.single().authorId
        )
    }

    @Test
    fun analysisAdapter_dropsInstagramComposerStatusText() {
        val adapted = InstagramCommentAnalysisAdapter.adapt(
            listOf(
                ParsedComment(
                    commentText = "\uB2F5\uAE00 \uB0A8\uAE30\uB294 \uC911",
                    boundsInScreen = BoundsRect(0, 0, 400, 80),
                    authorId = "composer"
                ),
                ParsedComment(
                    commentText = "A real Instagram comment",
                    boundsInScreen = BoundsRect(80, 300, 900, 360),
                    authorId = "safe.user"
                )
            )
        )

        assertEquals(listOf("A real Instagram comment"), adapted.map { it.commentText })
    }

    @Test
    fun safeAssembler_ignoresOtherPlatformsAndKeepsInstagramAuthor() {
        val batch = InstagramSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                result(
                    text = "A safe Instagram comment",
                    source = "android-accessibility-comment:instagram:safe.user"
                ),
                result(
                    text = "A harmful Instagram comment",
                    source = "android-accessibility-comment:instagram:blocked.user",
                    offensive = true
                ),
                result(
                    text = "A YouTube result must not leak into this mirror",
                    source = "android-accessibility-comment:youtube:other"
                ),
                result(
                    text = "\uB2F5\uAE00\uC744 \uB0A8\uAE30\uB294 \uC911",
                    source = "android-accessibility-comment:instagram:composer"
                )
            )
        )

        assertEquals(2, batch.rawLineCount)
        assertEquals(1, batch.safeComments.size)
        assertEquals("@safe.user", batch.safeComments.single().author)
        assertEquals(1, batch.harmfulCommentCount)
    }

    @Test
    fun surfaceDetector_detectsPhoneCommentPanelAndLeavesHeaderExposed() {
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = listOf(
                node("Comments", 100, 160, left = 24, right = 260),
                node("first.user First visible comment", 220, 300, left = 80, right = 1120),
                node("Reply", 310, 344, left = 150, right = 260),
                node(
                    "Add a comment...",
                    1780,
                    1840,
                    left = 0,
                    right = 1200,
                    viewId = "com.instagram.android:id/comment_composer_input"
                )
            ),
            screenWidth = 1200,
            screenHeight = 1920
        )

        assertNotNull(surface)
        assertEquals(168, surface!!.boundsInScreen.top)
        assertEquals(1780, surface.boundsInScreen.bottom)
        assertEquals(0, surface.boundsInScreen.left)
        assertEquals(1200, surface.boundsInScreen.right)
    }

    @Test
    fun surfaceDetector_acceptsExactHeaderWhenAccessibilityMarksOnlyHeaderInvisible() {
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = listOf(
                node(
                    "Comments",
                    70,
                    151,
                    left = 60,
                    right = 1117,
                    isVisible = false
                ),
                node("safe.one First visible comment", 264, 378, left = 192, right = 1260),
                node("safe.two Second visible comment", 447, 561, left = 192, right = 1260),
                node("Add a comment...", 2776, 2992, left = 0, right = 1344)
            ),
            screenWidth = 1344,
            screenHeight = 2992,
            density = 3f
        )

        assertNotNull(surface)
        assertEquals(175, surface!!.boundsInScreen.top)
        assertEquals(2776, surface.boundsInScreen.bottom)
        assertEquals(0, surface.boundsInScreen.left)
        assertEquals(1344, surface.boundsInScreen.right)
    }

    @Test
    fun surfaceDetector_doesNotTreatFeedPreviewAsOpenCommentPanel() {
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = listOf(
                node("feed.user A comment preview in the feed", 900, 960),
                node("Add a comment...", 1020, 1080)
            ),
            screenWidth = 1200,
            screenHeight = 1920
        )

        assertNull(surface)
    }

    @Test
    fun surfaceDetector_preservesTabletSidePanelGeometry() {
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = listOf(
                node("Comments", 220, 280, left = 1030, right = 1260),
                node(
                    "",
                    300,
                    1700,
                    left = 980,
                    right = 1920,
                    viewId = "com.instagram.android:id/comment_sheet_recycler_view"
                ),
                node("tablet.user A comment inside the right panel", 360, 430, left = 1060, right = 1810),
                node(
                    "Add a comment...",
                    1640,
                    1700,
                    left = 980,
                    right = 1920,
                    viewId = "com.instagram.android:id/comment_composer_input"
                )
            ),
            screenWidth = 1920,
            screenHeight = 1200
        )

        assertNotNull(surface)
        assertTrue(surface!!.boundsInScreen.left > 800)
        assertEquals(1920, surface.boundsInScreen.right)
        assertTrue(surface.boundsInScreen.top >= 280)
    }

    private fun node(
        text: String,
        top: Int,
        bottom: Int,
        left: Int = 64,
        right: Int = 1000,
        viewId: String? = null,
        isVisible: Boolean = true
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = "com.instagram.android",
            text = text,
            contentDescription = null,
            displayText = text,
            className = "android.widget.TextView",
            viewIdResourceName = viewId,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = isVisible
        )
    }

    private fun result(
        text: String,
        source: String,
        offensive: Boolean = false
    ): AndroidAnalysisResultItem {
        return AndroidAnalysisResultItem(
            original = text,
            boundsInScreen = BoundsRect(80, 300, 900, 360),
            authorId = source,
            isOffensive = offensive,
            isProfane = offensive,
            isToxic = false,
            isHate = false,
            scores = HarmScores(),
            evidenceSpans = emptyList()
        )
    }
}
