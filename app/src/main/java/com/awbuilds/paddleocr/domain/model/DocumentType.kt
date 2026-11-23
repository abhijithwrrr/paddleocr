package com.awbuilds.paddleocr.domain.model

/**
 * The onboarding document types supported by the POC.
 */
enum class DocumentType(
    val key: String,
    val title: String,
    val shortLabel: String,
    val description: String,
    val sampleAsset: String
) {
    PAN(
        key = "pan",
        title = "PAN Card",
        shortLabel = "PAN",
        description = "Permanent Account Number card",
        sampleAsset = "samples/sample_pan.png"
    ),
    AADHAAR(
        key = "aadhaar",
        title = "Aadhaar Card",
        shortLabel = "AAD",
        description = "UIDAI identity card",
        sampleAsset = "samples/sample_aadhaar.png"
    ),
    RESUME(
        key = "resume",
        title = "Resume / CV",
        shortLabel = "CV",
        description = "Candidate profile and work history",
        sampleAsset = "samples/sample_resume.png"
    ),
    EDUCATION(
        key = "education",
        title = "Education Certificate",
        shortLabel = "EDU",
        description = "Degree, marksheet or passing certificate",
        sampleAsset = "samples/sample_degree.png"
    ),
    BANK(
        key = "bank",
        title = "Bank Passbook",
        shortLabel = "BNK",
        description = "Account details for payroll",
        sampleAsset = "samples/sample_bank.png"
    ),
    OFFER(
        key = "offer",
        title = "Offer / Experience Letter",
        shortLabel = "LTR",
        description = "Joining or relieving documentation",
        sampleAsset = "samples/sample_offer.png"
    );

    companion object {
        fun fromKey(key: String): DocumentType? = entries.firstOrNull { it.key == key }
    }
}
