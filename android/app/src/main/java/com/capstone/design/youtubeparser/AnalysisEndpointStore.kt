package com.capstone.design.youtubeparser

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import java.net.URL

object AnalysisEndpointStore {

    private const val PREFS_NAME = "youtube_parser_settings"
    private const val KEY_ANALYSIS_INPUT = "analysis_input"
    private const val DEFAULT_EMULATOR_ANALYSIS_HOST = "10.0.2.2:8000"
    private const val DEFAULT_DEVICE_ANALYSIS_HOST = "127.0.0.1:8000"
    private const val REMOTE_DEVICE_ANALYSIS_HOST = "100.95.209.72:8000"
    private const val LEGACY_DEFAULT_ANALYSIS_HOST = REMOTE_DEVICE_ANALYSIS_HOST
    private const val LEGACY_DEFAULT_ANALYSIS_HOST_BARE = "100.95.209.72"
    private const val DEFAULT_ANALYSIS_PATH = "/analyze_android"
    private const val PRIMARY_ANALYSIS_PORT = 8000
    private const val ALTERNATE_ANALYSIS_PORT = 8010

    fun getRawInput(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ANALYSIS_INPUT, null)?.trim().orEmpty()
        if (stored.isBlank()) {
            return defaultHostForRuntime()
        }

        return if (
            stored == LEGACY_DEFAULT_ANALYSIS_HOST ||
            stored == LEGACY_DEFAULT_ANALYSIS_HOST_BARE
        ) {
            defaultHostForRuntime()
        } else {
            stored
        }
    }

    fun saveRawInput(context: Context, value: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_ANALYSIS_INPUT, value.trim())
        }
    }

    fun resolveAnalyzeUrl(context: Context): String {
        return resolveAnalyzeUrl(getRawInput(context))
    }

    fun resolveAnalyzeUrls(context: Context): List<String> {
        return buildAnalyzeUrlCandidates(
            rawInput = getRawInput(context),
            emulator = isLikelyEmulator()
        )
    }

    fun resolveAnalyzeUrl(rawInput: String): String {
        val raw = rawInput.trim().ifBlank { DEFAULT_EMULATOR_ANALYSIS_HOST }

        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return appendDefaultPathIfNeeded(raw)
        }

        val normalized = raw.trimEnd('/')
        val baseUrl = if (normalized.contains(":")) {
            "http://$normalized"
        } else {
            "http://$normalized:8000"
        }

        return appendDefaultPathIfNeeded(baseUrl)
    }

    internal fun buildAnalyzeUrlCandidates(
        rawInput: String,
        emulator: Boolean
    ): List<String> {
        val primaryUrl = resolveAnalyzeUrl(rawInput)
        val parsed = runCatching { URL(primaryUrl) }.getOrNull()
            ?: return listOf(primaryUrl)
        val primaryPort = parsed.port.takeIf { it > 0 } ?: parsed.defaultPort
        val candidatePorts = linkedSetOf(primaryPort)
        when (primaryPort) {
            PRIMARY_ANALYSIS_PORT -> candidatePorts += ALTERNATE_ANALYSIS_PORT
            ALTERNATE_ANALYSIS_PORT -> candidatePorts += PRIMARY_ANALYSIS_PORT
        }

        val candidates = linkedSetOf(primaryUrl)
        fun addHost(host: String) {
            candidatePorts.forEach { port ->
                if (port <= 0) return@forEach
                candidates += URL(
                    parsed.protocol,
                    host,
                    port,
                    parsed.file
                ).toString()
            }
        }

        addHost(parsed.host)
        if (emulator) {
            addHost("10.0.2.2")
        } else {
            addHost("127.0.0.1")
            addHost(REMOTE_DEVICE_ANALYSIS_HOST.substringBefore(':'))
        }
        return candidates.toList()
    }

    private fun appendDefaultPathIfNeeded(url: String): String {
        val normalized = url.trimEnd('/')
        return if (normalized.endsWith(DEFAULT_ANALYSIS_PATH)) {
            normalized
        } else {
            "$normalized$DEFAULT_ANALYSIS_PATH"
        }
    }

    private fun defaultHostForRuntime(): String {
        return if (isLikelyEmulator()) {
            DEFAULT_EMULATOR_ANALYSIS_HOST
        } else {
            DEFAULT_DEVICE_ANALYSIS_HOST
        }
    }

    private fun isLikelyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            product.contains("sdk") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }
}
