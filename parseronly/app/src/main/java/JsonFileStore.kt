package com.example.youtubeparser

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JsonFileStore {

    private const val TAG = "JsonFileStore"
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    private const val TIKTOK_ALT_PACKAGE = "com.ss.android.ugc.trill"
    private const val ROLLING_FILE_MAX_BYTES = 5 * 1024L

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private data class PlatformSaveConfig(
        val platformLabel: String,
        val sourcePackage: String,
        val rollingPrefix: String,
        val legacyPrefix: String,
        val requireAuthorId: Boolean,
        val dedupeSentenceText: Boolean
    )

    fun saveSnapshot(
        context: Context,
        snapshot: ParseSnapshot,
        sourcePackage: String
    ): File? {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "parse_results")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val config = saveConfigFor(sourcePackage)

        val normalizedComments = snapshot.comments
            .map { normalizeCommentForSave(it) }
            .filter { it.commentText.isNotBlank() }

        val platformFilteredComments = when (sourcePackage) {
            YOUTUBE_PACKAGE -> normalizedComments.filter { isSavableYouTubeComment(it) }
            INSTAGRAM_PACKAGE -> normalizedComments.filter { isSavableInstagramComment(it) }
            TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE -> normalizedComments.filter { isSavableTiktokComment(it) }
            else -> normalizedComments
        }

        val commentsToSave = filterNewPlatformComments(
            dir = dir,
            comments = platformFilteredComments,
            filePrefix = config.legacyPrefix,
            logLabel = config.platformLabel.lowercase(Locale.ROOT),
            requireAuthorId = config.requireAuthorId,
            dedupeSentenceText = config.dedupeSentenceText
        )

        if (commentsToSave.isEmpty()) {
            Log.d(TAG, "skip save: no new comments for $sourcePackage")
            return null
        }

        val normalizedSnapshot = snapshot.copy(
            comments = commentsToSave,
            savedCommentCount = commentsToSave.size
        )

        return saveRollingSnapshot(dir, normalizedSnapshot, config)
    }

    private fun saveConfigFor(sourcePackage: String): PlatformSaveConfig {
        return when (sourcePackage) {
            YOUTUBE_PACKAGE -> PlatformSaveConfig(
                platformLabel = "YouTube",
                sourcePackage = sourcePackage,
                rollingPrefix = "Youtube_comment_batch",
                legacyPrefix = "Youtube_comment",
                requireAuthorId = true,
                dedupeSentenceText = false
            )
            INSTAGRAM_PACKAGE -> PlatformSaveConfig(
                platformLabel = "Instagram",
                sourcePackage = sourcePackage,
                rollingPrefix = "Instagram_comment_batch",
                legacyPrefix = "Instagram_comment",
                requireAuthorId = true,
                dedupeSentenceText = true
            )
            TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE -> PlatformSaveConfig(
                platformLabel = "TikTok",
                sourcePackage = sourcePackage,
                rollingPrefix = "Tiktok_comment_batch",
                legacyPrefix = "Tiktok_comment",
                requireAuthorId = false,
                dedupeSentenceText = true
            )
            else -> PlatformSaveConfig(
                platformLabel = "Unknown",
                sourcePackage = sourcePackage,
                rollingPrefix = "comments_batch",
                legacyPrefix = "comments",
                requireAuthorId = false,
                dedupeSentenceText = true
            )
        }
    }

    private fun saveRollingSnapshot(
        dir: File,
        snapshot: ParseSnapshot,
        config: PlatformSaveConfig
    ): File? {
        val now = System.currentTimeMillis()
        val file = findActiveRollingFile(dir, config.rollingPrefix) ?: createRollingFile(dir, config.rollingPrefix, now)
        val current = readRollingFile(file) ?: RollingParseFile(
            platform = config.platformLabel,
            sourcePackage = config.sourcePackage,
            fileStartedAt = now,
            fileUpdatedAt = now,
            maxFileSizeBytes = ROLLING_FILE_MAX_BYTES,
            snapshots = emptyList()
        )

        val updated = current.copy(
            fileUpdatedAt = now,
            snapshots = current.snapshots + snapshot,
            summary = null
        )

        if (gson.toJson(updated).toByteArray(Charsets.UTF_8).size >= ROLLING_FILE_MAX_BYTES) {
            val finalized = updated.copy(
                fileUpdatedAt = now,
                summary = buildRollingSummary(updated.snapshots, now)
            )
            file.writeText(gson.toJson(finalized), Charsets.UTF_8)
            Log.d(TAG, "${config.platformLabel} rolling file finalized = ${file.absolutePath}")
        } else {
            file.writeText(gson.toJson(updated), Charsets.UTF_8)
        }

        Log.d(TAG, "saved ${config.platformLabel} rolling file = ${file.absolutePath}")
        return file
    }

    private fun findActiveRollingFile(dir: File, rollingPrefix: String): File? {
        val files = dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith("${rollingPrefix}_") &&
                file.extension.equals("json", ignoreCase = true)
        } ?: return null

        return files
            .sortedByDescending { it.lastModified() }
            .firstOrNull { file ->
                val rolling = readRollingFile(file) ?: return@firstOrNull false
                rolling.summary == null
            }
    }

    private fun createRollingFile(dir: File, rollingPrefix: String, now: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now))
        return File(dir, "${rollingPrefix}_$stamp.json")
    }

    private fun readRollingFile(file: File): RollingParseFile? {
        return runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), RollingParseFile::class.java)
        }.getOrNull()
    }

    private fun buildRollingSummary(
        snapshots: List<ParseSnapshot>,
        finalizedAt: Long
    ): RollingParseSummary {
        val durations = snapshots.mapNotNull { it.parseDurationMs }
        val speeds = snapshots.mapNotNull { it.commentsPerSecond }
        val totalParsed = snapshots.sumOf { it.parsedCommentCount ?: it.comments.size }
        val totalSaved = snapshots.sumOf { it.savedCommentCount ?: it.comments.size }

        return RollingParseSummary(
            finalizedAt = finalizedAt,
            snapshotCount = snapshots.size,
            totalParsedComments = totalParsed,
            totalSavedComments = totalSaved,
            averageParseDurationMs = durations.averageLongOrZero(),
            averageCommentsPerSecond = speeds.averageDoubleOrZero()
        )
    }

    private fun filterNewPlatformComments(
        dir: File,
        comments: List<ParsedComment>,
        filePrefix: String,
        logLabel: String,
        requireAuthorId: Boolean,
        dedupeSentenceText: Boolean
    ): List<ParsedComment> {
        val savedKeys = readSavedCommentKeys(dir, filePrefix, requireAuthorId, dedupeSentenceText)
        val currentKeys = mutableSetOf<String>()

        val filtered = comments.filter { comment ->
            val keys = commentDedupKeys(comment, requireAuthorId, dedupeSentenceText)
            if (keys.isEmpty()) return@filter true

            if (keys.any { it in savedKeys || it in currentKeys }) {
                false
            } else {
                currentKeys += keys
                true
            }
        }

        val skippedCount = comments.size - filtered.size
        if (skippedCount > 0) {
            Log.d(TAG, "$logLabel duplicate comments skipped = $skippedCount")
        }

        return filtered
    }

    private fun readSavedCommentKeys(
        dir: File,
        filePrefix: String,
        requireAuthorId: Boolean,
        dedupeSentenceText: Boolean
    ): Set<String> {
        val files = dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith("${filePrefix}_") &&
                file.extension.equals("json", ignoreCase = true)
        } ?: return emptySet()

        val keys = mutableSetOf<String>()
        for (file in files) {
            runCatching {
                val content = file.readText(Charsets.UTF_8)
                if (content.contains("\"snapshots\"")) {
                    val rollingFile = gson.fromJson(content, RollingParseFile::class.java)
                    rollingFile?.snapshots.orEmpty()
                        .flatMap { it.comments.orEmpty() }
                        .map { normalizeCommentForSave(it) }
                        .flatMapTo(keys) { commentDedupKeys(it, requireAuthorId, dedupeSentenceText) }
                } else {
                    val savedSnapshot = gson.fromJson(content, ParseSnapshot::class.java)
                    savedSnapshot?.comments.orEmpty()
                        .map { normalizeCommentForSave(it) }
                        .flatMapTo(keys) { commentDedupKeys(it, requireAuthorId, dedupeSentenceText) }
                }
            }.onFailure {
                Log.w(TAG, "failed to read saved parser json: ${file.name}", it)
            }
        }
        return keys
    }

    private fun List<Long>.averageLongOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    private fun List<Double>.averageDoubleOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    private fun commentDedupKeys(
        comment: ParsedComment,
        requireAuthorId: Boolean,
        dedupeSentenceText: Boolean
    ): List<String> {
        val authorId = comment.authorId
            ?.trim()
            ?.removePrefix("@")
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val commentText = comment.commentText
            .trim()
            .replace(Regex("\\s+"), " ")

        if (commentText.isBlank()) return emptyList()

        val keys = mutableListOf<String>()
        if (authorId.isBlank()) {
            if (!requireAuthorId) {
                sentenceTextDedupKey(commentText)?.takeIf { dedupeSentenceText }?.let { keys += it }
                bodyOnlyDedupKey(commentText)?.takeIf { dedupeSentenceText }?.let { keys += it }
            }
        } else {
            keys += "author\n$authorId\n$commentText"
            sentenceTextDedupKey(commentText)?.takeIf { dedupeSentenceText }?.let { keys += it }
        }

        return keys
    }

    private fun sentenceTextDedupKey(commentText: String): String? {
        val text = commentText
            .trim()
            .replace(Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]"""), "")
            .replace(Regex("\\s+"), " ")
        if (!isSentenceLikeDuplicateText(text)) return null
        return "sentence\n${text.lowercase(Locale.ROOT)}"
    }

    private fun bodyOnlyDedupKey(commentText: String): String? {
        val text = commentText
            .trim()
            .replace(Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]"""), "")
            .replace(Regex("\\s+"), " ")
        if (text.length < 2) return null
        if (text.all { !it.isLetterOrDigit() && Character.getType(it) != Character.OTHER_SYMBOL.toInt() }) return null
        return "body\n${text.lowercase(Locale.ROOT)}"
    }

    private fun isSentenceLikeDuplicateText(text: String): Boolean {
        if (text.length < 5) return false

        val letterOrDigitCount = text.count { it.isLetterOrDigit() }
        if (letterOrDigitCount < 4) return false

        if (text.all { !it.isLetterOrDigit() }) return false
        if (Regex("""^[ㅋㅎㅠㅜㅡ\s~!?.…]+$""").matches(text)) return false

        val hasWordGap = text.any { it.isWhitespace() }
        val hasSentencePunctuation = text.any { it in listOf('.', ',', '!', '?', '~', '…') }
        val hasKoreanSentenceSignal = listOf(
            "은", "는", "이", "가", "을", "를", "에", "에서", "한테", "하고",
            "하다", "해", "임", "요", "네", "다", "까", "죠", "듯", "면"
        ).any { text.contains(it) }

        return hasWordGap || hasSentencePunctuation || hasKoreanSentenceSignal
    }

    private fun isSavableInstagramComment(comment: ParsedComment): Boolean {
        val authorId = comment.authorId
            ?.trim()
            ?.removePrefix("@")
            .orEmpty()
        val text = comment.commentText.trim()

        if (authorId.isBlank()) return false
        if (isInstagramMetaAuthor(authorId)) return false
        if (text.isBlank()) return false
        if (looksLikeCountOnlyText(text)) return false
        if (isInstagramNonCommentSaveText(text)) return false
        if (looksLikeInstagramBottomCaption(comment)) return false

        return true
    }

    private fun isInstagramMetaAuthor(authorId: String): Boolean {
        val lower = authorId.trim().lowercase(Locale.ROOT)
        return lower in setOf("작성자", "오디오", "프로필", "댓글", "릴스", "reel", "reels")
    }

    private fun looksLikeCountOnlyText(text: String): Boolean {
        val t = text.trim()
        return Regex("""^\d{1,3}(,\d{3})+$""").matches(t) ||
            Regex("""^\d+(\.\d+)?[kKmM만천]?$""").matches(t)
    }

    private fun isSavableYouTubeComment(comment: ParsedComment): Boolean {
        val authorId = comment.authorId
            ?.trim()
            ?.removePrefix("@")
            .orEmpty()
        val text = comment.commentText.trim()

        if (authorId.isBlank()) return false
        if (text.isBlank()) return false
        if (looksLikeCountOnlyText(text)) return false
        if (isYouTubeNonCommentSaveText(text)) return false
        if (looksLikeYouTubeShortsSurfaceText(comment)) return false

        return true
    }

    private fun isYouTubeNonCommentSaveText(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
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
            lower.startsWith("view ") && lower.contains("replies")
    }

    private fun isSavableTiktokComment(comment: ParsedComment): Boolean {
        val text = comment.commentText.trim()

        if (text.isBlank()) return false
        if (looksLikeCountOnlyText(text)) return false
        if (isTiktokNonCommentSaveText(text)) return false
        if (looksLikeTiktokSurfaceCaption(comment)) return false

        return true
    }

    private fun isTiktokNonCommentSaveText(text: String): Boolean {
        val lower = text.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]"""), "")
            .trim()
        val compact = lower.replace(Regex("\\s+"), "")

        if (compact in setOf(
                "now",
                "justnow",
                "방금",
                "방금전",
                "오늘",
                "어제",
                "검색",
                "공유",
                "좋아요",
                "댓글",
                "답글",
                "팔로우",
                "프로필",
                "동영상",
                "스티커",
                "알림",
                "작성자",
                "사진",
                "게시물",
                "첫댓글",
                "편집효과",
                "음악",
                "숨겨짐",
                "ai생성미디어포함"
            )
        ) return true

        return lower == "크리에이터가 댓글 액세스를 제한했습니다." ||
            lower == "· 효과 사용" ||
            lower.startsWith("검색 ·") ||
            lower.startsWith("검색·") ||
            lower.startsWith("검색:") ||
            lower.startsWith("search ·") ||
            Regex("""^@\d{5,}$""").matches(lower) ||
            Regex("""^\d{1,2}-\d{1,2}$""").matches(lower) ||
            Regex("""^\d{1,2}:\d{2}$""").matches(lower) ||
            Regex("""^\d+(\.\d+)?[smhdw]$""").matches(lower) ||
            Regex("""^\d+(\.\d+)?(초|분|시간|일|주|개월|달|년)(전)?$""").matches(compact) ||
            Regex("""^댓글\s*[\d,]+개$""").matches(lower) ||
            Regex("""^협업자\s*[\d,]+명$""").matches(lower) ||
            Regex("""^게시물\s*[\d,.]+[km천만]?개$""").matches(lower) ||
            lower.contains("효과 사용") ||
            lower.contains("말 한마디 해주세요") ||
            lower.contains("크리에이터가 댓글 액세스를 제한했습니다") ||
            lower.contains("님이 게시한 동영상이 여기에 나타납니다")
    }

    private fun looksLikeTiktokSurfaceCaption(comment: ParsedComment): Boolean {
        val text = comment.commentText.trim()
        val bounds = comment.boundsInScreen
        val lower = text.lowercase(Locale.ROOT)

        val bottomLeftSurface =
            bounds.left <= 80 &&
                bounds.top >= 1450 &&
                bounds.right <= 650

        val externalOverlay =
            bounds.left <= 80 &&
                bounds.top >= 1450 &&
                bounds.bottom - bounds.top >= 180

        return bottomLeftSurface ||
            externalOverlay ||
            lower == "yt music" ||
            lower.contains("#fyp") ||
            (text.startsWith("#") && text.count { it == '#' } >= 1)
    }

    private fun looksLikeYouTubeShortsSurfaceText(comment: ParsedComment): Boolean {
        val text = comment.commentText.trim()
        val bounds = comment.boundsInScreen
        val lower = text.lowercase(Locale.ROOT)

        val bottomLeftShortsCaption =
            bounds.left <= 90 &&
                bounds.top >= 1450 &&
                bounds.right < 900

        val bottomAudioTitle =
            bounds.left <= 90 &&
                bounds.top >= 1450 &&
                text.contains(" · ")

        return bottomLeftShortsCaption ||
            bottomAudioTitle ||
            lower.endsWith(" #shorts") ||
            lower.contains("#shortsfeed")
    }

    private fun isInstagramNonCommentSaveText(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        return lower == "답글" ||
            lower == "답글 달기" ||
            lower == "좋아요" ||
            lower == "댓글" ||
            lower == "댓글 달기" ||
            lower.contains("님의 스토리") ||
            lower.contains("읽지 않은 스토리") ||
            lower.contains("게시했습니다") ||
            lower.contains("태그했습니다") ||
            lower.contains("님에게 댓글 추가") ||
            lower.contains("님이 만든 릴스입니다") ||
            lower.contains("재생하거나 일시 중지하려면") ||
            lower.contains("번역 보기") ||
            lower.contains("see translation") ||
            (lower.contains("님의 사진") && lower.contains("좋아요") && lower.contains("댓글")) ||
            (lower.contains("님의 동영상") && lower.contains("좋아요") && lower.contains("댓글")) ||
            (lower.contains("님의 carousel") && lower.contains("좋아요") && lower.contains("댓글")) ||
            lower.contains("photo을(를) 게시") ||
            lower.contains("video을(를) 게시") ||
            lower.contains("carousel을(를) 게시")
    }

    private fun looksLikeInstagramBottomCaption(comment: ParsedComment): Boolean {
        val text = comment.commentText.trim()
        if (!text.endsWith("…")) return false

        val bounds = comment.boundsInScreen
        val knownCaptionColumn = bounds.left in 140..190 && bounds.right in 900..1100
        val broadBottomCaption = bounds.left < 220 && bounds.right > 850 && bounds.top >= 1300
        return knownCaptionColumn || broadBottomCaption
    }

    private fun normalizeCommentForSave(comment: ParsedComment): ParsedComment {
        val original = comment.commentText.trim()

        val fullPattern = Regex("""^(.+?)님이\s*(.*?)\s*댓글을 달았습니다$""")
        val match = fullPattern.find(original)

        if (match != null) {
            val authorId = match.groupValues[1].trim()
            val cleanedComment = match.groupValues[2].trim()

            return comment.copy(
                commentText = cleanedComment,
                authorId = authorId.ifBlank { null }
            )
        }

        if (original.endsWith("댓글을 달았습니다")) {
            val cleaned = original.removeSuffix("댓글을 달았습니다").trim()
            return comment.copy(commentText = cleaned)
        }

        return comment
    }
}
