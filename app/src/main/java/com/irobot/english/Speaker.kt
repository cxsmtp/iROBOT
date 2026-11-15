package com.irobot.english

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Speaks English replies through the phone's text-to-speech engine. */
class Speaker(context: Context, private val onDone: () -> Unit = {}) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null
    var enabled = true

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.US)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) ready = tts.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.post(onDone) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post(onDone) }
            })
            pending?.let { say(it) }
            pending = null
        }
    }

    /** Speaks [text]; calls onDone when finished (or right away when replies are off). */
    fun say(text: String) {
        if (!enabled) { main.post(onDone); return }
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
