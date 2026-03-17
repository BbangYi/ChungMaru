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
import com.capstone.design.youtubeparser.UploadEndpointStore
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var serverEndpointInput: EditText
    private lateinit var savedEndpointText: TextView

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

        serverEndpointInput.setText(UploadEndpointStore.getRawInput(this))
        renderResolvedEndpoint()

        findViewById<MaterialButton>(R.id.saveEndpointButton).setOnClickListener {
            UploadEndpointStore.saveRawInput(this, serverEndpointInput.text?.toString().orEmpty())
            renderResolvedEndpoint()
            Toast.makeText(this, getString(R.string.server_endpoint_saved), Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.openAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun renderResolvedEndpoint() {
        val resolved = UploadEndpointStore.resolveUploadUrl(this)
        savedEndpointText.text = getString(R.string.saved_server_endpoint, resolved)
    }
}
