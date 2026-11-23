package com.awbuilds.paddleocr.domain.extraction.extractors

import com.awbuilds.paddleocr.domain.extraction.ExtractionUtils
import com.awbuilds.paddleocr.domain.extraction.FieldBuilder
import com.awbuilds.paddleocr.domain.extraction.TextLayout
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.ExtractionResult
import com.awbuilds.paddleocr.domain.model.OcrDocument

object ResumeExtractor {

    private val SECTION_HEADINGS = listOf(
        Regex("(?i)^(summary|profile|objective|about me)$"),
        Regex("(?i)^(education|academic|qualifications?)$"),
        Regex("(?i)^(experience|work experience|professional experience|employment|career history)$"),
        Regex("(?i)^(skills?|technical skills?|core competencies|technologies)$"),
        Regex("(?i)^(projects?|personal projects?)$"),
        Regex("(?i)^(certifications?|courses?|licenses?)$"),
        Regex("(?i)^(achievements?|awards?)$"),
        Regex("(?i)^(languages?)$"),
        Regex("(?i)^(interests?|hobbies)$"),
        Regex("(?i)^(references?)$"),
        Regex("(?i)^(declaration)$")
    )

    private val KNOWN_SKILLS = listOf(
        "kotlin", "java", "python", "javascript", "typescript", "react", "node",
        "android", "ios", "flutter", "spring", "sql", "mysql", "postgresql", "mongodb",
        "aws", "azure", "gcp", "docker", "kubernetes", "git", "linux", "figma",
        "machine learning", "deep learning", "nlp", "tensorflow", "pytorch", "opencv",
        "html", "css", "rest", "graphql", "firebase", "excel", "power bi", "tableau"
    )

    fun extract(
        ocr: OcrDocument,
        typeConfidence: Float = 0.7f,
        detectedType: DocumentType? = DocumentType.RESUME
    ): ExtractionResult {
        val layout = TextLayout(ocr.allLines)
        val builder = FieldBuilder(DocumentType.RESUME, typeConfidence, detectedType)

        val fullName = findName(layout)
        builder.add("fullName", "Full Name", fullName, if (fullName != null) 0.72f else 0f)

        val email = ExtractionUtils.extractEmail(layout.singleLine)
        builder.add("email", "Email", email, if (email != null) 0.95f else 0f)

        val phone = ExtractionUtils.extractPhone(layout.singleLine)
        builder.add("phone", "Phone", phone, if (phone != null) 0.9f else 0f)

        val linkedIn = ExtractionUtils.extractLinkedIn(layout.singleLine)
        builder.add("linkedIn", "LinkedIn", linkedIn, if (linkedIn != null) 0.9f else 0f)

        val gitHub = ExtractionUtils.extractGitHub(layout.singleLine)
        builder.add("github", "GitHub", gitHub, if (gitHub != null) 0.9f else 0f)

        val summary = section(layout, "summary", "profile", "objective", "about me")
        builder.add("summary", "Summary", summary, if (summary != null) 0.7f else 0f)

        val skills = extractSkills(layout)
        builder.add("skills", "Skills", skills.joinToString("\n"), if (skills.isNotEmpty()) 0.74f else 0f, isList = true)

        val experience = section(layout, "experience", "work experience", "professional experience", "employment", "career history")
        builder.add("experience", "Experience", experience, if (experience != null) 0.62f else 0f, isList = true)

        val education = section(layout, "education", "academic", "qualifications")
        builder.add("education", "Education", education, if (education != null) 0.64f else 0f, isList = true)

        val projects = section(layout, "projects", "personal projects")
        builder.add("projects", "Projects", projects, if (projects != null) 0.6f else 0f, isList = true)

        val certifications = section(layout, "certifications", "courses", "licenses")
        builder.add("certifications", "Certifications", certifications, if (certifications != null) 0.62f else 0f, isList = true)

        val years = ExtractionUtils.extractExperienceYears(layout.singleLine)
        builder.add("totalExperienceYears", "Total Experience (years)", years, if (years != null) 0.7f else 0f)

        if (experience == null) builder.warn("Experience section was not isolated. Verify role and date lines.")
        if (education == null) builder.warn("Education section was not isolated.")
        builder.warn("Resume extraction is best-effort; review all experience and education entries.")
        return builder.build()
    }

    private fun findName(layout: TextLayout): String? {
        val candidates = layout.rows.take(8).filter { row ->
            val t = row.text.lowercase()
            !t.contains("@") &&
                !ExtractionUtils.EMAIL_REGEX.containsMatchIn(t) &&
                !ExtractionUtils.PHONE_REGEX.containsMatchIn(t) &&
                !t.contains("resume") &&
                !t.contains("curriculum vitae") &&
                !t.contains("linkedin") &&
                !t.contains("github") &&
                SECTION_HEADINGS.none { it.matches(row.text) } &&
                !isFooter(row.text)
        }
        return candidates
            .maxByOrNull { it.height }?.text
            ?.let(ExtractionUtils::cleanPersonName)
            ?: candidates.mapNotNull { ExtractionUtils.cleanPersonName(it.text) }.firstOrNull()
    }

    private fun section(layout: TextLayout, vararg headings: String): String? {
        val start = headings.map { Regex("(?i)^\\s*${Regex.escape(it)}\\s*:?\\s*$") }
        val lines = layout.sectionBetween(start, SECTION_HEADINGS, maxRows = 40)
        return lines
            .filterNot { isFooter(it) }
            .joinToString("\n")
            .trim()
            .ifBlank { null }
    }

    private fun isFooter(line: String): Boolean =
        line.contains("SAMPLE DOCUMENT", ignoreCase = true) ||
            line.contains("generated for a software demonstration", ignoreCase = true) ||
            line.contains("Not a real ID", ignoreCase = true)

    private fun extractSkills(layout: TextLayout): List<String> {
        val sectionLines = layout.sectionBetween(
            start = listOf(Regex("(?i)^\\s*(technical\\s+)?skills?\\s*:?\\s*$"), Regex("(?i)^\\s*technologies\\s*:?\\s*$")),
            end = SECTION_HEADINGS,
            maxRows = 12
        )
        val fromSection = sectionLines
            .filterNot { isFooter(it) }
            .flatMap { ExtractionUtils.splitList(it) }
        if (fromSection.isNotEmpty()) return fromSection.distinct().take(30)

        val lower = layout.singleLine.lowercase()
        return KNOWN_SKILLS.filter { lower.contains(it) }.distinct()
    }
}
