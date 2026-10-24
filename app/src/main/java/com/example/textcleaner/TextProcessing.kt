package com.example.textcleaner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

class CleanTextActivity : BaseTextActivity() {
    override fun processText(rawText: String): String {
        return cleanText(rawText)
    }
}

class NoBracketsActivity : BaseTextActivity() {
    override fun processText(rawText: String): String {
        val noBrackets = rawText.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ")
        return cleanText(noBrackets)
    }
}

abstract class BaseTextActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var textToSpeak: String = ""

    abstract fun processText(rawText: String): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        textToSpeak = processText(selectedText)

        if (textToSpeak.isNotBlank()) {
            tts = TextToSpeech(this, this)
        } else {
            finish()
        }
    }

    protected fun cleanText(rawText: String): String {
        var cleaned = rawText.replace(Regex("""\(\d+\)|\[\d+\]"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\([^)]*?/goto\?url=[^)]*\)"""), "")
        cleaned = cleaned.replace(Regex("""[\s\r\n\p{P}\p{S}]+"""), " ")
        return cleaned.trim()
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

            // Split long text into safe 3,900-character chunks to bypass system limits
            val chunks = textToSpeak.chunked(3900)
            val lastIndex = chunks.size - 1

            // Listen for when the VERY LAST chunk finishes before shutting down
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "CHUNK_$lastIndex") {
                        finish()
                    }
                }
                override fun onError(utteranceId: String?) {
                    if (utteranceId == "CHUNK_$lastIndex") {
                        finish()
                    }
                }
            })

            // Feed chunks sequentially into the queue
            for ((i, chunk) in chunks.withIndex()) {
                val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(chunk, queueMode, null, "CHUNK_$i")
            }
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
