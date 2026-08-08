package com.capstone.design.youtubeparser

internal const val INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX =
    "android-accessibility-comment:instagram:"

object InstagramCommentAnalysisAdapter {
    fun adapt(comments: List<ParsedComment>): List<ParsedComment> {
        return comments
            .asSequence()
            .filter { comment ->
                InstagramCommentUiTextPolicy.isDisplayable(comment.commentText)
            }
            .map { comment ->
                val existingSource = comment.authorId.orEmpty().trim()
                if (existingSource.startsWith(INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX)) {
                    comment
                } else {
                    val author = existingSource.removePrefix("@")
                    comment.copy(
                        authorId = INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX + author
                    )
                }
            }
            .distinctBy { comment ->
                "${comment.authorId.orEmpty()}|${comment.commentText.trim().lowercase()}"
            }
            .toList()
    }
}

internal object InstagramCommentUiTextPolicy {
    private val exactUiTexts = setOf(
        "\uB313\uAE00",
        "comments",
        "\uB2F5\uAE00",
        "reply",
        "\uC88B\uC544\uC694",
        "like",
        "\uB313\uAE00 \uB2EC\uAE30",
        "\uB313\uAE00 \uB2EC\uAE30...",
        "\uB313\uAE00 \uCD94\uAC00",
        "\uB313\uAE00 \uCD94\uAC00...",
        "\uB2F5\uAE00 \uB2EC\uAE30",
        "\uB2F5\uAE00 \uB2EC\uAE30...",
        "\uB2F5\uAE00 \uCD94\uAC00",
        "\uB2F5\uAE00 \uCD94\uAC00...",
        "\uB2F5\uAE00 \uB0A8\uAE30\uB294 \uC911",
        "\uB2F5\uAE00\uC744 \uB0A8\uAE30\uB294 \uC911",
        "\uB313\uAE00 \uB0A8\uAE30\uB294 \uC911",
        "\uB313\uAE00\uC744 \uB0A8\uAE30\uB294 \uC911",
        "add a comment",
        "add a comment...",
        "write a reply",
        "write a reply...",
        "replying",
        "posting reply",
        "posting comment"
    )

    fun isDisplayable(value: String): Boolean {
        val text = value.replace(Regex("\\s+"), " ").trim()
        if (text.length < 2) return false
        val lower = text.lowercase()
        if (Regex("""^[\d,.]+$""").matches(text)) return false
        if (lower in exactUiTexts) return false
        if (lower.startsWith("replying to @")) return false
        if (lower.startsWith("reply to @")) return false
        if (lower.startsWith("@") && lower.endsWith("\uB2D8\uC5D0\uAC8C \uB2F5\uAE00 \uB0A8\uAE30\uB294 \uC911")) {
            return false
        }
        return true
    }
}

object InstagramSafeCommentAssembler {
    private const val INSTAGRAM_ACCESSIBILITY_SOURCE =
        "android-accessibility-comment:instagram"
    private const val UNKNOWN_AUTHOR = "@instagram"

    fun assembleAccessibilityResults(
        results: List<AndroidAnalysisResultItem>
    ): YoutubeSafeCommentBatch {
        return YoutubeSafeCommentAssembler.assemblePlatformAccessibilityResults(
            results = results.filter { result ->
                InstagramCommentUiTextPolicy.isDisplayable(result.original)
            },
            accessibilitySource = INSTAGRAM_ACCESSIBILITY_SOURCE,
            unknownAuthor = UNKNOWN_AUTHOR
        )
    }
}
