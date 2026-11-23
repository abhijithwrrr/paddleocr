package com.awbuilds.paddleocr.domain.model

data class OcrPoint(val x: Float, val y: Float)

/**
 * One recognized text line. Coordinates are in the coordinate space of the
 * bitmap that was passed to PaddleOCR.
 */
data class OcrLine(
    val text: String,
    val confidence: Float,
    val points: List<OcrPoint>,
    val page: Int = 0
) {
    val left: Float get() = points.minOfOrNull { it.x } ?: 0f
    val right: Float get() = points.maxOfOrNull { it.x } ?: 0f
    val top: Float get() = points.minOfOrNull { it.y } ?: 0f
    val bottom: Float get() = points.maxOfOrNull { it.y } ?: 0f
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
    val centerY: Float get() = (top + bottom) / 2f
}

data class OcrPage(val index: Int, val lines: List<OcrLine>)

data class OcrDocument(val pages: List<OcrPage>) {
    val allLines: List<OcrLine> = pages.flatMap { it.lines }
    val rawText: String = allLines.joinToString("\n") { it.text.trim() }.trim()

    fun page(pageIndex: Int): OcrPage? = pages.firstOrNull { it.index == pageIndex }

    companion object {
        fun singlePage(lines: List<OcrLine>): OcrDocument = OcrDocument(listOf(OcrPage(0, lines)))
    }
}

/**
 * A field shown on the review screen and exported to JSON.
 *
 * [isList] fields are edited as one item per line in the UI and exported as a
 * JSON array.
 */
data class StructuredField(
    val key: String,
    val label: String,
    val value: String?,
    val confidence: Float,
    val needsReview: Boolean,
    val isList: Boolean = false
)

data class ExtractionResult(
    val documentType: DocumentType,
    val typeConfidence: Float,
    val detectedType: DocumentType?,
    val fields: LinkedHashMap<String, StructuredField>,
    val warnings: List<String>
) {
    val reviewCount: Int get() = fields.values.count { it.needsReview }
    val filledCount: Int get() = fields.values.count { !it.value.isNullOrBlank() }
}

data class ScanResult(
    val expectedType: DocumentType?,
    val extraction: ExtractionResult,
    val ocr: OcrDocument,
    val pageCount: Int,
    val elapsedMs: Long
) {
    val documentType: DocumentType get() = extraction.documentType
    val fields: LinkedHashMap<String, StructuredField> get() = extraction.fields

    fun withField(key: String, value: String): ScanResult {
        val updated = LinkedHashMap(fields)
        updated[key]?.let { field ->
            updated[key] = if (value.isBlank()) {
                field.copy(value = null, confidence = 0f, needsReview = true)
            } else {
                field.copy(value = value, confidence = 1f, needsReview = false)
            }
        }
        return copy(extraction = extraction.copy(fields = updated))
    }
}
