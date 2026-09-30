package com.shlok.sam.engine.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import com.shlok.sam.core.di.ApplicationScope
import com.shlok.sam.core.identity.SamIdentity
import com.shlok.sam.core.identity.SpokenLanguage
import com.shlok.sam.core.network.NetworkMonitor
import com.shlok.sam.data.db.ConversationEntity
import com.shlok.sam.data.db.DevLogEntity
import com.shlok.sam.data.db.MessageEntity
import com.shlok.sam.data.db.SamDatabase
import com.shlok.sam.domain.model.SamCoreState
import com.shlok.sam.engine.command.CommandParser
import com.shlok.sam.engine.task.TaskEngine
import android.provider.Settings
import com.shlok.sam.engine.overlay.SamOverlayService
import com.shlok.sam.engine.voice.SpeechSession
import com.shlok.sam.engine.voice.TtsEngine
import com.shlok.sam.engine.voice.containsWakeWord
import com.shlok.sam.engine.voice.localeFor
import com.shlok.sam.domain.model.SystemMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class RuntimeUi(
    val state: SamCoreState = SamCoreState.IDLE,
    val transcript: String = "",
    val spoken: String = "",
    val amplitude: Float = 0f,
    val overlayExpanded: Boolean = false,
    val waitingConfirm: Boolean = false
)

@Singleton
class SamRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val db: SamDatabase,
    private val parser: CommandParser,
    private val tasks: TaskEngine,
    private val tts: TtsEngine,
    private val network: NetworkMonitor
) {
    private val main = Handler(Looper.getMainLooper())
    private val _ui = MutableStateFlow(RuntimeUi())
    val ui: StateFlow<RuntimeUi> = _ui
    private var session: SpeechSession? = null
    private var wakeMode = true
    private var conversationId: Long? = null
    private var lastLang = SpokenLanguage.ENGLISH
    private var lastReply = ""
    private var engineListening = false
    var overlayCallback: ((Boolean) -> Unit)? = null
    var engineActive: Boolean = false

    init {
        tts.init()
        tts.setSpeakingListener { speaking ->
            if (speaking) setState(SamCoreState.SPEAKING)
            else if (_ui.value.state == SamCoreState.SPEAKING) {
                setState(SamCoreState.SUCCESS)
                main.postDelayed({ if (_ui.value.state == SamCoreState.SUCCESS) setState(SamCoreState.IDLE) }, 900)
            }
        }
        scope.launch { ensureConversation() }
    }

    private suspend fun ensureConversation(): Long {
        conversationId?.let { return it }
        val latest = db.conversations().latest()
        val id = latest?.id ?: db.conversations().insert(ConversationEntity(title = "SAM"))
        conversationId = id
        return id
    }

    private fun setState(state: SamCoreState) {
        _ui.value = _ui.value.copy(state = state)
    }

    fun amplitude(): Float = _ui.value.amplitude

    fun startWakeListening() {
        wakeMode = true
        engineListening = true
        listen(commandMode = false)
    }

    fun startCommandListening() {
        wakeMode = false
        overlayCallback?.invoke(true)
        listen(commandMode = true)
    }

    fun onMicPressed() {
        tts.stop()
        wakeMode = false
        showOverlay()
        scope.launch {
            val name = db.userProfile().get()?.displayName ?: "there"
            speakAck(name)
            main.postDelayed({ listen(commandMode = true) }, 400)
        }
    }

    fun stopSpeaking() {
        tts.stop()
        setState(SamCoreState.IDLE)
    }

    fun cancelEverything() {
        tasks.cancel()
        tts.stop()
        session?.stop()
        setState(SamCoreState.IDLE)
        _ui.value = _ui.value.copy(transcript = "", spoken = "Cancelled.")
    }

    fun submitText(text: String) {
        scope.launch { handleUtterance(text, fromWake = false) }
    }

    private fun listen(commandMode: Boolean) {
        main.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _ui.value = _ui.value.copy(spoken = "Speech recognition is not available on this device.")
                setState(SamCoreState.ERROR)
                return@post
            }
            session?.destroy()
            val langPref = lastLang
            session = SpeechSession(
                context = context,
                locale = localeFor(langPref),
                onPartial = { partial ->
                    _ui.value = _ui.value.copy(transcript = partial)
                    if (!commandMode && containsWakeWord(partial)) {
                        session?.stop()
                        onWoken()
                    }
                },
                onRms = { rms -> _ui.value = _ui.value.copy(amplitude = rms) },
                onFinal = { text ->
                    if (commandMode) {
                        scope.launch { handleUtterance(text, fromWake = false) }
                    } else if (containsWakeWord(text)) {
                        onWoken()
                    } else {
                        // keep listening for wake word
                        main.postDelayed({ if (wakeMode) listen(false) }, 250)
                    }
                },
                onError = { err ->
                    if (commandMode) {
                        setState(SamCoreState.ERROR)
                        _ui.value = _ui.value.copy(spoken = err)
                    } else {
                        main.postDelayed({ if (wakeMode) listen(false) }, 400)
                    }
                }
            )
            setState(if (commandMode) SamCoreState.LISTENING else SamCoreState.IDLE)
            session?.start()
        }
    }

    private fun onWoken() {
        wakeMode = false
        showOverlay()
        scope.launch {
            val name = db.userProfile().get()?.displayName ?: "there"
            speakAck(name)
            main.postDelayed({ listen(commandMode = true) }, 500)
        }
    }

    private fun showOverlay() {
        overlayCallback?.invoke(true)
        if (Settings.canDrawOverlays(context)) {
            runCatching { SamOverlayService.start(context) }
        }
    }

    fun toggleOverlayExpanded() {
        _ui.value = _ui.value.copy(overlayExpanded = !_ui.value.overlayExpanded)
    }

    private fun speakAck(name: String) {
        val line = "Yes, $name?"
        _ui.value = _ui.value.copy(spoken = line, overlayExpanded = true)
        tts.speak(line, SpokenLanguage.ENGLISH)
    }

    private suspend fun handleUtterance(text: String, fromWake: Boolean) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) {
            setState(SamCoreState.ERROR)
            _ui.value = _ui.value.copy(spoken = "I didn't catch that.")
            return
        }
        if (containsWakeWord(cleaned) && cleaned.split(Regex("\\s+")).size <= 2) {
            onWoken()
            return
        }
        val cid = ensureConversation()
        db.messages().insert(MessageEntity(conversationId = cid, role = "user", text = cleaned))
        val profile = db.userProfile().get()
        val parsed = parser.parse(cleaned)
        lastLang = when (profile?.preferredLanguage) {
            "en" -> SpokenLanguage.ENGLISH
            "hi" -> SpokenLanguage.HINDI
            "mr" -> SpokenLanguage.MARATHI
            else -> parsed.language
        }
        val name = profile?.displayName ?: "there"
        val n = cleaned.lowercase().trim()
        if (n == "repeat" || n.contains("repeat that") || n.contains("again")) {
            if (lastReply.isNotBlank()) tts.speak(lastReply, lastLang)
            return
        }
        if (n.contains("short answer") && lastReply.isNotBlank()) {
            tts.speak(lastReply.takeWhile { it != '.' }.ifBlank { lastReply.take(80) }, lastLang)
            return
        }
        log("intent", "${parsed.category} conf=${parsed.confidence}")
        setState(SamCoreState.THINKING)
        _ui.value = _ui.value.copy(transcript = cleaned, overlayExpanded = true)
        setState(SamCoreState.EXECUTING)
        val result = tasks.run(parsed, name)
        val reply = result.message
        lastReply = reply
        db.messages().insert(MessageEntity(conversationId = cid, role = "sam", text = reply))
        _ui.value = _ui.value.copy(
            spoken = reply,
            waitingConfirm = tasks.hasPendingConfirmation(),
            state = if (result.success && result.verified) SamCoreState.SUCCESS else if (result.success) SamCoreState.SPEAKING else SamCoreState.ERROR
        )
        val silent = profile?.systemMode == SystemMode.SILENT.name
        if (!silent) {
            val spoken = if (profile?.responseStyle == "CONCISE") reply.take(280) else reply
            tts.speak(spoken, lastLang)
        }
        if (engineActive || engineListening) {
            main.postDelayed({ startWakeListening() }, 1800)
        }
    }

    private suspend fun log(tag: String, message: String) {
        db.logs().insert(DevLogEntity(level = "I", tag = tag, message = message.take(500)))
    }

    fun respondIdentity(): String = SamIdentity.aboutText()

    fun online(): Boolean = network.isOnline()
}
