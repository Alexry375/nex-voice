package com.nex.voice

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Minimal transparent overlay activity.
 * Shows a pulsing mic icon while recording, then sends to Telegram via Groq Whisper.
 *
 * Flow:
 *   1. Activity opens → starts recording immediately
 *   2. User taps the mic (or waits 30s) → stops recording
 *   3. Audio sent to Groq Whisper → transcript
 *   4. Transcript sent to Telegram bot → notification with "sent" confirmation
 *   5. Activity closes
 */
class AssistantActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pulseAnimator: ObjectAnimator? = null

    private lateinit var micIcon: ImageView
    private lateinit var statusText: TextView

    companion object {
        private const val NOTIF_CHANNEL = "nex_voice"
        private const val PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent overlay window
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        createNotificationChannel()

        // Check mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST
            )
            return
        }

        setupUI()
        startRecording()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            setupUI()
            startRecording()
        } else {
            showNotification("Nex", "Microphone permission required")
            finish()
        }
    }

    private fun setupUI() {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt()) // Semi-transparent black
            isClickable = true
            setOnClickListener { stopAndSend() }
        }

        // Mic icon
        micIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(0xFFFFFFFF.toInt())
        }
        val micParams = FrameLayout.LayoutParams(200, 200).apply {
            gravity = Gravity.CENTER
        }
        frame.addView(micIcon, micParams)

        // Status text
        statusText = TextView(this).apply {
            text = "Listening... Tap to send"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            bottomMargin = 200
        }
        frame.addView(statusText, textParams)

        setContentView(frame)

        // Pulse animation on mic
        pulseAnimator = ObjectAnimator.ofFloat(micIcon, View.SCALE_X, 1f, 1.3f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(micIcon, View.SCALE_Y, 1f, 1.3f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun startRecording() {
        audioFile = File(cacheDir, "nex_voice_${System.currentTimeMillis()}.ogg")

        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setOutputFile(audioFile!!.absolutePath)
            prepare()
            start()
        }
        isRecording = true

        // Auto-stop after 30 seconds
        scope.launch {
            delay(30_000)
            if (isRecording) stopAndSend()
        }
    }

    private fun stopAndSend() {
        if (!isRecording) return
        isRecording = false

        pulseAnimator?.cancel()
        statusText.text = "Sending..."
        micIcon.setColorFilter(0xFF888888.toInt())

        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            // Ignore
        }
        recorder = null

        val file = audioFile ?: run { finish(); return }

        scope.launch {
            try {
                val prefs = getSharedPreferences("nex_voice", MODE_PRIVATE)
                val botToken = prefs.getString("bot_token", "") ?: ""
                val chatId = prefs.getString("chat_id", "") ?: ""
                val groqKey = prefs.getString("groq_api_key", "") ?: ""

                if (botToken.isEmpty() || chatId.isEmpty()) {
                    showNotification("Nex", "Configure bot token and chat ID in settings")
                    finish()
                    return@launch
                }

                if (groqKey.isEmpty()) {
                    showNotification("Nex", "Configure Groq API key in settings")
                    finish()
                    return@launch
                }

                statusText.text = "Transcribing..."

                // Step 1: Transcribe via Groq Whisper
                val transcript = withContext(Dispatchers.IO) {
                    transcribeWithGroq(groqKey, file)
                }

                if (transcript.isNullOrBlank()) {
                    showNotification("Nex", "Could not transcribe voice")
                    finish()
                    return@launch
                }

                statusText.text = "Sending: $transcript"
                val bridgeUrl = prefs.getString("bridge_url", "http://100.96.206.81:3458") ?: ""

                // Step 2: Send transcript to bridge via REST API
                val sent = withContext(Dispatchers.IO) {
                    sendToBridge(bridgeUrl, transcript)
                }

                if (sent) {
                    showNotification("Nex", "🎤 $transcript")
                } else {
                    showNotification("Nex", "Failed to send — trying Telegram fallback")
                    // Fallback: send via Telegram bot sendMessage
                    withContext(Dispatchers.IO) {
                        sendTextToBot(botToken, chatId, "🎤 $transcript")
                    }
                }
            } catch (e: Exception) {
                showNotification("Nex", "Error: ${e.message?.take(80)}")
            } finally {
                file.delete()
                finish()
            }
        }
    }

    /**
     * Transcribe audio via Groq Whisper API.
     */
    private fun transcribeWithGroq(apiKey: String, file: File): String? {
        val client = OkHttpClient.Builder()
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "voice.ogg",
                file.asRequestBody("audio/ogg".toMediaType())
            )
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("language", "fr")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val json = response.body?.string() ?: return null
        // Parse {"text": "..."} manually to avoid adding a JSON library
        val match = Regex(""""text"\s*:\s*"(.*?)"""").find(json)
        return match?.groupValues?.get(1)
    }

    /**
     * Send transcribed text directly to the Nexus bridge server.
     */
    private fun sendToBridge(bridgeUrl: String, text: String): Boolean {
        val client = OkHttpClient.Builder()
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val json = """{"content":"${text.replace("\"", "\\\"")}","source":"nex-voice"}"""
        val body = RequestBody.create("application/json".toMediaType(), json)

        val request = Request.Builder()
            .url("$bridgeUrl/api/voice-message")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fallback: send text via Telegram bot API.
     */
    private fun sendTextToBot(botToken: String, chatId: String, text: String): Boolean {
        val client = OkHttpClient()

        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
            .build()

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendMessage")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        return response.isSuccessful
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL, "Nex Voice",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Nex voice assistant notifications"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, text: String) {
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(1, notif)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (isRecording) {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) {}
            recorder = null
            audioFile?.delete()
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        pulseAnimator?.cancel()
        if (isRecording) {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) {}
        }
    }
}
