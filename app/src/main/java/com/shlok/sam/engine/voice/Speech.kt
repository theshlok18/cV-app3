package com.shlok.sam.engine.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.shlok.sam.core.identity.SpokenLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var speakingCb: ((Boolean) -> Unit)? = null

    fun init(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(ready.get())
            return
        }
        tts = TextToSpeech(context) { status ->
            ready.set(status == TextToSpeech.SUCCESS)
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speakingCb?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    speakingCb?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speakingCb?.invoke(false)
                }
            })
            onReady(ready.get())
        }
    }

    fun setSpeakingListener(cb: (Boolean) -> Unit) {
        speakingCb = cb
    }

    fun stop() {
        tts?.stop()
        speakingCb?.invoke(false)
    }

    fun speak(text: String, language: SpokenLanguage) {
        val engine = tts ?: return
        val locale = localeFor(language)
        val ok = engine.isLanguageAvailable(locale)
        engine.language = if (ok >= TextToSpeech.LANG_AVAILABLE) locale else Locale.US
        engine.setSpeechRate(1.02f)
        engine.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }
}

class SpeechSession(
    private val context: Context,
    private val locale: Locale,
    private val onPartial: (String) -> Unit,
    private val onRms: (Float) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                onRms(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onError(errorLabel(error))
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.startListening(intent)
    }

    fun stop() {
        runCatching { recognizer.stopListening() }
    }

    fun destroy() {
        runCatching { recognizer.destroy() }
    }
}

fun errorLabel(code: Int) = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
    SpeechRecognizer.ERROR_CLIENT -> "Speech client error"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech needs a network connection right now"
    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine busy"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything"
    else -> "Speech recognition failed"
}

fun localeFor(language: SpokenLanguage) = when (language) {
    SpokenLanguage.MARATHI -> Locale("mr", "IN")
    SpokenLanguage.HINDI -> Locale("hi", "IN")
    else -> Locale("en", "IN")
}

fun containsWakeWord(text: String): Boolean {
    val n = text.lowercase().replace(Regex("[^a-z\\u0900-\\u097F\\s]"), " ")
    return n.contains("hey sam") || Regex("\\bsam\\b").containsMatchIn(n) ||
        n.contains("हे सॅम") || n.contains("हे सैम") || n.contains("सॅम") || n.contains("hey sam")
}
