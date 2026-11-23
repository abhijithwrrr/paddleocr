package com.awbuilds.paddleocr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.ui.components.DocBadge
import com.awbuilds.paddleocr.ui.components.StatusChip
import com.awbuilds.paddleocr.viewmodel.AppUiState
import com.awbuilds.paddleocr.viewmodel.SlotStatus

@Composable
fun HomeScreen(
    state: AppUiState,
    contentPadding: PaddingValues,
    onPick: (DocumentType) -> Unit,
    onOpen: (DocumentType) -> Unit,
    onSample: (DocumentType) -> Unit
) {
    val completed = state.slots.count { it.value.status == SlotStatus.READY }
    val total = DocumentType.entries.size
    val progress = completed.toFloat() / total

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BrandHeader() }
        item { ProgressCard(completed, total, progress, state.reviewCount) }
        item { SampleSection(onSample) }
        item {
            SectionLabel(
                title = "Documents to collect",
                caption = "Tap a document to choose an image or PDF"
            )
        }
        items(DocumentType.entries) { type ->
            val slot = state.slots[type]
            DocumentCard(
                type = type,
                status = slot?.status ?: SlotStatus.EMPTY,
                fieldCount = slot?.result?.fields?.values?.count { !it.value.isNullOrBlank() } ?: 0,
                reviewCount = slot?.result?.extraction?.reviewCount ?: 0,
                onClick = {
                    if (slot?.result != null) onOpen(type) else onPick(type)
                },
                onAction = {
                    if (slot?.result != null) onOpen(type) else onPick(type)
                }
            )
        }
        item {
            Spacer(Modifier.height(10.dp))
            PrivacyFooter()
        }
    }
}

@Composable
private fun SampleSection(onSample: (DocumentType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(
            title = "Try a sample",
            caption = "Bundled, fictional documents for a quick demo"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DocumentType.entries, key = { it.key }) { type ->
                AssistChip(
                    onClick = { onSample(type) },
                    label = { Text(type.shortLabel) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "ONBOARD OCR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 1.6.sp
            )
        }
        Text(
            text = "Candidate documents,\nread on this phone.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "PaddleOCR extracts the text. On-device rules turn it into reviewable fields.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProgressCard(completed: Int, total: Int, progress: Float, reviewCount: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF123B5D), Color(0xFF1B5F73))
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "$completed of $total documents ready",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = when {
                            reviewCount > 0 -> "$reviewCount need a quick review"
                            completed == total -> "Everything is reviewed"
                            else -> "Add the documents your onboarding needs"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCFE3F5)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = Color(0xFF7BD8C4),
                trackColor = Color.White.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun DocumentCard(
    type: DocumentType,
    status: SlotStatus,
    fieldCount: Int,
    reviewCount: Int,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(enabled = true),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocBadge(type)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = type.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    if (status == SlotStatus.PROCESSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                Text(
                    text = when {
                        status == SlotStatus.READY -> "$fieldCount fields captured"
                        status == SlotStatus.NEEDS_REVIEW -> "$reviewCount fields need review"
                        status == SlotStatus.PROCESSING -> "Reading document..."
                        else -> type.description
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                StatusChip(status)
            }
            TextButton(onClick = onAction) {
                Text(
                    text = if (status == SlotStatus.EMPTY) "Add" else "View",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivacyFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ON-DEVICE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Documents stay on this phone. Aadhaar and account numbers are masked in the export.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
