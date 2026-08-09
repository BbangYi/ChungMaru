package com.capstone.design

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.capstone.design.youtubeparser.YoutubeAccessibilityService
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LAUNCH_PREFS = "cleaner_launch_settings"
        private const val KEY_PENDING_PACKAGE = "pending_package"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private val TIKTOK_PACKAGES = listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<MaterialButton>(R.id.youtubeCleanerButton).setOnClickListener {
            startCleaner(
                displayName = getString(R.string.youtube_name),
                candidatePackages = listOf(YOUTUBE_PACKAGE)
            )
        }
        findViewById<MaterialButton>(R.id.instagramCleanerButton).setOnClickListener {
            startCleaner(
                displayName = getString(R.string.instagram_name),
                candidatePackages = listOf(INSTAGRAM_PACKAGE)
            )
        }
        findViewById<MaterialButton>(R.id.tiktokCleanerButton).setOnClickListener {
            startCleaner(
                displayName = getString(R.string.tiktok_name),
                candidatePackages = TIKTOK_PACKAGES
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isCleanerServiceEnabled()) return

        val prefs = getSharedPreferences(LAUNCH_PREFS, MODE_PRIVATE)
        val pendingPackage = prefs.getString(KEY_PENDING_PACKAGE, null) ?: return
        prefs.edit().remove(KEY_PENDING_PACKAGE).apply()
        launchPackage(pendingPackage)
    }

    private fun startCleaner(
        displayName: String,
        candidatePackages: List<String>
    ) {
        val packageName = candidatePackages.firstOrNull { candidate ->
            packageManager.getLaunchIntentForPackage(candidate) != null
        }
        if (packageName == null) {
            Toast.makeText(
                this,
                getString(R.string.cleaner_app_not_installed, displayName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isCleanerServiceEnabled()) {
            getSharedPreferences(LAUNCH_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_PACKAGE, packageName)
                .apply()
            Toast.makeText(
                this,
                getString(R.string.enable_cleaner_accessibility),
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        launchPackage(packageName)
    }

    private fun launchPackage(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, R.string.cleaner_launch_failed, Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(launchIntent)
    }

    private fun isCleanerServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!accessibilityEnabled) return false

        val expected = ComponentName(this, YoutubeAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { component -> component == expected }
    }
}
