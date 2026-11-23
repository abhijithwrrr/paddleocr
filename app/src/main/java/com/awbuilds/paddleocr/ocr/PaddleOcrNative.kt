package com.awbuilds.paddleocr.ocr

import android.graphics.Bitmap
import androidx.annotation.Keep

@Keep
object PaddleOcrNative {

    init {
        System.loadLibrary("paddleocr")
    }

    external fun nativeInit(
        detModelPath: String,
        recModelPath: String,
        clsModelPath: String,
        configPath: String,
        dictPath: String,
        cpuThreadNum: Int,
        cpuPowerMode: String
    ): Long

    external fun nativeRecognize(
        handle: Long,
        bitmap: Bitmap,
        maxSideLen: Int
    ): Array<OcrLineNative>?

    external fun nativeRelease(handle: Long)
}
