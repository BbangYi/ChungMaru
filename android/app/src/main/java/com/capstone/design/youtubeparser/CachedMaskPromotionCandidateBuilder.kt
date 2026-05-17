package com.capstone.design.youtubeparser

internal object CachedMaskPromotionCandidateBuilder {
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    fun buildYoutubeComments(
        nodes: List<ParsedTextNode>,
        visualRoiPlan: VisualTextRoiPlan,
        screenWidth: Int,
        screenHeight: Int,
        sceneRevision: Long
    ): List<ParsedComment> {
        val accessibilityComments = ScreenTextCandidateExtractor.extractCandidates(
            packageName = YOUTUBE_PACKAGE,
            nodes = nodes,
            sceneRevision = sceneRevision,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        ).map { candidate ->
            candidate.toParsedComment()
        }
        val semanticVisualComments = VisualTextSemanticFallbackPlanner.selectCandidates(
            visualRoiPlan = visualRoiPlan,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        return distinctByTextSourceAndBounds(accessibilityComments + semanticVisualComments)
    }

    private fun distinctByTextSourceAndBounds(candidates: List<ParsedComment>): List<ParsedComment> {
        return candidates.distinctBy { candidate ->
            val text = candidate.commentText.replace(Regex("\\s+"), " ").trim().lowercase()
            val bounds = candidate.boundsInScreen
            "${candidate.authorId.orEmpty()}|$text|${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        }
    }
}
