package com.capstone.design

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.capstone.design.youtubeparser.AnalysisDiagnosticsStore
import com.capstone.design.youtubeparser.AnalysisEndpointStore
import com.capstone.design.youtubeparser.AutomationSettingsStore
import com.capstone.design.youtubeparser.UploadEndpointStore
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var serverEndpointInput: EditText
    private lateinit var savedEndpointText: TextView
    private lateinit var analysisEndpointInput: EditText
    private lateinit var savedAnalysisEndpointText: TextView
    private lateinit var analysisDiagnosticsText: TextView
    private lateinit var automationIntervalInput: EditText
    private lateinit var automationStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        serverEndpointInput = findViewById(R.id.serverEndpointInput)
        savedEndpointText = findViewById(R.id.savedEndpointText)
        analysisEndpointInput = findViewById(R.id.analysisEndpointInput)
        savedAnalysisEndpointText = findViewById(R.id.savedAnalysisEndpointText)
        analysisDiagnosticsText = findViewById(R.id.analysisDiagnosticsText)
        automationIntervalInput = findViewById(R.id.automationIntervalInput)
        automationStatusText = findViewById(R.id.automationStatusText)

        serverEndpointInput.setText(UploadEndpointStore.getRawInput(this))
        analysisEndpointInput.setText(AnalysisEndpointStore.getRawInput(this))
        automationIntervalInput.setText(
            AutomationSettingsStore.getRotationIntervalMinutes(this).toString()
        )
        renderResolvedEndpoint()
        renderResolvedAnalysisEndpoint()
        renderAnalysisDiagnostics()
        renderAutomationStatus()

        findViewById<MaterialButton>(R.id.saveEndpointButton).setOnClickListener {
            UploadEndpointStore.saveRawInput(this, serverEndpointInput.text?.toString().orEmpty())
            renderResolvedEndpoint()
            Toast.makeText(this, getString(R.string.server_endpoint_saved), Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.saveAnalysisEndpointButton).setOnClickListener {
            AnalysisEndpointStore.saveRawInput(this, analysisEndpointInput.text?.toString().orEmpty())
            renderResolvedAnalysisEndpoint()
            Toast.makeText(this, getString(R.string.analysis_endpoint_saved), Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.openAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<MaterialButton>(R.id.refreshAnalysisDiagnosticsButton).setOnClickListener {
            renderAnalysisDiagnostics()
        }

        findViewById<MaterialButton>(R.id.startAutomationButton).setOnClickListener {
            AutomationSettingsStore.saveRotationIntervalMinutes(
                this,
                automationIntervalInput.text?.toString().orEmpty()
            )
            AutomationSettingsStore.savePlatformIndex(this, 0)
            AutomationSettingsStore.setEnabled(this, true)
            AutomationSettingsStore.saveStatus(this, getString(R.string.automation_status_started))
            renderAutomationStatus()
            launchYoutubeForAutomation()
            Toast.makeText(this, getString(R.string.automation_started), Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.stopAutomationButton).setOnClickListener {
            AutomationSettingsStore.setEnabled(this, false)
            AutomationSettingsStore.saveStatus(this, getString(R.string.automation_status_stopped))
            renderAutomationStatus()
            Toast.makeText(this, getString(R.string.automation_stopped), Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.refreshAutomationStatusButton).setOnClickListener {
            renderAutomationStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::analysisDiagnosticsText.isInitialized) {
            renderAnalysisDiagnostics()
        }
        if (::automationStatusText.isInitialized) {
            renderAutomationStatus()
        }
    }

    private fun renderResolvedEndpoint() {
        val resolved = UploadEndpointStore.resolveUploadUrl(this)
        savedEndpointText.text = getString(R.string.saved_server_endpoint, resolved)
    }

    private fun renderResolvedAnalysisEndpoint() {
        val resolved = AnalysisEndpointStore.resolveAnalyzeUrl(this)
        savedAnalysisEndpointText.text = getString(R.string.saved_analysis_endpoint, resolved)
    }

    private fun renderAnalysisDiagnostics() {
        val diagnostics = AnalysisDiagnosticsStore.getLatest(this)
        if (diagnostics == null) {
            analysisDiagnosticsText.text = getString(R.string.analysis_diagnostics_empty)
            return
        }

        val analyzedAt = SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA)
            .format(Date(diagnostics.analyzedAt))
        val status = if (diagnostics.ok) "OK" else "FAIL"
        analysisDiagnosticsText.text = getString(
            R.string.analysis_diagnostics_value,
            analyzedAt,
            status,
            diagnostics.packageName ?: "-",
            diagnostics.commentCount,
            diagnostics.offensiveCount,
            diagnostics.filteredCount,
            diagnostics.latencyMs,
            diagnostics.url,
            diagnostics.actionableSamples.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "-",
            diagnostics.error ?: "-"
        )
    }

    private fun renderAutomationStatus() {
        val status = AutomationSettingsStore.getStatus(this)
        val updatedAt = status?.updatedAt
            ?.takeIf { it > 0L }
            ?.let {
                SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA).format(Date(it))
            }
            ?: "-"

        automationStatusText.text = getString(
            R.string.automation_status_value,
            if (AutomationSettingsStore.isEnabled(this)) "ON" else "OFF",
            AutomationSettingsStore.getRotationIntervalMinutes(this),
            status?.message ?: "-",
            updatedAt
        )
    }

    private fun launchYoutubeForAutomation() {
        val launchIntent = packageManager
            .getLaunchIntentForPackage("com.google.android.youtube")
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        if (launchIntent == null) {
            Toast.makeText(this, getString(R.string.youtube_app_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(launchIntent)
    }
}
