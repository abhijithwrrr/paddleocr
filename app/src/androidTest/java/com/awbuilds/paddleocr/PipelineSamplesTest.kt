package com.awbuilds.paddleocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.paddleocr.data.DocumentLoader
import com.awbuilds.paddleocr.domain.extraction.ExtractionEngine
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.OcrDocument
import com.awbuilds.paddleocr.ocr.PaddleOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PipelineSamplesTest {

    @Test
    fun allSamplesProduceExpectedFields() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = DocumentLoader(context)
        val engine = PaddleOcrEngine(context)
        val expectations = mapOf(
            DocumentType.PAN to ("panNumber" to "ABCDE1234F"),
            DocumentType.AADHAAR to ("aadhaarNumber" to "9012"),
            DocumentType.RESUME to ("email" to "arjun.mehta@example.com"),
            DocumentType.EDUCATION to ("institution" to "NATIONAL"),
            DocumentType.BANK to ("ifsc" to "SBIN0001234"),
            DocumentType.OFFER to ("employerName" to "ACME")
        )

        try {
            engine.initialize()
            for ((type, expectation) in expectations) {
                val bitmap = loader.loadAsset(type.sampleAsset)
                try {
                    val lines = engine.recognize(bitmap)
                    val analysis = ExtractionEngine.analyze(
                        OcrDocument.singlePage(lines),
                        type
                    )
                    val value = analysis.extraction.fields[expectation.first]?.value
                    println("SAMPLE ${type.name}: ${expectation.first}=$value")
                    assertTrue(
                        "Expected ${expectation.first} of ${type.name} to contain " +
                            "'${expectation.second}', got '$value'",
                        value?.contains(expectation.second, ignoreCase = true) == true
                    )
                    if (type == DocumentType.RESUME) {
                        assertFalse(
                            "Resume certifications should not include the sample footer",
                            analysis.extraction.fields["certifications"]?.value
                                ?.contains("SAMPLE DOCUMENT", ignoreCase = true) == true
                        )
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            engine.release()
        }
    }

    @Test
    fun pdfLoaderRendersAllPages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "sample_documents.pdf")
        context.assets.open("samples/sample_documents.pdf").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            val loaded = DocumentLoader(context).load(Uri.fromFile(file))
            assertEquals(2, loaded.pages.size)
            loaded.pages.forEach { it.recycle() }
        } finally {
            file.delete()
        }
    }
}
