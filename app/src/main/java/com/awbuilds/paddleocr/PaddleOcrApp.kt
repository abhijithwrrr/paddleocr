package com.awbuilds.paddleocr

import android.app.Application
import com.awbuilds.paddleocr.ocr.PaddleOcrEngine

class PaddleOcrApp : Application() {
    val ocrEngine: PaddleOcrEngine by lazy { PaddleOcrEngine(this) }
}
