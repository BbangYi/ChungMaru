package com.capstone.design.youtubeparser

data class YoutubeSemanticFastMaskResult(
    val response: AndroidAnalysisResponse?,
    val source: String,
    val visitedNodeCount: Int,
    val semanticCandidateCount: Int,
    val matchedCount: Int,
    val skipReason: String?,
    val decisionSamples: List<String> = emptyList()
) {
    val routeSamples: List<String>
        get() = listOf(
            "youtube_semantic_fast/source=$source",
            "youtube_semantic_fast/visited=$visitedNodeCount",
            "youtube_semantic_fast/candidates=$semanticCandidateCount",
            "youtube_semantic_fast/matched=$matchedCount"
        ) + decisionSamples
}

object YoutubeSemanticFastMasker {
    private const val EVENT_SOURCE_MAX_NODES = 24
    private const val MAX_RESULT_COUNT = 4
    private const val MIN_WIDTH_PX = 24
    private const val MIN_HEIGHT_PX = 16
    private const val DIRECT_FALLBACK_MIN_TOP_PX = 180
    private const val MAX_TEXT_LENGTH = 240
    private const val COMMENT_AUTHOR_PREFIX = "android-accessibility-comment:"
    private const val SEMANTIC_DIRECT_AUTHOR_ID = "android-accessibility-comment:youtube-semantic-fast"
    private const val OCR_COMMENT_PANEL_PREFIX = "ocr:youtube-comment-panel:"
    private val whitespacePattern = Regex("\\s+")

    internal fun buildFromNodes(
        nodes: List<ParsedTextNode>,
        source: String,
        visitedNodeCount: Int,
        screenWidth: Int,
        screenHeight: Int,
        timestamp: Long
    ): YoutubeSemanticFastMaskResult {
        if (nodes.isEmpty()) {
            return YoutubeSemanticFastMaskResult(
                response = null,
                source = source,
                visitedNodeCount = visitedNodeCount,
                semanticCandidateCount = 0,
                matchedCount = 0,
                skipReason = "no_nodes"
            )
        }

        val semanticTargets = YoutubeAnalysisTargetExtractor.extractTargets(
            nodes = nodes,
            screenHeight = screenHeight
        )
        val commentSurfaceLikely = isCommentSurfaceLikely(nodes, semanticTargets)
        val commentScopedSemanticTargets = semanticTargets.filter { target ->
            isCommentTarget(target) ||
                commentSurfaceLikely && AndroidDecisionPolicy.hasCheapHarmfulSignal(target.commentText)
        }
        val directFallbackTargets = if (commentSurfaceLikely) {
            nodes.mapNotNull(::toDirectFallbackTarget)
        } else {
            emptyList()
        }
        val candidates = (commentScopedSemanticTargets + directFallbackTargets)
            .distinctBy { target ->
                val bounds = target.boundsInScreen
                "${target.commentText}|${bounds.left}|${bounds.top}|${bounds.right}|${bounds.bottom}"
            }
            .take(EVENT_SOURCE_MAX_NODES)
        val decisionSamples = mutableListOf<String>()

        val results = candidates
            .asSequence()
            .mapNotNull { target ->
                toResult(
                    target = target,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    decisionSamples = decisionSamples
                )
            }
            .take(MAX_RESULT_COUNT)
            .toList()

        if (results.isEmpty()) {
            return YoutubeSemanticFastMaskResult(
                response = null,
                source = source,
                visitedNodeCount = visitedNodeCount,
                semanticCandidateCount = candidates.size,
                matchedCount = 0,
                skipReason = if (candidates.isEmpty()) "no_semantic_candidates" else "no_local_match",
                decisionSamples = decisionSamples
            )
        }

        return YoutubeSemanticFastMaskResult(
            response = AndroidAnalysisResponse(
                timestamp = timestamp,
                filteredCount = 0,
                results = results
            ),
            source = source,
            visitedNodeCount = visitedNodeCount,
            semanticCandidateCount = candidates.size,
            matchedCount = results.size,
            skipReason = null,
            decisionSamples = decisionSamples
        )
    }

    private fun toDirectFallbackTarget(node: ParsedTextNode): ParsedComment? {
        if (!node.isVisibleToUser) return null
        if (node.top < DIRECT_FALLBACK_MIN_TOP_PX) return null

        val text = node.displayText
            ?.replace(whitespacePattern, " ")
            ?.trim()
            ?: return null
        if (text.length !in 2..MAX_TEXT_LENGTH) return null
        if (!VisualTextOcrCandidateFilter.shouldAnalyze(text)) return null

        val width = node.right - node.left
        val height = node.bottom - node.top
        if (width < MIN_WIDTH_PX || height < MIN_HEIGHT_PX) return null
        if (node.className.orEmpty().contains("Button", ignoreCase = true) && text.length < 14) {
            return null
        }

        return ParsedComment(
            commentText = text,
            boundsInScreen = BoundsRect(
                left = node.left,
                top = node.top,
                right = node.right,
                bottom = node.bottom
            ),
            authorId = SEMANTIC_DIRECT_AUTHOR_ID
        )
    }

    private fun isCommentSurfaceLikely(
        nodes: List<ParsedTextNode>,
        targets: List<ParsedComment>
    ): Boolean {
        if (targets.any(::isCommentTarget)) return true
        return nodes.any { node ->
            val text = node.displayText
                ?.replace(whitespacePattern, " ")
                ?.trim()
                .orEmpty()
            isCommentSurfaceMarker(text)
        }
    }

    private fun isCommentTarget(target: ParsedComment): Boolean {
        val authorId = target.authorId.orEmpty()
        return authorId.startsWith(COMMENT_AUTHOR_PREFIX) ||
            authorId.startsWith(OCR_COMMENT_PANEL_PREFIX)
    }

    private fun isCommentSurfaceMarker(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return lower == "comments" ||
            lower == "comment" ||
            lower == "replies" ||
            lower == "reply" ||
            text == "댓글" ||
            text == "답글" ||
            Regex("""^댓글\s*\d+""").containsMatchIn(text) ||
            Regex("""^comments?\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            lower.contains("add a comment") ||
            text.contains("댓글 추가")
    }

    private fun toResult(
        target: ParsedComment,
        screenWidth: Int,
        screenHeight: Int,
        decisionSamples: MutableList<String>
    ): AndroidAnalysisResultItem? {
        val text = target.commentText.replace(whitespacePattern, " ").trim()
        val decision = AndroidDecisionPolicy.decideFastCandidate(
            rawText = text,
            bounds = target.boundsInScreen,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            source = AndroidFastCandidateSource.YOUTUBE_SEMANTIC
        )
        if (decisionSamples.size < 8) {
            decisionSamples += decision.toSample("android_decision/youtube_semantic")
        }
        if (decision.action != AndroidRouteAction.DIRECT_OVERLAY) return null

        val originalLength = text.codePointCount(0, text.length)
        if (originalLength <= 0) return null
        val evidenceSpans = decision.ranges
            .mapNotNull { range ->
                val startChar = range.start.coerceIn(0, text.length)
                val endChar = range.end.coerceIn(startChar, text.length)
                val start = text.codePointCount(0, startChar)
                val end = text.codePointCount(0, endChar)
                if (end <= start) return@mapNotNull null
                EvidenceSpan(
                    text = range.visualText.ifBlank { range.analysisText },
                    start = start.coerceIn(0, originalLength),
                    end = end.coerceIn(0, originalLength),
                    score = 1.0
                )
            }
            .distinctBy { "${it.start}|${it.end}|${it.text.lowercase()}" }
            .take(MAX_RESULT_COUNT)
        if (evidenceSpans.isEmpty()) return null

        return AndroidAnalysisResultItem(
            original = text,
            boundsInScreen = target.boundsInScreen,
            authorId = normalizeAuthorId(target.authorId),
            isOffensive = true,
            isProfane = true,
            isToxic = false,
            isHate = false,
            scores = HarmScores(profanity = 1.0, toxicity = 0.0, hate = 0.0),
            evidenceSpans = evidenceSpans
        )
    }

    private fun normalizeAuthorId(authorId: String?): String {
        val value = authorId?.trim().orEmpty()
        return when {
            value.startsWith(COMMENT_AUTHOR_PREFIX) -> SEMANTIC_DIRECT_AUTHOR_ID
            value.startsWith("android-accessibility:") -> value
            else -> SEMANTIC_DIRECT_AUTHOR_ID
        }
    }

}
