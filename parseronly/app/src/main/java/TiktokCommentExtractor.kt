package com.example.youtubeparser

import kotlin.math.abs

object TiktokCommentExtractor {

    fun extractComments(nodes: List<ParsedTextNode>): List<ParsedComment> {
        if (nodes.isEmpty()) return emptyList()

        val sorted = nodes.mapNotNull { node ->
            val text = node.displayText?.trim() ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            node.copy(displayText = text)
        }.sortedWith(compareBy<ParsedTextNode> { it.top }.thenBy { it.left })

        val results = mutableListOf<ParsedComment>()
        val matchedBodyKeys = mutableSetOf<String>()

        for (i in sorted.indices) {
            val authorNode = sorted[i]
            if (!looksLikeAuthorCandidate(authorNode)) continue

            val candidates = mutableListOf<Pair<ParsedTextNode, Int>>()

            for (j in i + 1 until sorted.size) {
                val bodyNode = sorted[j]
                val bodyText = bodyNode.displayText ?: continue

                val gapFromAuthorTop = bodyNode.top - authorNode.top
                if (gapFromAuthorTop > 260) break

                if (
                    gapFromAuthorTop > 24 &&
                    abs(bodyNode.left - authorNode.left) <= 120 &&
                    looksLikeAuthorCandidate(bodyNode)
                ) {
                    break
                }

                if (isNonCommentText(bodyText)) continue
                if (!isLikelyCommentBody(bodyText)) continue

                val score = scoreAsCommentBody(authorNode, bodyNode, bodyText)
                if (score > 0) {
                    candidates += bodyNode to score
                }
            }

            val best = candidates.maxByOrNull { it.second }?.first ?: continue
            val bestText = best.displayText ?: continue
            matchedBodyKeys += nodeKey(best)

            results += ParsedComment(
                commentText = bestText,
                boundsInScreen = BoundsRect(best.left, best.top, best.right, best.bottom),
                authorId = normalizeAuthor(authorNode)
            )
        }

        // Keep body extraction coverage when the username is hidden or not exposed.
        for (node in sorted) {
            val text = node.displayText ?: continue
            if (nodeKey(node) in matchedBodyKeys) continue
            if (hasClickableSignal(node) && looksLikeAuthorCandidate(node)) continue
            if (isNonCommentText(text)) continue
            if (!isLikelyCommentBody(text)) continue

            results += ParsedComment(
                commentText = text,
                boundsInScreen = BoundsRect(node.left, node.top, node.right, node.bottom),
                authorId = null
            )
        }

        return results.distinctBy { comment ->
            val textKey = normalizeTextKey(comment.commentText)
            val authorKey = comment.authorId?.let { normalizeAuthorKey(it) }.orEmpty()
            if (authorKey.isNotBlank()) {
                "author:$authorKey\n$textKey"
            } else {
                "bounds:${comment.boundsInScreen.top}:${comment.boundsInScreen.left}\n$textKey"
            }
        }
    }

    private fun scoreAsCommentBody(
        author: ParsedTextNode,
        bodyNode: ParsedTextNode,
        body: String
    ): Int {
        if (!isLikelyCommentBody(body)) return 0

        val leftGap = abs(bodyNode.left - author.left)
        val verticalGap = bodyNode.top - author.bottom
        val width = bodyNode.right - bodyNode.left

        if (verticalGap !in -12..240) return 0

        var score = 0

        if (leftGap <= 80) score += 45
        else if (leftGap <= 150) score += 20
        else return 0

        if (verticalGap in -12..90) score += 45
        else if (verticalGap in 91..170) score += 25
        else score += 10

        if (width >= 300) score += 20
        else if (width >= 160) score += 10

        if (body.length >= 3) score += 10
        if (body.length >= 8) score += 10
        if (body.contains(" ")) score += 10
        if (body.count { it in '\uAC00'..'\uD7A3' } >= 2) score += 10
        if (hasEmojiOrSymbol(body)) score += 6
        if (hasClickableSignal(author)) score += 35
        if (author.className.orEmpty().contains("Button", ignoreCase = true)) score += 15

        return score
    }

    private fun looksLikeAuthorCandidate(node: ParsedTextNode): Boolean {
        val raw = node.displayText?.trim() ?: return false
        val t = cleanAuthorLabel(raw)
        val profileLabel = isProfileLabel(raw)
        if (t.isBlank()) return false
        if (raw.contains('\n')) return false
        if (t.any { it.isWhitespace() }) return false
        if (t.length !in 2..32) return false
        if ((!profileLabel && isNonCommentText(raw)) || isNonCommentText(t)) return false
        if (hasEmojiOrSymbol(t)) return false
        if (t.count { it == '.' } > 3) return false

        val normalized = t.removePrefix("@")
        if (normalized.isBlank()) return false
        if (normalized.all { it.isDigit() }) return false
        if (!normalized.all { it.isLetterOrDigit() || it == '_' || it == '.' }) return false

        val hasStrongUsernameSignal = t.startsWith("@") ||
            normalized.any { it.isDigit() || it == '_' || it == '.' } ||
            normalized.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasKoreanNameSignal = normalized.count { it in '\uAC00'..'\uD7A3' } in 2..12

        if (!hasClickableSignal(node) && !hasStrongUsernameSignal) return false

        return hasStrongUsernameSignal || hasKoreanNameSignal
    }

    private fun normalizeAuthor(node: ParsedTextNode): String? {
        val author = normalizeAuthorKey(cleanAuthorLabel(node.displayText.orEmpty()))
        return author.ifBlank { null }
    }

    private fun cleanAuthorLabel(text: String): String {
        return text.trim()
            .replace(Regex("""님의\s*프로필(로\s*이동)?$"""), "")
            .replace(Regex("""의\s*프로필(로\s*이동)?$"""), "")
            .trim()
    }

    private fun isProfileLabel(text: String): Boolean {
        val t = text.trim()
        return Regex(""".+님(?:의)?\s*프로필(로\s*이동)?$""").matches(t) ||
            Regex(""".+의\s*프로필(로\s*이동)?$""").matches(t)
    }

    private fun normalizeAuthorKey(text: String): String {
        return text.trim()
            .removePrefix("@")
            .lowercase()
    }

    private fun hasClickableSignal(node: ParsedTextNode): Boolean {
        return node.isClickable ||
            node.hasClickAction ||
            node.hasClickableAncestor ||
            node.className.orEmpty().contains("Button", ignoreCase = true)
    }

    private fun normalizeTextKey(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ")
    }

    private fun nodeKey(node: ParsedTextNode): String {
        return "${node.left}:${node.top}:${node.right}:${node.bottom}:${node.displayText}"
    }

    private fun isLikelyCommentBody(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2) return false
        if (isNonCommentText(t)) return false
        if (t.startsWith("#")) return false
        if (hasTooManyHashtags(t)) return false

        val koreanCount = t.count { it in '\uAC00'..'\uD7A3' }
        if (koreanCount >= 2) return true
        if (t.contains(" ")) return true
        if (t.length >= 5) return true
        if (t.any { it in listOf('.', ',', '!', '?', '~') }) return true
        if (hasEmojiOrSymbol(t)) return true

        return false
    }

    private fun isNonCommentText(text: String): Boolean {
        return isDateText(text) ||
            isEngagementCountText(text) ||
            isCountOnlyText(text) ||
            isMetaText(text)
    }

    private fun isDateText(text: String): Boolean {
        val t = text.trim().lowercase()
        val compact = t.replace(Regex("\\s+"), "")

        if (compact in setOf("now", "justnow", "방금", "방금전", "오늘", "어제")) return true

        return Regex("""^\d{1,2}-\d{1,2}$""").matches(t) ||
            Regex("""^\d{1,2}:\d{2}$""").matches(t) ||
            Regex("""^\d+(\.\d+)?[smhdw]$""").matches(t) ||
            Regex("""^\d+(\.\d+)?\s*(sec|secs|second|seconds|min|mins|minute|minutes|hr|hrs|hour|hours|day|days|week|weeks|mo|month|months|year|years)\s*(ago)?$""").matches(t) ||
            Regex("""^\d+(\.\d+)?(초|분|시간|일|주|개월|달|년)(전)?$""").matches(compact)
    }

    private fun isCountOnlyText(text: String): Boolean {
        return Regex("""^[\d,]+$""").matches(text.trim())
    }

    private fun isEngagementCountText(text: String): Boolean {
        val t = text.trim().lowercase().replace(",", "")
        return Regex("""^\d+(\.\d+)?[km]$""").matches(t) ||
            Regex("""^\d+(\.\d+)?(천|만|개|명|회)$""").matches(t) ||
            Regex("""^\d+(\.\d+)?\s*(likes?|좋아요)$""").matches(t)
    }

    private fun isMetaText(text: String): Boolean {
        val lower = text.trim().lowercase()
        val visible = lower
            .replace(Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]"""), "")
            .trim()
        if (visible.isBlank()) return true

        val exactMetaTexts = setOf(
            "reply",
            "replies",
            "view replies",
            "view more replies",
            "like",
            "likes",
            "follow",
            "following",
            "for you",
            "friends",
            "share",
            "search",
            "live",
            "profile",
            "home",
            "inbox",
            "comment",
            "comments",
            "reply...",
            "comment...",
            "back",
            "close",
            "see more",
            "more",
            "original poster",
            "creator",
            "author",
            "작성자",
            "답글",
            "답글 보기",
            "답글 달기",
            "댓글",
            "댓글 달기",
            "좋아요",
            "공유",
            "검색",
            "프로필",
            "홈",
            "팔로우",
            "팔로잉",
            "친구",
            "추천",
            "라이브",
            "동영상",
            "알림",
            "스티커",
            "닫기",
            "뒤로",
            "편집효과",
            "음악",
            "숨겨짐",
            "사진",
            "게시물",
            "ai 생성 미디어 포함",
            "크리에이터가 댓글 액세스를 제한했습니다.",
            "첫 댓글",
            "· 효과 사용"
        )

        return visible in exactMetaTexts ||
            visible.startsWith("검색 ·") ||
            visible.startsWith("검색·") ||
            visible.startsWith("검색:") ||
            visible.startsWith("search ·") ||
            Regex("""^@\d{5,}$""").matches(visible) ||
            Regex("""^댓글\s*[\d,]+개$""").matches(visible) ||
            Regex("""^협업자\s*[\d,]+명$""").matches(visible) ||
            Regex("""^게시물\s*[\d,.]+[km천만]?개$""").matches(visible) ||
            visible.contains("효과 사용") ||
            visible.contains("님이 게시한 동영상이 여기에 나타납니다") ||
            visible.contains("크리에이터가 댓글 액세스를 제한했습니다") ||
            isProfileLabel(visible) ||
            visible.contains("reply") ||
            visible.contains("mention") ||
            visible.contains("이 멘션") ||
            visible.contains("숨겨진 댓글") ||
            (visible.startsWith("게시물 ") && visible.endsWith("개")) ||
            visible.endsWith("likes") ||
            visible.endsWith("like") ||
            visible.endsWith("좋아요") ||
            visible.contains("댓글에 답글") ||
            visible.contains("프로필로 이동") ||
            visible.contains("스티커")
    }

    private fun hasTooManyHashtags(text: String): Boolean {
        val hashtagCount = text.count { it == '#' }
        return hashtagCount >= 4 || (text.trim().startsWith("#") && hashtagCount >= 2)
    }

    private fun hasEmojiOrSymbol(text: String): Boolean {
        return text.any {
            val type = Character.getType(it)
            type == Character.OTHER_SYMBOL.toInt() ||
                type == Character.SURROGATE.toInt()
        }
    }
}
