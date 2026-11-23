package com.awbuilds.paddleocr.domain.extraction

import com.awbuilds.paddleocr.domain.model.OcrLine
import kotlin.math.abs
import kotlin.math.max

data class LabeledValue(val value: String, val confidence: Float, val source: String)

data class TextRow(
    val lines: List<OcrLine>,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    val text: String,
    val confidence: Float
) {
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Turns raw OCR lines into reading-order rows and provides label → value
 * helpers. This is deliberately geometry-light: onboarding documents are
 * mostly single-column, and the review screen catches the rest.
 */
class TextLayout(val lines: List<OcrLine>) {

    val rows: List<TextRow> = buildRows(lines)
    val text: String = rows.joinToString("\n") { it.text }
    val singleLine: String = rows.joinToString(" ") { it.text }
    val confidence: Float = if (lines.isEmpty()) 0f else lines.map { it.confidence }.average().toFloat()

    fun linesMatching(regex: Regex): List<OcrLine> = lines.filter { regex.containsMatchIn(it.text) }

    fun firstLineMatching(regex: Regex): OcrLine? = lines.firstOrNull { regex.containsMatchIn(it.text) }

    fun rowsMatching(regex: Regex): List<TextRow> = rows.filter { regex.containsMatchIn(it.text) }

    /**
     * Finds the value associated with a label. Checks, in order:
     * 1. value after a colon on the same row,
     * 2. the next 1-2 rows (most ID cards put the value on the next line),
     * 3. a regex match anywhere in the text.
     */
    fun valueNear(
        labels: List<Regex>,
        valueRegex: Regex? = null,
        maxRowsBelow: Int = 2,
        stopLabels: List<Regex> = emptyList()
    ): LabeledValue? {
        for (label in labels) {
            for ((index, row) in rows.withIndex()) {
                val match = label.find(row.text) ?: continue

                val inline = row.text
                    .substring(match.range.last + 1)
                    .trim()
                    .trimStart(':', '-', '–', '—', '.')
                    .trim()

                if (inline.isNotBlank() && !looksLikeLabelOnly(inline) && !containsOtherLabel(inline, stopLabels)) {
                    val value = if (valueRegex == null) inline else valueRegex.find(inline)?.value?.trim()
                    if (!value.isNullOrBlank()) {
                        return LabeledValue(cleanValue(value), row.confidence, row.text)
                    }
                }

                if (valueRegex != null) {
                    // A value on the same row may have been merged into the row text.
                    val sameRowMatch = valueRegex.find(row.text)
                    if (sameRowMatch != null && sameRowMatch.range.first > match.range.last) {
                        return LabeledValue(cleanValue(sameRowMatch.value), row.confidence, row.text)
                    }
                }

                for (offset in 1..maxRowsBelow) {
                    val next = rows.getOrNull(index + offset) ?: break
                    if (next.top - row.bottom > row.height * 3.5f) break
                    if (containsOtherLabel(next.text, stopLabels)) break
                    val raw = cleanValue(next.text)
                    if (raw.isBlank() || looksLikeLabelOnly(raw)) continue
                    val value = if (valueRegex == null) raw else valueRegex.find(raw)?.value?.trim()
                    if (!value.isNullOrBlank()) {
                        return LabeledValue(cleanValue(value), next.confidence, next.text)
                    }
                }
            }
        }

        if (valueRegex != null) {
            val match = valueRegex.find(singleLine) ?: valueRegex.find(text)
            if (match != null) {
                return LabeledValue(cleanValue(match.value), confidence, match.value)
            }
        }
        return null
    }

    fun valueAfter(
        label: Regex,
        valueRegex: Regex? = null,
        maxRowsBelow: Int = 2,
        stopLabels: List<Regex> = emptyList()
    ): LabeledValue? = valueNear(listOf(label), valueRegex, maxRowsBelow, stopLabels)

    fun sectionBetween(start: List<Regex>, end: List<Regex>, maxRows: Int = 60): List<String> {
        val startIndex = rows.indexOfFirst { row -> start.any { it.containsMatchIn(row.text) } }
        if (startIndex < 0) return emptyList()
        val result = mutableListOf<String>()
        for (index in startIndex + 1 until rows.size) {
            val row = rows[index]
            if (end.any { it.containsMatchIn(row.text) }) break
            if (result.size >= maxRows) break
            val cleaned = cleanValue(row.text)
            if (cleaned.isNotBlank() && cleaned.length > 1) result.add(cleaned)
        }
        return result
    }

    private fun containsOtherLabel(value: String, labels: List<Regex>): Boolean {
        if (labels.isEmpty()) return false
        return labels.any { it.containsMatchIn(value) }
    }

    private fun looksLikeLabelOnly(value: String): Boolean {
        val normalized = value.lowercase().trim().trimEnd(':').trim()
        if (normalized.length > 40) return false
        return normalized in LABEL_WORDS || normalized.split(" ").all { it in LABEL_WORD_SET }
    }

    private fun buildRows(input: List<OcrLine>): List<TextRow> {
        if (input.isEmpty()) return emptyList()
        val sorted = input.sortedWith(compareBy({ it.top }, { it.left }))
        val groups = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            var placed = false
            for (group in groups) {
                val groupTop = group.minOf { it.top }
                val groupBottom = group.maxOf { it.bottom }
                val groupCenter = (groupTop + groupBottom) / 2f
                val overlap = max(0f, minOf(groupBottom, line.bottom) - maxOf(groupTop, line.top))
                val overlapRatio = overlap / minOf(groupBottom - groupTop, line.height).coerceAtLeast(1f)
                val sameVisualRow = overlapRatio > 0.45f ||
                    abs(groupCenter - line.centerY) < minOf(groupBottom - groupTop, line.height) * 0.55f
                if (sameVisualRow) {
                    group.add(line)
                    placed = true
                    break
                }
            }
            if (!placed) groups.add(mutableListOf(line))
        }

        return groups.map { group ->
            val ordered = group.sortedBy { it.left }
            val top = ordered.minOf { it.top }
            val bottom = ordered.maxOf { it.bottom }
            val left = ordered.minOf { it.left }
            val right = ordered.maxOf { it.right }
            val text = cleanValue(ordered.joinToString(" ") { it.text })
            TextRow(
                lines = ordered,
                top = top,
                bottom = bottom,
                left = left,
                right = right,
                text = text,
                confidence = ordered.map { it.confidence }.average().toFloat()
            )
        }.sortedWith(compareBy({ it.top }, { it.left }))
    }

    companion object {
        private val LABEL_WORDS = setOf(
            "name", "father name", "father's name", "mother name", "date of birth", "dob",
            "address", "gender", "pan", "aadhaar", "account number", "account no", "ifsc",
            "branch", "bank", "designation", "date of joining", "joining date", "signature",
            "mobile", "phone", "email", "resume", "curriculum vitae"
        )
        private val LABEL_WORD_SET = LABEL_WORDS.flatMap { it.split(" ") }.toSet() +
            setOf("father's", "date", "of", "birth", "no", "number", "account", "holder")
    }
}

fun cleanValue(value: String): String = value
    .replace('\u00A0', ' ')
    .replace(Regex("[\\t\\r]+"), " ")
    .replace(Regex("\\s{2,}"), " ")
    .trim()
    .trimStart(':', '-', '–', '—', '|')
    .trim()

fun normalizeSpaces(value: String): String = cleanValue(value).lowercase().replace(Regex("\\s+"), " ")
