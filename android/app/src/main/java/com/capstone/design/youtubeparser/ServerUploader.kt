package com.capstone.design.youtubeparser

import android.content.Context
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ServerUploader {

    private const val TAG = "ServerUploader"
    private const val CONNECT_TIMEOUT_MS = 1500
    private const val READ_TIMEOUT_MS = 2500
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    private const val TIKTOK_ALT_PACKAGE = "com.ss.android.ugc.trill"

    fun uploadJsonFile(context: Context, file: File, sourcePackage: String? = null): Boolean {
        return try {
            val boundary = "Boundary-${UUID.randomUUID()}"
            val lineEnd = "\r\n"
            val uploadUrl = UploadEndpointStore.resolveUploadUrl(context)

            val connection = URL(uploadUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.doInput = true
            connection.useCaches = false
            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            DataOutputStream(connection.outputStream).use { output ->
                if (!sourcePackage.isNullOrBlank()) {
                    writeFormField(
                        output,
                        boundary,
                        lineEnd,
                        "platform",
                        platformNameFromPackage(sourcePackage)
                    )
                    writeFormField(output, boundary, lineEnd, "source_package", sourcePackage)
                }

                output.writeBytes("--$boundary$lineEnd")
                output.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"$lineEnd"
                )
                output.writeBytes("Content-Type: application/json$lineEnd")
                output.writeBytes(lineEnd)

                file.inputStream().use { input ->
                    input.copyTo(output)
                }

                output.writeBytes(lineEnd)
                output.writeBytes("--$boundary--$lineEnd")
                output.flush()
            }

            val responseCode = connection.responseCode
            val responseText = try {
                connection.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }

            Log.d(TAG, "uploadUrl=$uploadUrl responseCode=$responseCode response=$responseText")
            responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "upload skipped: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            false
        }
    }

    private fun writeFormField(
        output: DataOutputStream,
        boundary: String,
        lineEnd: String,
        name: String,
        value: String
    ) {
        output.writeBytes("--$boundary$lineEnd")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"$lineEnd")
        output.writeBytes(lineEnd)
        output.write(value.toByteArray(Charsets.UTF_8))
        output.writeBytes(lineEnd)
    }

    private fun platformNameFromPackage(sourcePackage: String): String {
        return when (sourcePackage) {
            YOUTUBE_PACKAGE -> "youtube"
            INSTAGRAM_PACKAGE -> "instagram"
            TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE -> "tiktok"
            else -> "unknown"
        }
    }
}
