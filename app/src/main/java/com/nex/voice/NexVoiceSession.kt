package com.nex.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionSession
import android.widget.Toast

/**
 * Created when the assistant is invoked (power button long-press).
 * Launches the OverlayService directly — no activity, no app switching.
 */
class NexVoiceSession(context: Context) : VoiceInteractionSession(context) {

    override fun onHandleAssist(state: AssistState) {
        try {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, OverlayService::class.java)
                context.startForegroundService(intent)
            } else {
                // Need overlay permission — open activity
                Toast.makeText(context, "Nex: overlay permission needed", Toast.LENGTH_SHORT).show()
                val intent = Intent(context, AssistantActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Nex error: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
            // Fallback to activity
            try {
                val intent = Intent(context, AssistantActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
        finish()
    }
}
