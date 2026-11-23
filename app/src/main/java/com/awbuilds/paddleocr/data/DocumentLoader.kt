package com.awbuilds.paddleocr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class DocumentLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class LoadedDocument(val pages: List<Bitmap>) {
    val preview: Bitmap? get() = pages.firstOrNull()
}

class DocumentLoader(private val context: Context) {

    suspend fun load(uri: Uri): LoadedDocument = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        if (isPdf(uri, mime)) {
            loadPdf(uri)
        } else {
            val bitmap = loadImage(uri)
            LoadedDocument(listOf(bitmap))
        }
    }

    fun loadAsset(path: String): Bitmap {
        val decoded = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            ?: throw DocumentLoadException("The bundled sample image could not be decoded.")
        return downscale(decoded, MAX_IMAGE_DIMENSION)
    }

    private fun isPdf(uri: Uri, mime: String?): Boolean {
        if (mime == "application/pdf") return true
        if (uri.toString().lowercase().endsWith(".pdf")) return true
        val displayName = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        return displayName?.lowercase()?.endsWith(".pdf") == true
    }

    private fun loadImage(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw DocumentLoadException("The selected file could not be opened.")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DocumentLoadException("The selected file is not a readable image.")
        }

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIMENSION)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw DocumentLoadException("The selected image could not be decoded.")

        val oriented = applyExifOrientation(uri, decoded)
        return downscale(oriented, MAX_IMAGE_DIMENSION)
    }

    private fun loadPdf(uri: Uri): LoadedDocument {
        val pfd: ParcelFileDescriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw DocumentLoadException("The PDF could not be opened.")
        } catch (e: Exception) {
            throw DocumentLoadException("The PDF could not be opened.", e)
        }

        try {
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: Exception) {
                throw DocumentLoadException(
                    "This PDF could not be read. Password-protected PDFs are not supported.",
                    e
                )
            }
            renderer.use { pdf ->
                val pageCount = min(pdf.pageCount, MAX_PDF_PAGES)
                if (pageCount <= 0) throw DocumentLoadException("The PDF has no pages.")
                val pages = ArrayList<Bitmap>(pageCount)
                for (index in 0 until pageCount) {
                    pdf.openPage(index).use { page ->
                        val maxSide = max(page.width, page.height).toFloat().coerceAtLeast(1f)
                        val scale = (MAX_PDF_DIMENSION / maxSide).coerceIn(0.5f, 2f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bitmap)
                    }
                }
                return LoadedDocument(pages)
            }
        } catch (e: DocumentLoadException) {
            throw e
        } catch (e: IOException) {
            throw DocumentLoadException("The PDF could not be rendered.", e)
        } finally {
            try {
                pfd.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var largest = max(width, height)
        while (largest / 2 >= maxDimension) {
            largest /= 2
            sample *= 2
        }
        return sample
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 2400
        private const val MAX_PDF_DIMENSION = 2000f
        private const val MAX_PDF_PAGES = 8
    }
}
