package com.example.textcleaner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

// Context Option 1 Handler
class CleanTextActivity : BaseTextActivity() {
    override fun processText(rawText: String): String {
        return cleanText(rawText)
    }
}

// Context Option 2 Handler (Removes brackets first)
class NoBracketsActivity : BaseTextActivity() {
    override fun processText(rawText: String): String {
        val noBrackets = rawText.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), " ")
        return cleanText(noBrackets)
    }
}

// Shared Base Activity with Regex & TTS Engine
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
        // 1. Remove word counts: (2) or [2]
        var cleaned = rawText.replace(Regex("""\(\d+\)|\[\d+\]"""), "")
        
        // 2. Strip /goto?url= blocks
        cleaned = cleaned.replace(Regex("""(?i)\([^)]*?/goto\?url=[^)]*\)"""), "")
        
        // 3. Strip punctuation and irregular spacing
        cleaned = cleaned.replace(Regex("""[\s\r\n\p{P}\p{S}]+"""), " ")
        
        return cleaned.trim()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
        }
        finish() // Automatically closes menu in background
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
