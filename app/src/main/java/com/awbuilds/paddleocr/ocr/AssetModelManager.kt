package com.awbuilds.paddleocr.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ModelPaths(
    val det: String,
    val rec: String,
    val cls: String,
    val config: String,
    val dict: String
)

/**
 * Copies the PP-OCRv5 .nb models out of assets once. Paddle-Lite's
 * set_model_from_file() needs a real file path; APK assets are not files.
 */
object AssetModelManager {

    private const val VERSION = "ppocrv5-mobile-1"
    private const val ASSET_DIR = "ocr"
    private const val TARGET_DIR = "paddleocr_models"

    private val FILES = listOf(
        "PP-OCRv5_mobile_det.nb",
        "PP-OCRv5_mobile_rec.nb",
        "PP-LCNet_x0_25_textline_ori.nb",
        "config.txt",
        "ppocr_keys_ocrv5.txt"
    )

    suspend fun ensureModels(context: Context): ModelPaths = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, TARGET_DIR)
        if (!dir.exists()) dir.mkdirs()

        val versionFile = File(dir, "version.txt")
        val upToDate = versionFile.exists() && versionFile.readText().trim() == VERSION &&
            FILES.all { File(dir, it).length() > 0L }

        if (!upToDate) {
            FILES.forEach { name ->
                val target = File(dir, name)
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output, bufferSize = 256 * 1024)
                    }
                }
            }
            versionFile.writeText(VERSION)
        }

        ModelPaths(
            det = File(dir, "PP-OCRv5_mobile_det.nb").absolutePath,
            rec = File(dir, "PP-OCRv5_mobile_rec.nb").absolutePath,
            cls = File(dir, "PP-LCNet_x0_25_textline_ori.nb").absolutePath,
            config = File(dir, "config.txt").absolutePath,
            dict = File(dir, "ppocr_keys_ocrv5.txt").absolutePath
        )
    }
}
