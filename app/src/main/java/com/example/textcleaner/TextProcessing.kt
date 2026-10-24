package com.example.textcleaner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
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
                tts?.language = Locale.US // Fallback to US English if default fails
            }
            
            // Speak the text
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
            
            // Keep the activity alive for 3 seconds so the speech engine finishes outputting audio before shutting down
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 3000)
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
