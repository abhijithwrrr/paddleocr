package com.awbuilds.paddleocr.ocr

import android.content.Context
import android.graphics.Bitmap
import com.awbuilds.paddleocr.domain.model.OcrLine
import com.awbuilds.paddleocr.domain.model.OcrPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thin Kotlin facade over the Paddle-Lite / PP-OCRv5 native pipeline.
 * Initialization is expensive (model loading), inference is serialized.
 */
class PaddleOcrEngine(private val context: Context) {

    private val mutex = Mutex()
    private var handle: Long = 0L
    private var paths: ModelPaths? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (handle != 0L) return@withContext
        mutex.withLock {
            if (handle != 0L) return@withLock
            val modelPaths = AssetModelManager.ensureModels(context)
            val newHandle = PaddleOcrNative.nativeInit(
                detModelPath = modelPaths.det,
                recModelPath = modelPaths.rec,
                clsModelPath = modelPaths.cls,
                configPath = modelPaths.config,
                dictPath = modelPaths.dict,
                cpuThreadNum = 4,
                cpuPowerMode = "LITE_POWER_NO_BIND"
            )
            if (newHandle == 0L) {
                throw IllegalStateException(
                    "PaddleOCR failed to initialize. Check the native logs and model assets."
                )
            }
            paths = modelPaths
            handle = newHandle
        }
    }

    suspend fun recognize(
        bitmap: Bitmap,
        page: Int = 0,
        maxSideLen: Int = 1280
    ): List<OcrLine> = withContext(Dispatchers.Default) {
        initialize()
        mutex.withLock {
            val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            try {
                val raw = PaddleOcrNative.nativeRecognize(handle, argb, maxSideLen)
                    ?: emptyArray()
                raw.mapNotNull { line ->
                    val text = line.text.trim()
                    if (text.isEmpty()) return@mapNotNull null
                    val points = line.points
                    val coords = (0 until points.size / 2).map { index ->
                        OcrPoint(points[index * 2].toFloat(), points[index * 2 + 1].toFloat())
                    }
                    OcrLine(
                        text = text,
                        confidence = line.confidence.coerceIn(0f, 1f),
                        points = coords,
                        page = page
                    )
                }
            } finally {
                if (argb !== bitmap) argb.recycle()
            }
        }
    }

    fun release() {
        val current = handle
        if (current != 0L) {
            PaddleOcrNative.nativeRelease(current)
            handle = 0L
        }
    }

    fun modelVersion(): String = paths?.rec?.substringAfterLast('/') ?: "PP-OCRv5 mobile"
}
