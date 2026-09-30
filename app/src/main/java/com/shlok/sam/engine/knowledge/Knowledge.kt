package com.shlok.sam.engine.knowledge

import com.shlok.sam.core.identity.SpokenLanguage
import com.shlok.sam.core.network.NetworkMonitor
import com.shlok.sam.data.db.SamDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class KnowledgeHit(
    val title: String,
    val summary: String,
    val source: String,
    val url: String? = null
)

interface KnowledgeSource {
    val id: String
    suspend fun search(query: String, language: SpokenLanguage): KnowledgeHit?
}

@Singleton
class WikipediaSource @Inject constructor(
    private val client: OkHttpClient,
    private val network: NetworkMonitor
) : KnowledgeSource {
    override val id = "wikipedia"

    override suspend fun search(query: String, language: SpokenLanguage): KnowledgeHit? = withContext(Dispatchers.IO) {
        if (!network.isOnline()) return@withContext null
        val lang = when (language) {
            SpokenLanguage.MARATHI -> "mr"
            SpokenLanguage.HINDI -> "hi"
            else -> "en"
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=1&format=json&srlimit=1"
        val searchBody = get(searchUrl) ?: return@withContext null
        val title = runCatching {
            JSONObject(searchBody).getJSONObject("query").getJSONArray("search").getJSONObject(0).getString("title")
        }.getOrNull() ?: return@withContext null
        val titleEnc = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
        val summaryUrl = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$titleEnc"
        val sumBody = get(summaryUrl) ?: return@withContext null
        val obj = JSONObject(sumBody)
        val extract = obj.optString("extract")
        if (extract.isBlank()) return@withContext null
        KnowledgeHit(
            title = obj.optString("title", title),
            summary = extract,
            source = "Wikipedia ($lang)",
            url = obj.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
        )
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "SAM-Assistant/1.0 (educational; theshlok18)").build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }
}

@Singleton
class GoogleSearchSource @Inject constructor(
    private val android: com.shlok.sam.engine.android.AndroidController
) : KnowledgeSource {
    override val id = "google"
    override suspend fun search(query: String, language: SpokenLanguage): KnowledgeHit? {
        val opened = android.openGoogleSearch(query)
        return if (opened) {
            KnowledgeHit(
                title = query,
                summary = "Opened Google Search for \"$query\". SAM cannot scrape Google results without a Search API key, so the live page is the verified result.",
                source = "Google Search (device browser)",
                url = "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
            )
        } else null
    }
}

@Singleton
class UploadedDataSource @Inject constructor(
    private val db: SamDatabase
) : KnowledgeSource {
    override val id = "uploads"
    override suspend fun search(query: String, language: SpokenLanguage): KnowledgeHit? {
        val terms = query.split(Regex("\\s+")).filter { it.length > 2 }.take(6)
        if (terms.isEmpty()) return null
        val fallback = db.knowledge().likeChunks(terms.first())
        val hit = fallback.firstOrNull() ?: return null
        return KnowledgeHit("Uploaded document", hit.content.take(1200), "Personal knowledge", null)
    }
}

@Singleton
class LocalKnowledgeSource @Inject constructor() : KnowledgeSource {
    override val id = "local"
    override suspend fun search(query: String, language: SpokenLanguage): KnowledgeHit? {
        val q = query.lowercase()
        val aboutSam = q.contains("who are you") || q.contains("what is sam") || q.contains("your name") ||
            q.contains("तु काय") || q.contains("तू कोण")
        if (aboutSam) {
            return KnowledgeHit(
                "SAM",
                "I am SAM, Smart Autonomous Machine — a voice-first Android assistant. My name cannot be changed.",
                "Local"
            )
        }
        return null
    }
}

@Singleton
class KnowledgeManager @Inject constructor(
    private val wikipedia: WikipediaSource,
    private val google: GoogleSearchSource,
    private val uploads: UploadedDataSource,
    private val local: LocalKnowledgeSource
) {
    suspend fun answer(query: String, language: SpokenLanguage, preferUploads: Boolean, openGoogle: Boolean): KnowledgeHit? {
        if (preferUploads) uploads.search(query, language)?.let { return it }
        local.search(query, language)?.let { return it }
        if (openGoogle) google.search(query, language)?.let { return it }
        wikipedia.search(query, language)?.let { return it }
        uploads.search(query, language)?.let { return it }
        return null
    }

    suspend fun deepResearch(query: String, language: SpokenLanguage): List<KnowledgeHit> {
        val hits = mutableListOf<KnowledgeHit>()
        wikipedia.search(query, language)?.let { hits += it }
        uploads.search(query, language)?.let { hits += it }
        google.search(query, language)?.let { hits += it }
        return hits
    }
}
