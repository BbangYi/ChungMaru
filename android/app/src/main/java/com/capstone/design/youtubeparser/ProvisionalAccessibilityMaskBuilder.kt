package com.capstone.design.youtubeparser

object ProvisionalAccessibilityMaskBuilder {
    private const val MAX_PROVISIONAL_RESULT_COUNT = 12

    fun buildResponse(
        candidates: List<ScreenTextCandidate>,
        timestamp: Long
    ): AndroidAnalysisResponse? {
        val results = candidates
            .asSequence()
            .filter { candidate -> canRenderProvisionally(candidate) }
            .mapNotNull { candidate -> candidate.toProvisionalResult() }
            .take(MAX_PROVISIONAL_RESULT_COUNT)
            .toList()

        if (results.isEmpty()) return null

        return AndroidAnalysisResponse(
            timestamp = timestamp,
            filteredCount = 0,
            results = results
        )
    }

    private fun canRenderProvisionally(candidate: ScreenTextCandidate): Boolean {
        if (candidate.route.renderPolicy != CandidateRenderPolicy.DIRECT_OVERLAY) return false
        if (candidate.route.geometryPolicy == CandidateGeometryPolicy.VISUAL_OCR_EXACT) return false
        if (candidate.route.geometryPolicy == CandidateGeometryPolicy.VISUAL_FALLBACK) return false
        if (candidate.route.geometryPolicy == CandidateGeometryPolicy.ACCESSIBILITY_LOOKAHEAD) return false
        if (candidate.route.geometryPolicy == CandidateGeometryPolicy.ANALYSIS_ONLY) return false

        return candidate.source == CandidateSource.ACCESSIBILITY_TEXT ||
            candidate.source == CandidateSource.ACCESSIBILITY_TEXT_WITH_OCR_GEOMETRY
    }

    private fun ScreenTextCandidate.toProvisionalResult(): AndroidAnalysisResultItem? {
        val ranges = VisualTextOcrCandidateFilter.findAnalysisRanges(rawText)
        if (ranges.isEmpty()) return null

        return AndroidAnalysisResultItem(
            original = rawText,
            boundsInScreen = screenRect,
            authorId = backendSourceId,
            isOffensive = true,
            isProfane = true,
            isToxic = false,
            isHate = false,
            scores = HarmScores(profanity = 1.0, toxicity = 0.0, hate = 0.0),
            evidenceSpans = ranges.mapNotNull { range ->
                val start = rawText.codePointCount(0, range.start.coerceIn(0, rawText.length))
                val end = rawText.codePointCount(0, range.end.coerceIn(0, rawText.length))
                if (end <= start) return@mapNotNull null
                EvidenceSpan(
                    text = range.analysisText,
                    start = start,
                    end = end,
                    score = 1.0
                )
            }
        ).takeIf { it.evidenceSpans.isNotEmpty() }
    }
}
