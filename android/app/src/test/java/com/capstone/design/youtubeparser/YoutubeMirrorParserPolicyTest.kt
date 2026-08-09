package com.capstone.design.youtubeparser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeMirrorParserPolicyTest {
    @Test
    fun activeCollectionAwaitingBatch_usesExistingParser() {
        val expected = YoutubeMirrorParserPolicy.isExpectedBatch(
            isYoutube = true,
            mirrorEnabled = true,
            mirrorActive = true,
            collectionActive = true,
            awaitingBatch = true
        )

        assertTrue(expected)
        assertFalse(
            YoutubeMirrorParserPolicy.shouldSkipUnsolicitedAnalysis(
                isYoutube = true,
                mirrorEnabled = true,
                mirrorActive = true,
                expectedBatch = expected
            )
        )
    }

    @Test
    fun readyMirror_withoutPendingBatch_skipsDuplicateParserRun() {
        val expected = YoutubeMirrorParserPolicy.isExpectedBatch(
            isYoutube = true,
            mirrorEnabled = true,
            mirrorActive = true,
            collectionActive = false,
            awaitingBatch = false
        )

        assertFalse(expected)
        assertTrue(
            YoutubeMirrorParserPolicy.shouldSkipUnsolicitedAnalysis(
                isYoutube = true,
                mirrorEnabled = true,
                mirrorActive = true,
                expectedBatch = expected
            )
        )
    }

    @Test
    fun normalYoutubeWindow_withoutMirror_keepsExistingParserEnabled() {
        val expected = YoutubeMirrorParserPolicy.isExpectedBatch(
            isYoutube = true,
            mirrorEnabled = true,
            mirrorActive = false,
            collectionActive = false,
            awaitingBatch = false
        )

        assertFalse(expected)
        assertFalse(
            YoutubeMirrorParserPolicy.shouldSkipUnsolicitedAnalysis(
                isYoutube = true,
                mirrorEnabled = true,
                mirrorActive = false,
                expectedBatch = expected
            )
        )
    }

    @Test
    fun preMirrorSeed_requiresConfirmedNativeCommentPanel() {
        assertFalse(
            YoutubeMirrorParserPolicy.shouldCachePreMirrorSeed(
                isYoutube = true,
                mirrorEnabled = true,
                expectedBatch = false,
                nativeCommentPanelConfirmed = false
            )
        )
        assertTrue(
            YoutubeMirrorParserPolicy.shouldCachePreMirrorSeed(
                isYoutube = true,
                mirrorEnabled = true,
                expectedBatch = false,
                nativeCommentPanelConfirmed = true
            )
        )
    }

    @Test
    fun trustedCommentResult_requiresAuthorAndPanelContainedBounds() {
        assertTrue(
            YoutubeMirrorParserPolicy.isTrustedCommentResult(
                hasParsedAuthor = true,
                resultTop = 1_020,
                resultBottom = 1_110,
                panelTop = 843,
                panelBottom = 1_824
            )
        )
        assertFalse(
            YoutubeMirrorParserPolicy.isTrustedCommentResult(
                hasParsedAuthor = false,
                resultTop = 1_020,
                resultBottom = 1_110,
                panelTop = 843,
                panelBottom = 1_824
            )
        )
        assertFalse(
            YoutubeMirrorParserPolicy.isTrustedCommentResult(
                hasParsedAuthor = true,
                resultTop = 551,
                resultBottom = 665,
                panelTop = 843,
                panelBottom = 1_824
            )
        )
    }
}
