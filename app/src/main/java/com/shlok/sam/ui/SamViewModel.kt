package com.shlok.sam.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shlok.sam.core.network.NetworkMonitor
import com.shlok.sam.core.security.SecretStore
import com.shlok.sam.data.db.ConversationEntity
import com.shlok.sam.data.db.MemoryEntity
import com.shlok.sam.data.db.MessageEntity
import com.shlok.sam.data.db.OrbSettingsEntity
import com.shlok.sam.data.db.SamDatabase
import com.shlok.sam.data.db.UserProfileEntity
import com.shlok.sam.engine.ai.AIProviderManager
import com.shlok.sam.engine.knowledge.DocumentIndexer
import com.shlok.sam.engine.runtime.SamRuntime
import com.shlok.sam.engine.task.TaskEngine
import com.shlok.sam.engine.vision.VisualLens
import com.shlok.sam.engine.voice.SamEngineService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SamViewModel @Inject constructor(
    app: Application,
    val runtime: SamRuntime,
    val tasks: TaskEngine,
    val db: SamDatabase,
    val secrets: SecretStore,
    val ai: AIProviderManager,
    val indexer: DocumentIndexer,
    val lens: VisualLens,
    val network: NetworkMonitor
) : AndroidViewModel(app) {

    val profile = db.userProfile().observe().map { it ?: UserProfileEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfileEntity())
    val orb = db.orb().observe().map { it ?: OrbSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrbSettingsEntity())
    val messages = db.conversations().observe()
    val memories = db.memory().observeAll()
    val taskList = db.tasks().observe()
    val providers = db.providers().observe()
    val docs = db.knowledge().observeDocs()
    val logs = db.logs().observe()
    val notifications = db.notifications().observe()
    private val conversationId = MutableStateFlow(0L)
    val chatMessages = conversationId.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList()) else db.messages().observe(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MessageEntity>())

    init {
        viewModelScope.launch {
            val latest = db.conversations().latest()
            conversationId.value = latest?.id ?: db.conversations().insert(ConversationEntity(title = "SAM"))
        }
    }

    fun submit(text: String) = runtime.submitText(text)
    fun mic() = runtime.onMicPressed()
    fun stop() = runtime.cancelEverything()
    fun stopSpeak() = runtime.stopSpeaking()

    fun saveProfile(block: UserProfileEntity.() -> UserProfileEntity) {
        viewModelScope.launch {
            val cur = db.userProfile().get() ?: UserProfileEntity()
            db.userProfile().upsert(cur.block())
        }
    }

    fun saveOrb(block: OrbSettingsEntity.() -> OrbSettingsEntity) {
        viewModelScope.launch {
            val cur = db.orb().get() ?: OrbSettingsEntity()
            db.orb().upsert(cur.block())
        }
    }

    fun setEngine(on: Boolean) {
        val ctx = getApplication<Application>()
        if (on) SamEngineService.start(ctx) else SamEngineService.stop(ctx)
        saveProfile { copy(engineEnabled = on) }
    }

    fun saveMemory(title: String, content: String) {
        viewModelScope.launch { db.memory().insert(MemoryEntity(title = title, content = content)) }
    }

    fun deleteMemory(entity: MemoryEntity) {
        viewModelScope.launch { db.memory().delete(entity) }
    }

    fun clearMemory() {
        viewModelScope.launch { db.memory().clear() }
    }

    fun ingest(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val r = indexer.ingest(uri)
            onDone(r.fold({ it }, { "Couldn't index: ${it.message}" }))
        }
    }

    fun updateMemory(entity: MemoryEntity, title: String, content: String) {
        viewModelScope.launch {
            db.memory().update(entity.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
        }
    }

    fun saveKey(id: String, key: String) {
        secrets.putKey(id, key)
    }

    fun testProvider(id: String, model: String, base: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val r = ai.provider(id, base).test(model)
            db.providers().get(id)?.let {
                db.providers().upsert(it.copy(lastOk = r.ok, lastStatus = if (r.ok) "Connected" else r.text, model = model, enabled = r.ok, baseUrl = base))
            }
            onDone(if (r.ok) "Connected" else r.text)
        }
    }
}
