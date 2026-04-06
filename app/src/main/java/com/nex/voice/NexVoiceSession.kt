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
        AppLog.init(context)
        AppLog.d("=== onHandleAssist triggered (power button) ===")
        try {
            val canOverlay = Settings.canDrawOverlays(context)
            AppLog.d("canDrawOverlays=$canOverlay")
            if (canOverlay) {
                val intent = Intent(context, OverlayService::class.java)
                AppLog.d("Starting OverlayService via startForegroundService")
                context.startForegroundService(intent)
                AppLog.d("startForegroundService called OK")
            } else {
                AppLog.w("No overlay permission — falling back to AssistantActivity")
                Toast.makeText(context, "Nex: overlay permission needed", Toast.LENGTH_SHORT).show()
                val intent = Intent(context, AssistantActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            AppLog.e("onHandleAssist error", e)
            Toast.makeText(context, "Nex error: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(context, AssistantActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
        AppLog.d("Calling finish() on VoiceInteractionSession")
        finish()
    }
}
