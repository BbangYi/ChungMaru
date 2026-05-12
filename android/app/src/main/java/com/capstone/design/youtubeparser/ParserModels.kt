package com.capstone.design.youtubeparser

import com.google.gson.annotations.SerializedName

data class BoundsRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class ParsedTextNode(
    val packageName: String,
    val text: String?,
    val contentDescription: String?,
    val displayText: String?,
    val className: String?,
    val viewIdResourceName: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val approxTop: Int,
    val isVisibleToUser: Boolean
)

data class ParsedComment(
    val commentText: String,
    val boundsInScreen: BoundsRect,
    @SerializedName("author_id")
    val authorId: String? = null
)

enum class CandidateSource {
    ACCESSIBILITY_TEXT,
    ACCESSIBILITY_TEXT_WITH_OCR_GEOMETRY,
    VISUAL_OCR
}

enum class CandidateRole {
    CONTENT,
    USER_INPUT,
    TITLE,
    SNIPPET,
    THUMBNAIL_TEXT,
    VIDEO_FRAME_TEXT,
    BUTTON_OR_NAVIGATION
}

data class CharBox(
    val start: Int,
    val end: Int,
    val boundsInScreen: BoundsRect,
    val text: String? = null
)

data class ScreenTextCandidate(
    val id: String,
    val packageName: String,
    val source: CandidateSource,
    val role: CandidateRole,
    val rawText: String,
    val normalizedVariants: List<String> = emptyList(),
    val screenRect: BoundsRect,
    val charBoxes: List<CharBox>? = null,
    val confidence: Float? = null,
    val sceneRevision: Long = 0L,
    val captureId: String? = null,
    val roiId: String? = null,
    val backendSourceId: String? = null
) {
    fun toParsedComment(): ParsedComment {
        return ParsedComment(
            commentText = rawText,
            boundsInScreen = screenRect,
            authorId = backendSourceId ?: "screen:${source.name.lowercase()}:${role.name.lowercase()}"
        )
    }
}

data class ParseSnapshot(
    val timestamp: Long,
    val comments: List<ParsedComment>
)

data class EvidenceSpan(
    val text: String,
    val start: Int,
    val end: Int,
    val score: Double
)

data class HarmScores(
    val profanity: Double = 0.0,
    val toxicity: Double = 0.0,
    val hate: Double = 0.0
)

data class AndroidAnalysisResultItem(
    val original: String,
    val boundsInScreen: BoundsRect,
    @SerializedName("author_id")
    val authorId: String? = null,
    @SerializedName("is_offensive")
    val isOffensive: Boolean,
    @SerializedName("is_profane")
    val isProfane: Boolean,
    @SerializedName("is_toxic")
    val isToxic: Boolean,
    @SerializedName("is_hate")
    val isHate: Boolean,
    val scores: HarmScores,
    @SerializedName("evidence_spans")
    val evidenceSpans: List<EvidenceSpan>
)

data class AndroidAnalysisResponse(
    val timestamp: Long,
    @SerializedName("filtered_count")
    val filteredCount: Int,
    val results: List<AndroidAnalysisResultItem>
)

data class AndroidAnalysisAttempt(
    val ok: Boolean,
    val packageName: String? = null,
    val url: String,
    val sensitivity: Int? = null,
    val latencyMs: Long,
    val commentCount: Int,
    val offensiveCount: Int,
    val filteredCount: Int,
    val overlayCandidateCount: Int = 0,
    val overlayRenderedCount: Int = 0,
    val overlaySkippedUnstableCount: Int = 0,
    val overlayRenderedSamples: List<String> = emptyList(),
    val visualCaptureSupported: Boolean = false,
    val visualCaptureReason: String = VisualTextCaptureSupport.REASON_SERVICE_NOT_CONNECTED,
    val visualRoiCandidateCount: Int = 0,
    val visualRoiSelectedCount: Int = 0,
    val response: AndroidAnalysisResponse? = null,
    val actionableSamples: List<String> = emptyList(),
    val error: String? = null
)

data class AndroidAnalysisDiagnostics(
    val analyzedAt: Long,
    val ok: Boolean,
    val packageName: String?,
    val url: String,
    val sensitivity: Int?,
    val latencyMs: Long,
    val commentCount: Int,
    val offensiveCount: Int,
    val filteredCount: Int,
    val overlayCandidateCount: Int,
    val overlayRenderedCount: Int,
    val overlaySkippedUnstableCount: Int,
    val overlayRenderedSamples: List<String>,
    val visualCaptureSupported: Boolean,
    val visualCaptureReason: String,
    val visualRoiCandidateCount: Int,
    val visualRoiSelectedCount: Int,
    val actionableSamples: List<String>,
    val error: String?
)
