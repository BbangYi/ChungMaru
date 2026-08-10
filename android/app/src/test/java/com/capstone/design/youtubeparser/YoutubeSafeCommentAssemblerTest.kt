package com.capstone.design.youtubeparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSafeCommentAssemblerTest {
    @Test
    fun assembleAccessibilityResults_usesCompositeRowsWithoutLineDuplicates() {
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "Safe full comment",
                    authorId = "android-accessibility-comment:youtube:@safe"
                ),
                accessibilityResult(
                    text = "Safe full",
                    authorId = "android-accessibility-comment:youtube:@safe:line:140"
                ),
                accessibilityResult(
                    text = "Top",
                    authorId = "android-accessibility-comment:youtube:@ui"
                ),
                accessibilityResult(
                    text = "Newest",
                    authorId = "android-accessibility-comment:youtube:@ui"
                )
            )
        )

        assertEquals(1, batch.rawLineCount)
        assertEquals(1, batch.safeComments.size)
        assertEquals("@safe", batch.safeComments.single().author)
        assertEquals("Safe full comment", batch.safeComments.single().text)
    }

    @Test
    fun assembleAccessibilityResults_keepsLineFallbackForAnotherCommentIdentity() {
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "First complete comment",
                    authorId = "android-accessibility-comment:youtube:@first"
                ),
                accessibilityResult(
                    text = "Second line-only comment",
                    authorId = "android-accessibility-comment:youtube:@second:line:320"
                )
            )
        )

        assertEquals(2, batch.rawLineCount)
        assertEquals(
            listOf("@first", "@second"),
            batch.safeComments.map { comment -> comment.author }
        )
        assertEquals(
            listOf("First complete comment", "Second line-only comment"),
            batch.safeComments.map { comment -> comment.text }
        )
    }

    @Test
    fun assembleAccessibilityResults_treatsAuthorlessLineAsUnknownAuthor() {
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "Authorless parser fallback",
                    authorId = "android-accessibility-comment:youtube:line:420"
                )
            )
        )

        assertEquals("@youtube", batch.safeComments.single().author)
    }

    @Test
    fun assembleAccessibilityResults_blocksCompositeWhenItsLineIsHarmful() {
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "A normal prefix followed by a blocked phrase",
                    authorId =
                        "android-accessibility-lookahead:" +
                            "android-accessibility-comment:youtube:@blocked"
                ),
                accessibilityResult(
                    text = "blocked phrase",
                    authorId =
                        "android-accessibility-comment:youtube:@blocked:line:240",
                    offensive = true
                )
            )
        )
        val buffer = YoutubeSafeCommentBuffer()

        assertEquals(1, batch.rawLineCount)
        assertEquals(1, batch.harmfulCommentCount)
        assertEquals(0, buffer.add(batch))
        assertTrue(buffer.comments().isEmpty())
    }

    @Test
    fun assembleAccessibilityResults_restoresAtSignForOriginalParserAuthorId() {
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "원본 파서에서 가져온 안전한 본문",
                    authorId = "android-accessibility-comment:youtube:sampleuser"
                )
            )
        )

        assertEquals("@sampleuser", batch.safeComments.single().author)
    }

    @Test
    fun accessibilitySource_acceptsAuthorlessExistingParserResult() {
        val source = "android-accessibility-comment:youtube"
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "작성자 연결 없이도 보존할 댓글",
                    authorId = source
                )
            )
        )

        assertTrue(YoutubeSafeCommentAssembler.isYoutubeAccessibilitySource(source))
        assertEquals(1, batch.rawLineCount)
        assertEquals("@youtube", batch.safeComments.single().author)
        assertEquals(null, YoutubeSafeCommentAssembler.youtubeAuthorLabel(source))
    }

    @Test
    fun youtubeAuthorLabel_normalizesLookaheadAndAtSign() {
        assertEquals(
            "@sampleuser",
            YoutubeSafeCommentAssembler.youtubeAuthorLabel(
                "android-accessibility-lookahead:" +
                    "android-accessibility-comment:youtube:sampleuser"
            )
        )
    }

    @Test
    fun accessibilitySource_rejectsPrefixCollision() {
        val source = "android-accessibility-comment:youtube-other:@sampleuser"
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "This must not be treated as a YouTube comment",
                    authorId = source
                )
            )
        )

        assertFalse(YoutubeSafeCommentAssembler.isYoutubeAccessibilitySource(source))
        assertEquals(0, batch.rawLineCount)
        assertTrue(batch.safeComments.isEmpty())
    }

    @Test
    fun assembleAccessibilityResults_rejectsRealYoutubePlaybackControls() {
        val controls = listOf(
            "동영상 일지중지",
            "다음 동영상",
            "0분 5초 중 0분 1초",
            "다른 사용자 5명과 함께 이 동영상에 좋아요 표시",
            "댓글 27개 보기",
            "동영상 공유",
            "리믹스",
            "이 사운드를 사용하는 동영상 더보기",
            "구독: 새로운 콘텐츠 이용 가능",
            "드래그 핸들",
            "댓글 정보",
            "[Music]",
            "좋아요 취소",
            "좋아요 6개",
            "댓글 싫어요 표시",
            "작업 메뉴",
            "한국어로 번역",
            "나와 사용자 2명이 이 댓글을 좋아함"
        )
        val batch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            controls.map { text ->
                accessibilityResult(
                    text = text,
                    authorId = "android-accessibility-comment:youtube"
                )
            }
        )

        assertEquals(0, batch.rawLineCount)
        assertTrue(batch.safeComments.isEmpty())
    }

    @Test
    fun youtubeAuthorLabel_removesParserLineSuffix() {
        assertEquals(
            "@sampleuser",
            YoutubeSafeCommentAssembler.youtubeAuthorLabel(
                "android-accessibility-comment:youtube:@sampleuser:line:240"
            )
        )
    }

    @Test
    fun buffer_retractsEarlierSafeCommentWhenAuthorIsLaterHarmful() {
        val buffer = YoutubeSafeCommentBuffer()
        val safeBatch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "처음에는 안전하게 인식된 댓글",
                    authorId = "android-accessibility-comment:youtube:@same"
                )
            )
        )
        val harmfulBatch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "뒤 화면에서 유해 표현 감지",
                    authorId = "android-accessibility-comment:youtube:@same",
                    offensive = true
                )
            )
        )

        assertEquals(1, buffer.add(safeBatch))
        assertEquals(1, buffer.comments().size)
        assertEquals(0, buffer.add(harmfulBatch))
        assertTrue(buffer.comments().isEmpty())
    }

    @Test
    fun buffer_retractsOverlappingHarmfulTextWhenAuthorChanges() {
        val buffer = YoutubeSafeCommentBuffer()
        val safeBatch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "사는 게 너무 힘들고 포기하고 싶어요",
                    authorId = "android-accessibility-comment:youtube:@first"
                )
            )
        )
        val harmfulBatch = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "너무 힘들고 포기 하고 싶어요",
                    authorId = "android-accessibility-comment:youtube:@different",
                    offensive = true
                )
            )
        )

        assertEquals(1, buffer.add(safeBatch))
        assertEquals(0, buffer.add(harmfulBatch))
        assertTrue(buffer.comments().isEmpty())
    }

    @Test
    fun buffer_deduplicatesAcrossViewportsAndUsesBoundedInitialTarget() {
        val buffer = YoutubeSafeCommentBuffer(
            initialSafeTarget = 2,
            maxInitialRawLines = 20
        )
        val first = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "첫 번째 댓글",
                    authorId = "android-accessibility-comment:youtube:@one"
                ),
                accessibilityResult(
                    text = "두 번째 댓글",
                    authorId = "android-accessibility-comment:youtube:@two"
                )
            )
        )
        val repeated = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "두 번째 댓글",
                    authorId = "android-accessibility-comment:youtube:@two"
                )
            )
        )

        assertEquals(2, buffer.add(first))
        assertEquals(0, buffer.add(repeated))
        assertEquals(2, buffer.comments().size)
        assertFalse(
            buffer.shouldFinishInitialCollection(
                capturedViewports = 1,
                forwardSteps = 0,
                maxForwardSteps = 3
            )
        )
        assertTrue(
            buffer.shouldFinishInitialCollection(
                capturedViewports = 2,
                forwardSteps = 1,
                maxForwardSteps = 3
            )
        )
    }

    @Test
    fun buffer_appendsOnlyNewSafeCommentsFromLaterViewport() {
        val buffer = YoutubeSafeCommentBuffer()
        val firstViewport = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "First viewport comment",
                    authorId = "android-accessibility-comment:youtube:@one"
                ),
                accessibilityResult(
                    text = "Overlapping viewport comment",
                    authorId = "android-accessibility-comment:youtube:@two"
                )
            )
        )
        val laterViewport = YoutubeSafeCommentAssembler.assembleAccessibilityResults(
            listOf(
                accessibilityResult(
                    text = "Overlapping viewport comment",
                    authorId = "android-accessibility-comment:youtube:@two"
                ),
                accessibilityResult(
                    text = "New safe comment",
                    authorId = "android-accessibility-comment:youtube:@three"
                ),
                accessibilityResult(
                    text = "New harmful comment",
                    authorId = "android-accessibility-comment:youtube:@blocked",
                    offensive = true
                )
            )
        )

        assertEquals(2, buffer.add(firstViewport))
        assertEquals(1, buffer.add(laterViewport))
        assertEquals(
            listOf("@one", "@two", "@three"),
            buffer.comments().map { comment -> comment.author }
        )
        assertFalse(buffer.comments().any { comment -> comment.author == "@blocked" })
    }

    private fun accessibilityResult(
        text: String,
        authorId: String,
        offensive: Boolean = false
    ): AndroidAnalysisResultItem {
        return AndroidAnalysisResultItem(
            original = text,
            boundsInScreen = BoundsRect(
                left = 80,
                top = 120,
                right = 980,
                bottom = 180
            ),
            authorId = authorId,
            isOffensive = offensive,
            isProfane = offensive,
            isToxic = false,
            isHate = false,
            scores = HarmScores(),
            evidenceSpans = emptyList()
        )
    }
}
