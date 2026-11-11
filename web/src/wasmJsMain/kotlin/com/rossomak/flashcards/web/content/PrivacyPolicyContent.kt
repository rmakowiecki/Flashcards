package com.rossomak.flashcards.web.content

import com.rossomak.flashcards.web.content.LegalBlock.BulletList
import com.rossomak.flashcards.web.content.LegalBlock.Paragraph

/** The Privacy Policy linked from the Play Console listing and the app's Account screen. */
val PrivacyPolicyDocument = LegalDocument(
    title = "Flashcards Privacy Policy",
    subtitle = "Last updated: November 12, 2026",
    intro = listOf(
        "This policy explains what data the Flashcards Android app (\"the app\", \"we\") collects, why, who processes it, and how you can delete it.",
    ),
    sections = listOf(
        LegalSection(
            title = "Data we collect",
            blocks = listOf(
                BulletList(
                    listOf(
                        "<strong>Account data.</strong> When you sign in with Google or GitHub, we receive that account's user ID, " +
                            "email address, display name and profile photo URL through Firebase Authentication. If you pick favorite " +
                            "topics during setup before signing in, the app first creates an anonymous account to save them. It " +
                            "becomes your account when you sign in, or is discarded if you sign in to an account you already have.",
                        "<strong>Study data.</strong> Your favorites, study sessions (dates, durations, which flashcards you studied " +
                            "and how you rated them), per-flashcard progress, XP, level and streak.",
                        "<strong>Problem reports.</strong> Reports you submit about a flashcard's content.",
                        "<strong>Bug reports.</strong> When you report a bug, we receive your description, the severity you pick, " +
                            "the app version, device model and Android version, linked to your account. Please do not include " +
                            "personal information in the description.",
                        "<strong>Support emails.</strong> Contact support opens an email draft that starts with your account ID, " +
                            "the app version, device model and Android version. You choose whether to send it.",
                        "<strong>Voice answers (optional).</strong> Only if you turn on Voice answering. See below.",
                        "<strong>Device integrity check.</strong> To confirm that requests to our server come from a genuine copy " +
                            "of the app, the app gets an attestation from Google Play Integrity through Firebase App Check.",
                        "<strong>Crash diagnostics.</strong> When the app crashes or hits an error, it sends a crash report: the stack " +
                            "trace, recent app log lines, the app version, device model and Android version, and a random identifier " +
                            "for this installation of the app. For a crash in native code it also includes a snapshot of the app's " +
                            "memory at the time of the crash. Reports carry no account identifier.",
                    ),
                ),
                Paragraph(
                    "Some data is stored only on your device: settings such as your daily goal and study preferences, a cache " +
                        "of flashcards, and study sessions waiting to be sent to our server. Uninstalling the app removes it.",
                ),
                Paragraph("The app does not show ads, does not use analytics or advertising SDKs, and does not sell your data."),
            ),
        ),
        LegalSection(
            title = "Microphone and voice answering",
            blocks = listOf(
                Paragraph(
                    "The app uses the microphone only for the voice test during setup and for Voice answering, and asks for " +
                        "your permission first. The voice test records you, changes your voice and plays it back, all on your " +
                        "device: nothing from it leaves the device.",
                ),
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
                        "To investigate and fix bugs you report.",
                        "To answer your support requests.",
                        "To protect our server from requests that do not come from the app.",
                        "To find and fix crashes and errors in the app.",
                    ),
                ),
            ),
        ),
        LegalSection(
            title = "Where data is stored",
            blocks = listOf(
                Paragraph(
                    "Account and study data, problem reports and bug reports are stored in Google Firebase (Authentication, " +
                        "Cloud Firestore and Cloud Functions), operated by Google LLC. Crash reports are processed by Firebase " +
                        "Crashlytics, and device integrity checks by Firebase App Check and Google Play Integrity, all operated by " +
                        "Google LLC. Firebase records your IP address when you sign in and call our server, to protect it from " +
                        "abuse. Support emails are kept in our Gmail inbox. Data is encrypted in transit.",
                ),
            ),
        ),
        LegalSection(
            title = "Data retention and deletion",
            blocks = listOf(
                Paragraph(
                    "We keep your account data, study data, problem reports and bug reports for as long as your account exists.",
                ),
                Paragraph(
                    "You can delete your account and all of this data in the app, or without the app by email. Our " +
                        "<a href=\"/delete-account\">account deletion page</a> has the steps for both.",
                ),
                Paragraph(
                    "We delete your support emails when you ask us to. Crash reports are not linked to your account. Google keeps " +
                        "them for 90 days, then removes them from its systems.",
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
