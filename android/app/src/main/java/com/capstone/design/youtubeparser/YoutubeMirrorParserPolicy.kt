package com.capstone.design.youtubeparser

internal object YoutubeMirrorParserPolicy {
    fun isExpectedBatch(
        isYoutube: Boolean,
        mirrorEnabled: Boolean,
        mirrorActive: Boolean,
        collectionActive: Boolean,
        awaitingBatch: Boolean
    ): Boolean {
        return isYoutube &&
            mirrorEnabled &&
            mirrorActive &&
            collectionActive &&
            awaitingBatch
    }

    fun shouldSkipUnsolicitedAnalysis(
        isYoutube: Boolean,
        mirrorEnabled: Boolean,
        mirrorActive: Boolean,
        expectedBatch: Boolean
    ): Boolean {
        return isYoutube && mirrorEnabled && mirrorActive && !expectedBatch
    }

    fun shouldCachePreMirrorSeed(
        isYoutube: Boolean,
        mirrorEnabled: Boolean,
        expectedBatch: Boolean,
        nativeCommentPanelConfirmed: Boolean
    ): Boolean {
        return isYoutube &&
            mirrorEnabled &&
            !expectedBatch &&
            nativeCommentPanelConfirmed
    }

    fun isTrustedCommentResult(
        hasParsedAuthor: Boolean,
        resultTop: Int,
        resultBottom: Int,
        panelTop: Int,
        panelBottom: Int
    ): Boolean {
        if (!hasParsedAuthor) return false
        if (resultBottom <= resultTop || panelBottom <= panelTop) return false
        return resultTop >= panelTop && resultBottom <= panelBottom
    }
}
