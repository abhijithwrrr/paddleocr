package com.awbuilds.paddleocr.domain.extraction.extractors

import com.awbuilds.paddleocr.domain.extraction.ExtractionUtils
import com.awbuilds.paddleocr.domain.extraction.FieldBuilder
import com.awbuilds.paddleocr.domain.extraction.TextLayout
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.OcrDocument

object BankExtractor {

    private val BANK_NAMES = listOf(
        "State Bank of India", "SBI", "HDFC Bank", "ICICI Bank", "Axis Bank",
        "Kotak Mahindra Bank", "Punjab National Bank", "PNB", "Bank of Baroda",
        "Canara Bank", "Union Bank of India", "IDFC First Bank", "Yes Bank",
        "IndusInd Bank", "Federal Bank", "South Indian Bank", "Bank of India",
        "Central Bank of India", "Indian Bank", "UCO Bank", "Paytm Payments Bank",
        "Airtel Payments Bank", "Jana Small Finance Bank", "AU Small Finance Bank"
    )

    private val ACCOUNT_STOPS = listOf(
        Regex("(?i)^\\s*ifsc\\b"),
        Regex("(?i)^\\s*branch\\b"),
        Regex("(?i)^\\s*bank\\b"),
        Regex("(?i)^\\s*name\\b")
    )

    fun extract(
        ocr: OcrDocument,
        typeConfidence: Float = 0.82f,
        detectedType: DocumentType? = DocumentType.BANK
    ): ExtractionResult {
        val layout = TextLayout(ocr.allLines)
        val builder = FieldBuilder(DocumentType.BANK, typeConfidence, detectedType)

        val ifscMatch = ExtractionUtils.IFSC_REGEX.find(layout.singleLine)
            ?: Regex("""\b[A-Za-z0-9]{11}\b""").findAll(layout.singleLine)
                .mapNotNull { ExtractionUtils.fixIfsc(it.value) }
                .firstOrNull()
        val ifsc = when (ifscMatch) {
            is MatchResult -> ExtractionUtils.fixIfsc(ifscMatch.value)
            is String -> ifscMatch
            else -> null
        }
        builder.add("ifsc", "IFSC Code", ifsc, if (ifsc != null) 0.92f else 0f)

        val accountRaw = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*account\\s*(?:no|number)?\\b"),
                Regex("(?i)^\\s*a/c\\s*(?:no|number)?\\b")
            ),
            valueRegex = Regex("""\d[\d\s\-]{7,20}\d"""),
            stopLabels = ACCOUNT_STOPS
        )?.value
        val account = ExtractionUtils.maskAccount(accountRaw)
        builder.add("accountNumber", "Account Number", account, if (account != null) 0.86f else 0f)

        val holder = layout.valueNear(
            labels = listOf(
                Regex("(?i)^\\s*name\\b"),
                Regex("(?i)^\\s*account\\s+holder\\b"),
                Regex("(?i)^\\s*customer\\s+name\\b")
            ),
            stopLabels = ACCOUNT_STOPS
        )?.value?.let(ExtractionUtils::cleanPersonName)
        builder.add("accountHolderName", "Account Holder Name", holder, if (holder != null) 0.78f else 0f)

        val bankName = extractBankName(layout)
        builder.add("bankName", "Bank Name", bankName, if (bankName != null) 0.82f else 0f)

        val branch = layout.valueNear(
            labels = listOf(Regex("(?i)^\\s*branch\\b")),
            stopLabels = ACCOUNT_STOPS
        )?.value?.let { value ->
            value.replace(Regex("(?i)^\\s*branch\\s*[:\\-]?\\s*"), "").trim().ifBlank { null }
        }
        builder.add("branch", "Branch", branch, if (branch != null) 0.74f else 0f)

        val accountType = Regex("(?i)\\b(savings?|current|salary|jan dhan|nre|nro|recurring|fixed deposit)\\b")
            .find(layout.singleLine)?.groupValues?.get(1)
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
        builder.add("accountType", "Account Type", accountType, if (accountType != null) 0.72f else 0f)

        if (ifsc == null) builder.warn("IFSC not detected. The first page of a passbook usually contains it.")
        if (account == null) builder.warn("Account number not detected.")
        return builder.build()
    }

    private fun extractBankName(layout: TextLayout): String? {
        val text = layout.singleLine
        BANK_NAMES.firstOrNull { text.contains(it, ignoreCase = true) }?.let { return it }
        val line = layout.lines.firstOrNull { line ->
            Regex("(?i)\\bbank\\b").containsMatchIn(line.text) &&
                !Regex("(?i)(branch|ifsc|account|statement|customer)").containsMatchIn(line.text)
        }
        return line?.text?.trim()?.takeIf { it.length in 3..70 }
    }
}
