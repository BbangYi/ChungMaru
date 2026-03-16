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

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    fun saveSnapshot(context: Context, snapshot: ParseSnapshot): File {
        val dir = File(context.filesDir, "parse_results")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "youtube_comments_$stamp.json")

        file.writeText(gson.toJson(snapshot), Charsets.UTF_8)
        Log.d(TAG, "saved file = ${file.absolutePath}")

        return file
    }
}