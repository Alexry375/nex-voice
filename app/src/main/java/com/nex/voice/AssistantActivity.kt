package com.nex.voice

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Floating overlay assistant.
 * Shows a compact recording bar at the bottom of the screen, on top of other apps.
 * Two send buttons: Nex (bridge) and NexBuilder (port 3459).
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

        // Make the activity truly transparent — no window background
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        // Make the activity translucent so the user sees their current app
        window.attributes = window.attributes.apply {
            dimAmount = 0f
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        createNotificationChannel()

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

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun setupUI() {
        // Root frame — fully transparent, doesn't block touches except on the bar
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Bottom bar container
        val barBg = GradientDrawable().apply {
            setColor(0xF0111111.toInt())
            cornerRadius = dpToPx(24).toFloat()
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = barBg
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            elevation = dpToPx(8).toFloat()
        }

        // Mic icon (pulsing)
        micIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(0xFFFF4444.toInt()) // Red = recording
        }
        bar.addView(micIcon, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
            marginEnd = dpToPx(12)
        })

        // Status text
        statusText = TextView(this).apply {
            text = "Listening..."
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        bar.addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Send to Nex button
        val btnNex = TextView(this).apply {
            text = "Nex"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            val bg = GradientDrawable().apply {
                setColor(0xFF2563EB.toInt()) // Blue
                cornerRadius = dpToPx(16).toFloat()
            }
            background = bg
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            gravity = Gravity.CENTER
            setOnClickListener { stopAndSend("nex") }
        }
        bar.addView(btnNex, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = dpToPx(8)
        })

        // Send to NexBuilder button
        val btnBuilder = TextView(this).apply {
            text = "Builder"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            val bg = GradientDrawable().apply {
                setColor(0xFF7C3AED.toInt()) // Purple
                cornerRadius = dpToPx(16).toFloat()
            }
            background = bg
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            gravity = Gravity.CENTER
            setOnClickListener { stopAndSend("nexbuilder") }
        }
        bar.addView(btnBuilder)

        // Cancel button (X)
        val btnCancel = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF888888.toInt())
            textSize = 18f
            setPadding(dpToPx(12), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener { cancelAndClose() }
        }
        bar.addView(btnCancel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dpToPx(8)
        })

        // Position bar at bottom with margins
        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            leftMargin = dpToPx(16)
            rightMargin = dpToPx(16)
            bottomMargin = dpToPx(40)
        }
        root.addView(bar, barParams)

        setContentView(root)

        // Pulse animation on mic
        pulseAnimator = ObjectAnimator.ofFloat(micIcon, View.ALPHA, 1f, 0.3f).apply {
            duration = 500
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

        // Auto-stop after 60 seconds
        scope.launch {
            delay(60_000)
            if (isRecording) stopAndSend("nex")
        }
    }

    private fun stopAndSend(target: String) {
        if (!isRecording) return
        isRecording = false

        pulseAnimator?.cancel()
        statusText.text = "Transcribing..."
        micIcon.setColorFilter(0xFF888888.toInt())

        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {}
        recorder = null

        val file = audioFile ?: run { finish(); return }

        scope.launch {
            try {
                val prefs = getSharedPreferences("nex_voice", MODE_PRIVATE)
                val groqKey = prefs.getString("groq_api_key", "") ?: ""

                if (groqKey.isEmpty()) {
                    showNotification("Nex", "Configure Groq API key in settings")
                    finish()
                    return@launch
                }

                val transcript = withContext(Dispatchers.IO) {
                    transcribeWithGroq(groqKey, file)
                }

                if (transcript.isNullOrBlank()) {
                    showNotification("Nex", "Could not transcribe voice")
                    finish()
                    return@launch
                }

                statusText.text = "Sending..."

                val bridgeUrl = prefs.getString("bridge_url", "http://100.96.206.81:3459") ?: ""
                val targetUrl = when (target) {
                    "nex" -> bridgeUrl.replace(":3459", ":3458") // Nex = bridge port
                    "nexbuilder" -> bridgeUrl // NexBuilder port
                    else -> bridgeUrl
                }

                val sent = withContext(Dispatchers.IO) {
                    sendToBridge(targetUrl, transcript)
                }

                val label = if (target == "nex") "Nex" else "NexBuilder"
                if (sent) {
                    showNotification(label, "🎤 $transcript")
                } else {
                    // Fallback to Telegram
                    val botToken = prefs.getString("bot_token", "") ?: ""
                    val chatId = prefs.getString("chat_id", "") ?: ""
                    if (botToken.isNotEmpty() && chatId.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            sendTextToBot(botToken, chatId, "🎤 $transcript")
                        }
                        showNotification(label, "🎤 (via TG) $transcript")
                    } else {
                        showNotification(label, "Failed to send")
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

    private fun cancelAndClose() {
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
        val match = Regex(""""text"\s*:\s*"(.*?)"""").find(json)
        return match?.groupValues?.get(1)
    }

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
        cancelAndClose()
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
