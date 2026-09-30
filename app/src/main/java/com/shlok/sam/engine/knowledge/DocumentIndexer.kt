package com.shlok.sam.engine.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.shlok.sam.data.db.KnowledgeChunkEntity
import com.shlok.sam.data.db.KnowledgeDocumentEntity
import com.shlok.sam.data.db.SamDatabase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SamDatabase
) {
    suspend fun ingest(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri)
            val mime = context.contentResolver.getType(uri).orEmpty()
            val text = extract(uri, mime, name)
            if (text.isBlank()) error("No extractable text in $name")
            val id = db.knowledge().insertDoc(
                KnowledgeDocumentEntity(name = name, mime = mime.ifBlank { "application/octet-stream" }, uri = uri.toString())
            )
            val chunks = chunk(text)
            db.knowledge().insertChunks(
                chunks.mapIndexed { i, c -> KnowledgeChunkEntity(documentId = id, chunkIndex = i, content = c) }
            )
            "Indexed $name (${chunks.size} sections)."
        }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment ?: "document"
    }

    private suspend fun extract(uri: Uri, mime: String, name: String): String {
        val lower = name.lowercase()
        return when {
            mime.contains("pdf") || lower.endsWith(".pdf") -> extractPdf(uri)
            mime.contains("word") || lower.endsWith(".docx") -> extractDocxLikeXml(uri)
            mime.contains("spreadsheet") || lower.endsWith(".xlsx") || lower.endsWith(".csv") -> extractPlain(uri).ifBlank { extractDocxLikeXml(uri) }
            mime.startsWith("image/") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> extractImage(uri)
            else -> extractPlain(uri)
        }
    }

    private fun extractPlain(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            return BufferedReader(InputStreamReader(input)).readText()
        }
        return ""
    }

    private fun extractPdf(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                return PDFTextStripper().getText(doc)
            }
        }
        return ""
    }

    private fun extractDocxLikeXml(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                val sb = StringBuilder()
                while (entry != null) {
                    if (entry.name.endsWith(".xml")) {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        sb.append(xml.replace(Regex("<[^>]+>"), " "))
                    }
                    entry = zip.nextEntry
                }
                return sb.toString()
            }
        }
        return ""
    }

    private suspend fun extractImage(uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val result = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await()
        return result.text
    }

    private fun chunk(text: String, size: Int = 900): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= size) return listOf(clean)
        val out = mutableListOf<String>()
        var i = 0
        while (i < clean.length) {
            val end = (i + size).coerceAtMost(clean.length)
            out += clean.substring(i, end)
            i += size - 80
        }
        return out
    }
}
