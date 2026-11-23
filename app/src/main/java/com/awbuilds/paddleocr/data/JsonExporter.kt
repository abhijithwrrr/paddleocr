package com.awbuilds.paddleocr.data

import com.awbuilds.paddleocr.domain.model.ScanResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import kotlin.math.roundToInt

object JsonExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(result: ScanResult, includeRawText: Boolean = false): String {
        val payload = buildJsonObject {
            put("schemaVersion", "1.0")
            put("documentType", result.documentType.name)
            put("documentTitle", result.documentType.title)
            put("typeConfidence", round(result.extraction.typeConfidence))

            putJsonObject("fields") {
                result.fields.values.forEach { field ->
                    putJsonObject(field.key) {
                        put("label", field.label)
                        if (field.isList) {
                            putJsonArray("value") {
                                field.value?.lines()
                                    ?.map { it.trim() }
                                    ?.filter { it.isNotBlank() }
                                    ?.forEach { add(it) }
                            }
                        } else {
                            put("value", field.value)
                        }
                        put("confidence", round(field.confidence))
                        put("needsReview", field.needsReview)
                    }
                }
            }

            putJsonObject("meta") {
                put("engine", "PaddleOCR PP-OCRv5 mobile / Paddle-Lite")
                put("pages", result.pageCount)
                put("processedAt", Instant.now().toString())
                put("elapsedMs", result.elapsedMs)
                put("expectedType", result.expectedType?.name)
                put("detectedType", result.extraction.detectedType?.name)
                put("detectedTypeConfidence", round(result.extraction.typeConfidence))
                putJsonArray("warnings") {
                    result.extraction.warnings.forEach { add(it) }
                }
                if (includeRawText) put("rawText", result.ocr.rawText)
            }
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun round(value: Float): Double = (value * 100.0).roundToInt() / 100.0
}
