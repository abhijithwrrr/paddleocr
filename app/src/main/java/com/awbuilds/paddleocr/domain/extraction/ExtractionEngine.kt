package com.awbuilds.paddleocr.domain.extraction

import com.awbuilds.paddleocr.domain.extraction.extractors.AadhaarExtractor
import com.awbuilds.paddleocr.domain.extraction.extractors.BankExtractor
import com.awbuilds.paddleocr.domain.extraction.extractors.EducationExtractor
import com.awbuilds.paddleocr.domain.extraction.extractors.OfferExtractor
import com.awbuilds.paddleocr.domain.extraction.extractors.PanExtractor
import com.awbuilds.paddleocr.domain.extraction.extractors.ResumeExtractor
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.OcrDocument

data class ScanAnalysis(
    val detectedType: DocumentType,
    val detectedConfidence: Float,
    val extraction: ExtractionResult,
    val warnings: List<String>
)

object ExtractionEngine {

    fun extract(
        ocr: OcrDocument,
        type: DocumentType,
        typeConfidence: Float = 1f,
        detectedType: DocumentType? = null
    ): ExtractionResult = when (type) {
        DocumentType.PAN -> PanExtractor.extract(ocr, typeConfidence, detectedType)
        DocumentType.AADHAAR -> AadhaarExtractor.extract(ocr, typeConfidence, detectedType)
        DocumentType.RESUME -> ResumeExtractor.extract(ocr, typeConfidence, detectedType)
        DocumentType.EDUCATION -> EducationExtractor.extract(ocr, typeConfidence, detectedType)
        DocumentType.BANK -> BankExtractor.extract(ocr, typeConfidence, detectedType)
        DocumentType.OFFER -> OfferExtractor.extract(ocr, typeConfidence, detectedType)
    }

    fun analyze(ocr: OcrDocument, expectedType: DocumentType?): ScanAnalysis {
        val prediction = DocumentClassifier.classify(ocr)
        val warnings = mutableListOf<String>()

        val effectiveType = when {
            expectedType != null -> expectedType
            prediction.confidence >= 0.35f -> prediction.type
            else -> {
                warnings.add("Could not confidently identify the document type; defaulted to Resume.")
                DocumentType.RESUME
            }
        }

        if (expectedType != null && prediction.type != expectedType && prediction.confidence >= 0.55f) {
            warnings.add(
                "This document looks more like ${prediction.type.title} " +
                    "(${Math.round(prediction.confidence * 100)}% match), but it was read as " +
                    "${expectedType.title}. You can change the type on the review screen."
            )
        }

        val typeConfidence = if (expectedType != null && prediction.type == expectedType) {
            prediction.confidence.coerceAtLeast(0.8f)
        } else if (expectedType != null) {
            0.6f
        } else {
            prediction.confidence
        }

        val extraction = extract(ocr, effectiveType, typeConfidence, prediction.type)
        warnings.addAll(extraction.warnings)
        return ScanAnalysis(
            detectedType = prediction.type,
            detectedConfidence = prediction.confidence,
            extraction = if (warnings == extraction.warnings) {
                extraction
            } else {
                extraction.copy(warnings = warnings.distinct())
            },
            warnings = warnings.distinct()
        )
    }

    fun reextract(ocr: OcrDocument, type: DocumentType): ExtractionResult =
        extract(ocr, type, typeConfidence = 1f, detectedType = type)
}
