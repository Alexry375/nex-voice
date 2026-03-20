package com.nex.voice

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * Simple settings screen to configure:
 * - Telegram Bot Token (the bot that handles voice messages)
 * - Chat ID (your Telegram user ID)
 * - Button to request default assistant role
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val ROLE_REQUEST = 200
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

        layout.addView(TextView(this).apply {
            text = "\nConfigure your Telegram bot connection.\n"
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

        // Set as default assistant button
        layout.addView(Button(this).apply {
            text = "Set as Default Assistant"
            setOnClickListener { requestAssistantRole() }
        })

        // Status
        layout.addView(TextView(this).apply {
            text = "\n\nHow it works:\n" +
                "1. Set this app as default assistant\n" +
                "2. Long-press power button → records your voice\n" +
                "3. Tap the screen to stop → sends to Nex via Telegram\n" +
                "4. You receive the response as a Telegram message"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
        })

        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun requestAssistantRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                Toast.makeText(this, "Already set as default assistant ✓", Toast.LENGTH_SHORT).show()
            } else {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                startActivityForResult(intent, ROLE_REQUEST)
            }
        } else {
            Toast.makeText(this, "Go to Settings > Default Apps > Digital Assistant", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ROLE_REQUEST) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Nex is now your default assistant ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not set as default", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
