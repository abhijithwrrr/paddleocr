package com.awbuilds.paddleocr.domain.extraction

import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.OcrDocument

data class TypePrediction(
    val type: DocumentType,
    val confidence: Float,
    val scores: Map<DocumentType, Float>
)

object DocumentClassifier {

    private val PAN_PATTERN = Regex("""\b[A-Za-z]{5}[0-9]{4}[A-Za-z]\b""")
    private val IFSC_PATTERN = Regex("""\b[A-Za-z]{4}0[A-Za-z0-9]{6}\b""")
    private val AADHAAR_PATTERN = Regex("""(?<!\d)\d{4}[\s\-]?\d{4}[\s\-]?\d{4}(?!\d)""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    fun classify(ocr: OcrDocument): TypePrediction {
        val text = ocr.rawText.lowercase()
        val compact = text.replace(Regex("\\s+"), " ")
        val scores = linkedMapOf<DocumentType, Float>()

        var pan = 0f
        if (compact.contains("income tax")) pan += 3f
        if (compact.contains("permanent account number")) pan += 4f
        if (PAN_PATTERN.containsMatchIn(ocr.rawText)) pan += 5f
        if (compact.contains("government of india")) pan += 1f
        if (compact.contains("father's name") || compact.contains("father name")) pan += 2f
        scores[DocumentType.PAN] = pan

        var aadhaar = 0f
        if (compact.contains("aadhaar") || compact.contains("aadhar")) aadhaar += 4f
        if (compact.contains("uidai")) aadhaar += 4f
        if (AADHAAR_PATTERN.containsMatchIn(ocr.rawText)) aadhaar += 4f
        if (compact.contains("government of india")) aadhaar += 1f
        if (Regex("(?i)\\b(male|female|transgender)\\b").containsMatchIn(ocr.rawText)) aadhaar += 1f
        scores[DocumentType.AADHAAR] = aadhaar

        var resume = 0f
        if (compact.contains("resume") || compact.contains("curriculum vitae")) resume += 4f
        if (EMAIL.containsMatchIn(ocr.rawText)) resume += 3f
        if (ExtractionUtils.PHONE_REGEX.containsMatchIn(ocr.rawText)) resume += 2f
        if (compact.contains("linkedin") || compact.contains("github")) resume += 2f
        listOf("experience", "education", "skills", "projects", "certifications").forEach { section ->
            if (Regex("(?im)^\\s*${section}s?\\s*:?\\s*$").containsMatchIn(ocr.rawText)) resume += 2f
        }
        if (pan > 2f || aadhaar > 2f) resume -= 3f
        scores[DocumentType.RESUME] = resume.coerceAtLeast(0f)

        var education = 0f
        if (Regex("(?i)\\b(university|college|institute|school|academy)\\b").containsMatchIn(ocr.rawText)) education += 4f
        if (Regex("(?i)\\b(bachelor|master|b\\.?tech|m\\.?tech|b\\.?sc|m\\.?sc|mba|diploma|degree)\\b")
                .containsMatchIn(ocr.rawText)) education += 4f
        if (Regex("(?i)\\b(certificate|marksheet|convocation|awarded|passed)\\b").containsMatchIn(ocr.rawText)) education += 2f
        if (Regex("(?i)\\b(cgpa|gpa|percentage|first class)\\b").containsMatchIn(ocr.rawText)) education += 2f
        if (pan > 2f || aadhaar > 2f || compact.contains("offer letter")) education -= 2f
        scores[DocumentType.EDUCATION] = education.coerceAtLeast(0f)

        var bank = 0f
        if (IFSC_PATTERN.containsMatchIn(ocr.rawText)) bank += 6f
        if (Regex("(?i)\\b(account\\s*(no|number)|a/c\\s*(no|number))\\b").containsMatchIn(ocr.rawText)) bank += 4f
        if (Regex("(?i)\\bbank\\b").containsMatchIn(ocr.rawText)) bank += 2f
        if (Regex("(?i)\\bbranch\\b").containsMatchIn(ocr.rawText)) bank += 2f
        if (Regex("(?i)\\b(savings?|current account|salary account)\\b").containsMatchIn(ocr.rawText)) bank += 2f
        scores[DocumentType.BANK] = bank

        var offer = 0f
        if (Regex("(?i)\\b(offer letter|appointment letter|experience certificate|relieving letter|employment letter)\\b")
                .containsMatchIn(ocr.rawText)) offer += 5f
        if (Regex("(?i)\\bdear\\b").containsMatchIn(ocr.rawText)) offer += 2f
        if (Regex("(?i)\\bdesignation\\b|\\bposition of\\b").containsMatchIn(ocr.rawText)) offer += 2f
        if (Regex("(?i)\\b(pvt\\.?|private|limited|ltd\\.?|llp|technologies|solutions)\\b").containsMatchIn(ocr.rawText)) offer += 2f
        if (Regex("(?i)\\b(joining|date of relieving|last working day|ctc|salary)\\b").containsMatchIn(ocr.rawText)) offer += 2f
        scores[DocumentType.OFFER] = offer

        val sorted = scores.entries.sortedByDescending { it.value }
        val top = sorted.firstOrNull() ?: return TypePrediction(DocumentType.RESUME, 0f, scores)
        val second = sorted.getOrNull(1)?.value ?: 0f
        val confidence = when {
            top.value <= 0f -> 0f
            second <= 0f -> 0.95f
            else -> (top.value / (top.value + second)).coerceIn(0.35f, 0.95f)
        }
        return TypePrediction(top.key, confidence, scores)
    }
}
