package com.shlok.sam.engine.ai

import com.shlok.sam.core.network.NetworkMonitor
import com.shlok.sam.core.security.SecretStore
import com.shlok.sam.data.db.SamDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val JSON = "application/json; charset=utf-8".toMediaType()

data class AIChatResult(val text: String, val providerId: String, val ok: Boolean)

interface AIProvider {
    val id: String
    suspend fun chat(system: String, user: String, model: String?): AIChatResult
    suspend fun test(model: String?): AIChatResult
}

class GeminiProvider(
    private val client: OkHttpClient,
    private val secrets: SecretStore
) : AIProvider {
    override val id = "gemini"
    override suspend fun chat(system: String, user: String, model: String?): AIChatResult = withContext(Dispatchers.IO) {
        val key = secrets.getKey(id) ?: return@withContext AIChatResult("Gemini API key is not configured.", id, false)
        val m = model?.ifBlank { null } ?: "gemini-2.0-flash"
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .toString()
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody(JSON))
            .build()
        http(client, req, id) { raw ->
            JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }

    override suspend fun test(model: String?) = chat("Reply with the word OK only.", "ping", model)
}

class OpenAICompatibleProvider(
    private val client: OkHttpClient,
    private val secrets: SecretStore,
    override val id: String,
    private val defaultBase: String,
    private val defaultModel: String
) : AIProvider {
    override suspend fun chat(system: String, user: String, model: String?): AIChatResult = withContext(Dispatchers.IO) {
        val key = secrets.getKey(id) ?: return@withContext AIChatResult("$id API key is not configured.", id, false)
        val cfgBase = defaultBase.trimEnd('/')
        val m = model?.ifBlank { null } ?: defaultModel
        val payload = JSONObject()
            .put("model", m)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            .toString()
        val req = Request.Builder()
            .url("$cfgBase/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(payload.toRequestBody(JSON))
            .build()
        http(client, req, id) { raw ->
            JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    override suspend fun test(model: String?) = chat("Reply with the word OK only.", "ping", model)
}

class AnthropicProvider(
    private val client: OkHttpClient,
    private val secrets: SecretStore
) : AIProvider {
    override val id = "anthropic"
    override suspend fun chat(system: String, user: String, model: String?): AIChatResult = withContext(Dispatchers.IO) {
        val key = secrets.getKey(id) ?: return@withContext AIChatResult("Anthropic API key is not configured.", id, false)
        val m = model?.ifBlank { null } ?: "claude-3-5-haiku-20241022"
        val payload = JSONObject()
            .put("model", m)
            .put("max_tokens", 800)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toRequestBody(JSON))
            .build()
        http(client, req, id) { raw ->
            JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    override suspend fun test(model: String?) = chat("Reply with the word OK only.", "ping", model)
}

class LocalProvider : AIProvider {
    override val id = "local"
    override suspend fun chat(system: String, user: String, model: String?): AIChatResult {
        return AIChatResult(
            "I can help with apps, alarms, Wikipedia, Google Search, notifications, and device actions without a cloud model. Connect a provider in Connectors for deeper reasoning.",
            id,
            true
        )
    }

    override suspend fun test(model: String?) = AIChatResult("Local engine ready.", id, true)
}

@Singleton
class AIProviderManager @Inject constructor(
    private val client: OkHttpClient,
    private val secrets: SecretStore,
    private val db: SamDatabase,
    private val network: NetworkMonitor
) {
    fun provider(id: String, baseUrl: String = ""): AIProvider = when (id) {
        "gemini" -> GeminiProvider(client, secrets)
        "openai" -> OpenAICompatibleProvider(client, secrets, "openai", "https://api.openai.com/v1", "gpt-4o-mini")
        "anthropic" -> AnthropicProvider(client, secrets)
        "groq" -> OpenAICompatibleProvider(client, secrets, "groq", "https://api.groq.com/openai/v1", "llama-3.1-8b-instant")
        "custom" -> OpenAICompatibleProvider(
            client, secrets, "custom",
            baseUrl.ifBlank { "https://api.openai.com/v1" },
            "gpt-4o-mini"
        )
        else -> LocalProvider()
    }

    suspend fun chat(system: String, user: String): AIChatResult {
        val profile = db.userProfile().get()
        val selected = profile?.aiProvider ?: "local"
        if (selected == "local" || !network.isOnline()) return LocalProvider().chat(system, user, null)
        val cfg = db.providers().get(selected)
        val result = provider(selected, cfg?.baseUrl.orEmpty()).chat(system, user, cfg?.model)
        return if (result.ok) result else {
            AIChatResult("${result.text} Falling back to the local engine.", "local", false)
        }
    }
}

private fun http(client: OkHttpClient, req: Request, id: String, parse: (String) -> String): AIChatResult {
    return try {
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) AIChatResult("Provider returned ${resp.code}.", id, false)
            else AIChatResult(parse(raw).trim(), id, true)
        }
    } catch (_: Exception) {
        AIChatResult("Connection failed.", id, false)
    }
}
