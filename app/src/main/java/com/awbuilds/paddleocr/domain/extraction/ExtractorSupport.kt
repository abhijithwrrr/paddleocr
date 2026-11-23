package com.awbuilds.paddleocr.domain.extraction

import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.StructuredField

internal class FieldBuilder(
    private val type: DocumentType,
    private val typeConfidence: Float,
    private val detectedType: DocumentType?
) {
    private val fields = LinkedHashMap<String, StructuredField>()
    private val warnings = mutableListOf<String>()

    fun add(
        key: String,
        label: String,
        value: String?,
        confidence: Float,
        isList: Boolean = false
    ) {
        val cleaned = when {
            value == null -> null
            isList -> value.lines().map { it.trim() }.filter { it.isNotBlank() }
                .joinToString("\n").ifBlank { null }
            else -> cleanValue(value).ifBlank { null }
        }
        val finalConfidence = if (cleaned == null) 0f else confidence.coerceIn(0f, 1f)
        fields[key] = StructuredField(
            key = key,
            label = label,
            value = cleaned,
            confidence = finalConfidence,
            needsReview = cleaned == null || finalConfidence < REVIEW_THRESHOLD,
            isList = isList
        )
    }

    fun addMissing(key: String, label: String, isList: Boolean = false) {
        add(key, label, null, 0f, isList)
    }

    fun warn(message: String) {
        if (message.isNotBlank()) warnings.add(message)
    }

    fun build(): ExtractionResult = ExtractionResult(
        documentType = type,
        typeConfidence = typeConfidence,
        detectedType = detectedType,
        fields = fields,
        warnings = warnings
    )

    companion object {
        const val REVIEW_THRESHOLD = 0.74f
    }
}
