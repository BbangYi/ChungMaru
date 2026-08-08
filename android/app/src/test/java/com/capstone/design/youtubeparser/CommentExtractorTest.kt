package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentExtractorTest {

    @Test
    fun youtubeExtractor_matchesStandaloneParserAndAnalysisContract() {
        val parsed = YoutubeCommentExtractor.extractComments(
            listOf(
                node(text = "@creator", top = 1000, bottom = 1040),
                node(text = "2시간 전", top = 1048, bottom = 1080),
                node(text = "이 영상 정리 진짜 깔끔하네요", top = 1088, bottom = 1148, width = 720),
                node(text = "답글", top = 1156, bottom = 1184)
            )
        )

        assertEquals(1, parsed.size)
        assertEquals("이 영상 정리 진짜 깔끔하네요", parsed.single().commentText)
        assertEquals("creator", parsed.single().authorId)

        val adapted = YoutubeCommentAnalysisAdapter.adapt(parsed)
        assertEquals(1, adapted.size)
        assertEquals(
            "android-accessibility-comment:youtube:creator",
            adapted.single().authorId
        )
    }

    @Test
    fun youtubeExtractor_keepsOriginalSingleNodeMultilineBody() {
        val body = "문제를 잘못 만듦.\n원을 그리는 것과 지우는 것 1회 허용이라고 써야지"
        val comments = YoutubeCommentExtractor.extractComments(
            listOf(
                node(text = "@wide_tablet", top = 820, bottom = 860, left = 96, width = 460),
                node(text = "7년 전", top = 864, bottom = 900, left = 96, width = 160),
                node(text = body, top = 908, bottom = 1010, left = 96, width = 1048),
                node(text = "답글 총 4개 보기", top = 1020, bottom = 1070, left = 96, width = 280)
            )
        )

        assertEquals(1, comments.size)
        assertEquals(body, comments.single().commentText)
        assertEquals(BoundsRect(96, 908, 1144, 1010), comments.single().boundsInScreen)
    }

    @Test
    fun youtubeExtractor_neverPromotesTabletReplyControls() {
        val comments = YoutubeCommentExtractor.extractComments(
            listOf(
                node(text = "@first_user", top = 820, bottom = 860, left = 96, width = 460),
                node(text = "답글 보기", top = 908, bottom = 970, left = 96, width = 280),
                node(text = "@second_user", top = 1080, bottom = 1120, left = 96, width = 460),
                node(text = "답글 총 4개 보기", top = 1168, bottom = 1230, left = 96, width = 320)
            )
        )

        assertTrue(comments.isEmpty())
    }

    @Test
    fun youtubeExtractor_usesActualBodiesFromWideTabletRows() {
        val firstBody = "첫 번째 실제 본문입니다"
        val secondBody = "두 번째 실제 본문입니다"
        val parsed = YoutubeCommentExtractor.extractComments(
            listOf(
                node(text = "정렬 기준", top = 610, bottom = 660, left = 64, width = 200),
                node(text = "@first_user", top = 820, bottom = 860, left = 96, width = 460),
                node(text = "7년 전", top = 864, bottom = 900, left = 96, width = 160),
                node(text = firstBody, top = 908, bottom = 970, left = 96, width = 900),
                node(text = "답글 보기", top = 978, bottom = 1030, left = 900, width = 180),
                node(text = "@second_user", top = 1080, bottom = 1120, left = 96, width = 460),
                node(text = "3일 전", top = 1124, bottom = 1160, left = 96, width = 160),
                node(text = secondBody, top = 1168, bottom = 1230, left = 96, width = 900),
                node(text = "답글 총 2개 보기", top = 1238, bottom = 1290, left = 900, width = 220)
            )
        )

        assertEquals(listOf(firstBody, secondBody), parsed.map { it.commentText })
        val adapted = YoutubeCommentAnalysisAdapter.adapt(parsed)
        assertEquals(
            listOf(
                "android-accessibility-comment:youtube:first_user",
                "android-accessibility-comment:youtube:second_user"
            ),
            adapted.map { it.authorId }
        )
    }

    @Test
    fun youtubeAnalysisAdapter_reappliesOriginalSaveFilter() {
        val comments = YoutubeCommentAnalysisAdapter.adapt(
            listOf(
                parsedComment(author = "creator", text = "정상 본문입니다"),
                parsedComment(author = "creator", text = "View replies"),
                parsedComment(author = "creator", text = "1,234")
            )
        )

        assertEquals(listOf("정상 본문입니다"), comments.map { it.commentText })
    }

    @Test
    fun youtubeExtractor_doesNotPromoteStandaloneTextWithoutCommentControls() {
        val comments = YoutubeCommentExtractor.extractComments(
            listOf(
                node(text = "A video title near the top of the page", top = 630, bottom = 720, left = 96, width = 1164),
                node(text = "Share", top = 730, bottom = 800, left = 96, width = 180),
                node(text = "Reply", top = 730, bottom = 800, left = 300, width = 180)
            )
        )

        assertTrue(comments.isEmpty())
    }

    @Test
    fun instagramExtractor_supportsCombinedCommentText() {
        val comments = InstagramCommentExtractor.extractComments(
            listOf(
                node(text = "user.name 이 장면 너무 좋네요", top = 1200, bottom = 1260),
                node(text = "답글", top = 1268, bottom = 1296)
            )
        )

        assertEquals(1, comments.size)
        assertEquals("이 장면 너무 좋네요", comments.single().commentText)
    }

    @Test
    fun tiktokExtractor_keepsAuthorId() {
        val comments = TiktokCommentExtractor.extractComments(
            listOf(
                node(text = "@creator_12", top = 1100, bottom = 1140),
                node(text = "2일 전", top = 1148, bottom = 1176),
                node(text = "이 부분 진짜 웃겨요", top = 1184, bottom = 1244, width = 680)
            )
        )

        assertEquals(1, comments.size)
        assertEquals("이 부분 진짜 웃겨요", comments.single().commentText)
        assertEquals("creator_12", comments.single().authorId)
    }

    private fun parsedComment(author: String, text: String): ParsedComment {
        return ParsedComment(
            commentText = text,
            boundsInScreen = BoundsRect(96, 900, 1144, 980),
            authorId = author
        )
    }

    private fun node(
        text: String,
        top: Int,
        bottom: Int,
        left: Int = 64,
        width: Int = 640
    ): ParsedTextNode {
        return ParsedTextNode(
            packageName = "test.package",
            text = text,
            contentDescription = null,
            displayText = text,
            className = "android.widget.TextView",
            viewIdResourceName = null,
            left = left,
            top = top,
            right = left + width,
            bottom = bottom,
            approxTop = top,
            isVisibleToUser = true
        )
    }
}
