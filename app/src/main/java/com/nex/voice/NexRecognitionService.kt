package com.nex.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService

/**
 * Stub RecognitionService — required by some Android OEMs to register
 * the app as a valid "voice assistant" for the power button long-press.
 * We don't actually use this for recognition (we use Groq Whisper),
 * but it needs to exist for the system to accept us.
 */
class NexRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback?) {
        // Not used — we handle recording in AssistantActivity
    }

    override fun onCancel(callback: Callback?) {}

    override fun onStopListening(callback: Callback?) {}
}
