package com.awbuilds.paddleocr

import com.awbuilds.paddleocr.domain.extraction.ExtractionEngine
import com.awbuilds.paddleocr.domain.extraction.ExtractionUtils
import com.awbuilds.paddleocr.domain.model.DocumentType
import com.awbuilds.paddleocr.domain.model.OcrDocument
import com.awbuilds.paddleocr.domain.model.OcrLine
import com.awbuilds.paddleocr.domain.model.OcrPage
import com.awbuilds.paddleocr.domain.model.OcrPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionTest {

    private fun document(vararg lines: String): OcrDocument = OcrDocument(
        listOf(
            OcrPage(
                0,
                lines.mapIndexed { index, text ->
                    val top = index * 34f
                    OcrLine(
                        text = text,
                        confidence = 0.95f,
                        points = listOf(
                            OcrPoint(10f, top),
                            OcrPoint(700f, top),
                            OcrPoint(700f, top + 26f),
                            OcrPoint(10f, top + 26f)
                        )
                    )
                }
            )
        )
    )

    @Test
    fun panExtractionFindsKeyFields() {
        val result = ExtractionEngine.extract(
            document(
                "INCOME TAX DEPARTMENT",
                "GOVT. OF INDIA",
                "PERMANENT ACCOUNT NUMBER CARD",
                "Name",
                "RAHUL SHARMA",
                "Father's Name",
                "VIJAY SHARMA",
                "Date of Birth",
                "01/01/1990",
                "ABCDE1234F"
            ),
            DocumentType.PAN
        )
        assertEquals("ABCDE1234F", result.fields["panNumber"]?.value)
        assertEquals("RAHUL SHARMA", result.fields["fullName"]?.value)
        assertEquals("VIJAY SHARMA", result.fields["fatherName"]?.value)
        assertEquals("1990-01-01", result.fields["dateOfBirth"]?.value)
    }

    @Test
    fun aadhaarExtractionMasksNumberAndFindsAddress() {
        val result = ExtractionEngine.extract(
            document(
                "GOVERNMENT OF INDIA",
                "AADHAAR",
                "Name",
                "PRIYA NAIR",
                "Date of Birth",
                "15/08/1992",
                "Gender",
                "Female",
                "Address",
                "12 MG Road, Indiranagar, Bengaluru, Karnataka 560038",
                "XXXX XXXX 9012",
                "Mobile",
                "9876543210"
            ),
            DocumentType.AADHAAR
        )
        assertEquals("XXXX XXXX 9012", result.fields["aadhaarNumber"]?.value)
        assertEquals("PRIYA NAIR", result.fields["fullName"]?.value)
        assertEquals("1992-08-15", result.fields["dateOfBirth"]?.value)
        assertEquals("Female", result.fields["gender"]?.value)
        assertEquals("560038", result.fields["pinCode"]?.value)
        assertTrue(result.fields["address"]?.value?.contains("MG Road") == true)
        assertEquals("9876543210", result.fields["mobileNumber"]?.value)
    }

    @Test
    fun bankExtractionFindsIfscAndMasksAccount() {
        val result = ExtractionEngine.extract(
            document(
                "STATE BANK OF INDIA",
                "SAVINGS ACCOUNT",
                "Account Holder Name",
                "RAHUL SHARMA",
                "Account Number",
                "123456789012",
                "IFSC Code",
                "SBIN0001234",
                "Branch",
                "MG Road, Bengaluru"
            ),
            DocumentType.BANK
        )
        assertEquals("SBIN0001234", result.fields["ifsc"]?.value)
        assertEquals("XXXX9012", result.fields["accountNumber"]?.value)
        assertEquals("RAHUL SHARMA", result.fields["accountHolderName"]?.value)
        assertEquals("State Bank of India", result.fields["bankName"]?.value)
        assertEquals("MG Road, Bengaluru", result.fields["branch"]?.value)
        assertEquals("Savings", result.fields["accountType"]?.value)
    }

    @Test
    fun resumeExtractionFindsContactAndSections() {
        val result = ExtractionEngine.extract(
            document(
                "ARJUN MEHTA",
                "arjun.mehta@example.com | +91 98765 43210 | linkedin.com/in/arjunmehta",
                "SUMMARY",
                "Android developer with 4 years of experience building Kotlin applications.",
                "SKILLS",
                "Kotlin, Java, Android, SQL, Git",
                "EXPERIENCE",
                "Senior Android Developer - Acme Technologies",
                "Jan 2022 - Present",
                "EDUCATION",
                "B.Tech in Computer Science",
                "2016 - 2020"
            ),
            DocumentType.RESUME
        )
        assertEquals("ARJUN MEHTA", result.fields["fullName"]?.value)
        assertEquals("arjun.mehta@example.com", result.fields["email"]?.value)
        assertEquals("+919876543210", result.fields["phone"]?.value)
        assertTrue(result.fields["skills"]?.value?.contains("Kotlin") == true)
        assertTrue(result.fields["experience"]?.value?.contains("Acme") == true)
        assertTrue(result.fields["education"]?.value?.contains("B.Tech") == true)
    }

    @Test
    fun educationExtractionFindsDegreeAndYear() {
        val result = ExtractionEngine.extract(
            document(
                "NATIONAL INSTITUTE OF TECHNOLOGY",
                "BACHELOR OF TECHNOLOGY",
                "This is to certify that ARJUN MEHTA",
                "has successfully completed the degree of",
                "Bachelor of Technology in Computer Science",
                "with First Class with Distinction",
                "in the year 2020"
            ),
            DocumentType.EDUCATION
        )
        assertEquals("ARJUN MEHTA", result.fields["candidateName"]?.value)
        assertTrue(result.fields["institution"]?.value?.contains("NATIONAL INSTITUTE") == true)
        assertTrue(result.fields["degree"]?.value?.contains("Bachelor of Technology") == true)
        assertEquals("Computer Science", result.fields["fieldOfStudy"]?.value)
        assertEquals("2020", result.fields["yearOfPassing"]?.value)
        assertTrue(result.fields["gradeOrPercentage"]?.value?.contains("First Class") == true)
    }

    @Test
    fun offerExtractionFindsEmployerAndDates() {
        val result = ExtractionEngine.extract(
            document(
                "ACME TECHNOLOGIES PVT LTD",
                "OFFER LETTER",
                "Dear Arjun Mehta,",
                "We are pleased to appoint you for the position of Senior Android Developer.",
                "Designation: Senior Android Developer",
                "Date of Joining: 01/07/2024",
                "Employment Type: Full-time",
                "Annual CTC: INR 18,00,000 per annum"
            ),
            DocumentType.OFFER
        )
        assertEquals("ACME TECHNOLOGIES PVT LTD", result.fields["employerName"]?.value)
        assertEquals("Arjun Mehta", result.fields["candidateName"]?.value)
        assertEquals("Senior Android Developer", result.fields["designation"]?.value)
        assertEquals("2024-07-01", result.fields["dateOfJoining"]?.value)
        assertEquals("Full-time", result.fields["employmentType"]?.value)
        assertTrue(result.fields["annualCTC"]?.value?.contains("18,00,000") == true)
    }

    @Test
    fun ocrConfusionCorrectionWorks() {
        assertEquals("ABCDE1234F", ExtractionUtils.fixPan("ABCDE1Z34F"))
        assertEquals("SBIN0001234", ExtractionUtils.fixIfsc("SBINO001234"))
        assertEquals("XXXX XXXX 9012", ExtractionUtils.maskAadhaar("1234 5678 9012"))
        assertEquals("XXXX9012", ExtractionUtils.maskAccount("123456789012"))
        assertEquals("1990-01-01", ExtractionUtils.parseDate("01 Jan 1990"))
        assertEquals("1992-08-15", ExtractionUtils.parseDate("15/08/1992"))
    }

    @Test
    fun classifierRecognizesPanAndBank() {
        val pan = document(
            "INCOME TAX DEPARTMENT",
            "PERMANENT ACCOUNT NUMBER CARD",
            "Name",
            "RAHUL SHARMA",
            "ABCDE1234F"
        )
        assertEquals(DocumentType.PAN, com.awbuilds.paddleocr.domain.extraction.DocumentClassifier.classify(pan).type)

        val bank = document(
            "STATE BANK OF INDIA",
            "Account Number 123456789012",
            "IFSC SBIN0001234",
            "Branch MG Road"
        )
        assertEquals(DocumentType.BANK, com.awbuilds.paddleocr.domain.extraction.DocumentClassifier.classify(bank).type)
    }
}
