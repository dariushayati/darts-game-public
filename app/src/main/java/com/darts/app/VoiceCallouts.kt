package com.darts.app

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Spoken Gunslinger callouts. English words are used so "17" is never read as a digit string. */
fun numberCallout(number: Int): String = when (number) {
    1 -> "one"
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    8 -> "eight"
    9 -> "nine"
    10 -> "ten"
    11 -> "eleven"
    12 -> "twelve"
    13 -> "thirteen"
    14 -> "fourteen"
    15 -> "fifteen"
    16 -> "sixteen"
    17 -> "seventeen"
    18 -> "eighteen"
    19 -> "nineteen"
    20 -> "twenty"
    25 -> "bull"
    else -> number.toString()
}

/**
 * Thin lifecycle wrapper around Android TextToSpeech. Construction starts engine
 * initialisation immediately so the first target reveal is usually ready to speak.
 * If the engine is still warming up, the latest target is spoken once it becomes ready
 * (within a short window) instead of speaking a stale number much later.
 */
class VoiceCallouts(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var released = false
    private var pendingNumber: Int? = null
    private var pendingAt = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (released) return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            val locale = Locale.ENGLISH
            if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = locale
            }
            ready = true
            val pending = pendingNumber
            val age = SystemClock.elapsedRealtime() - pendingAt
            pendingNumber = null
            if (pending != null && age <= PREWARM_WINDOW_MS) speakNow(pending)
        }
    }

    fun speakNumber(number: Int) {
        if (released) return
        if (!ready) {
            pendingNumber = number
            pendingAt = SystemClock.elapsedRealtime()
            return
        }
        speakNow(number)
    }

    private fun speakNow(number: Int) {
        val engine = tts ?: return
        engine.speak(
            numberCallout(number),
            TextToSpeech.QUEUE_FLUSH,
            null,
            "gunslinger-target-$number",
        )
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        released = true
        ready = false
        pendingNumber = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val PREWARM_WINDOW_MS = 4_000L
    }
}
