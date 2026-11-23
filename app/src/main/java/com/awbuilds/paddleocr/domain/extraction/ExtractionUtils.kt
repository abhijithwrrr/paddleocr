package com.awbuilds.paddleocr.domain.extraction

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object ExtractionUtils {

    val PAN_REGEX = Regex("""\b[A-Za-z]{5}[0-9OoIlSsBbZzGg]{4}[A-Za-z]\b""")
    val IFSC_REGEX = Regex("""\b[A-Za-z]{4}[0O][A-Za-z0-9]{6}\b""")
    val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    val PHONE_REGEX = Regex("""(?<!\d)(?:\+?91[\s\-]?)?[6-9](?:[\s\-]?\d){9}(?!\d)""")
    val AADHAAR_REGEX = Regex("""(?<!\d)(\d{4})[\s\-]?(\d{4})[\s\-]?(\d{4})(?!\d)""")
    val MASKED_AADHAAR_REGEX = Regex("""(?i)\b[X*]{4}[\s\-]?[X*]{4}[\s\-]?(\d{4})\b""")
    val PIN_REGEX = Regex("""\b[1-9]\d{5}\b""")
    val YEAR_REGEX = Regex("""\b(19|20)\d{2}\b""")
    val PERCENTAGE_REGEX = Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*%""")
    val CGPA_REGEX = Regex("""(?i)(?:CGPA|GPA|C\.G\.P\.A)\s*[:\-]?\s*(\d{1,2}(?:\.\d{1,2})?)""")
    val EXPERIENCE_REGEX = Regex("""(?i)(\d{1,2}(?:\.\d)?)\s*(?:\+)?\s*(?:years?|yrs?)""")

    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    private val DATE_NUMERIC = Regex("""(?<!\d)(\d{1,2})[\s/\-.](\d{1,2})[\s/\-.](\d{4})(?!\d)""")
    private val DATE_ISO = Regex("""(?<!\d)(\d{4})[\s/\-.](\d{1,2})[\s/\-.](\d{1,2})(?!\d)""")
    private val DATE_DMY_TEXT = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]{3,9})[,.]?\s+(\d{4})\b""")
    private val DATE_MDY_TEXT = Regex("""(?i)\b([A-Za-z]{3,9})\s+(\d{1,2})(?:st|nd|rd|th)?[,.]?\s+(\d{4})\b""")

    fun fixPan(raw: String): String? {
        val cleaned = raw.uppercase(Locale.US).replace(Regex("""[^A-Z0-9]"""), "")
        if (cleaned.length < 10) return null
        val windows = if (cleaned.length == 10) listOf(cleaned) else cleaned.windowed(10)
        for (window in windows) {
            val chars = window.toCharArray()
            for (i in 0..4) {
                chars[i] = when (chars[i]) {
                    '0' -> 'O'; '1' -> 'I'; '2' -> 'Z'; '5' -> 'S'; '6' -> 'G'; '8' -> 'B'
                    else -> chars[i]
                }
            }
            for (i in 5..8) {
                chars[i] = when (chars[i]) {
                    'O', 'Q', 'D' -> '0'; 'I', 'L', 'T' -> '1'; 'Z' -> '2'; 'A' -> '4'
                    'S' -> '5'; 'G' -> '6'; 'B' -> '8'; 'P' -> '9'
                    else -> chars[i]
                }
            }
            val candidate = String(chars)
            if (Regex("""^[A-Z]{5}[0-9]{4}[A-Z]$""").matches(candidate)) return candidate
        }
        return null
    }

    fun fixIfsc(raw: String): String? {
        val cleaned = raw.uppercase(Locale.US).replace(Regex("""[^A-Z0-9]"""), "")
        if (cleaned.length < 11) return null
        val windows = if (cleaned.length == 11) listOf(cleaned) else cleaned.windowed(11)
        for (window in windows) {
            val chars = window.toCharArray()
            for (i in 0..3) {
                chars[i] = when (chars[i]) {
                    '0' -> 'O'; '1' -> 'I'; '5' -> 'S'; '8' -> 'B'; '2' -> 'Z'
                    else -> chars[i]
                }
            }
            if (chars[4] == 'O') chars[4] = '0'
            val candidate = String(chars)
            if (Regex("""^[A-Z]{4}0[A-Z0-9]{6}$""").matches(candidate)) return candidate
        }
        return null
    }

    fun maskAadhaar(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        MASKED_AADHAAR_REGEX.find(raw)?.let { return "XXXX XXXX ${it.groupValues[1]}" }
        val match = AADHAAR_REGEX.find(raw) ?: return raw
        return "XXXX XXXX ${match.groupValues[3]}"
    }

    fun maskAccount(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 4) return raw
        return "XXXX${digits.takeLast(4)}"
    }

    fun parseDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        DATE_NUMERIC.find(raw)?.let { m ->
            var day = m.groupValues[1].toIntOrNull() ?: return@let
            var month = m.groupValues[2].toIntOrNull() ?: return@let
            val year = m.groupValues[3].toIntOrNull() ?: return@let
            if (month > 12 && day <= 12) { val t = day; day = month; month = t }
            if (month !in 1..12 || day !in 1..31) return@let
            return formatDate(year, month, day)
        }
        DATE_ISO.find(raw)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@let
            val month = m.groupValues[2].toIntOrNull() ?: return@let
            val day = m.groupValues[3].toIntOrNull() ?: return@let
            if (month !in 1..12 || day !in 1..31) return@let
            return formatDate(year, month, day)
        }
        DATE_DMY_TEXT.find(raw)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val month = MONTHS[m.groupValues[2].lowercase(Locale.US).take(3)]
                ?: MONTHS[m.groupValues[2].lowercase(Locale.US)]
                ?: return@let
            val year = m.groupValues[3].toIntOrNull() ?: return@let
            return formatDate(year, month, day)
        }
        DATE_MDY_TEXT.find(raw)?.let { m ->
            val month = MONTHS[m.groupValues[1].lowercase(Locale.US).take(3)]
                ?: MONTHS[m.groupValues[1].lowercase(Locale.US)]
                ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            val year = m.groupValues[3].toIntOrNull() ?: return@let
            return formatDate(year, month, day)
        }
        YEAR_REGEX.find(raw)?.let { return it.value }
        return null
    }

    fun findDates(raw: String): List<String> {
        val values = linkedSetOf<String>()
        DATE_NUMERIC.findAll(raw).forEach { m ->
            var day = m.groupValues[1].toIntOrNull() ?: return@forEach
            var month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val year = m.groupValues[3].toIntOrNull() ?: return@forEach
            if (month > 12 && day <= 12) {
                val swap = day
                day = month
                month = swap
            }
            if (month in 1..12 && day in 1..31) values.add(formatDate(year, month, day))
        }
        DATE_ISO.findAll(raw).forEach { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val day = m.groupValues[3].toIntOrNull() ?: return@forEach
            if (month in 1..12 && day in 1..31) values.add(formatDate(year, month, day))
        }
        DATE_DMY_TEXT.findAll(raw).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = MONTHS[m.groupValues[2].lowercase(Locale.US).take(3)] ?: return@forEach
            val year = m.groupValues[3].toIntOrNull() ?: return@forEach
            values.add(formatDate(year, month, day))
        }
        DATE_MDY_TEXT.findAll(raw).forEach { m ->
            val month = MONTHS[m.groupValues[1].lowercase(Locale.US).take(3)] ?: return@forEach
            val day = m.groupValues[2].toIntOrNull() ?: return@forEach
            val year = m.groupValues[3].toIntOrNull() ?: return@forEach
            values.add(formatDate(year, month, day))
        }
        return values.toList()
    }

    fun parseYear(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return YEAR_REGEX.find(raw)?.value
    }

    fun extractPercentage(raw: String): String? = PERCENTAGE_REGEX.find(raw)?.groupValues?.get(1)?.plus("%")

    fun extractCgpa(raw: String): String? {
        CGPA_REGEX.find(raw)?.let { return it.groupValues[1] }
        val reverse = Regex("""(?i)(\d{1,2}(?:\.\d{1,2})?)\s*(?:CGPA|GPA)""").find(raw)
        return reverse?.groupValues?.get(1)
    }

    fun extractExperienceYears(raw: String): String? =
        EXPERIENCE_REGEX.find(raw)?.groupValues?.get(1)

    fun extractEmail(raw: String): String? = EMAIL_REGEX.find(raw)?.value?.trim()

    fun extractPhone(raw: String): String? {
        val match = PHONE_REGEX.find(raw) ?: return null
        val digits = match.value.replace(Regex("""[^\d+]"""), "")
        return digits
    }

    fun extractLinkedIn(raw: String): String? =
        Regex("""(?i)(?:https?://)?(?:www\.)?linkedin\.com/[A-Za-z0-9_/\-]+""").find(raw)?.value

    fun extractGitHub(raw: String): String? =
        Regex("""(?i)(?:https?://)?(?:www\.)?github\.com/[A-Za-z0-9_/\-]+""").find(raw)?.value

    fun cleanPersonName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var value = cleanValue(raw)
            .replace(Regex("""(?i)^(name|full name|father'?s? name|mother'?s? name|candidate name|employee name|account holder(?: name)?|mr\.?|mrs\.?|ms\.?|dr\.?)\s*[:\-]?\s*"""), "")
            .replace(Regex("""(?i)\s+(name|father'?s? name|date of birth|dob|address|gender|signature)$"""), "")
            .trim(' ', ',', ';', '.', ':', '-')
        value = value.replace(Regex("""\s{2,}"""), " ")
        if (value.any { it.isDigit() } || value.contains('@')) return null
        val words = value.split(" ").filter { it.isNotBlank() }
        if (words.size !in 2..5) return null
        if (!words.all { it.any { c -> c.isLetter() } }) return null
        if (STOP_NAMES.any { value.lowercase().contains(it) }) return null
        return value
    }

    fun looksLikeName(raw: String?): Boolean = cleanPersonName(raw) != null

    fun listFromMultiline(value: String?): List<String> =
        value?.lines()?.map { it.trim().trimStart('-', '•', '*', '·') }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun correctCommonWords(value: String): String {
        return value.split(Regex("""\s+""")).joinToString(" ") { word ->
            val core = word.trim('.', ',', ';', ':', '(', ')')
            val replacement = COMMON_WORD_FIXES[core.lowercase()] ?: return@joinToString word
            val fixed = when {
                core.all { it.isUpperCase() } -> replacement.uppercase()
                core.firstOrNull()?.isUpperCase() == true ->
                    replacement.replaceFirstChar { it.uppercaseChar() }
                else -> replacement
            }
            word.replace(core, fixed)
        }
    }

    fun normalizeCompanySuffix(value: String): String = value
        .replace(Regex("(?i)\\bPVT\\.?\\s+L$"), "PVT LTD")
        .replace(Regex("(?i)\\bPVT\\.?\\s+LT$"), "PVT LTD")
        .replace(Regex("(?i)\\bPVT\\.?\\s+LTD\\.?$"), "PVT LTD")
        .replace(Regex("(?i)\\bPRIVATE\\.?$"), "PRIVATE LIMITED")

    private val COMMON_WORD_FIXES = mapOf(
        "technolugy" to "technology",
        "technolgy" to "technology",
        "universtiy" to "university",
        "univeristy" to "university",
        "univercity" to "university",
        "instiute" to "institute",
        "institue" to "institute",
        "insttute" to "institute",
        "limted" to "limited"
    )

    fun splitList(value: String): List<String> =
        value.split(',', '|', '•', '·', ';')
            .map { it.trim().trimStart('-', '*') }
            .filter { it.length in 1..60 && it.any { c -> c.isLetter() } }
            .distinct()

    private fun formatDate(year: Int, month: Int, day: Int): String {
        return try {
            LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            "%04d-%02d-%02d".format(Locale.US, year, month, day)
        }
    }

    private val STOP_NAMES = listOf(
        "income tax", "government of india", "permanent account", "date of birth",
        "father", "mother", "address", "male", "female", "transgender", "signature",
        "aadhaar", "uidai", "resume", "curriculum vitae", "university", "college",
        "institute", "bank", "branch", "account", "ifsc", "offer letter",
        "experience certificate", "salary", "designation", "department"
    )
}
