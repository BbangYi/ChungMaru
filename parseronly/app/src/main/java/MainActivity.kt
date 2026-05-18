package com.example.youtubeparser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var intervalInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        val textView = TextView(this).apply {
            text = "SNS Comment Parser\n\nSelect one platform to run automation only for that app."
            textSize = 18f
        }

        intervalInput = EditText(this).apply {
            hint = "App switch interval in minutes"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(AutomationSettingsStore.getRotationIntervalMinutes(this@MainActivity).toString())
        }

        val youtubeButton = createStartButton(
            text = "Start YouTube Only",
            platformMode = AutomationSettingsStore.PLATFORM_YOUTUBE,
            platformLabel = "YouTube",
            packageNames = listOf(YOUTUBE_PACKAGE)
        )

        val instagramButton = createStartButton(
            text = "Start Instagram Only",
            platformMode = AutomationSettingsStore.PLATFORM_INSTAGRAM,
            platformLabel = "Instagram",
            packageNames = listOf(INSTAGRAM_PACKAGE)
        )

        val tiktokButton = createStartButton(
            text = "Start TikTok Only",
            platformMode = AutomationSettingsStore.PLATFORM_TIKTOK,
            platformLabel = "TikTok",
            packageNames = listOf(TIKTOK_PACKAGE, TIKTOK_ALT_PACKAGE)
        )

        val stopButton = Button(this).apply {
            text = "Stop Automation"
            setOnClickListener {
                AutomationSettingsStore.setEnabled(this@MainActivity, false)
                AutomationSettingsStore.saveStatus(this@MainActivity, "Automation stopped")
                renderAutomationStatus()
                Toast.makeText(this@MainActivity, "Automation stopped", Toast.LENGTH_SHORT).show()
            }
        }

        val refreshButton = Button(this).apply {
            text = "Refresh Status"
            setOnClickListener {
                renderAutomationStatus()
            }
        }

        statusText = TextView(this).apply {
            textSize = 14f
        }

        val accessibilityButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(textView)
        layout.addView(intervalInput)
        layout.addView(youtubeButton)
        layout.addView(instagramButton)
        layout.addView(tiktokButton)
        layout.addView(stopButton)
        layout.addView(refreshButton)
        layout.addView(statusText)
        layout.addView(accessibilityButton)

        setContentView(layout)
        renderAutomationStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            renderAutomationStatus()
        }
    }

    private fun createStartButton(
        text: String,
        platformMode: String,
        platformLabel: String,
        packageNames: List<String>
    ): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener {
                startPlatformOnly(platformMode, platformLabel, packageNames)
            }
        }
    }

    private fun startPlatformOnly(
        platformMode: String,
        platformLabel: String,
        packageNames: List<String>
    ) {
        AutomationSettingsStore.saveRotationIntervalMinutes(
            this,
            intervalInput.text?.toString().orEmpty()
        )
        AutomationSettingsStore.savePlatformMode(this, platformMode)
        AutomationSettingsStore.savePlatformIndex(this, 0)
        AutomationSettingsStore.setEnabled(this, true)
        AutomationSettingsStore.saveStatus(this, "$platformLabel-only automation started")
        renderAutomationStatus()
        launchFirstAvailable(packageNames, platformLabel)
        Toast.makeText(this, "$platformLabel-only automation started", Toast.LENGTH_SHORT).show()
    }

    private fun renderAutomationStatus() {
        val status = AutomationSettingsStore.getStatus(this)
        val updatedAt = status?.updatedAt
            ?.takeIf { it > 0L }
            ?.let { SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA).format(Date(it)) }
            ?: "-"

        statusText.text = buildString {
            append("Automation: ")
            append(if (AutomationSettingsStore.isEnabled(this@MainActivity)) "ON" else "OFF")
            append("\nMode: ")
            append(AutomationSettingsStore.getPlatformModeLabel(this@MainActivity))
            append("\nApp switch interval: ")
            append(AutomationSettingsStore.getRotationIntervalMinutes(this@MainActivity))
            append(" min")
            append("\nLatest status: ")
            append(status?.message ?: "-")
            append("\nUpdated: ")
            append(updatedAt)
        }
    }

    private fun launchFirstAvailable(packageNames: List<String>, platformLabel: String) {
        for (packageName in packageNames) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                return
            }
        }

        Toast.makeText(this, "$platformLabel app was not found", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
        const val TIKTOK_ALT_PACKAGE = "com.ss.android.ugc.trill"
    }
}
