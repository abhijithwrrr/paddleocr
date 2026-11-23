package com.awbuilds.paddleocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.paddleocr.data.DocumentLoader
import com.awbuilds.paddleocr.ocr.PaddleOcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddleOcrEngineTest {

    @Test
    fun panSampleProducesText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = DocumentLoader(context).loadAsset("samples/sample_pan.png")
        val engine = PaddleOcrEngine(context)
        try {
            engine.initialize()
            val lines = engine.recognize(bitmap)
            val text = lines.joinToString("\n") { "${it.text} | ${it.confidence}" }
            println("OCR_RESULT_START\n$text\nOCR_RESULT_END")
            assertTrue("Expected some OCR text, got none", lines.isNotEmpty())
            assertTrue(
                "Expected PAN sample text, got:\n$text",
                lines.any { it.text.contains("RAHUL", ignoreCase = true) } ||
                    lines.any { it.text.contains("ABCDE1234F", ignoreCase = true) } ||
                    lines.any { it.text.contains("INCOME", ignoreCase = true) }
            )
        } finally {
            engine.release()
            bitmap.recycle()
        }
    }
}
