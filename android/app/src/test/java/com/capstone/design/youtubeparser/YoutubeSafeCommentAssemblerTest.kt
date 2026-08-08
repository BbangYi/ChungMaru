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
