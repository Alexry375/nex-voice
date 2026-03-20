package com.nex.voice

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Foreground service that shows a floating recording bar.
 * Doesn't steal focus from the current app.
 */
class OverlayService : Service() {

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var statusText: TextView? = null
    private var micIcon: ImageView? = null

    companion object {
        const val TAG = "NexOverlay"
        const val NOTIF_CHANNEL = "nex_voice"
        const val NOTIF_CHANNEL_FG = "nex_voice_fg"
        const val NOTIF_ID = 2
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        try {
            createNotificationChannels()
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "Foreground service started OK")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground: ${e.message}", e)
            Toast.makeText(this, "Nex overlay error: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand, overlayView=${overlayView != null}")
        if (overlayView == null) {
            try {
                showOverlay()
                startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay or start recording: ${e.message}", e)
                Toast.makeText(this, "Nex: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
                cleanup()
            }
        }
        return START_NOT_STICKY
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun showOverlay() {
        Log.d(TAG, "showOverlay")
        val barBg = GradientDrawable().apply {
            setColor(0xF0111111.toInt())
            cornerRadius = dpToPx(24).toFloat()
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = barBg
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }

        micIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(0xFFFF4444.toInt())
        }
        bar.addView(micIcon, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
            marginEnd = dpToPx(12)
        })

        statusText = TextView(this).apply {
            text = "Listening..."
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        bar.addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnNex = TextView(this).apply {
            text = "Nex"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(0xFF2563EB.toInt())
                cornerRadius = dpToPx(16).toFloat()
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            gravity = Gravity.CENTER
            setOnClickListener { stopAndSend("nex") }
        }
        bar.addView(btnNex, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dpToPx(8) })

        val btnBuilder = TextView(this).apply {
            text = "Builder"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(0xFF7C3AED.toInt())
                cornerRadius = dpToPx(16).toFloat()
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            gravity = Gravity.CENTER
            setOnClickListener { stopAndSend("nexbuilder") }
        }
        bar.addView(btnBuilder)

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
        ).apply { marginStart = dpToPx(8) })

        val wrapper = FrameLayout(this).apply {
            addView(bar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(16)
                rightMargin = dpToPx(16)
            })
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dpToPx(40)
        }

        overlayView = wrapper
        windowManager?.addView(wrapper, params)
        Log.d(TAG, "Overlay view added")
    }

    private fun startRecording() {
        Log.d(TAG, "startRecording")

        // Check permission at runtime
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            Toast.makeText(this, "Nex: Mic permission not granted. Open app first.", Toast.LENGTH_LONG).show()
            cleanup()
            return
        }

        audioFile = File(cacheDir, "nex_voice_${System.currentTimeMillis()}.ogg")

        try {
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
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
            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "setAudioSource failed: ${e.message}", e)
            Toast.makeText(this, "Nex: setAudioSource failed. ${e.message?.take(60)}", Toast.LENGTH_LONG).show()
            cleanup()
            return
        }

        scope.launch {
            delay(60_000)
            if (isRecording) stopAndSend("nex")
        }
    }

    private fun stopAndSend(target: String) {
        if (!isRecording) return
        isRecording = false
        Log.d(TAG, "stopAndSend target=$target")

        statusText?.text = "Transcribing..."
        micIcon?.setColorFilter(0xFF888888.toInt())

        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null

        val file = audioFile ?: run { cleanup(); return }

        scope.launch {
            try {
                val prefs = getSharedPreferences("nex_voice", MODE_PRIVATE)
                val groqKey = prefs.getString("groq_api_key", "") ?: ""

                if (groqKey.isEmpty()) {
                    showNotification("Nex", "Configure Groq API key in settings")
                    cleanup()
                    return@launch
                }

                val transcript = withContext(Dispatchers.IO) {
                    transcribeWithGroq(groqKey, file)
                }

                if (transcript.isNullOrBlank()) {
                    showNotification("Nex", "Could not transcribe voice")
                    cleanup()
                    return@launch
                }

                Log.d(TAG, "Transcript: $transcript")
                statusText?.text = "Sending..."

                val botToken = prefs.getString("bot_token", "") ?: ""
                val chatId = prefs.getString("chat_id", "") ?: ""
                val label = if (target == "nex") "Nex" else "NexBuilder"

                // Try bridge first (direct HTTP to Pi)
                val bridgeUrl = prefs.getString("bridge_url", "http://100.96.206.81:3459") ?: ""
                val targetUrl = when (target) {
                    "nex" -> bridgeUrl.replace(":3459", ":3458")
                    else -> bridgeUrl
                }

                val bridgeSent = withContext(Dispatchers.IO) {
                    sendToBridge(targetUrl, transcript)
                }

                if (bridgeSent) {
                    showNotification(label, "🎤 $transcript")
                } else {
                    // Fallback: open Telegram chat with transcript pre-filled
                    val botUsername = if (target == "nex") "nex_cc_bot" else "nexBuilder_cc_bot"
                    val encodedText = java.net.URLEncoder.encode("🎤 $transcript", "UTF-8")
                    val deepLink = "https://t.me/$botUsername?text=$encodedText"
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        showNotification(label, "🎤 Tap send in Telegram")
                    } catch (e: Exception) {
                        showNotification(label, "Failed: ${e.message?.take(60)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                showNotification("Nex", "Error: ${e.message?.take(80)}")
            } finally {
                file.delete()
                cleanup()
            }
        }
    }

    private fun cancelAndClose() {
        if (isRecording) {
            try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
            recorder = null
            audioFile?.delete()
        }
        cleanup()
    }

    private fun cleanup() {
        Log.d(TAG, "cleanup")
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun transcribeWithGroq(apiKey: String, file: File): String? {
        val client = OkHttpClient.Builder()
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "voice.ogg", file.asRequestBody("audio/ogg".toMediaType()))
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
        return Regex(""""text"\s*:\s*"(.*?)"""").find(json)?.groupValues?.get(1)
    }

    private fun sendToBridge(bridgeUrl: String, text: String): Boolean {
        val client = OkHttpClient.Builder()
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val json = """{"content":"${text.replace("\"", "\\\"")}","source":"nex-voice"}"""
        val body = RequestBody.create("application/json".toMediaType(), json)
        val request = Request.Builder().url("$bridgeUrl/api/voice-message").post(body).build()
        return try { client.newCall(request).execute().isSuccessful } catch (_: Exception) { false }
    }

    private fun sendTextToBot(botToken: String, chatId: String, text: String): Boolean {
        val body = FormBody.Builder().add("chat_id", chatId).add("text", text).build()
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendMessage")
            .post(body).build()
        return OkHttpClient().newCall(request).execute().isSuccessful
    }

    private fun createNotificationChannels() {
        val ch1 = NotificationChannel(NOTIF_CHANNEL, "Nex Voice", NotificationManager.IMPORTANCE_DEFAULT)
        val ch2 = NotificationChannel(NOTIF_CHANNEL_FG, "Nex Recording", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while recording"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)?.apply {
            createNotificationChannel(ch1)
            createNotificationChannel(ch2)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_FG)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Nex Voice")
            .setContentText("Recording...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        scope.cancel()
        if (isRecording) {
            try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        }
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
    }
}
