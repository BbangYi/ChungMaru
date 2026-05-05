package com.capstone.design.youtubeparser

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class VisualTextOcrProcessor {

    private data class RecognizerSpec(
        val name: String,
        val recognizer: TextRecognizer
    )

    private val recognizers = listOf(
        RecognizerSpec(
            name = "korean",
            recognizer = TextRecognition.getClient(
                KoreanTextRecognizerOptions.Builder().build()
            )
        ),
        RecognizerSpec(
            name = "latin",
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        )
    )

    fun recognize(
        screenshot: Bitmap,
        rois: List<VisualTextRoi>,
        onComplete: (List<ParsedComment>) -> Unit
    ) {
        val selectedRois = rois.take(MAX_ROIS_PER_PASS)
        if (selectedRois.isEmpty() || screenshot.isRecycled) {
            onComplete(emptyList())
            return
        }

        val workItems = selectedRois.flatMap { roi ->
            recognizers.map { recognizer -> roi to recognizer }
        }
        val pendingCount = AtomicInteger(workItems.size)
        val candidates = Collections.synchronizedList(mutableListOf<ParsedComment>())

        fun finishOne() {
            if (pendingCount.decrementAndGet() == 0) {
                onComplete(deduplicate(candidates))
            }
        }

        workItems.forEach { (roi, recognizerSpec) ->
            val crop = cropBitmap(screenshot, roi.boundsInScreen)
            if (crop == null) {
                finishOne()
                return@forEach
            }

            val image = InputImage.fromBitmap(crop, 0)
            recognizerSpec.recognizer
                .process(image)
                .addOnSuccessListener { text ->
                    candidates += text.toParsedComments(roi.boundsInScreen)
                }
                .addOnFailureListener {
                    // OCR is a best-effort visual supplement; accessibility text analysis remains primary.
                }
                .addOnCompleteListener {
                    if (!crop.isRecycled) {
                        crop.recycle()
                    }
                    finishOne()
                }
        }
    }

    private fun cropBitmap(
        screenshot: Bitmap,
        bounds: BoundsRect
    ): Bitmap? {
        val left = bounds.left.coerceIn(0, screenshot.width)
        val top = bounds.top.coerceIn(0, screenshot.height)
        val right = bounds.right.coerceIn(left, screenshot.width)
        val bottom = bounds.bottom.coerceIn(top, screenshot.height)
        val width = right - left
        val height = bottom - top

        if (width < MIN_CROP_WIDTH_PX || height < MIN_CROP_HEIGHT_PX) return null

        return runCatching {
            Bitmap.createBitmap(screenshot, left, top, width, height)
        }.getOrNull()
    }

    private fun Text.toParsedComments(roiBounds: BoundsRect): List<ParsedComment> {
        return textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line -> line.toParsedComment(roiBounds) }
    }

    private fun Text.Line.toParsedComment(roiBounds: BoundsRect): ParsedComment? {
        val lineText = text.replace(Regex("\\s+"), " ").trim()
        if (!isUsefulOcrText(lineText)) return null

        val box = boundingBox ?: return null
        val translated = translateBounds(box, roiBounds) ?: return null
        return ParsedComment(
            commentText = lineText,
            boundsInScreen = translated
        )
    }

    private fun translateBounds(
        box: Rect,
        roiBounds: BoundsRect
    ): BoundsRect? {
        val left = roiBounds.left + box.left
        val top = roiBounds.top + box.top
        val right = roiBounds.left + box.right
        val bottom = roiBounds.top + box.bottom

        if (right - left < MIN_TEXT_WIDTH_PX || bottom - top < MIN_TEXT_HEIGHT_PX) {
            return null
        }

        return BoundsRect(
            left = max(roiBounds.left, left),
            top = max(roiBounds.top, top),
            right = min(roiBounds.right, right),
            bottom = min(roiBounds.bottom, bottom)
        )
    }

    private fun isUsefulOcrText(text: String): Boolean {
        if (text.length !in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH) return false
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (lower in COMMON_UI_LABELS) return false
        if (Regex("""^[\d.,]+\s*[kmb]?$""", RegexOption.IGNORE_CASE).matches(text)) return false
        return text.any { it.isLetterOrDigit() || it.code in 0xAC00..0xD7A3 }
    }

    private fun deduplicate(items: List<ParsedComment>): List<ParsedComment> {
        return items
            .distinctBy { item ->
                val bounds = item.boundsInScreen
                val textKey = item.commentText.lowercase()
                val roundedLeft = bounds.left / 8
                val roundedTop = bounds.top / 8
                "$textKey|$roundedLeft|$roundedTop"
            }
            .sortedWith(compareBy<ParsedComment> { it.boundsInScreen.top }.thenBy { it.boundsInScreen.left })
            .take(MAX_OCR_TEXT_CANDIDATES)
    }

    companion object {
        private const val MAX_ROIS_PER_PASS = 2
        private const val MAX_OCR_TEXT_CANDIDATES = 12
        private const val MIN_CROP_WIDTH_PX = 80
        private const val MIN_CROP_HEIGHT_PX = 40
        private const val MIN_TEXT_WIDTH_PX = 20
        private const val MIN_TEXT_HEIGHT_PX = 12
        private const val MIN_TEXT_LENGTH = 2
        private const val MAX_TEXT_LENGTH = 120

        private val COMMON_UI_LABELS = setOf(
            "all",
            "shorts",
            "videos",
            "watched",
            "unwatched",
            "home",
            "subscriptions",
            "share",
            "save",
            "download",
            "전체",
            "동영상",
            "홈",
            "구독"
        )
    }
}
