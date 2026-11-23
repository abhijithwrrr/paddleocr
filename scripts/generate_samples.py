#!/usr/bin/env python3
"""Generate clearly fictional sample documents for the Onboard OCR demo."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1000, 1400
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/samples"
FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"
BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)


def canvas():
    image = Image.new("RGB", (W, H), "white")
    return image, ImageDraw.Draw(image)


def centered(draw, text, y, size=40, bold=False, fill=(25, 25, 25)):
    f = font(size, bold)
    width = draw.textlength(text, font=f)
    draw.text(((W - width) / 2, y), text, font=f, fill=fill)


def left(draw, text, x, y, size=34, bold=False, fill=(25, 25, 25)):
    draw.text((x, y), text, font=font(size, bold), fill=fill)


def rule(draw, y, x1=60, x2=W - 60, fill=(185, 185, 185), width=2):
    draw.line((x1, y, x2, y), fill=fill, width=width)


def wrap(draw, text, x, y, max_width, size=32, fill=(35, 35, 35), leading=10):
    f = font(size)
    words, lines, current = text.split(), [], ""
    for word in words:
        trial = (current + " " + word).strip()
        if draw.textlength(trial, font=f) <= max_width:
            current = trial
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    for line in lines:
        draw.text((x, y), line, font=f, fill=fill)
        y += size + leading
    return y


def sample_marks(draw):
    draw.rectangle((W - 220, 34, W - 40, 84), fill=(245, 245, 245), outline=(210, 210, 210))
    centered_marks = "SAMPLE"
    draw.text((W - 186, 44), centered_marks, font=font(27, True), fill=(120, 120, 120))
    draw.text(
        (60, H - 62),
        "SAMPLE DOCUMENT — generated for a software demonstration. Not a real ID.",
        font=font(20),
        fill=(135, 135, 135),
    )


def save(image, name):
    sample_marks(ImageDraw.Draw(image))
    image.save(OUT / name, quality=95)


OUT.mkdir(parents=True, exist_ok=True)

# PAN
image, d = canvas()
centered(d, "INCOME TAX DEPARTMENT", 55, 34, True)
centered(d, "GOVT. OF INDIA", 100, 30)
centered(d, "PERMANENT ACCOUNT NUMBER CARD", 170, 40, True)
rule(d, 230)
d.rectangle((60, 260, 300, 520), outline=(205, 205, 205), width=2)
centered(d, "PHOTO", 380, 28, False, (180, 180, 180))
left(d, "Name", 350, 290, 32, True)
left(d, "RAHUL SHARMA", 350, 335, 46, True)
left(d, "Father's Name", 350, 420, 32, True)
left(d, "VIJAY SHARMA", 350, 465, 40, True)
left(d, "Date of Birth", 350, 550, 32, True)
left(d, "1 January 1990", 350, 595, 40, True)
rule(d, 680)
centered(d, "ABCDE1234F", 730, 78, True)
centered(d, "Permanent Account Number", 840, 28, False, (80, 80, 80))
rule(d, 990)
left(d, "Signature", 60, 1120, 28, True, (80, 80, 80))
rule(d, 1180, 60, 350, (80, 80, 80), 2)
save(image, "sample_pan.png")

# Aadhaar
image, d = canvas()
centered(d, "GOVERNMENT OF INDIA", 50, 36, True)
centered(d, "AADHAAR", 105, 54, True, (120, 70, 25))
rule(d, 190)
left(d, "Name", 60, 240, 32, True)
left(d, "PRIYA NAIR", 60, 285, 44, True)
left(d, "Date of Birth", 60, 365, 32, True)
left(d, "15 August 1992", 60, 410, 40, True)
left(d, "Gender", 60, 490, 32, True)
left(d, "Female", 60, 535, 40, True)
left(d, "Address", 60, 625, 32, True)
y = wrap(d, "12 MG Road, Indiranagar, Bengaluru, Karnataka 560038", 60, 675, 820, 34)
rule(d, y + 30)
centered(d, "XXXX XXXX 9012", y + 80, 54, True)
centered(d, "Aadhaar Number", y + 150, 28, False, (80, 80, 80))
left(d, "Mobile", 60, y + 230, 30, True)
left(d, "9876543210", 60, y + 270, 36)
save(image, "sample_aadhaar.png")

# Resume
image, d = canvas()
centered(d, "RESUME", 50, 30, True, (90, 90, 90))
centered(d, "ARJUN MEHTA", 110, 56, True)
centered(d, "arjun.mehta@example.com  |  +91 98765 43210  |  linkedin.com/in/arjunmehta", 185, 24, False, (60, 60, 60))
rule(d, 240)
left(d, "SUMMARY", 60, 280, 30, True, (20, 70, 120))
wrap(d, "Android developer with 4 years of experience building Kotlin applications, offline-first data flows and document automation tools.", 60, 325, 860, 28)
left(d, "SKILLS", 60, 450, 30, True, (20, 70, 120))
wrap(d, "Kotlin, Java, Android, Jetpack Compose, SQL, Git, REST APIs, Firebase, Python", 60, 495, 860, 28)
left(d, "EXPERIENCE", 60, 590, 30, True, (20, 70, 120))
left(d, "Senior Android Developer — Acme Technologies Pvt Ltd", 60, 640, 30, True)
left(d, "Jan 2022 - Present", 60, 685, 26, False, (90, 90, 90))
wrap(d, "Built onboarding and KYC flows; integrated camera capture and on-device OCR. Led a team of two engineers.", 60, 730, 860, 26)
left(d, "Android Developer — ByteWorks Solutions", 60, 840, 30, True)
left(d, "Jun 2020 - Dec 2021", 60, 885, 26, False, (90, 90, 90))
wrap(d, "Developed customer-facing Android apps and offline sync features used by 50,000 monthly users.", 60, 930, 860, 26)
left(d, "EDUCATION", 60, 1040, 30, True, (20, 70, 120))
left(d, "B.Tech in Computer Science — National Institute of Technology", 60, 1090, 28, True)
left(d, "2016 - 2020  |  CGPA 8.4 / 10", 60, 1135, 26, False, (90, 90, 90))
left(d, "CERTIFICATIONS", 60, 1220, 30, True, (20, 70, 120))
wrap(d, "Google Associate Android Developer (2021), AWS Cloud Practitioner (2022)", 60, 1265, 860, 26)
save(image, "sample_resume.png")

# Degree
image, d = canvas()
centered(d, "NATIONAL INSTITUTE OF TECHNOLOGY", 70, 38, True, (20, 60, 110))
centered(d, "BACHELOR OF TECHNOLOGY", 140, 46, True)
rule(d, 220)
centered(d, "This is to certify that", 300, 34)
centered(d, "ARJUN MEHTA", 370, 54, True)
centered(d, "has successfully completed the degree of", 460, 30)
centered(d, "Bachelor of Technology in Computer Science", 520, 38, True)
centered(d, "with First Class with Distinction", 610, 34)
centered(d, "in the year 2020", 700, 34)
rule(d, 820)
left(d, "Certificate No: NIT/CSE/2020/1187", 60, 880, 26, False, (80, 80, 80))
left(d, "Date of Issue: 15/06/2020", 60, 930, 26, False, (80, 80, 80))
centered(d, "Registrar", 1120, 30, True, (70, 70, 70))
rule(d, 1100, 380, 620, (80, 80, 80), 2)
save(image, "sample_degree.png")

# Bank
image, d = canvas()
centered(d, "STATE BANK OF INDIA", 60, 44, True, (30, 70, 140))
centered(d, "SAVINGS ACCOUNT", 130, 30, True, (80, 80, 80))
rule(d, 210)
left(d, "Account Holder Name", 60, 270, 30, True)
left(d, "RAHUL SHARMA", 60, 315, 42, True)
left(d, "Account Number", 60, 410, 30, True)
left(d, "123456789012", 60, 455, 42, True)
left(d, "IFSC Code", 60, 550, 30, True)
left(d, "SBIN0001234", 60, 595, 42, True)
left(d, "Branch", 60, 690, 30, True)
left(d, "MG Road, Bengaluru", 60, 735, 38, True)
left(d, "Account Type", 60, 830, 30, True)
left(d, "Savings", 60, 875, 38, True)
rule(d, 1000)
left(d, "Customer ID: 88221190", 60, 1060, 28, False, (80, 80, 80))
left(d, "Nomination: Registered", 60, 1110, 28, False, (80, 80, 80))
save(image, "sample_bank.png")

# Offer
image, d = canvas()
centered(d, "ACME TECHNOLOGIES PVT LTD", 60, 44, True, (20, 60, 120))
centered(d, "OFFER LETTER", 135, 34, True, (90, 90, 90))
rule(d, 210)
left(d, "Dear Arjun Mehta,", 60, 280, 32)
wrap(d, "We are pleased to appoint you for the position of Senior Android Developer with Acme Technologies Pvt Ltd. Your employment will begin on the date mentioned below.", 60, 350, 870, 30, leading=12)
left(d, "Designation: Senior Android Developer", 60, 560, 32, True)
left(d, "Date of Joining: 1 July 2024", 60, 630, 32, True)
left(d, "Employment Type: Full-time", 60, 700, 32, True)
left(d, "Annual CTC: INR 18,00,000 per annum", 60, 770, 32, True)
left(d, "Work Location: Bengaluru, Karnataka", 60, 840, 32)
wrap(d, "Please confirm your acceptance of this offer by signing and returning a copy of this letter. We look forward to welcoming you to the team.", 60, 940, 870, 28, leading=10)
left(d, "For Acme Technologies Pvt Ltd", 60, 1120, 28, True, (80, 80, 80))
left(d, "Human Resources", 60, 1170, 28, False, (80, 80, 80))
save(image, "sample_offer.png")

print(f"Generated samples in {OUT}")
