package com.driss.puzzles.ui.game

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads short words aloud for children, in French when the device is set to French and a French voice
 * is installed, otherwise in English. Does nothing if the device has no speech engine.
 */
class Speaker(context: Context) {
    private var ready = false
    private var french = false
    private val wantFrench = context.resources.configuration.locales[0].language == "fr"
    private var pending: Pair<String?, String?>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status != TextToSpeech.SUCCESS) return@TextToSpeech
        french = wantFrench && tts.setLanguage(Locale.FRENCH) >= TextToSpeech.LANG_AVAILABLE
        if (!french) tts.setLanguage(Locale.ENGLISH)
        tts.setSpeechRate(0.9f)
        ready = true
        pending?.let { (en, fr) -> say(en, fr) }
        pending = null
    }

    fun say(en: String?, fr: String?) {
        if (!ready) {
            pending = en to fr
            return
        }
        val text = (if (french) fr else en) ?: return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
    }

    /** "Well done!" followed by the picture's word. */
    fun cheer(en: String?, fr: String?) =
        say(en?.let { "Well done! $it!" } ?: "Well done!", fr?.let { "Bravo ! $it !" } ?: "Bravo !")

    fun release() = tts.shutdown()
}
