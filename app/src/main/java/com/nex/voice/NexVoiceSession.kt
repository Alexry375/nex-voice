package com.nex.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/**
 * Created when the assistant is invoked (power button long-press).
 * Launches the AssistantActivity which handles recording + sending.
 */
class NexVoiceSession(context: Context) : VoiceInteractionSession(context) {

    override fun onHandleAssist(state: AssistState) {
        val intent = Intent(context, AssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        finish()
    }
}
