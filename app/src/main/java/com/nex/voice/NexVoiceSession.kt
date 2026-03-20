package com.nex.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionSession

/**
 * Created when the assistant is invoked (power button long-press).
 * Launches the OverlayService directly — no activity, no app switching.
 */
class NexVoiceSession(context: Context) : VoiceInteractionSession(context) {

    override fun onHandleAssist(state: AssistState) {
        // If overlay permission granted, start service directly
        if (Settings.canDrawOverlays(context)) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        } else {
            // Need to open activity to request permission (one-time)
            val intent = Intent(context, AssistantActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        finish()
    }
}
