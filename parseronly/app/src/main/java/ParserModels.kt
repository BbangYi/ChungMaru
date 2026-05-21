package com.example.youtubeparser

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
    val isVisibleToUser: Boolean,
    val isClickable: Boolean = false,
    val hasClickAction: Boolean = false,
    val hasClickableAncestor: Boolean = false
)

data class ParsedComment(
    val commentText: String,
    val boundsInScreen: BoundsRect,
    @SerializedName("author_id")
    val authorId: String? = null
)

data class ParseSnapshot(
    val timestamp: Long,
    @SerializedName("source_package")
    val sourcePackage: String? = null,
    @SerializedName("parse_started_at")
    val parseStartedAt: Long? = null,
    @SerializedName("parse_finished_at")
    val parseFinishedAt: Long? = null,
    @SerializedName("parse_duration_ms")
    val parseDurationMs: Long? = null,
    @SerializedName("visible_node_count")
    val visibleNodeCount: Int? = null,
    @SerializedName("parsed_comment_count")
    val parsedCommentCount: Int? = null,
    @SerializedName("saved_comment_count")
    val savedCommentCount: Int? = null,
    @SerializedName("comments_per_second")
    val commentsPerSecond: Double? = null,
    val comments: List<ParsedComment>
)

data class RollingParseSummary(
    @SerializedName("finalized_at")
    val finalizedAt: Long,
    @SerializedName("snapshot_count")
    val snapshotCount: Int,
    @SerializedName("total_parsed_comments")
    val totalParsedComments: Int,
    @SerializedName("total_saved_comments")
    val totalSavedComments: Int,
    @SerializedName("average_parse_duration_ms")
    val averageParseDurationMs: Double,
    @SerializedName("average_comments_per_second")
    val averageCommentsPerSecond: Double
)

data class RollingParseFile(
    val platform: String,
    @SerializedName("source_package")
    val sourcePackage: String,
    @SerializedName("file_started_at")
    val fileStartedAt: Long,
    @SerializedName("file_updated_at")
    val fileUpdatedAt: Long,
    @SerializedName("max_file_size_bytes")
    val maxFileSizeBytes: Long,
    val snapshots: List<ParseSnapshot>,
    val summary: RollingParseSummary? = null
)
