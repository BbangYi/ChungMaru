package com.capstone.design.youtubeparser

data class YoutubeSafeComment(
    val key: String,
    val author: String,
    val metadata: String,
    val text: String
)

data class YoutubeSafeCommentBatch(
    val rawLineCount: Int,
    val safeComments: List<YoutubeSafeComment>,
    val harmfulCommentCount: Int,
    val harmfulAuthors: Set<String> = emptySet(),
    val harmfulKeys: Set<String> = emptySet(),
    val harmfulTexts: Set<String> = emptySet()
)

object YoutubeSafeCommentAssembler {
    private const val UNKNOWN_AUTHOR = "@youtube"
    private const val YOUTUBE_ACCESSIBILITY_SOURCE = "android-accessibility-comment:youtube"
    private const val ACCESSIBILITY_LOOKAHEAD_PREFIX = "android-accessibility-lookahead:"
    private val whitespace = Regex("\\s+")
    private val trailingExpansionLabel =
        Regex("\\s+(?:read more|더보기)$", RegexOption.IGNORE_CASE)
    private val replyCountLabel =
        Regex("^\\d+\\s*(?:repl(?:y|ies)|답글)\\s*[>›]?$", RegexOption.IGNORE_CASE)
    private val commentCountActionLabel =
        Regex("^(?:댓글\\s*\\d+\\s*개\\s*보기|view\\s+\\d+\\s+comments?)$", RegexOption.IGNORE_CASE)
    private val koreanPlaybackProgressLabel =
        Regex("^\\d+\\s*분(?:\\s*\\d+\\s*초)?\\s*중\\s*\\d+\\s*분(?:\\s*\\d+\\s*초)?$")
    private val clockPlaybackProgressLabel =
        Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?\\s*/\\s*\\d{1,2}:\\d{2}(?::\\d{2})?$")
    private val uiLabels = setOf(
        "검열중",
        "검열 중",
        "loading",
        "드래그 핸들",
        "댓글 정보",
        "좋아요 취소",
        "댓글 싫어요 표시",
        "작업 메뉴",
        "한국어로 번역",
        "[music]",
        "[음악]",
        "답글",
        "reply",
        "learn more",
        "read more",
        "top",
        "newest",
        "top comments",
        "newest first",
        "close",
        "about comments",
        "drag handle",
        "comment",
        "comment...",
        "comments",
        "좋아요",
        "싫어요",
        "더보기",
        "댓글"
    )
    private val youtubeControlLabels = setOf(
        "동영상 일시중지",
        "동영상 일지중지",
        "동영상 재생",
        "다음 동영상",
        "이전 동영상",
        "동영상 공유",
        "리믹스",
        "pause video",
        "play video",
        "next video",
        "previous video",
        "share video",
        "remix"
    )

    fun assembleAccessibilityResults(
        results: List<AndroidAnalysisResultItem>
    ): YoutubeSafeCommentBatch {
        return assemblePlatformAccessibilityResults(
            results = results,
            accessibilitySource = YOUTUBE_ACCESSIBILITY_SOURCE,
            unknownAuthor = UNKNOWN_AUTHOR
        )
    }

    internal fun isYoutubeAccessibilitySource(authorId: String?): Boolean {
        return isAccessibilitySource(
            source = normalizedAccessibilitySource(authorId),
            accessibilitySource = YOUTUBE_ACCESSIBILITY_SOURCE
        )
    }

    internal fun youtubeAuthorLabel(authorId: String?): String? {
        val source = accessibilityResultIdentity(authorId)
        if (!isAccessibilitySource(source, YOUTUBE_ACCESSIBILITY_SOURCE)) return null
        val author = source
            .removePrefix(YOUTUBE_ACCESSIBILITY_SOURCE)
            .trimStart(':')
            .trim()
            .removePrefix("@")
        return author.takeIf { value -> value.isNotBlank() }?.let { value -> "@$value" }
    }

    internal fun assemblePlatformAccessibilityResults(
        results: List<AndroidAnalysisResultItem>,
        accessibilitySource: String,
        unknownAuthor: String
    ): YoutubeSafeCommentBatch {
        val accessibilityResults = results.filter { result ->
            isAccessibilitySource(
                source = normalizedAccessibilitySource(result.authorId),
                accessibilitySource = accessibilitySource
            )
        }
        if (accessibilityResults.isEmpty()) {
            return YoutubeSafeCommentBatch(
                rawLineCount = 0,
                safeComments = emptyList(),
                harmfulCommentCount = 0
            )
        }

        val displayResults = accessibilityResults
            .groupByTo(LinkedHashMap()) { result ->
                accessibilityResultIdentity(result.authorId)
            }
            .values
            .flatMap { identityResults ->
                identityResults
                    .filterNot { result -> isAccessibilityLineResult(result.authorId) }
                    .ifEmpty { identityResults }
            }
            .filter { result ->
                val text = normalize(result.original)
                    .replace(trailingExpansionLabel, "")
                    .trim()
                isUsefulLine(text)
            }
        val harmfulAuthors = linkedSetOf<String>()
        val harmfulKeys = linkedSetOf<String>()
        val harmfulTexts = linkedSetOf<String>()
        val harmfulIdentities = linkedSetOf<String>()

        accessibilityResults
            .asSequence()
            .filter { result -> result.isOffensive }
            .forEach { result ->
                val text = normalize(result.original)
                    .replace(trailingExpansionLabel, "")
                    .trim()
                if (!isUsefulLine(text)) return@forEach

                val author = parseAccessibilityAuthor(result.authorId, accessibilitySource, unknownAuthor)
                harmfulKeys += buildKey(author, text)
                harmfulTexts += text
                harmfulIdentities += "${author.lowercase()}|${text.lowercase()}"
                if (author != unknownAuthor) {
                    harmfulAuthors += author.lowercase()
                }
            }

        val distinctSafe = LinkedHashMap<String, YoutubeSafeComment>()
        displayResults.forEach { result ->
            val text = normalize(result.original)
                .replace(trailingExpansionLabel, "")
                .trim()
            if (!isUsefulLine(text) || result.isOffensive) return@forEach

            val author = parseAccessibilityAuthor(result.authorId, accessibilitySource, unknownAuthor)
            val key = buildKey(author, text)
            distinctSafe.putIfAbsent(
                key,
                YoutubeSafeComment(
                    key = key,
                    author = author,
                    metadata = "",
                    text = text
                )
            )
        }

        return YoutubeSafeCommentBatch(
            rawLineCount = displayResults.size,
            safeComments = distinctSafe.values.toList(),
            harmfulCommentCount = harmfulIdentities.size,
            harmfulAuthors = harmfulAuthors,
            harmfulKeys = harmfulKeys,
            harmfulTexts = harmfulTexts
        )
    }

    private fun normalizedAccessibilitySource(authorId: String?): String {
        return authorId.orEmpty()
            .removePrefix(ACCESSIBILITY_LOOKAHEAD_PREFIX)
            .trim()
    }

    private fun accessibilityResultIdentity(authorId: String?): String {
        return normalizedAccessibilitySource(authorId).substringBefore(":line:")
    }

    private fun isAccessibilityLineResult(authorId: String?): Boolean {
        return normalizedAccessibilitySource(authorId).contains(":line:")
    }

    private fun isAccessibilitySource(source: String, accessibilitySource: String): Boolean {
        return source == accessibilitySource || source.startsWith("$accessibilitySource:")
    }

    private fun parseAccessibilityAuthor(
        authorId: String?,
        accessibilitySource: String,
        unknownAuthor: String
    ): String {
        val source = accessibilityResultIdentity(authorId)
        val suffix = source
            .removePrefix(accessibilitySource)
            .trimStart(':')
        val author = suffix
            .trim()
            .removePrefix("@")
            .takeIf { value -> value.length in 1..95 }
            ?: return unknownAuthor
        return "@$author"
    }
    private fun isUsefulLine(text: String): Boolean {
        if (text.length < 2) return false
        val lowercase = text.lowercase()
        if (lowercase in uiLabels) return false
        if (lowercase in youtubeControlLabels) return false
        if (replyCountLabel.matches(text)) return false
        if (commentCountActionLabel.matches(text)) return false
        if (koreanPlaybackProgressLabel.matches(text)) return false
        if (clockPlaybackProgressLabel.matches(text)) return false
        if (Regex("^좋아요\\s*\\d+개$").matches(text)) return false
        if (lowercase.contains("이 댓글을 좋아함")) return false
        if (lowercase.contains("이 동영상에 좋아요 표시")) return false
        if (lowercase.startsWith("liked by ") && lowercase.contains(" this video")) return false
        if (lowercase.startsWith("이 사운드를 사용하는 동영상")) return false
        if (lowercase.startsWith("videos using this sound")) return false
        if (lowercase.startsWith("구독:") || lowercase.startsWith("subscribe:")) return false
        if (lowercase.startsWith("pinned by @")) return false
        if (lowercase.startsWith("고정한 댓글") || lowercase.startsWith("고정됨")) return false
        if (lowercase.startsWith("remember to keep comments respectful")) return false
        if (lowercase.startsWith("community guidelines")) return false
        if (text.all { character -> character.isDigit() || character.isWhitespace() }) return false
        return true
    }

    private fun buildKey(author: String, text: String): String {
        return "${author.lowercase()}|${text.lowercase()}"
    }

    private fun normalize(value: String): String {
        return value.replace(whitespace, " ").trim()
    }

}

class YoutubeSafeCommentBuffer(
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

    private val commentsByKey = LinkedHashMap<String, YoutubeSafeComment>()
    private val blockedAuthors = linkedSetOf<String>()
    private val blockedKeys = linkedSetOf<String>()
    private val blockedTextFingerprints = linkedSetOf<String>()

    var rawLineCount: Int = 0
        private set

    var harmfulCommentCount: Int = 0
        private set

    fun add(batch: YoutubeSafeCommentBatch): Int {
        rawLineCount += batch.rawLineCount
        harmfulCommentCount += batch.harmfulCommentCount
        blockedAuthors += batch.harmfulAuthors.map { author -> author.lowercase() }
        blockedKeys += batch.harmfulKeys
        blockedTextFingerprints += batch.harmfulTexts
            .map(::textFingerprint)
            .filter { fingerprint -> fingerprint.length >= MIN_CONTAINED_HARMFUL_LENGTH }
        commentsByKey.entries.removeAll { (key, comment) ->
            key in blockedKeys ||
                comment.author.lowercase() in blockedAuthors ||
                isBlockedText(comment.text)
        }
        var added = 0
        batch.safeComments.forEach { comment ->
            if (
                commentsByKey.size < maxStoredComments &&
                comment.key !in blockedKeys &&
                comment.author.lowercase() !in blockedAuthors &&
                !isBlockedText(comment.text) &&
                commentsByKey.values.none { existing ->
                    isNearDuplicateText(existing.text, comment.text)
                } &&
                commentsByKey.putIfAbsent(comment.key, comment) == null
            ) {
                added += 1
            }
        }
        return added
    }

    fun comments(): List<YoutubeSafeComment> = commentsByKey.values.toList()

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

    private fun textFingerprint(value: String): String {
        return value.lowercase().filter { character -> character.isLetterOrDigit() }
    }

    private fun ngramDice(first: String, second: String): Float {
        val firstGrams = first.windowed(NGRAM_SIZE).toSet()
        val secondGrams = second.windowed(NGRAM_SIZE).toSet()
        if (firstGrams.isEmpty() || secondGrams.isEmpty()) return 0f
        val intersection = firstGrams.count { gram -> gram in secondGrams }
        return (2f * intersection) / (firstGrams.size + secondGrams.size)
    }

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
        blockedAuthors.clear()
        blockedKeys.clear()
        blockedTextFingerprints.clear()
        rawLineCount = 0
        harmfulCommentCount = 0
    }
}
