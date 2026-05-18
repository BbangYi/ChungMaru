package com.example.youtubeparser

object InstagramCommentExtractor {

    fun extractComments(nodes: List<ParsedTextNode>): List<ParsedComment> {
        if (nodes.isEmpty()) return emptyList()

        val sorted = nodes.mapNotNull { node ->
            val text = node.displayText?.trim() ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            node.copy(displayText = text)
        }.sortedWith(compareBy<ParsedTextNode> { it.top }.thenBy { it.left })

        val results = mutableListOf<ParsedComment>()

        // 1) combined text: "username body..."
        for (node in sorted) {
            val text = node.displayText ?: continue
            val body = extractBodyFromCombinedComment(text)
            if (body != null && isLikelyCommentBody(body)) {
                results += ParsedComment(
                    commentText = body,
                    boundsInScreen = BoundsRect(node.left, node.top, node.right, node.bottom)
                )
            }
        }

        // 2) separate username row -> nearest body row
        for (i in sorted.indices) {
            val anchor = sorted[i]
            val anchorText = anchor.displayText ?: continue
            if (!looksLikeUsername(anchorText)) continue

            for (j in i + 1 until sorted.size) {
                val next = sorted[j]
                val nextText = next.displayText ?: continue

                if (next.top - anchor.top > 180) break
                if (looksLikeUsername(nextText)) break

                if (isDateText(nextText)) continue
                if (isMetaText(nextText)) continue
                if (isLikelyCommentBody(nextText)) {
                    results += ParsedComment(
                        commentText = nextText,
                        boundsInScreen = BoundsRect(next.left, next.top, next.right, next.bottom)
                    )
                    break
                }
            }
        }

        // 3) fallback: body-looking standalone text
        for (node in sorted) {
            val text = node.displayText ?: continue
            if (!isLikelyCommentBody(text)) continue
            if (looksLikeUsername(text)) continue
            if (isDateText(text)) continue
            if (isMetaText(text)) continue

            results += ParsedComment(
                commentText = text,
                boundsInScreen = BoundsRect(node.left, node.top, node.right, node.bottom)
            )
        }

        return results.distinctBy { "${it.commentText}|${it.boundsInScreen.top}|${it.boundsInScreen.left}" }
    }

    private fun extractBodyFromCombinedComment(text: String): String? {
        val trimmed = text.trim()
        val match = Regex("""^([A-Za-z0-9._]{3,30})\s+(.+)$""").find(trimmed) ?: return null
        val body = match.groupValues[2].trim()
        if (body.isBlank()) return null
        if (isDateText(body)) return null
        if (isMetaText(body)) return null
        if (!isLikelyCommentBody(body)) return null
        return body
    }

    private fun looksLikeUsername(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith("@")) return true
        return !t.contains(" ") &&
                t.length in 3..30 &&
                t.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    private fun isDateText(text: String): Boolean {
        val t = text.trim()
        return t.endsWith("초 전") ||
                t.endsWith("분 전") ||
                t.endsWith("시간 전") ||
                t.endsWith("일 전") ||
                t.endsWith("주 전") ||
                Regex("""^\d+월\s*\d+일$""").matches(t)
    }

    private fun isMetaText(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower == "답글" ||
                lower == "좋아요" ||
                lower == "리포스트" ||
                lower == "댓글 달기" ||
                lower == "저장" ||
                lower == "관심 없음" ||
                lower == "관심 있음" ||
                lower == "숨겨진 댓글 보기" ||
                lower == "캡션" ||
                lower.contains("님에게 댓글 추가") ||
                isLikeSummaryText(lower) ||
                lower.contains("팔로우") ||
                lower.contains("follow") ||
                (lower.contains("답글") && lower.contains("더 보기")) ||
                lower.endsWith("님의 프로필로 이동") ||
                lower.endsWith("님의 스토리 보기") ||
                lower.endsWith("님의 프로필 사진") ||
                lower == "프로필 사진" ||
                lower == "대화 참여하기..." ||
                lower == "회원님의 생각을 남겨보세요." ||
                lower == "검색 및 탐색하기" ||
                lower == "검색" ||
                lower == "search" ||
                lower == "공유" ||
                lower == "share" ||
                lower == "활동" ||
                lower == "만들기" ||
                lower == "프로필" ||
                lower == "reel" ||
                lower == "reels" ||
                lower == "릴스" ||
                lower.contains("님이 만든 릴스입니다") ||
                lower.contains("재생하거나 일시 중지하려면") ||
                lower.contains("번역 보기") ||
                lower.contains("see translation") ||
                lower.contains("더 보기") ||
                lower.contains("more")
    }

    private fun isLikelyCommentBody(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2) return false
        if (isMetaText(t)) return false
        if (isDateText(t)) return false

        // pure username-like text is not a comment body
        if (looksLikeUsername(t)) return false

        // obvious caption / like-summary patterns
        if (isLikeSummaryText(t)) return false
        if (hasTooManyHashtags(t)) return false

        val koreanCount = t.count { it in '\uAC00'..'\uD7A3' }
        if (koreanCount >= 2) return true
        if (t.contains(" ")) return true
        if (t.any { it in listOf('…', '.', ',', '!', '?', 'ㅋ', 'ㅎ', 'ㅠ', 'ㅜ') }) return true
        if (t.any { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }) return true

        return false
    }

    private fun isLikeSummaryText(text: String): Boolean {
        val t = text.trim().lowercase()
        return (t.contains("님 외") && t.contains("좋아합니다")) ||
                t.contains("명이 좋아합니다") ||
                t.endsWith("좋아요")
    }

    private fun hasTooManyHashtags(text: String): Boolean {
        val hashtagCount = text.count { it == '#' }
        if (hashtagCount >= 3) return true
        if (text.trim().startsWith("#") && hashtagCount >= 2) return true
        return false
    }
}