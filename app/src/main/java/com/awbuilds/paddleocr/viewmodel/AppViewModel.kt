package com.awbuilds.paddleocr.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.awbuilds.paddleocr.PaddleOcrApp
import com.awbuilds.paddleocr.data.DocumentLoader
import com.awbuilds.paddleocr.domain.extraction.ExtractionEngine
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.OcrDocument
import com.awbuilds.paddleocr.domain.model.OcrPage
import com.awbuilds.paddleocr.domain.model.ScanResult
import com.awbuilds.paddleocr.ocr.PaddleOcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PaddleOcrApp
    private val loader = DocumentLoader(application)
    private val engine: PaddleOcrEngine = app.ocrEngine

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var processingJob: Job? = null

    fun requestPicker(type: DocumentType) {
        _state.update { it.copy(pendingType = type, error = null) }
    }

    fun clearPendingPicker() {
        _state.update { it.copy(pendingType = null) }
    }

    fun onDocumentPicked(uri: Uri) {
        val type = _state.value.pendingType ?: DocumentType.PAN
        _state.update { it.copy(pendingType = null, error = null) }
        startProcessing(type) { loader.load(uri) }
    }

    fun processSample(type: DocumentType) {
        _state.update { it.copy(error = null) }
        startProcessing(type) {
            val bitmap = withContext(Dispatchers.IO) { loader.loadAsset(type.sampleAsset) }
            com.awbuilds.paddleocr.data.LoadedDocument(listOf(bitmap))
        }
    }

    fun openResult(type: DocumentType) {
        val slot = _state.value.slots[type] ?: return
        val result = slot.result ?: return
        _state.update {
            it.copy(screen = AppScreen.Review(type, result, slot.preview), error = null)
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _state.update { it.copy(screen = AppScreen.Home) }
    }

    fun backHome() {
        processingJob?.cancel()
        processingJob = null
        _state.update { it.copy(screen = AppScreen.Home, showExport = false) }
    }

    fun updateField(key: String, value: String) {
        val current = _state.value
        val review = current.screen as? AppScreen.Review ?: return
        val updatedResult = review.result.withField(key, value)
        val slotState = current.slots[review.slotType]
        val needsReview = updatedResult.fields.values.any { it.needsReview }
        val updatedSlots = current.slots.toMutableMap()
        updatedSlots[review.slotType] = (slotState ?: SlotState()).copy(
            status = if (needsReview) SlotStatus.NEEDS_REVIEW else SlotStatus.READY,
            result = updatedResult,
            preview = review.preview
        )
        _state.update {
            it.copy(
                screen = review.copy(result = updatedResult),
                slots = updatedSlots
            )
        }
    }

    fun changeDocumentType(type: DocumentType) {
        val current = _state.value
        val review = current.screen as? AppScreen.Review ?: return
        val newExtraction = ExtractionEngine.reextract(review.result.ocr, type)
        val updated = review.result.copy(
            expectedType = type,
            extraction = newExtraction
        )
        val updatedSlots = current.slots.toMutableMap()
        val slot = updatedSlots[review.slotType]
        if (slot != null) {
            updatedSlots[review.slotType] = slot.copy(
                status = if (newExtraction.reviewCount > 0) SlotStatus.NEEDS_REVIEW else SlotStatus.READY,
                result = updated
            )
        }
        _state.update {
            it.copy(screen = review.copy(result = updated), slots = updatedSlots)
        }
    }

    fun showExport() {
        _state.update { it.copy(showExport = true) }
    }

    fun hideExport() {
        _state.update { it.copy(showExport = false) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun startProcessing(
        type: DocumentType,
        load: suspend () -> com.awbuilds.paddleocr.data.LoadedDocument
    ) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            var pages: List<Bitmap> = emptyList()
            try {
                _state.update { current ->
                    val slots = current.slots.toMutableMap()
                    slots[type] = (slots[type] ?: SlotState()).copy(status = SlotStatus.PROCESSING)
                    current.copy(
                        screen = AppScreen.Processing("Opening document"),
                        slots = slots,
                        error = null
                    )
                }

                val loaded = load()
                pages = loaded.pages
                val preview = loaded.preview
                updateStage("Preparing PaddleOCR models", preview)
                engine.initialize()

                val started = System.currentTimeMillis()
                val ocrPages = ArrayList<OcrPage>(pages.size)
                pages.forEachIndexed { index, bitmap ->
                    updateStage(
                        if (pages.size == 1) "Reading document" else "Reading page ${index + 1} of ${pages.size}",
                        preview
                    )
                    val lines = engine.recognize(bitmap, page = index, maxSideLen = 1280)
                    ocrPages.add(OcrPage(index, lines))
                }

                updateStage("Extracting structured fields", preview)
                val ocr = OcrDocument(ocrPages)
                val analysis = withContext(Dispatchers.Default) {
                    ExtractionEngine.analyze(ocr, type)
                }
                val result = ScanResult(
                    expectedType = type,
                    extraction = analysis.extraction.copy(warnings = analysis.warnings),
                    ocr = ocr,
                    pageCount = pages.size,
                    elapsedMs = System.currentTimeMillis() - started
                )

                val keepPreview = preview
                pages.forEach { if (it !== keepPreview) it.recycle() }

                Log.i(
                    "OnboardOcr",
                    "Extracted ${result.documentType.key}: " +
                        result.fields.values.joinToString { "${it.key}=${it.value}" }
                )
                _state.update { current ->
                    val slots = current.slots.toMutableMap()
                    slots[type] = SlotState(
                        status = if (result.fields.values.any { it.needsReview }) {
                            SlotStatus.NEEDS_REVIEW
                        } else {
                            SlotStatus.READY
                        },
                        result = result,
                        preview = keepPreview
                    )
                    current.copy(
                        screen = AppScreen.Review(type, result, keepPreview),
                        slots = slots
                    )
                }
            } catch (cancelled: CancellationException) {
                pages.forEach { it.recycle() }
                throw cancelled
            } catch (error: Exception) {
                pages.forEach { it.recycle() }
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: "Something went wrong while reading the document."
                _state.update { current ->
                    val slots = current.slots.toMutableMap()
                    slots[type] = SlotState()
                    current.copy(
                        screen = AppScreen.Home,
                        slots = slots,
                        error = message
                    )
                }
            } finally {
                processingJob = null
            }
        }
    }

    private fun updateStage(stage: String, preview: Bitmap?) {
        _state.update { current ->
            val processing = current.screen as? AppScreen.Processing ?: return@update current
            current.copy(screen = processing.copy(stage = stage, preview = preview ?: processing.preview))
        }
    }
}
