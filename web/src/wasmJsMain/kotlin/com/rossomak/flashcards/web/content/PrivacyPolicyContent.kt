package com.rossomak.flashcards.web.content

import com.rossomak.flashcards.web.content.LegalBlock.BulletList
import com.rossomak.flashcards.web.content.LegalBlock.Paragraph

/** The Privacy Policy linked from the Play Console listing and the app's Account screen. */
val PrivacyPolicyDocument = LegalDocument(
    title = "Flashcards Privacy Policy",
    subtitle = "Last updated: October 3, 2026",
    intro = listOf(
        "This policy explains what data the Flashcards Android app (\"the app\", \"we\") collects, why, who processes it, and how you can delete it.",
    ),
    sections = listOf(
        LegalSection(
            title = "Data we collect",
            blocks = listOf(
                BulletList(
                    listOf(
                        "<strong>Account data.</strong> When you sign in with Google, we receive your Google account's user ID, email " +
                            "address, display name and profile photo URL through Firebase Authentication.",
                        "<strong>Study data.</strong> Your favorites, study sessions (dates, durations, which flashcards you studied " +
                            "and how you rated them), per-flashcard progress, XP, level and streak.",
                        "<strong>Content you create.</strong> Private flashcards you write, and problem reports you submit about flashcards.",
                        "<strong>Voice answers (optional).</strong> Only if you turn on Voice answering. See below.",
                    ),
                ),
                Paragraph("Some settings, such as your daily goal and study preferences, are stored only on your device."),
                Paragraph("The app does not show ads, does not use analytics or advertising SDKs, and does not sell your data."),
            ),
        ),
        LegalSection(
            title = "Voice answering",
            blocks = listOf(
                Paragraph(
                    "Voice answering is off by default. When you use it, the app records your spoken answer through the microphone " +
                        "and transforms the voice audio on your device before it leaves the device. The transformed audio is then sent " +
                        "to our server and forwarded to:",
                ),
                BulletList(
                    listOf(
                        "<strong>ElevenLabs</strong>, which transcribes the audio to text;",
                        "<strong>Google Vertex AI (Gemini)</strong>, which grades the transcript against the flashcard's expected answer.",
                    ),
                ),
                Paragraph(
                    "We do not store your audio or transcripts. Our server discards them once your answer is graded. ElevenLabs and " +
                        "Google process this data under their own terms and privacy policies.",
                ),
            ),
        ),
        LegalSection(
            title = "How we use data",
            blocks = listOf(
                BulletList(
                    listOf(
                        "To sign you in and keep your study progress across devices.",
                        "To calculate progress, XP, levels and streaks.",
                        "To grade spoken answers when you use Voice answering.",
                        "To fix flashcards you report as having problems.",
                    ),
                ),
            ),
        ),
        LegalSection(
            title = "Where data is stored",
            blocks = listOf(
                Paragraph(
                    "Account and study data is stored in Google Firebase (Authentication, Cloud Firestore and Cloud Functions), " +
                        "operated by Google LLC. Data is encrypted in transit.",
                ),
            ),
        ),
        LegalSection(
            title = "Data retention and deletion",
            blocks = listOf(
                Paragraph(
                    "We keep your account and study data for as long as your account exists. You can ask us to delete your account " +
                        "and all associated data by following the steps on our <a href=\"/delete-account\">account deletion page</a>. " +
                        "We delete it within 30 days of your request.",
                ),
            ),
        ),
        LegalSection(
            title = "Children",
            blocks = listOf(Paragraph("The app is not directed at children under 13, and we do not knowingly collect their data.")),
        ),
        LegalSection(
            title = "Changes to this policy",
            blocks = listOf(Paragraph("If this policy changes, we will update this page and the date above.")),
        ),
        LegalSection(
            title = "Contact",
            blocks = listOf(
                Paragraph("Questions or deletion requests: <a href=\"mailto:flashcardsdev@gmail.com\">flashcardsdev@gmail.com</a>"),
            ),
        ),
    ),
)
