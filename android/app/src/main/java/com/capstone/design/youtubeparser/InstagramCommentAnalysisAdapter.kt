package com.capstone.design.youtubeparser

internal const val INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX =
    "android-accessibility-comment:instagram:"

data class InstagramSafeComment(
    val key: String,
    val author: String,
    val text: String,
    val isReply: Boolean
)

data class InstagramSafeCommentBatch(
    val rawLineCount: Int,
    val safeComments: List<InstagramSafeComment>,
    val harmfulCommentCount: Int,
    val harmfulKeys: Set<String> = emptySet(),
    val harmfulTexts: Set<String> = emptySet()
)

object InstagramCommentAnalysisAdapter {
    fun adapt(comments: List<ParsedComment>): List<ParsedComment> {
        return comments
            .asSequence()
            .filter { comment ->
                InstagramCommentUiTextPolicy.isDisplayable(comment.commentText)
            }
            .mapNotNull { comment ->
                val author = parseAuthor(comment.authorId) ?: return@mapNotNull null
                comment.copy(
                    authorId = INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX + author
                )
            }
            .distinctBy { comment ->
                "${comment.authorId.orEmpty()}|${normalize(comment.commentText).lowercase()}"
            }
            .toList()
    }

    private fun parseAuthor(value: String?): String? {
        val source = value.orEmpty().trim()
        val candidate = if (source.startsWith(INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX)) {
            source.removePrefix(INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX)
                .substringBefore(":line:")
        } else {
            source
        }.trim().removePrefix("@")
        return candidate.takeIf(InstagramAuthorPolicy::isValid)
    }

    private fun normalize(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()
}

internal object InstagramAuthorPolicy {
    private val username = Regex("^[A-Za-z0-9._]{1,64}$")

    fun isValid(value: String): Boolean = username.matches(value)
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
        "posting comment",
        "작성자"
    )

    fun isDisplayable(value: String): Boolean {
        val text = value.replace(Regex("\\s+"), " ").trim()
        if (text.length < 2) return false
        val lower = text.lowercase()
        if (Regex("^[\\d,.]+$").matches(text)) return false
        if (lower in exactUiTexts) return false
        if (Regex("^답글\\s*[\\d,.]+개?\\s*더\\s*보기$").matches(lower)) return false
        if (lower.endsWith("댓글을 달았습니다")) return false
        if (lower.startsWith("좋아요 ") && lower.contains("개")) return false
        if (lower.startsWith("replying to @")) return false
        if (lower.startsWith("reply to @")) return false
        if (lower.startsWith("@") && lower.endsWith("\uB2D8\uC5D0\uAC8C \uB2F5\uAE00 \uB0A8\uAE30\uB294 \uC911")) {
            return false
        }
        return true
    }
}

object InstagramSafeCommentAssembler {
    private const val ACCESSIBILITY_LOOKAHEAD_PREFIX =
        "android-accessibility-lookahead:"
    private val whitespace = Regex("\\s+")
    private val trailingExpansionLabel =
        Regex("\\s+(?:read more|more|\uB354\uBCF4\uAE30)$", RegexOption.IGNORE_CASE)

    fun assembleAccessibilityResults(
        results: List<AndroidAnalysisResultItem>
    ): InstagramSafeCommentBatch {
        val candidates = results.mapNotNull(::toCandidate)
        if (candidates.isEmpty()) {
            return InstagramSafeCommentBatch(
                rawLineCount = 0,
                safeComments = emptyList(),
                harmfulCommentCount = 0
            )
        }

        val primaryCandidates = candidates
            .filterNot { candidate -> candidate.source.contains(":line:") }
            .ifEmpty { candidates }
        val minimumLeft = primaryCandidates.minOfOrNull { candidate ->
            candidate.result.boundsInScreen.left
        } ?: 0
        val harmfulKeys = linkedSetOf<String>()
        val harmfulTexts = linkedSetOf<String>()

        candidates.filter { candidate -> candidate.result.isOffensive }
            .forEach { candidate ->
                harmfulKeys += buildKey(candidate.author, candidate.text)
                harmfulTexts += candidate.text
            }

        val safeComments = LinkedHashMap<String, InstagramSafeComment>()
        primaryCandidates.forEach { candidate ->
            if (candidate.result.isOffensive) return@forEach
            val key = buildKey(candidate.author, candidate.text)
            safeComments.putIfAbsent(
                key,
                InstagramSafeComment(
                    key = key,
                    author = candidate.author,
                    text = candidate.text,
                    isReply = candidate.result.boundsInScreen.left >= minimumLeft + 24 ||
                        candidate.text.startsWith("@")
                )
            )
        }

        return InstagramSafeCommentBatch(
            rawLineCount = primaryCandidates.size,
            safeComments = safeComments.values.toList(),
            harmfulCommentCount = candidates
                .filter { candidate -> candidate.result.isOffensive }
                .distinctBy { candidate -> buildKey(candidate.author, candidate.text) }
                .size,
            harmfulKeys = harmfulKeys,
            harmfulTexts = harmfulTexts
        )
    }

    private fun toCandidate(result: AndroidAnalysisResultItem): Candidate? {
        val source = result.authorId.orEmpty()
            .removePrefix(ACCESSIBILITY_LOOKAHEAD_PREFIX)
            .trim()
        if (!source.startsWith(INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX)) return null
        val author = source.removePrefix(INSTAGRAM_COMMENT_AUTHOR_SOURCE_PREFIX)
            .substringBefore(":line:")
            .trim()
            .removePrefix("@")
            .takeIf(InstagramAuthorPolicy::isValid)
            ?: return null
        val text = normalize(result.original)
            .replace(trailingExpansionLabel, "")
            .trim()
        if (!InstagramCommentUiTextPolicy.isDisplayable(text)) return null
        return Candidate(
            result = result,
            source = source,
            author = author,
            text = text
        )
    }

    private fun buildKey(author: String, text: String): String =
        "${author.lowercase()}|${text.lowercase()}"

    private fun normalize(value: String): String =
        value.replace(whitespace, " ").trim()

    private data class Candidate(
        val result: AndroidAnalysisResultItem,
        val source: String,
        val author: String,
        val text: String
    )
}

class InstagramSafeCommentBuffer(
    private val initialSafeTarget: Int = 10,
    private val maxInitialRawLines: Int = 48,
    private val maxStoredComments: Int = 120
) {
    companion object {
        private const val MIN_CONTAINED_HARMFUL_LENGTH = 5
        private const val MIN_FUZZY_LENGTH = 10
        private const val HARMFUL_DICE_THRESHOLD = 0.52f
        private const val DUPLICATE_DICE_THRESHOLD = 0.72f
        private const val NGRAM_SIZE = 3
    }

    private val commentsByKey = LinkedHashMap<String, InstagramSafeComment>()
    private val blockedKeys = linkedSetOf<String>()
    private val blockedTextFingerprints = linkedSetOf<String>()

    var rawLineCount: Int = 0
        private set

    var harmfulCommentCount: Int = 0
        private set

    fun add(batch: InstagramSafeCommentBatch): Int {
        rawLineCount += batch.rawLineCount
        harmfulCommentCount += batch.harmfulCommentCount
        blockedKeys += batch.harmfulKeys
        blockedTextFingerprints += batch.harmfulTexts
            .map(::textFingerprint)
            .filter { fingerprint -> fingerprint.length >= MIN_CONTAINED_HARMFUL_LENGTH }
        commentsByKey.entries.removeAll { (key, comment) ->
            key in blockedKeys || isBlockedText(comment.text)
        }

        var added = 0
        batch.safeComments.forEach { comment ->
            val duplicate = commentsByKey.values.any { existing ->
                existing.author.equals(comment.author, ignoreCase = true) &&
                    isNearDuplicateText(existing.text, comment.text)
            }
            if (
                commentsByKey.size < maxStoredComments &&
                comment.key !in blockedKeys &&
                !isBlockedText(comment.text) &&
                !duplicate &&
                commentsByKey.putIfAbsent(comment.key, comment) == null
            ) {
                added += 1
            }
        }
        return added
    }

    fun comments(): List<InstagramSafeComment> = commentsByKey.values.toList()

    fun shouldFinishInitialCollection(
        capturedViewports: Int,
        forwardSteps: Int,
        maxForwardSteps: Int
    ): Boolean {
        if (forwardSteps >= maxForwardSteps) return true
        if (rawLineCount >= maxInitialRawLines) return true
        return capturedViewports >= 2 && commentsByKey.size >= initialSafeTarget
    }

    fun clear() {
        commentsByKey.clear()
        blockedKeys.clear()
        blockedTextFingerprints.clear()
        rawLineCount = 0
        harmfulCommentCount = 0
    }

    private fun isBlockedText(value: String): Boolean {
        val candidate = textFingerprint(value)
        if (candidate.length < MIN_CONTAINED_HARMFUL_LENGTH) return false
        return blockedTextFingerprints.any { harmful ->
            candidate.contains(harmful) ||
                (candidate.length >= 8 && harmful.contains(candidate)) ||
                (
                    minOf(candidate.length, harmful.length) >= MIN_FUZZY_LENGTH &&
                        ngramDice(candidate, harmful) >= HARMFUL_DICE_THRESHOLD
                    )
        }
    }

    private fun isNearDuplicateText(first: String, second: String): Boolean {
        val firstFingerprint = textFingerprint(first)
        val secondFingerprint = textFingerprint(second)
        if (firstFingerprint == secondFingerprint) return true
        if (minOf(firstFingerprint.length, secondFingerprint.length) < MIN_FUZZY_LENGTH) {
            return false
        }
        if (
            firstFingerprint.contains(secondFingerprint) ||
            secondFingerprint.contains(firstFingerprint)
        ) {
            return true
        }
        return ngramDice(firstFingerprint, secondFingerprint) >= DUPLICATE_DICE_THRESHOLD
    }

    private fun textFingerprint(value: String): String =
        value.lowercase().filter { character -> character.isLetterOrDigit() }

    private fun ngramDice(first: String, second: String): Float {
        val firstGrams = first.windowed(NGRAM_SIZE).toSet()
        val secondGrams = second.windowed(NGRAM_SIZE).toSet()
        if (firstGrams.isEmpty() || secondGrams.isEmpty()) return 0f
        val intersection = firstGrams.count { gram -> gram in secondGrams }
        return (2f * intersection) / (firstGrams.size + secondGrams.size)
    }
}
