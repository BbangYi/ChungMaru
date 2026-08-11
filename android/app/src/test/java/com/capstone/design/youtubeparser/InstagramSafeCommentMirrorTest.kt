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
    fun extractor_parsesKoreanReelsAccessibilityAnnouncements() {
        val comments = InstagramCommentExtractor.extractComments(
            listOf(
                node("dongwonmall", 789, 821, left = 258, right = 452),
                node("23시간 전", 789, 821, left = 454, right = 526),
                node(
                    "dongwonmall님이 참치는 무조건 있어야지. 댓글을 달았습니다",
                    829,
                    865,
                    left = 258,
                    right = 540
                ),
                node("작성자", 942, 975, left = 549, right = 615),
                node(
                    "heromaganight.15님이 Ai 댓글을 달았습니다",
                    1289,
                    1325,
                    left = 258,
                    right = 540
                ),
                node("답글 6개 더 보기", 1386, 1417, left = 320, right = 481)
            )
        )

        assertEquals(
            listOf("참치는 무조건 있어야지.", "Ai"),
            comments.map { comment -> comment.commentText }
        )
        assertEquals(
            listOf("dongwonmall", "heromaganight.15"),
            comments.mapNotNull { comment -> comment.authorId }
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
    fun analysisAdapter_dropsAuthorlessFeedCaptionFromCommentPanel() {
        val adapted = InstagramCommentAnalysisAdapter.adapt(
            listOf(
                ParsedComment(
                    commentText = "\uAC15\uB989\uD3B8 \uD83D\uDE8C\uD83D\uDE85\uD83C\uDF0A\uD83D\uDC99 \u2026",
                    boundsInScreen = BoundsRect(161, 1728, 1096, 1776),
                    authorId = null
                ),
                ParsedComment(
                    commentText = "\uB108\uBB34 \uADC0\uC5FD\uB2E4",
                    boundsInScreen = BoundsRect(130, 166, 302, 202),
                    authorId = "byuru"
                )
            )
        )

        assertEquals(listOf("\uB108\uBB34 \uADC0\uC5FD\uB2E4"), adapted.map { it.commentText })
        assertEquals(
            "android-accessibility-comment:instagram:byuru",
            adapted.single().authorId
        )
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
        assertEquals("safe.user", batch.safeComments.single().author)
        assertEquals(1, batch.harmfulCommentCount)
    }

    @Test
    fun safeAssembler_marksIndentedOrMentionedRowsAsReplies() {
        val batch = InstagramSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                result(
                    text = "A top-level Instagram comment",
                    source = "android-accessibility-comment:instagram:top.user",
                    left = 130
                ),
                result(
                    text = "@top.user This is a reply",
                    source = "android-accessibility-comment:instagram:reply.user",
                    left = 192
                )
            )
        )

        assertEquals(2, batch.safeComments.size)
        assertTrue(!batch.safeComments[0].isReply)
        assertTrue(batch.safeComments[1].isReply)
    }

    @Test
    fun instagramBuffer_removesPreviouslySafeTextWhenLaterMarkedHarmful() {
        val buffer = InstagramSafeCommentBuffer()
        val safe = InstagramSafeComment(
            key = "same.user|unsafe phrase",
            author = "same.user",
            text = "unsafe phrase",
            isReply = false
        )
        buffer.add(
            InstagramSafeCommentBatch(
                rawLineCount = 1,
                safeComments = listOf(safe),
                harmfulCommentCount = 0
            )
        )

        buffer.add(
            InstagramSafeCommentBatch(
                rawLineCount = 1,
                safeComments = emptyList(),
                harmfulCommentCount = 1,
                harmfulKeys = setOf(safe.key),
                harmfulTexts = setOf(safe.text)
            )
        )

        assertTrue(buffer.comments().isEmpty())
    }

    @Test
    fun instagramBuffer_keepsSameTextFromDifferentAuthors() {
        val buffer = InstagramSafeCommentBuffer()
        val comments = listOf("first.user", "second.user").map { author ->
            InstagramSafeComment(
                key = "$author|\uADC0\uC5EC\uC6CC",
                author = author,
                text = "\uADC0\uC5EC\uC6CC",
                isReply = false
            )
        }

        buffer.add(
            InstagramSafeCommentBatch(
                rawLineCount = comments.size,
                safeComments = comments,
                harmfulCommentCount = 0
            )
        )

        assertEquals(2, buffer.comments().size)
    }

    @Test
    fun surfaceDetector_detectsLiveTabletReelsBottomSheetGeometry() {
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = listOf(
                node(
                    "",
                    48,
                    1824,
                    left = 0,
                    right = 1200,
                    viewId = "com.instagram.android:id/bottom_sheet_container"
                ),
                node(
                    "",
                    772,
                    1550,
                    left = 128,
                    right = 1071,
                    viewId = "com.instagram.android:id/main_list_view"
                ),
                node(
                    "",
                    772,
                    1550,
                    left = 128,
                    right = 1071,
                    viewId = "com.instagram.android:id/sticky_header_list"
                ),
                node(
                    "dongwonmall님이 참치는 무조건 있어야지. 댓글을 달았습니다",
                    829,
                    865,
                    left = 258,
                    right = 540
                ),
                node(
                    "",
                    1551,
                    1632,
                    left = 128,
                    right = 1071,
                    viewId = "com.instagram.android:id/above_composer_views"
                ),
                node(
                    "",
                    1648,
                    1728,
                    left = 128,
                    right = 1071,
                    viewId = "com.instagram.android:id/comment_composer_parent_updated"
                )
            ),
            screenWidth = 1200,
            screenHeight = 1920,
            density = 2f
        )

        assertNotNull(surface)
        assertEquals(InstagramCommentSurfaceKind.REELS_BOTTOM_SHEET, surface!!.kind)
        assertEquals(720, surface.boundsInScreen.top)
        assertEquals(1728, surface.boundsInScreen.bottom)
        assertEquals(128, surface.boundsInScreen.left)
        assertEquals(1071, surface.boundsInScreen.right)
    }

    @Test
    fun surfaceDetector_detectsReelsStructureBeforeCommentsAreExposed() {
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = listOf(
                node(
                    "",
                    48,
                    1824,
                    left = 0,
                    right = 1200,
                    viewId = "com.instagram.android:id/bottom_sheet_container"
                ),
                node(
                    "",
                    772,
                    1550,
                    left = 128,
                    right = 1071,
                    viewId = "com.instagram.android:id/main_list_view"
                ),
                node(
                    "",
                    1551,
                    1632,
                    left = 128,
                    right = 1071,
                    viewId = "com.instagram.android:id/above_composer_views"
                ),
                node(
                    "",
                    1648,
                    1728,
                    left = 128,
                    right = 1071,
                    viewId = "com.instagram.android:id/comment_composer_parent_updated"
                )
            ),
            screenWidth = 1200,
            screenHeight = 1920,
            density = 2f
        )

        assertNotNull(surface)
        assertEquals(0, surface!!.commentCount)
        assertEquals(InstagramCommentSurfaceKind.REELS_BOTTOM_SHEET, surface.kind)
    }

    @Test
    fun reelsLoadingGate_acceptsOnlyCommentTriggersInsideReelsActions() {
        val reelsAncestors = listOf(
            "com.instagram.android:id/comment_button",
            null,
            "com.instagram.android:id/clips_ufi_component"
        )

        assertTrue(
            InstagramReelsLoadingGate.matchesTrigger(
                "com.instagram.android:id/comment_button",
                reelsAncestors
            )
        )
        assertTrue(
            InstagramReelsLoadingGate.matchesTrigger(
                "com.instagram.android:id/comment_count",
                reelsAncestors
            )
        )
        assertTrue(
            !InstagramReelsLoadingGate.matchesTrigger(
                "com.instagram.android:id/direct_share_button",
                reelsAncestors
            )
        )
        assertTrue(
            !InstagramReelsLoadingGate.matchesTrigger(
                "com.instagram.android:id/comment_button",
                listOf("com.instagram.android:id/feed_ufi_component")
            )
        )
        assertTrue(
            InstagramReelsLoadingGate.matchesTrigger(
                viewIdResourceName = "com.instagram.android:id/comment_button",
                ancestorViewIds = emptyList(),
                sourceBounds = BoundsRect(968, 983, 1056, 1071),
                screenWidth = 1200,
                screenHeight = 1920
            )
        )
        assertTrue(
            !InstagramReelsLoadingGate.matchesTrigger(
                viewIdResourceName = "com.instagram.android:id/comment_button",
                ancestorViewIds = emptyList(),
                sourceBounds = BoundsRect(80, 1200, 168, 1288),
                screenWidth = 1200,
                screenHeight = 1920
            )
        )
    }

    @Test
    fun reelsLoadingGate_matchesLiveTabletCommentSheetBounds() {
        val spec = InstagramReelsLoadingGate.createLoadingSpec(
            screenWidth = 1200,
            screenHeight = 1920
        )

        assertNotNull(spec)
        assertEquals(128, spec!!.left)
        assertEquals(720, spec.top)
        assertEquals(943, spec.width)
        assertEquals(1008, spec.height)
        assertEquals(MaskOverlayStyle.LOADING, spec.style)
    }

    @Test
    fun surfaceDetector_rejectsRegularPostCommentSurfaceWithoutReelsStructure() {
        val surface = InstagramCommentSurfaceDetector.detect(
            nodes = listOf(
                node("Comments", 100, 160, left = 24, right = 260),
                node("first.user First visible comment", 220, 300, left = 80, right = 1120),
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

        assertNull(surface)
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
    fun surfaceDetector_rejectsTabletSidePanelThatIsNotReelsBottomSheet() {
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

        assertNull(surface)
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
        offensive: Boolean = false,
        left: Int = 80
    ): AndroidAnalysisResultItem {
        return AndroidAnalysisResultItem(
            original = text,
            boundsInScreen = BoundsRect(left, 300, 900, 360),
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
