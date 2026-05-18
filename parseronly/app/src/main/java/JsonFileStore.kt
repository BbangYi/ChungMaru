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

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

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

        val normalizedComments = snapshot.comments
            .map { normalizeCommentForSave(it) }
            .filter { it.commentText.isNotBlank() }

        val commentsToSave = when (sourcePackage) {
            YOUTUBE_PACKAGE -> filterNewPlatformComments(
                dir = dir,
                comments = normalizedComments,
                filePrefix = "Youtube_comment",
                logLabel = "youtube",
                requireAuthorId = true,
                dedupeSentenceText = false
            )
            TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE -> filterNewPlatformComments(
                dir = dir,
                comments = normalizedComments,
                filePrefix = "Tiktok_comment",
                logLabel = "tiktok",
                requireAuthorId = false,
                dedupeSentenceText = true
            )
            else -> normalizedComments
        }

        if (commentsToSave.isEmpty()) {
            Log.d(TAG, "skip save: no new comments for $sourcePackage")
            return null
        }

        val normalizedSnapshot = snapshot.copy(comments = commentsToSave)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

        val prefix = when (sourcePackage) {
            INSTAGRAM_PACKAGE -> "Instagram_comment"
            YOUTUBE_PACKAGE -> "Youtube_comment"
            TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE -> "Tiktok_comment"
            else -> "comments"
        }

        val file = File(dir, "${prefix}_$stamp.json")

        file.writeText(gson.toJson(normalizedSnapshot), Charsets.UTF_8)
        Log.d(TAG, "saved file = ${file.absolutePath}")

        return file
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
                val savedSnapshot = gson.fromJson(
                    file.readText(Charsets.UTF_8),
                    ParseSnapshot::class.java
                )
                savedSnapshot?.comments.orEmpty()
                    .map { normalizeCommentForSave(it) }
                    .flatMapTo(keys) { commentDedupKeys(it, requireAuthorId, dedupeSentenceText) }
            }.onFailure {
                Log.w(TAG, "failed to read saved parser json: ${file.name}", it)
            }
        }
        return keys
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
