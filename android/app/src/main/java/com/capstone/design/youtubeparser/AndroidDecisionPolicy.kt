package com.capstone.design.youtubeparser

internal enum class AndroidRouteAction {
    DROP,
    CACHE_ONLY,
    DIRECT_OVERLAY,
    BACKEND_ANALYZE,
    OCR_REQUIRED,
    HOLD_ANALYSIS_ONLY
}

internal enum class AndroidBoundsTrust {
    EXACT_SMALL,
    EXACT_COMPACT,
    EXACT_WIDE,
    ESTIMATED,
    VISUAL_EXACT,
    UNKNOWN
}

internal enum class AndroidFastCandidateSource {
    EVENT_SOURCE,
    BROWSER_ROOT,
    BROWSER_EXACT_RANGE,
    BROWSER_COMPACT,
    YOUTUBE_SEMANTIC,
    VISUAL_ESTIMATED
}

internal data class AndroidRouteDecision(
    val action: AndroidRouteAction,
    val dropReason: String,
    val backendSent: Boolean,
    val ocrRequired: Boolean,
    val overlayAllowed: Boolean,
    val boundsTrust: AndroidBoundsTrust,
    val ranges: List<VisualTextOcrCandidateFilter.CandidateRange> = emptyList()
) {
    fun toSample(prefix: String): String {
        return "$prefix/route_action=${action.name.lowercase()} " +
            "drop_reason=$dropReason backend_sent=$backendSent " +
            "ocr_required=$ocrRequired overlay_allowed=$overlayAllowed " +
            "bounds_trust=${boundsTrust.name.lowercase()}"
    }
}

internal object AndroidDecisionPolicy {
    private const val MIN_TEXT_LENGTH = 2
    private const val MAX_FAST_TEXT_LENGTH = 240
    private const val MIN_WIDTH_PX = 18
    private const val MIN_HEIGHT_PX = 14
    private const val EXACT_SMALL_MAX_WIDTH_RATIO = 0.72f
    private const val EXACT_SMALL_MAX_HEIGHT_PX = 128
    private const val COMPACT_MAX_WIDTH_PX = 540
    private const val COMPACT_MAX_HEIGHT_PX = 128
    private const val YOUTUBE_COMMENT_MAX_WIDTH_RATIO = 0.96f
    private const val YOUTUBE_COMMENT_MAX_HEIGHT_PX = 380
    private const val YOUTUBE_COMMENT_MAX_HEIGHT_RATIO = 0.32f
    private const val WIDE_MAX_HEIGHT_RATIO = 0.36f
    private val whitespacePattern = Regex("\\s+")

    fun decideFastCandidate(
        rawText: String,
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int,
        source: AndroidFastCandidateSource,
        recentFingerprintMatched: Boolean = false
    ): AndroidRouteDecision {
        val text = rawText.replace(whitespacePattern, " ").trim()
        if (recentFingerprintMatched) {
            return decision(
                action = AndroidRouteAction.CACHE_ONLY,
                reason = "recent-fingerprint",
                boundsTrust = AndroidBoundsTrust.UNKNOWN
            )
        }
        if (text.length !in MIN_TEXT_LENGTH..MAX_FAST_TEXT_LENGTH) {
            return decision(
                action = AndroidRouteAction.DROP,
                reason = "text-length",
                boundsTrust = classifyBounds(bounds, screenWidth, screenHeight, source)
            )
        }
        if (source == AndroidFastCandidateSource.BROWSER_ROOT && isUrlLikeText(text)) {
            return decision(
                action = AndroidRouteAction.DROP,
                reason = "url-like-text",
                boundsTrust = classifyBounds(bounds, screenWidth, screenHeight, source)
            )
        }

        val boundsTrust = classifyBounds(bounds, screenWidth, screenHeight, source)
        if (boundsTrust == AndroidBoundsTrust.UNKNOWN) {
            return decision(
                action = AndroidRouteAction.DROP,
                reason = "invalid-bounds",
                boundsTrust = boundsTrust
            )
        }

        val ranges = VisualTextOcrCandidateFilter.findAnalysisRanges(text)
        if (ranges.isEmpty()) {
            val action = if (VisualTextOcrCandidateFilter.shouldAnalyze(text)) {
                AndroidRouteAction.BACKEND_ANALYZE
            } else {
                AndroidRouteAction.DROP
            }
            return decision(
                action = action,
                reason = if (action == AndroidRouteAction.BACKEND_ANALYZE) {
                    "needs-backend-verification"
                } else {
                    "no-cheap-harmful-signal"
                },
                boundsTrust = boundsTrust
            )
        }

        return when (boundsTrust) {
            AndroidBoundsTrust.EXACT_SMALL,
            AndroidBoundsTrust.EXACT_COMPACT,
            AndroidBoundsTrust.VISUAL_EXACT -> decision(
                action = AndroidRouteAction.DIRECT_OVERLAY,
                reason = "cheap-hit-trusted-bounds",
                boundsTrust = boundsTrust,
                ranges = ranges
            )
            AndroidBoundsTrust.ESTIMATED -> decision(
                action = AndroidRouteAction.OCR_REQUIRED,
                reason = "estimated-geometry",
                boundsTrust = boundsTrust,
                ranges = ranges
            )
            AndroidBoundsTrust.EXACT_WIDE -> decision(
                action = AndroidRouteAction.HOLD_ANALYSIS_ONLY,
                reason = "wide-bounds",
                boundsTrust = boundsTrust,
                ranges = ranges
            )
            AndroidBoundsTrust.UNKNOWN -> decision(
                action = AndroidRouteAction.DROP,
                reason = "invalid-bounds",
                boundsTrust = boundsTrust,
                ranges = ranges
            )
        }
    }

    fun hasCheapHarmfulSignal(text: String): Boolean {
        return VisualTextOcrCandidateFilter.findAnalysisRanges(text).isNotEmpty()
    }

    private fun classifyBounds(
        bounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int,
        source: AndroidFastCandidateSource
    ): AndroidBoundsTrust {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        if (width < MIN_WIDTH_PX || height < MIN_HEIGHT_PX) return AndroidBoundsTrust.UNKNOWN
        if (screenWidth <= 0 || screenHeight <= 0) return AndroidBoundsTrust.UNKNOWN

        if (source == AndroidFastCandidateSource.VISUAL_ESTIMATED) {
            return AndroidBoundsTrust.ESTIMATED
        }
        if (source == AndroidFastCandidateSource.BROWSER_EXACT_RANGE) {
            return AndroidBoundsTrust.EXACT_SMALL
        }
        if (source == AndroidFastCandidateSource.BROWSER_COMPACT) {
            return if (width <= COMPACT_MAX_WIDTH_PX && height <= COMPACT_MAX_HEIGHT_PX) {
                AndroidBoundsTrust.EXACT_COMPACT
            } else {
                AndroidBoundsTrust.EXACT_WIDE
            }
        }
        if (source == AndroidFastCandidateSource.YOUTUBE_SEMANTIC) {
            val commentLikeWidth = width <= (screenWidth * YOUTUBE_COMMENT_MAX_WIDTH_RATIO).toInt()
            val commentLikeHeight = height <= minOf(
                YOUTUBE_COMMENT_MAX_HEIGHT_PX,
                (screenHeight * YOUTUBE_COMMENT_MAX_HEIGHT_RATIO).toInt()
            )
            if (commentLikeWidth && commentLikeHeight) return AndroidBoundsTrust.EXACT_COMPACT
        }

        val smallWidth = width <= (screenWidth * EXACT_SMALL_MAX_WIDTH_RATIO).toInt()
        val smallHeight = height <= EXACT_SMALL_MAX_HEIGHT_PX
        if (smallWidth && smallHeight) return AndroidBoundsTrust.EXACT_SMALL

        val wideHeightAllowed = height <= (screenHeight * WIDE_MAX_HEIGHT_RATIO).toInt()
        return if (wideHeightAllowed) {
            AndroidBoundsTrust.EXACT_WIDE
        } else {
            AndroidBoundsTrust.UNKNOWN
        }
    }

    private fun decision(
        action: AndroidRouteAction,
        reason: String,
        boundsTrust: AndroidBoundsTrust,
        ranges: List<VisualTextOcrCandidateFilter.CandidateRange> = emptyList()
    ): AndroidRouteDecision {
        return AndroidRouteDecision(
            action = action,
            dropReason = reason,
            backendSent = false,
            ocrRequired = action == AndroidRouteAction.OCR_REQUIRED,
            overlayAllowed = action == AndroidRouteAction.DIRECT_OVERLAY,
            boundsTrust = boundsTrust,
            ranges = ranges
        )
    }

    private fun isUrlLikeText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("www.") ||
            lower.contains("://") ||
            lower.contains("/search?q=") ||
            lower.contains("?q=") ||
            lower.contains("&q=")
    }
}
