package com.capstone.design.youtubeparser

import kotlin.math.max
import kotlin.math.min

internal data class YoutubeCommentOcrAnchor(
    val authorLabel: String,
    val authorBounds: BoundsRect,
    val rowBounds: BoundsRect,
    val replyBounds: BoundsRect
)

internal data class YoutubeCommentOcrRowPlan(
    val authorLabel: String,
    val bodyBounds: BoundsRect,
    val captureBounds: BoundsRect
) {
    fun toRoi(): VisualTextRoi {
        return VisualTextRoi(
            boundsInScreen = captureBounds,
            source = YoutubeCommentOcrFallback.ROI_SOURCE,
            priority = -10,
            reason = "youtube-comment-body-fallback",
            sourceText = authorLabel
        )
    }
}

internal object YoutubeCommentOcrFallback {
    const val ROI_SOURCE = "youtube-comment-panel"

    private const val MAX_ROWS = 6
    private const val BODY_LEFT_INSET_PX = 72
    private const val BODY_RIGHT_INSET_PX = 72
    private const val BODY_TOP_GAP_PX = 2
    private const val BODY_BOTTOM_GAP_PX = 8
    private const val MIN_BODY_WIDTH_PX = 160
    private const val MIN_BODY_HEIGHT_PX = 12
    private const val MIN_CAPTURE_HEIGHT_PX = 44
    private const val BODY_MATCH_SLOP_PX = 8
    private const val ACCESSIBILITY_SOURCE = "android-accessibility-comment:youtube"

    private val whitespace = Regex("\\s+")
    private val numericLabel = Regex("^[\\d,.]+(?:[kmb천만])?$", RegexOption.IGNORE_CASE)
    private val relativeTimeLabel = Regex(
        "^\\d+\\s*(?:초|분|시간|일|주|개월|달|년|second|minute|hour|day|week|month|year)s?\\s*(?:전|ago)(?:\\s*\\(수정됨\\))?$",
        RegexOption.IGNORE_CASE
    )
    private val replyCountLabel = Regex(
        "^(?:답글\\s*)?(?:총\\s*)?\\d+\\s*개?(?:의\\s*)?(?:답글)?\\s*(?:보기)?$",
        RegexOption.IGNORE_CASE
    )
    private val exactUiLabels = setOf(
        "답글",
        "댓글",
        "댓글 정보",
        "좋아요",
        "싫어요",
        "더보기",
        "작업 메뉴",
        "인기순",
        "회원",
        "최신순",
        "댓글 올리기...",
        "검열 중",
        "댓글을 확인하고 있습니다",
        "reply",
        "replies",
        "like",
        "dislike",
        "read more",
        "show more",
        "top",
        "newest"
    )

    fun planRows(
        anchors: List<YoutubeCommentOcrAnchor>,
        panelBounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): List<YoutubeCommentOcrRowPlan> {
        if (screenWidth <= 0 || screenHeight <= 0) return emptyList()
        if (!panelBounds.isValid()) return emptyList()

        return anchors
            .asSequence()
            .sortedWith(compareBy<YoutubeCommentOcrAnchor> { it.rowBounds.top }.thenBy { it.rowBounds.left })
            .mapNotNull { anchor ->
                planRow(anchor, panelBounds, screenWidth, screenHeight)
            }
            .distinctBy { plan ->
                val bounds = plan.captureBounds
                "${plan.authorLabel.lowercase()}@${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            }
            .take(MAX_ROWS)
            .toList()
    }

    fun assembleComments(
        rows: List<YoutubeCommentOcrRowPlan>,
        ocrCandidates: List<ParsedComment>
    ): List<ParsedComment> {
        if (rows.isEmpty() || ocrCandidates.isEmpty()) return emptyList()

        return rows.mapNotNull { row ->
            val matchingLines = ocrCandidates
                .asSequence()
                .filter { candidate -> candidate.belongsTo(row) }
                .mapNotNull { candidate ->
                    val text = normalize(candidate.commentText)
                    text.takeIf { isLikelyBodyText(it, row.authorLabel) }
                        ?.let { candidate.copy(commentText = it) }
                }
                .sortedWith(
                    compareBy<ParsedComment> { it.boundsInScreen.top }
                        .thenBy { it.boundsInScreen.left }
                )
                .distinctBy { candidate -> normalize(candidate.commentText).lowercase() }
                .toList()
            if (matchingLines.isEmpty()) return@mapNotNull null

            val text = matchingLines.joinToString(" ") { candidate -> candidate.commentText }
            if (!isLikelyBodyText(text, row.authorLabel)) return@mapNotNull null
            val bounds = BoundsRect(
                left = matchingLines.minOf { it.boundsInScreen.left },
                top = matchingLines.minOf { it.boundsInScreen.top },
                right = matchingLines.maxOf { it.boundsInScreen.right },
                bottom = matchingLines.maxOf { it.boundsInScreen.bottom }
            )
            ParsedComment(
                commentText = text,
                boundsInScreen = bounds,
                authorId = "$ACCESSIBILITY_SOURCE:${authorToken(row.authorLabel)}"
            )
        }.distinctBy { comment ->
            "${comment.authorId.orEmpty().lowercase()}|${normalize(comment.commentText).lowercase()}"
        }
    }

    private fun planRow(
        anchor: YoutubeCommentOcrAnchor,
        panelBounds: BoundsRect,
        screenWidth: Int,
        screenHeight: Int
    ): YoutubeCommentOcrRowPlan? {
        if (!anchor.authorBounds.isValid() || !anchor.rowBounds.isValid() || !anchor.replyBounds.isValid()) {
            return null
        }
        val authorLabel = normalize(anchor.authorLabel)
        if (!authorLabel.startsWith('@') || authorLabel.length !in 2..96) return null

        val clippedPanel = panelBounds.clamp(screenWidth, screenHeight) ?: return null
        val bodyLeft = max(
            max(anchor.rowBounds.left + BODY_LEFT_INSET_PX, anchor.authorBounds.right + 16),
            clippedPanel.left
        ).coerceIn(0, screenWidth)
        val bodyRight = min(
            min(anchor.rowBounds.right - BODY_RIGHT_INSET_PX, clippedPanel.right),
            screenWidth
        ).coerceAtLeast(bodyLeft)
        val bodyTop = max(
            max(anchor.authorBounds.bottom + BODY_TOP_GAP_PX, anchor.rowBounds.top),
            clippedPanel.top
        ).coerceIn(0, screenHeight)
        val bodyBottom = min(
            min(anchor.replyBounds.top - BODY_BOTTOM_GAP_PX, anchor.rowBounds.bottom),
            clippedPanel.bottom
        ).coerceAtLeast(bodyTop)
        if (bodyRight - bodyLeft < MIN_BODY_WIDTH_PX || bodyBottom - bodyTop < MIN_BODY_HEIGHT_PX) {
            return null
        }

        val bodyBounds = BoundsRect(bodyLeft, bodyTop, bodyRight, bodyBottom)
        val captureBounds = expandCaptureBounds(
            bodyBounds = bodyBounds,
            rowBounds = anchor.rowBounds,
            replyBounds = anchor.replyBounds,
            panelBounds = clippedPanel,
            screenHeight = screenHeight
        ) ?: return null
        return YoutubeCommentOcrRowPlan(
            authorLabel = authorLabel,
            bodyBounds = bodyBounds,
            captureBounds = captureBounds
        )
    }

    private fun expandCaptureBounds(
        bodyBounds: BoundsRect,
        rowBounds: BoundsRect,
        replyBounds: BoundsRect,
        panelBounds: BoundsRect,
        screenHeight: Int
    ): BoundsRect? {
        val currentHeight = bodyBounds.bottom - bodyBounds.top
        if (currentHeight >= MIN_CAPTURE_HEIGHT_PX) return bodyBounds

        val missing = MIN_CAPTURE_HEIGHT_PX - currentHeight
        var top = max(max(rowBounds.top, panelBounds.top), bodyBounds.top - (missing / 2 + 4))
        var bottom = min(
            min(min(rowBounds.bottom, panelBounds.bottom), replyBounds.bottom),
            bodyBounds.bottom + (missing - missing / 2 + 4)
        )
        if (bottom - top < MIN_CAPTURE_HEIGHT_PX) {
            top = max(max(rowBounds.top, panelBounds.top), bottom - MIN_CAPTURE_HEIGHT_PX)
        }
        if (bottom - top < MIN_CAPTURE_HEIGHT_PX) {
            bottom = min(min(rowBounds.bottom, panelBounds.bottom), top + MIN_CAPTURE_HEIGHT_PX)
        }
        top = top.coerceIn(0, screenHeight)
        bottom = bottom.coerceIn(top, screenHeight)
        if (bottom - top < MIN_CAPTURE_HEIGHT_PX) return null
        return BoundsRect(bodyBounds.left, top, bodyBounds.right, bottom)
    }

    private fun ParsedComment.belongsTo(row: YoutubeCommentOcrRowPlan): Boolean {
        val metadata = VisualTextOcrMetadataCodec.decode(authorId) ?: return false
        if (metadata.source != ROI_SOURCE || metadata.roiBoundsInScreen != row.captureBounds) return false
        val bounds = boundsInScreen
        if (!bounds.isValid()) return false
        val centerY = (bounds.top + bounds.bottom) / 2
        val body = row.bodyBounds
        val verticalMatch = centerY in (body.top - BODY_MATCH_SLOP_PX)..(body.bottom + BODY_MATCH_SLOP_PX)
        val horizontalOverlap = min(bounds.right, body.right) > max(bounds.left, body.left)
        return verticalMatch && horizontalOverlap
    }

    private fun isLikelyBodyText(text: String, authorLabel: String): Boolean {
        val value = normalize(text)
        if (value.length < 2) return false
        val lowercase = value.lowercase()
        if (lowercase in exactUiLabels) return false
        if (numericLabel.matches(value) || relativeTimeLabel.matches(value) || replyCountLabel.matches(value)) {
            return false
        }
        if (lowercase == normalize(authorLabel).lowercase()) return false
        if (lowercase == normalize(authorLabel.removePrefix("@")).lowercase()) return false
        if (lowercase.contains("이 댓글을 좋아") || lowercase.contains("like this comment")) return false
        if (lowercase.contains("댓글 싫어요") || lowercase.contains("dislike this comment")) return false
        if (lowercase.contains("답글 총") && lowercase.endsWith("보기")) return false
        return value.any { char -> char.isLetterOrDigit() || char.code in 0xAC00..0xD7A3 }
    }

    private fun authorToken(authorLabel: String): String {
        return normalize(authorLabel)
            .removePrefix("@")
            .replace(':', '_')
            .ifBlank { "youtube" }
    }

    private fun normalize(value: String): String = value.replace(whitespace, " ").trim()

    private fun BoundsRect.isValid(): Boolean = right > left && bottom > top

    private fun BoundsRect.clamp(screenWidth: Int, screenHeight: Int): BoundsRect? {
        val safeLeft = left.coerceIn(0, screenWidth)
        val safeTop = top.coerceIn(0, screenHeight)
        val safeRight = right.coerceIn(safeLeft, screenWidth)
        val safeBottom = bottom.coerceIn(safeTop, screenHeight)
        if (safeRight <= safeLeft || safeBottom <= safeTop) return null
        return BoundsRect(safeLeft, safeTop, safeRight, safeBottom)
    }
}
