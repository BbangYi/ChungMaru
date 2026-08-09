package com.capstone.design.youtubeparser

internal object YoutubeMirrorNodeVisibilityPolicy {
    fun canReadNode(
        isVisibleToUser: Boolean,
        mirrorActive: Boolean,
        trustedCommentScope: Boolean
    ): Boolean {
        return isVisibleToUser || (mirrorActive && trustedCommentScope)
    }
}
