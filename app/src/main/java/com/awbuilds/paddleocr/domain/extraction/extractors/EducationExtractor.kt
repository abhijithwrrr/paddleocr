package com.awbuilds.paddleocr.domain.extraction.extractors

import com.awbuilds.paddleocr.domain.extraction.ExtractionUtils
import com.awbuilds.paddleocr.domain.extraction.FieldBuilder
import com.awbuilds.paddleocr.domain.extraction.TextLayout
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.OcrDocument

object EducationExtractor {

    private val INSTITUTION_REGEX = Regex("(?i)\\b(university|college|institute|school|academy|polytechnic)\\b")
    private val DEGREE_REGEX = Regex(
        "(?i)\\b(bachelor|master|b\\.?tech|m\\.?tech|b\\.?e\\b|m\\.?e\\b|b\\.?sc|m\\.?sc|" +
            "b\\.?com|m\\.?com|b\\.?a\\b|m\\.?a\\b|mba|mca|bca|diploma|doctor|ph\\.?d|" +
            "high school|intermediate|senior secondary|secondary|class xii|class x)\\b"
    )

    fun extract(
        ocr: OcrDocument,
        typeConfidence: Float = 0.8f,
        detectedType: DocumentType? = DocumentType.EDUCATION
    ): ExtractionResult {
        val layout = TextLayout(ocr.allLines)
        val builder = FieldBuilder(DocumentType.EDUCATION, typeConfidence, detectedType)

        val name = extractName(layout)
        builder.add("candidateName", "Candidate Name", name, if (name != null) 0.76f else 0f)

        val institutionLine = layout.lines
            .filter { INSTITUTION_REGEX.containsMatchIn(it.text) }
            .map { it to it.confidence }
            .firstOrNull()
        val institution = institutionLine?.first?.text?.let { cleanInstitution(it) }
        builder.add("institution", "Institution", institution, institutionLine?.second ?: 0f)

        val degreeLine = layout.lines
            .filter { DEGREE_REGEX.containsMatchIn(it.text) }
            .maxByOrNull { it.text.length }
        val degree = degreeLine?.text?.let { cleanDegree(it) }
        builder.add("degree", "Degree / Qualification", degree, degreeLine?.confidence ?: 0f)

        val fieldOfStudy = degree?.let { extractFieldOfStudy(it) }
        builder.add("fieldOfStudy", "Field of Study", fieldOfStudy, if (fieldOfStudy != null) 0.68f else 0f)

        val year = extractPassingYear(layout)
        builder.add("yearOfPassing", "Year of Passing", year, if (year != null) 0.8f else 0f)

        val grade = extractGrade(layout)
        builder.add("gradeOrPercentage", "Grade / Percentage", grade, if (grade != null) 0.76f else 0f)

        if (institution == null) builder.warn("Institution name was not detected.")
        if (degree == null) builder.warn("Degree name was not detected; check the certificate layout.")
        return builder.build()
    }

    private fun extractName(layout: TextLayout): String? {
        val labels = listOf(
            Regex("(?i)^\\s*(?:candidate|student|awardee)?\\s*name\\b")
        )
        layout.valueNear(labels)?.value?.let(ExtractionUtils::cleanPersonName)?.let { return it }

        val certifyRegex = Regex(
            "(?i:certify that|awarded to|conferred (?:up)?on|this is to certify that)\\s+" +
                "(?i:mr\\.?|mrs\\.?|ms\\.?|miss\\s+)?\\s*" +
                "([A-Z][A-Za-z.'\\-]+(?:\\s+[A-Z][A-Za-z.'\\-]+){1,4})"
        )
        certifyRegex.find(layout.singleLine)?.groupValues?.get(1)?.let(ExtractionUtils::cleanPersonName)
            ?.let { return it }

        return layout.rows.take(10)
            .mapNotNull { ExtractionUtils.cleanPersonName(it.text) }
            .firstOrNull()
    }

    private fun cleanInstitution(raw: String): String? {
        val line = raw.replace(Regex("(?i)^\\s*(from|at|institution|university|college)\\s*[:\\-]?\\s*"), "")
        return ExtractionUtils.correctCommonWords(line.trim(' ', ',', ';', '.', '-'))
            .ifBlank { null }
            ?.takeIf { it.length in 3..90 }
    }

    private fun cleanDegree(raw: String): String? {
        val value = raw
            .replace(Regex("(?i)^\\s*(degree|qualification|course)\\s*[:\\-]?\\s*"), "")
            .trim(' ', ',', ';', '.', '-')
        return value.ifBlank { null }?.takeIf { it.length in 3..90 }
    }

    private fun extractFieldOfStudy(degree: String): String? {
        val match = Regex("(?i)\\bin\\s+([A-Za-z][A-Za-z &\\-]{2,40})").find(degree)
            ?: Regex("(?i)\\bof\\s+([A-Za-z][A-Za-z &\\-]{4,40})").find(degree)
        val value = match?.groupValues?.get(1)?.trim()
        return value?.takeIf { !it.equals("technology", true) && !it.equals("science", true) }
    }

    private fun extractPassingYear(layout: TextLayout): String? {
        val yearLines = layout.lines.filter { ExtractionUtils.YEAR_REGEX.containsMatchIn(it.text) }
        val contextual = yearLines.firstOrNull {
            Regex("(?i)(year|passed|passing|awarded|convocation|degree|graduated)").containsMatchIn(it.text)
        }
        val year = contextual?.let { ExtractionUtils.parseYear(it.text) }
            ?: yearLines.mapNotNull { ExtractionUtils.parseYear(it.text) }
                .maxOrNull()
        return year?.takeIf { it.toIntOrNull()?.let { y -> y in 1950..2035 } == true }
    }

    private fun extractGrade(layout: TextLayout): String? {
        val text = layout.singleLine
        ExtractionUtils.extractCgpa(text)?.let { return "CGPA $it" }
        ExtractionUtils.extractPercentage(text)?.let { return it }
        Regex("(?i)\\b(first class (?:with )?distinction|first division|first class|second class|third class|distinction)\\b")
            .find(text)?.groupValues?.get(1)?.let { raw ->
                return raw.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { c -> c.uppercaseChar() }
                }
            }
        return null
    }
}
