package com.capstone.design.youtubeparser

internal object AccessibilityCharacterBoxPolicy {
    private const val MAX_INPUT_TEXT_LENGTH = 96
    private const val MAX_ACTIONABLE_TEXT_LENGTH = 2000
    private const val MAX_ACTIONABLE_REQUEST_COUNT = 8

    data class RequestRange(
        val start: Int,
        val length: Int
    )

    fun shouldRequest(
        rawText: String,
        displayText: String,
        className: String?,
        viewIdResourceName: String?
    ): Boolean {
        return requestRanges(
            rawText = rawText,
            displayText = displayText,
            className = className,
            viewIdResourceName = viewIdResourceName
        ).isNotEmpty()
    }

    fun requestRanges(
        rawText: String,
        displayText: String,
        className: String?,
        viewIdResourceName: String?
    ): List<RequestRange> {
        val displayMatchesRawText = rawText == displayText || rawText.trim() == displayText
        if (rawText.isBlank()) return emptyList()
        if (!displayMatchesRawText) return emptyList()

        val actionableRanges = VisualTextOcrCandidateFilter.findAnalysisRanges(rawText)
        if (actionableRanges.isNotEmpty()) {
            if (rawText.length > MAX_ACTIONABLE_TEXT_LENGTH) return emptyList()
            return actionableRanges
                .asSequence()
                .map { range ->
                    RequestRange(
                        start = range.start.coerceIn(0, rawText.length),
                        length = (range.end - range.start).coerceAtLeast(0)
                    )
                }
                .filter { range -> range.length > 0 && range.start + range.length <= rawText.length }
                .distinct()
                .take(MAX_ACTIONABLE_REQUEST_COUNT)
                .toList()
        }

        if (!isInputLike(className, viewIdResourceName) || rawText.length > MAX_INPUT_TEXT_LENGTH) {
            return emptyList()
        }

        return listOf(RequestRange(start = 0, length = rawText.length))
    }

    private fun isInputLike(
        className: String?,
        viewIdResourceName: String?
    ): Boolean {
        return className.orEmpty().contains("EditText", ignoreCase = true) ||
            viewIdResourceName.orEmpty().contains("search", ignoreCase = true) ||
            viewIdResourceName.orEmpty().contains("query", ignoreCase = true) ||
            viewIdResourceName.orEmpty().contains("input", ignoreCase = true)
    }
}
