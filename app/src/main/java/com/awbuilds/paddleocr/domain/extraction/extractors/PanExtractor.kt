package com.awbuilds.paddleocr.domain.extraction.extractors

import com.awbuilds.paddleocr.domain.extraction.ExtractionUtils
import com.awbuilds.paddleocr.domain.extraction.FieldBuilder
import com.awbuilds.paddleocr.domain.extraction.TextLayout
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.OcrDocument

object PanExtractor {

    private val PAN_STOPS = listOf(
        Regex("(?i)^\\s*father'?s?\\s+name\\b"),
        Regex("(?i)^\\s*date\\s+of\\s+birth\\b"),
        Regex("(?i)^\\s*signature\\b")
    )

    fun extract(
        ocr: OcrDocument,
        typeConfidence: Float = 0.9f,
        detectedType: DocumentType? = DocumentType.PAN
    ): ExtractionResult {
        val layout = TextLayout(ocr.allLines)
        val builder = FieldBuilder(DocumentType.PAN, typeConfidence, detectedType)

        val panMatch = ExtractionUtils.PAN_REGEX.find(layout.singleLine)
            ?: Regex("""\b[A-Za-z0-9]{10}\b""").findAll(layout.singleLine)
                .mapNotNull { ExtractionUtils.fixPan(it.value) }
                .firstOrNull()

        val pan = when (panMatch) {
            is MatchResult -> ExtractionUtils.fixPan(panMatch.value)
            is String -> panMatch
            else -> null
        }
        builder.add("panNumber", "PAN Number", pan, panConfidence(layout, pan))

        val name = layout.valueNear(
            labels = listOf(Regex("(?i)^\\s*(?:full\\s+)?name\\b")),
            stopLabels = PAN_STOPS
        )?.value?.let(ExtractionUtils::cleanPersonName)
            ?: fallbackName(layout)
        builder.add("fullName", "Full Name", name, name?.let { 0.78f } ?: 0f)

        val father = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*father'?s?\\s+name\\b"),
                Regex("(?i)^\\s*father\\s*/\\s*husband'?s?\\s+name\\b")
            ),
            stopLabels = PAN_STOPS
        )?.value?.let(ExtractionUtils::cleanPersonName)
        builder.add("fatherName", "Father's Name", father, father?.let { 0.76f } ?: 0f)

        val dobRaw = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*date\\s+of\\s+birth\\b"),
                Regex("(?i)^\\s*dob\\b")
            ),
            stopLabels = PAN_STOPS
        )?.value
        val dob = ExtractionUtils.parseDate(dobRaw)
            ?: ExtractionUtils.findDates(layout.singleLine).firstOrNull()
        builder.add("dateOfBirth", "Date of Birth", dob, dob?.let { 0.84f } ?: 0f)

        if (pan == null) builder.warn("PAN number was not detected with confidence.")
        if (name == null) builder.warn("Name could not be isolated; check the raw OCR text.")
        return builder.build()
    }

    private fun panConfidence(layout: TextLayout, pan: String?): Float {
        if (pan == null) return 0f
        val line = layout.firstLineMatching(Regex(Regex.escape(pan), RegexOption.IGNORE_CASE))
        return line?.confidence?.coerceAtLeast(0.8f) ?: 0.82f
    }

    private fun fallbackName(layout: TextLayout): String? {
        return layout.rows
            .dropWhile { row ->
                val lower = row.text.lowercase()
                lower.contains("income tax") || lower.contains("permanent account") ||
                    lower.contains("government of india") || lower.contains("pan")
            }
            .mapNotNull { ExtractionUtils.cleanPersonName(it.text) }
            .firstOrNull { candidate ->
                !candidate.lowercase().contains("department") &&
                    !candidate.lowercase().contains("signature")
            }
    }
}
