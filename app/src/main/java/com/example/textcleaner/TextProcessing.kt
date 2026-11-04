package com.example.textcleaner

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

// 1. Invisible Launchers (Die instantly to free the browser)

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

class CleanTextActivity : BaseProcessActivity() {
    override fun processText(rawText: String): String = cleanText(rawText)
}

class NoBracketsActivity : BaseProcessActivity() {
    override fun processText(rawText: String): String {
        val noBrackets = rawText.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ")
        return cleanText(noBrackets)
    }
}

abstract class BaseProcessActivity : Activity() {
    abstract fun processText(rawText: String): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        val processed = processText(selectedText)

        if (processed.isNotBlank()) {
            // Hand off text to the background service and die immediately
            val serviceIntent = Intent(this, TtsService::class.java).apply {
                putExtra("TEXT_TO_SPEAK", processed)
            }
            startService(serviceIntent)
        }
        
        // Free the browser instantly with zero delay
        finish()
    }

    protected fun cleanText(rawText: String): String {
        var cleaned = rawText.replace(Regex("""\(\d+\)|\[\d+\]"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\([^)]*?/goto\?url=[^)]*\)"""), "")
        cleaned = cleaned.replace(Regex("""[\s\r\n\p{P}\p{S}]+"""), " ")
        return cleaned.trim()
    }
}

class StopActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stop the background service instantly
        stopService(Intent(this, TtsService::class.java))
        finish()
    }
}


// 2. The Background Ghost Worker (Handles TTS independently so browser stays free)

class TtsService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var pendingText: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pendingText = intent?.getStringExtra("TEXT_TO_SPEAK") ?: ""
        
        if (tts == null && pendingText.isNotBlank()) {
            tts = TextToSpeech(this, this)
        } else if (tts != null && pendingText.isNotBlank()) {
            // If already running, queue up new text instantly
            speakChunks(pendingText)
        }
        
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = tts?.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }

            // Don't steal audio focus from background media
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("CHUNK_LAST_") == true) {
                        stopSelf() // Shut down service when finished
                    }
                }
                override fun onError(utteranceId: String?) {
                    if (utteranceId?.startsWith("CHUNK_LAST_") == true) {
                        stopSelf()
                    }
                }
            })

            speakChunks(pendingText)
        } else {
            stopSelf()
        }
    }

    private fun speakChunks(text: String) {
        val chunks = text.chunked(3900)
        val batchId = System.currentTimeMillis()

        for ((i, chunk) in chunks.withIndex()) {
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (i == chunks.size - 1) "CHUNK_LAST_$batchId" else "CHUNK_${i}_$batchId"
            tts?.speak(chunk, queueMode, null, utteranceId)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown() // Fixed arrow typo to safe call
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
