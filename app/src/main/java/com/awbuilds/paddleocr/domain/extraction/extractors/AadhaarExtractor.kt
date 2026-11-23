package com.awbuilds.paddleocr.domain.extraction.extractors

import com.awbuilds.paddleocr.domain.extraction.ExtractionUtils
import com.awbuilds.paddleocr.domain.extraction.FieldBuilder
import com.awbuilds.paddleocr.domain.extraction.TextLayout
import com.awbuilds.paddleocr.domain.extraction.cleanValue
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.OcrDocument

object AadhaarExtractor {

    private val AADHAAR_STOPS = listOf(
        Regex("(?i)^\\s*date\\s+of\\s+birth\\b"),
        Regex("(?i)^\\s*year\\s+of\\s+birth\\b"),
        Regex("(?i)^\\s*gender\\b"),
        Regex("(?i)^\\s*address\\b"),
        Regex("(?i)^\\s*mobile\\b"),
        Regex("(?i)^\\s*signature\\b")
    )

    fun extract(
        ocr: OcrDocument,
        typeConfidence: Float = 0.9f,
        detectedType: DocumentType? = DocumentType.AADHAAR
    ): ExtractionResult {
        val layout = TextLayout(ocr.allLines)
        val builder = FieldBuilder(DocumentType.AADHAAR, typeConfidence, detectedType)

        val aadhaarRaw = ExtractionUtils.MASKED_AADHAAR_REGEX.find(layout.singleLine)?.value
            ?: ExtractionUtils.AADHAAR_REGEX.find(layout.singleLine)?.value
        val aadhaar = ExtractionUtils.maskAadhaar(aadhaarRaw)
        builder.add("aadhaarNumber", "Aadhaar Number", aadhaar, aadhaar?.let { 0.9f } ?: 0f)

        val name = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*(?:full\\s+)?name\\b"),
                Regex("(?i)^\\s*नाम\\b")
            ),
            stopLabels = AADHAAR_STOPS
        )?.value?.let(ExtractionUtils::cleanPersonName)
            ?: fallbackName(layout)
        builder.add("fullName", "Full Name", name, name?.let { 0.76f } ?: 0f)

        val dobRaw = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*dob\\b"),
                Regex("(?i)^\\s*date\\s+of\\s+birth\\b"),
                Regex("(?i)^\\s*year\\s+of\\s+birth\\b"),
                Regex("(?i)^\\s*yob\\b")
            ),
            stopLabels = AADHAAR_STOPS
        )?.value
        val dob = ExtractionUtils.parseDate(dobRaw)
            ?: ExtractionUtils.findDates(layout.singleLine).firstOrNull()
        builder.add("dateOfBirth", "Date of Birth / Year", dob, dob?.let { 0.82f } ?: 0f)

        val gender = Regex("(?i)\\b(male|female|transgender)\\b")
            .find(layout.singleLine)?.groupValues?.get(1)?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
        builder.add("gender", "Gender", gender, gender?.let { 0.86f } ?: 0f)

        val address = extractAddress(layout)
        builder.add("address", "Address", address, address?.let { 0.68f } ?: 0f)

        val pin = ExtractionUtils.PIN_REGEX.findAll(layout.singleLine)
            .map { it.value }
            .lastOrNull()
        builder.add("pinCode", "PIN Code", pin, pin?.let { 0.8f } ?: 0f)

        val mobile = ExtractionUtils.extractPhone(layout.singleLine)
        builder.add("mobileNumber", "Mobile Number", mobile, mobile?.let { 0.72f } ?: 0f)

        if (aadhaar == null) builder.warn("Aadhaar number was not detected; verify the image quality.")
        if (address == null) builder.warn("Address block not isolated. Aadhaar back side is usually needed.")
        return builder.build()
    }

    private fun fallbackName(layout: TextLayout): String? {
        val nameRow = layout.rows.firstOrNull { row ->
            Regex("(?i)^\\s*name\\b").containsMatchIn(row.text)
        }
        if (nameRow != null) {
            val index = layout.rows.indexOf(nameRow)
            layout.rows.getOrNull(index + 1)?.text
                ?.let(ExtractionUtils::cleanPersonName)
                ?.let { return it }
        }

        // Older Aadhaar cards begin the identity block with "To,".
        val toIndex = layout.rows.indexOfFirst { Regex("(?i)^\\s*to\\s*,?\\s*$").matches(it.text) }
        if (toIndex >= 0) {
            for (row in layout.rows.drop(toIndex + 1).take(4)) {
                ExtractionUtils.cleanPersonName(row.text)?.let { return it }
            }
        }

        return layout.rows.take(8)
            .mapNotNull { ExtractionUtils.cleanPersonName(it.text) }
            .firstOrNull()
    }

    private fun extractAddress(layout: TextLayout): String? {
        val startRegex = listOf(
            Regex("(?i)^\\s*address\\b"),
            Regex("(?i)^\\s*(?:s/o|d/o|w/o|c/o)\\b"),
            Regex("(?i)^\\s*to\\s*,?\\s*$")
        )
        val startIndex = layout.rows.indexOfFirst { row -> startRegex.any { it.containsMatchIn(row.text) } }
        if (startIndex < 0) return null

        val parts = mutableListOf<String>()
        val startRow = layout.rows[startIndex]
        val inline = cleanValue(
            startRow.text.replace(Regex("(?i)^\\s*address\\s*[:\\-]?\\s*"), "")
        )
        if (inline.isNotBlank()) parts.add(inline)

        for (index in startIndex + 1 until layout.rows.size) {
            val row = layout.rows[index]
            if (ExtractionUtils.PIN_REGEX.containsMatchIn(row.text)) {
                parts.add(row.text)
                break
            }
            if (row.text.length > 3 && !Regex("(?i)^(mobile|gender|dob|date of birth)\\b").containsMatchIn(row.text)) {
                parts.add(row.text)
            }
            if (parts.size >= 7) break
        }

        return parts
            .joinToString(", ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .ifBlank { null }
    }
}
