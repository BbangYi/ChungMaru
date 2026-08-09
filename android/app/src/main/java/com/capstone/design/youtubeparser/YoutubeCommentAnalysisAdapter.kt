package com.capstone.design.youtubeparser

/** Adapts the standalone parser output to the live model and mirror contract. */
object YoutubeCommentAnalysisAdapter {
    private const val SOURCE_PREFIX = "android-accessibility-comment:youtube:"

    fun adapt(comments: List<ParsedComment>): List<ParsedComment> {
        return comments
            .asSequence()
            .filter(::isSavableYoutubeComment)
            .map { comment ->
                val author = comment.authorId.orEmpty().trim().removePrefix("@")
                comment.copy(authorId = "$SOURCE_PREFIX$author")
            }
            .toList()
    }

    private fun isSavableYoutubeComment(comment: ParsedComment): Boolean {
        val author = comment.authorId.orEmpty().trim().removePrefix("@")
        val text = comment.commentText.trim()
        if (author.isBlank() || text.isBlank()) return false
        if (looksLikeCountOnlyText(text)) return false
        return !isYoutubeNonCommentText(text)
    }

    private fun looksLikeCountOnlyText(text: String): Boolean {
        val trimmed = text.trim()
        return Regex("""^\d{1,3}(,\d{3})+$""").matches(trimmed) ||
            Regex("""^\d+(\.\d+)?[kKmM만천]?$""").matches(trimmed)
    }

    private fun isYoutubeNonCommentText(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower == "답글" ||
            lower == "답글 달기" ||
            lower == "좋아요" ||
            lower == "싫어요" ||
            lower == "댓글" ||
            lower == "댓글 추가" ||
            lower == "댓글 추가..." ||
            lower == "공유" ||
            lower == "구독" ||
            lower == "홈" ||
            lower == "shorts" ||
            lower == "쇼츠" ||
            lower == "나" ||
            lower == "검색" ||
            lower == "뒤로" ||
            lower == "닫기" ||
            lower == "정렬 기준" ||
            lower == "인기 댓글순" ||
            lower == "최신순" ||
            lower == "드래그 핸들" ||
            lower == "댓글 정보" ||
            lower == "좋아요 취소" ||
            lower == "댓글 싫어요 표시" ||
            lower == "작업 메뉴" ||
            lower == "한국어로 번역" ||
            lower == "[music]" ||
            lower == "[음악]" ||
            lower == "reply" ||
            lower == "reply..." ||
            lower == "comment" ||
            lower == "comment..." ||
            lower == "add a comment" ||
            lower == "sort comments" ||
            lower == "like" ||
            lower == "dislike" ||
            lower == "close" ||
            lower == "back" ||
            lower == "share" ||
            lower == "subscribe" ||
            lower == "view reply" ||
            lower == "view replies" ||
            Regex("^좋아요\\s*\\d+개$").matches(lower) ||
            lower.contains("이 댓글을 좋아함") ||
            lower.startsWith("view ") && lower.contains("replies")
    }
}
