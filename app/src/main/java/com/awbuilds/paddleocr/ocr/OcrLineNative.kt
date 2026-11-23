package com.awbuilds.paddleocr.ocr

import androidx.annotation.Keep

/**
 * Mirrors the C++ result object. The constructor signature is part of the JNI
 * contract and must stay `(String, float, int[])`.
 */
@Keep
class OcrLineNative(
    val text: String,
    val confidence: Float,
    val points: IntArray
)
