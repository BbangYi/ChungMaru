package com.example.youtubeparser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
            text = "YouTube Parser\n\n기존 파싱은 그대로 두고 자동 운전만 제어합니다."
            textSize = 18f
        }

        intervalInput = EditText(this).apply {
            hint = "앱 전환 간격(분): 테스트 1, 실제 180"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(AutomationSettingsStore.getRotationIntervalMinutes(this@MainActivity).toString())
        }

        statusText = TextView(this).apply {
            textSize = 14f
        }

        val startButton = Button(this).apply {
            text = "자동 시작"
            setOnClickListener {
                AutomationSettingsStore.saveRotationIntervalMinutes(
                    this@MainActivity,
                    intervalInput.text?.toString().orEmpty()
                )
                AutomationSettingsStore.savePlatformIndex(this@MainActivity, 0)
                AutomationSettingsStore.setEnabled(this@MainActivity, true)
                AutomationSettingsStore.saveStatus(
                    this@MainActivity,
                    "자동 운전 시작: YouTube Shorts부터 진입합니다."
                )
                renderAutomationStatus()
                launchYoutubeShorts()
                Toast.makeText(this@MainActivity, "자동 운전 시작", Toast.LENGTH_SHORT).show()
            }
        }

        val stopButton = Button(this).apply {
            text = "자동 중지"
            setOnClickListener {
                AutomationSettingsStore.setEnabled(this@MainActivity, false)
                AutomationSettingsStore.saveStatus(this@MainActivity, "자동 운전 중지됨")
                renderAutomationStatus()
                Toast.makeText(this@MainActivity, "자동 운전 중지", Toast.LENGTH_SHORT).show()
            }
        }

        val refreshButton = Button(this).apply {
            text = "자동 상태 새로고침"
            setOnClickListener {
                renderAutomationStatus()
            }
        }

        val button = Button(this).apply {
            text = "접근성 설정 열기"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(textView)
        layout.addView(intervalInput)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(refreshButton)
        layout.addView(statusText)
        layout.addView(button)

        setContentView(layout)
        renderAutomationStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            renderAutomationStatus()
        }
    }

    private fun renderAutomationStatus() {
        val status = AutomationSettingsStore.getStatus(this)
        val updatedAt = status?.updatedAt
            ?.takeIf { it > 0L }
            ?.let { SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA).format(Date(it)) }
            ?: "-"

        statusText.text = buildString {
            append("자동 운전: ")
            append(if (AutomationSettingsStore.isEnabled(this@MainActivity)) "ON" else "OFF")
            append("\n앱 전환 간격: ")
            append(AutomationSettingsStore.getRotationIntervalMinutes(this@MainActivity))
            append("분")
            append("\n최근 상태: ")
            append(status?.message ?: "-")
            append("\n업데이트: ")
            append(updatedAt)
        }
    }

    private fun launchYoutubeShorts() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "YouTube 앱을 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}
