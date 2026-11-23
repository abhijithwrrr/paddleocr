package com.awbuilds.paddleocr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.viewmodel.SlotStatus

@Composable
fun DocBadge(type: DocumentType, modifier: Modifier = Modifier) {
    val color = badgeColor(type)
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type.shortLabel,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
fun StatusChip(status: SlotStatus) {
    val (label, color) = when (status) {
        SlotStatus.EMPTY -> "Not added" to MaterialTheme.colorScheme.onSurfaceVariant
        SlotStatus.PROCESSING -> "Reading" to MaterialTheme.colorScheme.tertiary
        SlotStatus.NEEDS_REVIEW -> "Review" to MaterialTheme.colorScheme.tertiary
        SlotStatus.READY -> "Ready" to MaterialTheme.colorScheme.secondary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun ConfidenceBadge(confidence: Float, needsReview: Boolean) {
    val (label, color) = when {
        needsReview && confidence <= 0f -> "Missing" to MaterialTheme.colorScheme.error
        needsReview -> "Check" to MaterialTheme.colorScheme.tertiary
        else -> "High" to MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.11f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun badgeColor(type: DocumentType): Color = when (type) {
    DocumentType.PAN -> Color(0xFF1D5C8A)
    DocumentType.AADHAAR -> Color(0xFF9A5B1E)
    DocumentType.RESUME -> Color(0xFF2F6B57)
    DocumentType.EDUCATION -> Color(0xFF5B4B9A)
    DocumentType.BANK -> Color(0xFF8A3A3A)
    DocumentType.OFFER -> Color(0xFF3C5A8A)
}
