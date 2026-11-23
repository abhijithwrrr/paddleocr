package com.awbuilds.paddleocr.domain.extraction.extractors

import com.awbuilds.paddleocr.domain.extraction.ExtractionUtils
import com.awbuilds.paddleocr.domain.extraction.FieldBuilder
import com.awbuilds.paddleocr.domain.extraction.TextLayout
import com.awbuilds.paddleocr.domain.extraction.cleanValue
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.OcrDocument

object OfferExtractor {

    private val DATE_STOPS = listOf(
        Regex("(?i)^\\s*designation\\b"),
        Regex("(?i)^\\s*candidate\\b"),
        Regex("(?i)^\\s*salary\\b"),
        Regex("(?i)^\\s*ctc\\b")
    )

    fun extract(
        ocr: OcrDocument,
        typeConfidence: Float = 0.75f,
        detectedType: DocumentType? = DocumentType.OFFER
    ): ExtractionResult {
        val layout = TextLayout(ocr.allLines)
        val builder = FieldBuilder(DocumentType.OFFER, typeConfidence, detectedType)

        val employer = extractEmployer(layout)
        builder.add("employerName", "Employer", employer, if (employer != null) 0.78f else 0f)

        val candidate = extractCandidate(layout)
        builder.add("candidateName", "Candidate Name", candidate, if (candidate != null) 0.8f else 0f)

        val designation = extractDesignation(layout)
        builder.add("designation", "Designation", designation, if (designation != null) 0.76f else 0f)

        val joiningRaw = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*date\\s+of\\s+joining\\b"),
                Regex("(?i)^\\s*joining\\s+date\\b"),
                Regex("(?i)^\\s*date\\s+of\\s+appointment\\b"),
                Regex("(?i)^\\s*effective\\s+date\\b")
            ),
            stopLabels = DATE_STOPS
        )?.value
        val joining = ExtractionUtils.parseDate(joiningRaw)
        builder.add("dateOfJoining", "Date of Joining", joining, if (joining != null) 0.8f else 0f)

        val relievingRaw = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*date\\s+of\\s+relieving\\b"),
                Regex("(?i)^\\s*relieving\\s+date\\b"),
                Regex("(?i)^\\s*last\\s+working\\s+day\\b"),
                Regex("(?i)^\\s*date\\s+of\\s+exit\\b")
            ),
            stopLabels = DATE_STOPS
        )?.value
        val relieving = ExtractionUtils.parseDate(relievingRaw)
        builder.add("dateOfRelieving", "Date of Relieving", relieving, if (relieving != null) 0.8f else 0f)

        val employmentType = Regex("(?i)\\b(full[\\s-]?time|part[\\s-]?time|contract|internship|intern|permanent|probation|temporary)\\b")
            .find(layout.singleLine)?.groupValues?.get(1)
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
        builder.add("employmentType", "Employment Type", employmentType, if (employmentType != null) 0.7f else 0f)

        val ctc = extractCtc(layout)
        builder.add("annualCTC", "Annual CTC", ctc, if (ctc != null) 0.7f else 0f)

        if (employer == null) builder.warn("Employer name not detected from the letterhead.")
        if (joining == null && relieving == null) builder.warn("No joining/relieving date detected.")
        return builder.build()
    }

    private fun extractEmployer(layout: TextLayout): String? {
        val labelValue = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*(?:company|employer|organisation|organization)\\b")
            )
        )?.value
        labelValue?.let { value ->
            val normalized = ExtractionUtils.normalizeCompanySuffix(cleanValue(value))
            normalized.takeIf { it.length in 3..90 }?.let { return it }
        }

        val companySuffix = Regex("(?i)\\b(pvt\\.?|private|limited|ltd\\.?|llp|technologies|solutions|systems|services|corporation|inc\\.?)\\b")
        return layout.rows.take(3)
            .map { it.text }
            .firstOrNull { companySuffix.containsMatchIn(it) && !ExtractionUtils.PHONE_REGEX.containsMatchIn(it) }
            ?.let {
                ExtractionUtils.normalizeCompanySuffix(cleanValue(it))
                    .takeIf { value -> value.length in 3..90 }
            }
    }

    private fun extractCandidate(layout: TextLayout): String? {
        val dear = Regex("(?i)\\bdear\\s+(?:mr\\.?|mrs\\.?|ms\\.?|miss\\s+)?\\s*([A-Za-z][A-Za-z.'\\-]+(?:\\s+[A-Za-z][A-Za-z.'\\-]+){0,4})")
            .find(layout.singleLine)?.groupValues?.get(1)
        dear?.let(ExtractionUtils::cleanPersonName)?.let { return it }

        layout.valueNear(labels = listOf(Regex("(?i)^\\s*(?:candidate|employee)\\s+name\\b")))
            ?.value?.let(ExtractionUtils::cleanPersonName)?.let { return it }

        val salutation = Regex("(?i)\\b(?:mr\\.?|mrs\\.?|ms\\.?)\\s+([A-Z][A-Za-z.'\\-]+(?:\\s+[A-Z][A-Za-z.'\\-]+){1,3})")
            .find(layout.singleLine)?.groupValues?.get(1)
        return salutation?.let(ExtractionUtils::cleanPersonName)
    }

    private fun extractDesignation(layout: TextLayout): String? {
        val label = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*designation\\b"),
                Regex("(?i)^\\s*position\\b"),
                Regex("(?i)^\\s*role\\b")
            )
        )?.value?.let { cleanValue(it) }
        label?.takeIf { it.length in 2..70 }?.let { return it }

        val patterns = listOf(
            Regex("(?i)position\\s+of\\s+([A-Za-z][A-Za-z /\\-]{2,50}?)(?:\\s+with|\\s+at|\\.|,|$)"),
            Regex("(?i)appointed\\s+as\\s+([A-Za-z][A-Za-z /\\-]{2,50}?)(?:\\.|,|$)"),
            Regex("(?i)post\\s+of\\s+([A-Za-z][A-Za-z /\\-]{2,50}?)(?:\\.|,|$)")
        )
        for (pattern in patterns) {
            pattern.find(layout.singleLine)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.length in 2..70 }?.let { return it }
        }
        return null
    }

    private fun extractCtc(layout: TextLayout): String? {
        val text = layout.singleLine
        Regex("(?i)(?:CTC|cost\\s+to\\s+company|annual\\s+compensation|salary)[^\\n]{0,60}?((?:INR|Rs\\.?|₹)?\\s?\\d[\\d,.]*(?:\\s?(?:lakhs?|lacs?|LPA|per\\s+annum))?)")
            .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:LPA|lakhs?|lacs?|per\\s+annum)\\b")
            .find(text)?.value?.trim()
    }
}
