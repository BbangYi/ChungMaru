package com.capstone.design.youtubeparser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeMirrorNodeVisibilityPolicyTest {
    @Test
    fun visibleNode_isAlwaysReadable() {
        assertTrue(
            YoutubeMirrorNodeVisibilityPolicy.canReadNode(
                isVisibleToUser = true,
                mirrorActive = false,
                trustedCommentScope = false
            )
        )
    }

    @Test
    fun hiddenNode_isReadableOnlyBehindActiveMirrorCommentScope() {
        assertTrue(
            YoutubeMirrorNodeVisibilityPolicy.canReadNode(
                isVisibleToUser = false,
                mirrorActive = true,
                trustedCommentScope = true
            )
        )
        assertFalse(
            YoutubeMirrorNodeVisibilityPolicy.canReadNode(
                isVisibleToUser = false,
                mirrorActive = false,
                trustedCommentScope = true
            )
        )
        assertFalse(
            YoutubeMirrorNodeVisibilityPolicy.canReadNode(
                isVisibleToUser = false,
                mirrorActive = true,
                trustedCommentScope = false
            )
        )
    }
}
