package com.shlok.sam.engine.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.shlok.sam.engine.ai.AIProviderManager
import com.shlok.sam.engine.android.SamAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class LensResult(
    val ocr: String,
    val description: String,
    val uiHints: List<String>
)

@Singleton
class VisualLens @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ai: AIProviderManager
) {
    suspend fun analyze(bitmap: Bitmap?, uri: Uri?, question: String?): LensResult {
        val image = when {
            bitmap != null -> InputImage.fromBitmap(bitmap, 0)
            uri != null -> InputImage.fromFilePath(context, uri)
            else -> return LensResult("", "No image provided.", emptyList())
        }
        val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
        val ui = SamAccessibilityService.instance?.dumpVisibleText(24).orEmpty()
        val prompt = buildString {
            append("Describe this scene from OCR text only. Do not invent unseen details. OCR:\n")
            append(ocr.take(2500))
            if (!question.isNullOrBlank()) append("\nQuestion: $question")
        }
        val desc = if (ocr.isBlank()) {
            "I couldn't read text in this image. Connect a vision-capable model or try a clearer photo."
        } else {
            val aiResult = ai.chat("You are SAM. Answer only from provided OCR. If unsure, say so.", prompt)
            if (aiResult.ok && aiResult.providerId != "local") aiResult.text
            else "Visible text:\n${ocr.take(1500)}"
        }
        return LensResult(ocr = ocr, description = desc, uiHints = ui)
    }
}
