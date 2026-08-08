package com.example.lolipet.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.lolipet.Prefs
import java.util.Locale

/** TTS 语音回复管理 */
class PetTts(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINESE
            tts?.setSpeechRate(1.05f)
            ready = true
            pendingText?.let { speak(it) }
            pendingText = null
        } else {
            ready = false
        }
    }

    /** 朗读文本（如果 TTS 开启） */
    fun speak(text: String) {
        if (!Prefs.ttsEnabled) return
        val clean = text.replace(Regex("[。！？]+$"), "")
            .replace(Regex("[（(].*?[)）]"), "")
            .trim()
        if (clean.isEmpty()) return
        if (!ready) {
            pendingText = clean
            return
        }
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "pet_speak")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
