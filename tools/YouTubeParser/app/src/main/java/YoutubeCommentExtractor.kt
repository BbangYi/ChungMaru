package com.example.youtubeparser

import kotlin.math.abs

object YoutubeCommentExtractor {

    fun extractComments(nodes: List<ParsedTextNode>): List<ParsedComment> {
        val sorted = nodes.sortedWith(compareBy<ParsedTextNode> { it.top }.thenBy { it.left })
        val result = mutableListOf<ParsedComment>()

        for (i in sorted.indices) {
            val author = sorted[i]
            val authorText = author.displayText.orEmpty()

            if (!looksLikeAuthor(authorText)) continue

            val candidates = mutableListOf<Pair<ParsedTextNode, Int>>()

            for (j in i + 1 until sorted.size) {
                val node = sorted[j]
                val text = node.displayText.orEmpty()

                if (looksLikeAuthor(text)) break

                val verticalGap = node.top - author.bottom
                if (verticalGap < 0) continue
                if (verticalGap > 220) break

                val score = scoreAsCommentBody(author, node, text)
                if (score > 0) {
                    candidates += node to score
                }
            }

            val best = candidates.maxByOrNull { it.second }?.first ?: continue

            result += ParsedComment(
                commentText = best.displayText.orEmpty(),
                boundsInScreen = BoundsRect(
                    left = best.left,
                    top = best.top,
                    right = best.right,
                    bottom = best.bottom
                )
            )
        }

        return result.distinctBy {
            "${it.commentText}|${it.boundsInScreen.top}|${it.boundsInScreen.left}"
        }
    }

    private fun scoreAsCommentBody(
        author: ParsedTextNode,
        node: ParsedTextNode,
        text: String
    ): Int {
        if (text.isBlank()) return 0
        if (looksLikeAuthor(text)) return 0
        if (looksLikeTime(text)) return 0
        if (isUiJunk(text)) return 0

        var score = 0

        val sameColumn = abs(node.left - author.left) <= 40
        if (sameColumn) score += 50

        val verticalGap = node.top - author.bottom
        if (verticalGap in 0..80) score += 40
        else if (verticalGap in 81..160) score += 20

        val width = node.right - node.left
        if (width >= 500) score += 30
        else if (width >= 250) score += 15

        val len = text.length
        if (len >= 5) score += 20
        if (len >= 20) score += 15

        if (text.contains("\n")) score += 10

        return score
    }

    private fun looksLikeAuthor(text: String): Boolean {
        return text.startsWith("@") && text.length >= 2
    }

    private fun looksLikeTime(text: String): Boolean {
        return text.lowercase().contains("ago")
    }

    private fun isUiJunk(text: String): Boolean {
        val lower = text.lowercase()

        if (lower.startsWith("comments.")) return true
        if (lower == "sort comments") return true
        if (lower == "reply") return true
        if (lower == "reply...") return true
        if (lower == "comment...") return true
        if (lower == "view reply") return true
        if (lower.startsWith("view ") && lower.contains(" total replies")) return true
        if (lower.contains("like this comment")) return true
        if (lower.contains("like this reply")) return true
        if (lower.contains("dislike this comment")) return true
        if (lower.contains("dislike this reply")) return true
        if (lower.contains("action menu")) return true
        if (lower.contains("open camera")) return true
        if (lower.contains("drag handle")) return true
        if (lower.contains("video player")) return true
        if (lower.contains("minutes")) return true
        if (lower.contains("seconds")) return true
        if (lower == "back") return true
        if (lower == "close") return true
        if (lower.endsWith(" likes") || lower.endsWith(" like")) return true

        return false
    }
}