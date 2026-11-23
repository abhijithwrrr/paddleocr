package com.awbuilds.paddleocr.viewmodel

import android.graphics.Bitmap
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ScanResult

sealed interface AppScreen {
    data object Home : AppScreen
    data class Processing(
        val stage: String,
        val preview: Bitmap? = null
    ) : AppScreen

    data class Review(
        val slotType: DocumentType,
        val result: ScanResult,
        val preview: Bitmap?
    ) : AppScreen
}

enum class SlotStatus { EMPTY, PROCESSING, NEEDS_REVIEW, READY }

data class SlotState(
    val status: SlotStatus = SlotStatus.EMPTY,
    val result: ScanResult? = null,
    val preview: Bitmap? = null
)

data class AppUiState(
    val screen: AppScreen = AppScreen.Home,
    val slots: Map<DocumentType, SlotState> = DocumentType.entries.associateWith { SlotState() },
    val pendingType: DocumentType? = null,
    val error: String? = null,
    val showExport: Boolean = false
) {
    val completedCount: Int get() = slots.values.count { it.status == SlotStatus.READY }
    val reviewCount: Int get() = slots.values.count { it.status == SlotStatus.NEEDS_REVIEW }
}
