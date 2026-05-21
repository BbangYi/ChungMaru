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
        val matchedBodyKeys = mutableSetOf<String>()
        val screenBottom = sorted.maxOfOrNull { it.bottom } ?: 0
        val screenWidth = sorted.maxOfOrNull { it.right } ?: 0

        // 1) accessibility announcement: "username님이 body 댓글을 달았습니다"
        for (node in sorted) {
            if (isLikelyBottomOverlayText(node, screenBottom, screenWidth)) continue

            val text = node.displayText ?: continue
            val comment = extractAnnouncementComment(text) ?: continue
            if (isLikelyCommentBody(comment.body)) {
                results += ParsedComment(
                    commentText = comment.body,
                    boundsInScreen = BoundsRect(node.left, node.top, node.right, node.bottom),
                    authorId = comment.authorId
                )
                matchedBodyKeys += nodeKey(node)
            }
        }

        // 2) combined text: "username body..."
        for (node in sorted) {
            if (nodeKey(node) in matchedBodyKeys) continue
            if (isLikelyBottomOverlayText(node, screenBottom, screenWidth)) continue

            val text = node.displayText ?: continue
            val comment = extractBodyFromCombinedComment(text)
            if (comment != null && isLikelyCommentBody(comment.body)) {
                results += ParsedComment(
                    commentText = comment.body,
                    boundsInScreen = BoundsRect(node.left, node.top, node.right, node.bottom),
                    authorId = comment.authorId
                )
                matchedBodyKeys += nodeKey(node)
            }
        }

        // 3) separate username row -> nearest body row
        for (i in sorted.indices) {
            val anchor = sorted[i]
            val anchorText = anchor.displayText ?: continue
            if (!looksLikeUsername(anchorText)) continue
            if (isMetaText(anchorText)) continue

            for (j in i + 1 until sorted.size) {
                val next = sorted[j]
                val nextText = next.displayText ?: continue

                if (next.top - anchor.top > 180) break
                if (looksLikeUsername(nextText)) break
                if (isLikelyBottomOverlayText(next, screenBottom, screenWidth)) continue

                if (isDateText(nextText)) continue
                if (isMetaText(nextText)) continue
                if (isLikelyCommentBody(nextText)) {
                    results += ParsedComment(
                        commentText = nextText,
                        boundsInScreen = BoundsRect(next.left, next.top, next.right, next.bottom),
                        authorId = normalizeAuthor(anchorText)
                    )
                    matchedBodyKeys += nodeKey(next)
                    break
                }
            }
        }

        // 4) fallback: body-looking standalone text
        for (node in sorted) {
            if (nodeKey(node) in matchedBodyKeys) continue
            if (isLikelyStandaloneUiNoise(node, screenBottom, screenWidth)) continue

            val text = node.displayText ?: continue
            if (!isLikelyCommentBody(text)) continue
            if (looksLikeUsername(text)) continue
            if (isDateText(text)) continue
            if (isMetaText(text)) continue

            results += ParsedComment(
                commentText = text,
                boundsInScreen = BoundsRect(node.left, node.top, node.right, node.bottom),
                authorId = findNearestAuthorForBody(node, sorted)
            )
        }

        return results.distinctBy { comment ->
            val authorKey = comment.authorId.orEmpty().trim().lowercase()
            val textKey = normalizeTextKey(comment.commentText)
            if (authorKey.isNotBlank()) {
                "author:$authorKey\n$textKey"
            } else {
                "bounds:${comment.boundsInScreen.top}:${comment.boundsInScreen.left}\n$textKey"
            }
        }
    }

    private data class ExtractedInstagramComment(
        val body: String,
        val authorId: String? = null
    )

    private fun extractAnnouncementComment(text: String): ExtractedInstagramComment? {
        val trimmed = text.trim()
        val match = Regex("""^(.+?)님이\s*(.*?)\s*댓글을 달았습니다$""").find(trimmed) ?: return null
        val authorId = normalizeAuthor(match.groupValues[1])
        val body = match.groupValues[2].trim()
        if (body.isBlank()) return null
        if (isDateText(body)) return null
        if (isMetaText(body)) return null
        if (!isLikelyCommentBody(body)) return null
        return ExtractedInstagramComment(body = body, authorId = authorId)
    }

    private fun extractBodyFromCombinedComment(text: String): ExtractedInstagramComment? {
        val trimmed = text.trim()
        val match = Regex("""^([A-Za-z0-9._]{3,30})\s+(.+)$""").find(trimmed) ?: return null
        val authorId = normalizeAuthor(match.groupValues[1])
        val body = match.groupValues[2].trim()
        if (body.isBlank()) return null
        if (isDateText(body)) return null
        if (isMetaText(body)) return null
        if (!isLikelyCommentBody(body)) return null
        return ExtractedInstagramComment(body = body, authorId = authorId)
    }

    private fun looksLikeUsername(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith("@")) return true
        return !t.contains(" ") &&
                t.length in 3..30 &&
                t.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    private fun normalizeAuthor(text: String): String? {
        val author = text.trim()
            .removePrefix("@")
            .replace(Regex("""님의\s*프로필(로\s*이동)?$"""), "")
            .replace(Regex("""의\s*프로필(로\s*이동)?$"""), "")
            .trim()

        if (author.isBlank()) return null
        if (isMetaText(author)) return null
        if (isDateText(author)) return null
        if (author in setOf("작성자", "오디오", "프로필", "댓글")) return null
        return author
    }

    private fun isDateText(text: String): Boolean {
        val t = text.trim().lowercase()
        val compact = t.replace(Regex("\\s+"), "")

        return t.endsWith("초 전") ||
                t.endsWith("분 전") ||
                t.endsWith("시간 전") ||
                t.endsWith("일 전") ||
                t.endsWith("주 전") ||
                t.endsWith("개월 전") ||
                t.endsWith("년 전") ||
                Regex("""^\d+월\s*\d+일$""").matches(t) ||
                Regex("""^\d+(\.\d+)?(초|분|시간|일|주|개월|달|년)(전)?$""").matches(compact) ||
                Regex("""^\d+(\.\d+)?[smhdw]$""").matches(t)
    }

    private fun isMetaText(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower == "답글" ||
                lower == "답글 달기" ||
                lower == "좋아요" ||
                lower == "리포스트" ||
                lower == "댓글" ||
                lower == "댓글 달기" ||
                lower == "작성자" ||
                lower == "오디오" ||
                lower == "아직 댓글이 없습니다" ||
                lower == "댓글을 남겨보세요." ||
                lower == "이 게시물에 대한 댓글 기능이 제한되었습니다." ||
                lower == "더 알아보기" ||
                lower == "게시" ||
                lower == "저장" ||
                lower == "관심 없음" ||
                lower == "관심 있음" ||
                lower == "숨겨진 댓글 보기" ||
                lower == "캡션" ||
                lower == "릴스 트레이 컨테이너" ||
                lower == "미디어 컬렉션 썸네일" ||
                lower == "스토리에 추가" ||
                lower == "내 스토리" ||
                lower.contains("님에게 댓글 추가") ||
                isLikeSummaryText(lower) ||
                lower.contains("팔로우") ||
                lower.contains("follow") ||
                lower.contains("adscomponent") ||
                lower.contains("썸네일") ||
                lower.contains("님의 스토리") ||
                lower.contains("읽지 않은 스토리") ||
                lower.contains("태그했습니다") ||
                lower.contains("댓글 기능이 제한") ||
                lower.contains("숨겨졌습니다") ||
                lower.endsWith("… 광고") ||
                lower.contains(" · 광고") ||
                lower == "광고" ||
                Regex("""^응답\s*[\d,]+개$""").matches(lower) ||
                Regex("""^댓글\s*[\d,]+개$""").matches(lower) ||
                Regex("""^.+님이\s*.+\s*(photo|video|carousel|사진|동영상).+게시했습니다$""").matches(lower) ||
                Regex("""^.+님의\s*(사진|동영상|photo|video|carousel).*(좋아요|댓글).+""").matches(lower) ||
                lower.endsWith("(으)로 이동") ||
                (lower.contains("답글") && lower.contains("더 보기")) ||
                lower.endsWith("님의 프로필로 이동") ||
                lower.endsWith("님의 스토리 보기") ||
                lower.endsWith("님의 프로필 사진") ||
                lower == "프로필 사진" ||
                lower == "대화 참여하기..." ||
                lower == "대화를 시작해보세요..." ||
                lower == "회원님의 생각을 남겨보세요." ||
                lower == "앱 열기" ||
                lower == "열기" ||
                lower == "지금 신청하기" ||
                lower == "신청하기" ||
                lower == "프로필 방문" ||
                lower == "방문" ||
                lower == "보기" ||
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

    private fun isLikelyStandaloneUiNoise(
        node: ParsedTextNode,
        screenBottom: Int,
        screenWidth: Int
    ): Boolean {
        val text = node.displayText?.trim().orEmpty()
        if (isMetaText(text)) return true
        if (isLikelyBottomOverlayText(node, screenBottom, screenWidth)) return true
        if (screenBottom > 0 && node.top < (screenBottom * 0.32f).toInt()) return true
        if (node.className.orEmpty().contains("Button", ignoreCase = true)) return true
        return false
    }

    private fun isLikelyBottomOverlayText(
        node: ParsedTextNode,
        screenBottom: Int,
        screenWidth: Int
    ): Boolean {
        if (screenBottom <= 0 || screenWidth <= 0) return false

        val text = node.displayText?.trim().orEmpty()
        if (text.isBlank()) return false

        val leftRatio = node.left.toFloat() / screenWidth.toFloat()
        val rightRatio = node.right.toFloat() / screenWidth.toFloat()
        val widthRatio = (node.right - node.left).toFloat() / screenWidth.toFloat()
        val inReelsCaptionColumn = leftRatio in 0.12f..0.16f &&
            rightRatio in 0.74f..0.84f &&
            widthRatio in 0.58f..0.72f
        val knownTabletReelsCaptionColumn = node.left in 150..175 && node.right in 930..990
        val nearBottom = node.bottom >= (screenBottom * 0.86f).toInt()
        val overlayText = text.endsWith("…") ||
            text.endsWith("… 광고") ||
            text.contains(" · 광고") ||
            text == "광고"

        return (inReelsCaptionColumn || knownTabletReelsCaptionColumn) && nearBottom && overlayText
    }

    private fun isLikelyCommentBody(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2) return false
        if (isMetaText(t)) return false
        if (isDateText(t)) return false

        // pure username-like text is not a comment body
        if (looksLikeUsername(t)) return false
        if (looksLikeCountText(t)) return false

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

    private fun looksLikeCountText(text: String): Boolean {
        val t = text.trim()
        return Regex("""^\d{1,3}(,\d{3})+$""").matches(t) ||
                Regex("""^\d+(\.\d+)?[kKmM만천]?$""").matches(t)
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

    private fun findNearestAuthorForBody(
        bodyNode: ParsedTextNode,
        sorted: List<ParsedTextNode>
    ): String? {
        return sorted.asReversed()
            .asSequence()
            .filter { candidate -> candidate.top <= bodyNode.top }
            .filter { candidate -> bodyNode.top - candidate.top <= 280 }
            .filter { candidate -> kotlin.math.abs(candidate.left - bodyNode.left) <= 180 }
            .mapNotNull { candidate ->
                val text = candidate.displayText?.trim().orEmpty()
                if (text == bodyNode.displayText?.trim().orEmpty()) return@mapNotNull null
                if (!looksLikeUsername(text)) return@mapNotNull null
                if (isMetaText(text) || isDateText(text)) return@mapNotNull null
                normalizeAuthor(text)
            }
            .firstOrNull()
    }

    private fun normalizeTextKey(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ")
    }

    private fun nodeKey(node: ParsedTextNode): String {
        return "${node.left}:${node.top}:${node.right}:${node.bottom}:${node.displayText}"
    }
}
