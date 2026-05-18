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
            YOUTUBE_PACKAGE -> filterNewYoutubeComments(dir, normalizedComments)
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

    private fun filterNewYoutubeComments(
        dir: File,
        comments: List<ParsedComment>
    ): List<ParsedComment> {
        val savedKeys = readSavedYoutubeCommentKeys(dir)
        val currentKeys = mutableSetOf<String>()

        val filtered = comments.filter { comment ->
            val key = youtubeDedupKey(comment) ?: return@filter true
            if (key in savedKeys || key in currentKeys) {
                false
            } else {
                currentKeys += key
                true
            }
        }

        val skippedCount = comments.size - filtered.size
        if (skippedCount > 0) {
            Log.d(TAG, "youtube duplicate comments skipped = $skippedCount")
        }

        return filtered
    }

    private fun readSavedYoutubeCommentKeys(dir: File): Set<String> {
        val files = dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith("Youtube_comment_") &&
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
                    .mapNotNullTo(keys) { youtubeDedupKey(it) }
            }.onFailure {
                Log.w(TAG, "failed to read saved youtube json: ${file.name}", it)
            }
        }
        return keys
    }

    private fun youtubeDedupKey(comment: ParsedComment): String? {
        val authorId = comment.authorId
            ?.trim()
            ?.removePrefix("@")
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val commentText = comment.commentText
            .trim()
            .replace(Regex("\\s+"), " ")

        if (authorId.isBlank() || commentText.isBlank()) return null

        return "$authorId\n$commentText"
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
