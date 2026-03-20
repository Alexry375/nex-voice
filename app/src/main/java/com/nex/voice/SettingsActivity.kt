package com.nex.voice

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var logText: TextView

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        appendLog("Role request result: ${result.resultCode} (OK=${RESULT_OK})")
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Nex is now your default assistant ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Not set — try the manual method below", Toast.LENGTH_LONG).show()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("nex_voice", MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "Nex Voice"
            textSize = 28f
            setTextColor(0xFF000000.toInt())
        })

        // Status indicator
        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        layout.addView(statusText)

        layout.addView(TextView(this).apply {
            text = "Configure your Telegram bot connection.\n"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
        })

        // Bot Token
        layout.addView(TextView(this).apply {
            text = "Telegram Bot Token"
            textSize = 14f
        })
        val tokenInput = EditText(this).apply {
            hint = "123456:ABC-DEF..."
            setText(prefs.getString("bot_token", ""))
            isSingleLine = true
        }
        layout.addView(tokenInput)

        // Chat ID
        layout.addView(TextView(this).apply {
            text = "\nYour Telegram Chat ID"
            textSize = 14f
        })
        val chatIdInput = EditText(this).apply {
            hint = "5468140531"
            setText(prefs.getString("chat_id", ""))
            isSingleLine = true
        }
        layout.addView(chatIdInput)

        // Save button
        layout.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.edit()
                    .putString("bot_token", tokenInput.text.toString().trim())
                    .putString("chat_id", chatIdInput.text.toString().trim())
                    .apply()
                Toast.makeText(this@SettingsActivity, "Saved ✓", Toast.LENGTH_SHORT).show()
            }
        })

        // Set as default assistant — automatic via RoleManager
        layout.addView(Button(this).apply {
            text = "Set as Default Assistant (auto)"
            setOnClickListener { requestAssistantRole() }
        })

        // Fallback — open system settings directly
        layout.addView(Button(this).apply {
            text = "Open System Assistant Settings (manual)"
            setOnClickListener { openAssistantSettings() }
        })

        // How it works
        layout.addView(TextView(this).apply {
            text = "\nHow it works:\n" +
                "1. Set this app as default assistant\n" +
                "2. Long-press power button → records your voice\n" +
                "3. Tap the screen to stop → sends to Nex via Telegram\n" +
                "4. You receive the response as a Telegram message"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
        })

        // Debug log
        layout.addView(TextView(this).apply {
            text = "\n— Debug Log —"
            textSize = 12f
            setTextColor(0xFF999999.toInt())
        })
        logText = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFF666666.toInt())
            setTextIsSelectable(true)
        }
        layout.addView(logText)

        setContentView(ScrollView(this).apply { addView(layout) })

        updateStatus()
        appendLog("App started, Android ${android.os.Build.VERSION.SDK_INT}")
    }

    private fun requestAssistantRole() {
        try {
            val roleManager = getSystemService(RoleManager::class.java)
            appendLog("RoleManager available: ${roleManager != null}")

            val isAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
            val isHeld = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            appendLog("ROLE_ASSISTANT available=$isAvailable, held=$isHeld")

            if (isAvailable) {
                if (isHeld) {
                    Toast.makeText(this, "Already set as default assistant ✓", Toast.LENGTH_SHORT).show()
                } else {
                    appendLog("Requesting ROLE_ASSISTANT...")
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                    roleRequestLauncher.launch(intent)
                }
            } else {
                appendLog("ROLE_ASSISTANT not available on this device")
                Toast.makeText(this, "Use the manual method — tap 'Open System Assistant Settings'", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            appendLog("ERROR: ${e.message}")
            Log.e("NexVoice", "Role request failed", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAssistantSettings() {
        try {
            // Try the direct assistant settings intent
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            startActivity(intent)
            appendLog("Opened ACTION_VOICE_INPUT_SETTINGS")
        } catch (e: Exception) {
            appendLog("ACTION_VOICE_INPUT_SETTINGS failed: ${e.message}")
            try {
                // Fallback: open default apps settings
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
                appendLog("Opened ACTION_MANAGE_DEFAULT_APPS_SETTINGS")
            } catch (e2: Exception) {
                appendLog("Fallback failed: ${e2.message}")
                // Last resort: open general settings
                startActivity(Intent(Settings.ACTION_SETTINGS))
                appendLog("Opened general settings")
            }
        }
    }

    private fun updateStatus() {
        try {
            val roleManager = getSystemService(RoleManager::class.java)
            val isHeld = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            if (isHeld) {
                statusText.text = "✅ Nex is your default assistant"
                statusText.setTextColor(0xFF22C55E.toInt())
            } else {
                statusText.text = "⚠️ Nex is NOT the default assistant"
                statusText.setTextColor(0xFFEF4444.toInt())
            }
        } catch (e: Exception) {
            statusText.text = "❓ Cannot check assistant status"
            statusText.setTextColor(0xFFFF9800.toInt())
        }
    }

    private fun appendLog(msg: String) {
        Log.d("NexVoice", msg)
        runOnUiThread {
            val current = logText.text.toString()
            logText.text = if (current.isEmpty()) msg else "$current\n$msg"
        }
    }
}
