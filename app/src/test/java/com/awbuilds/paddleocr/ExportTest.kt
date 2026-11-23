package com.awbuilds.paddleocr

import com.awbuilds.paddleocr.data.JsonExporter
import com.awbuilds.paddleocr.domain.extraction.ExtractionEngine
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.OcrDocument
import com.awbuilds.paddleocr.domain.model.OcrLine
import com.awbuilds.paddleocr.domain.model.OcrPage
import com.awbuilds.paddleocr.domain.model.OcrPoint
import com.awbuilds.paddleocr.domain.model.ScanResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    private fun sampleScan(): ScanResult {
        val lines = listOf(
            "INCOME TAX DEPARTMENT",
            "Name",
            "RAHUL SHARMA",
            "Father's Name",
            "VIJAY SHARMA",
            "Date of Birth",
            "1 January 1990",
            "ABCDE1234F"
        ).mapIndexed { index, text ->
            OcrLine(
                text = text,
                confidence = 0.95f,
                points = listOf(
                    OcrPoint(0f, index * 30f),
                    OcrPoint(500f, index * 30f),
                    OcrPoint(500f, index * 30f + 24f),
                    OcrPoint(0f, index * 30f + 24f)
                )
            )
        }
        val ocr = OcrDocument(listOf(OcrPage(0, lines)))
        val extraction = ExtractionEngine.extract(ocr, DocumentType.PAN)
        return ScanResult(
            expectedType = DocumentType.PAN,
            extraction = extraction,
            ocr = ocr,
            pageCount = 1,
            elapsedMs = 1234
        )
    }

    @Test
    fun jsonContainsStructuredFieldsAndMetadata() {
        val json = JsonExporter.toJson(sampleScan())
        assertTrue(json.contains("\"documentType\": \"PAN\""))
        assertTrue(json.contains("\"panNumber\""))
        assertTrue(json.contains("\"ABCDE1234F\""))
        assertTrue(json.contains("\"needsReview\""))
        assertTrue(json.contains("\"confidence\""))
        assertFalse("Raw OCR should be excluded by default", json.contains("\"rawText\""))
    }

    @Test
    fun jsonCanIncludeRawTextOnRequest() {
        val json = JsonExporter.toJson(sampleScan(), includeRawText = true)
        assertTrue(json.contains("\"rawText\""))
        assertTrue(json.contains("INCOME TAX DEPARTMENT"))
    }

    @Test
    fun editingAFieldUpdatesTheResult() {
        val scan = sampleScan()
        val edited = scan.withField("fullName", "RAHUL K SHARMA")
        assertTrue(edited.fields["fullName"]?.value == "RAHUL K SHARMA")
        assertFalse(edited.fields["fullName"]?.needsReview == true)
        val json = JsonExporter.toJson(edited)
        assertTrue(json.contains("RAHUL K SHARMA"))
    }

    @Test
    fun documentTypeKeysRoundTrip() {
        DocumentType.entries.forEach { type ->
            assertNotNull(DocumentType.fromKey(type.key))
        }
    }
}
