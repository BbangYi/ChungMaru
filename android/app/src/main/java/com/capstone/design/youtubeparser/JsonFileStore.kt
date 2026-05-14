package com.capstone.design.youtubeparser

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File

object JsonFileStore {

    private const val TAG = "JsonFileStore"
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    private const val TIKTOK_ALT_PACKAGE = "com.ss.android.ugc.trill"
    private const val MAX_JSONL_BYTES = 5_000_000L
    private const val MAX_JSONL_RETAINED_LINES = 2_000
    private const val MAX_LEGACY_JSON_FILES_PER_DIR = 20
    private val legacyTimestampedJsonPattern = Regex(""".+_\d{8}_\d{6}_\d{3}\.json""")

    private val prettyGson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    private val lineGson = GsonBuilder().create()

    fun saveSnapshot(
        context: Context,
        snapshot: ParseSnapshot,
        sourcePackage: String
    ): File {
        val normalizedSnapshot = snapshot.copy(
            comments = snapshot.comments
                .map { normalizeCommentForSave(it) }
                .filter { it.commentText.isNotBlank() }
        )

        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val prefix = platformPrefix(sourcePackage, suffix = "comments")

        val archiveFile = File(File(baseDir, "parse_results"), "$prefix.jsonl")
        appendJsonLine(archiveFile, normalizedSnapshot)
        pruneLegacyTimestampedJsonFiles(archiveFile.parentFile)
        Log.d(TAG, "appended snapshot jsonl = ${archiveFile.absolutePath}")

        val uploadFile = File(File(baseDir, "upload_cache"), "${prefix}_latest.json")
        writeJsonFile(uploadFile, normalizedSnapshot)
        Log.d(TAG, "saved latest upload file = ${uploadFile.absolutePath}")

        return uploadFile
    }

    fun saveAnalysisResponse(
        context: Context,
        response: AndroidAnalysisResponse,
        sourcePackage: String
    ): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val prefix = platformPrefix(sourcePackage, suffix = "analysis")
        val file = File(File(baseDir, "analysis_results"), "$prefix.jsonl")

        appendJsonLine(file, response)
        pruneLegacyTimestampedJsonFiles(file.parentFile)
        Log.d(TAG, "appended analysis jsonl = ${file.absolutePath}")
        return file
    }

    private fun platformPrefix(sourcePackage: String, suffix: String): String {
        return when (sourcePackage) {
            YOUTUBE_PACKAGE -> "youtube_$suffix"
            INSTAGRAM_PACKAGE -> "instagram_$suffix"
            TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE -> "tiktok_$suffix"
            else -> suffix
        }
    }

    private fun appendJsonLine(file: File, value: Any) {
        file.parentFile?.mkdirs()
        file.appendText(lineGson.toJson(value) + "\n", Charsets.UTF_8)
        trimJsonlFileIfNeeded(file)
    }

    private fun writeJsonFile(file: File, value: Any) {
        file.parentFile?.mkdirs()
        file.writeText(prettyGson.toJson(value), Charsets.UTF_8)
    }

    private fun trimJsonlFileIfNeeded(file: File) {
        if (file.length() <= MAX_JSONL_BYTES) return

        val retainedLines = file.readLines(Charsets.UTF_8).takeLast(MAX_JSONL_RETAINED_LINES)
        file.writeText(retainedLines.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
    }

    private fun pruneLegacyTimestampedJsonFiles(dir: File?) {
        val legacyFiles = dir
            ?.listFiles { file ->
                file.isFile && legacyTimestampedJsonPattern.matches(file.name)
            }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(MAX_LEGACY_JSON_FILES_PER_DIR)

        legacyFiles.forEach { file ->
            if (!file.delete()) {
                Log.d(TAG, "failed to prune legacy json = ${file.absolutePath}")
            }
        }
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
            return comment.copy(
                commentText = original.removeSuffix("댓글을 달았습니다").trim()
            )
        }

        return comment
    }
}
