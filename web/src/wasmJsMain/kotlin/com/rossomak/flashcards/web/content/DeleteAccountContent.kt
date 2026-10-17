package com.rossomak.flashcards.web.content

import com.rossomak.flashcards.web.content.LegalBlock.BulletList
import com.rossomak.flashcards.web.content.LegalBlock.NumberedList
import com.rossomak.flashcards.web.content.LegalBlock.Paragraph

/** The account deletion steps the Play Console's data deletion question links to. */
val DeleteAccountDocument = LegalDocument(
    title = "Delete your Flashcards account",
    subtitle = "For the Flashcards Android app by rmakowiecki",
    intro = listOf(
        "You can ask us to delete your Flashcards account and all data linked to it at any time, without opening the app.",
    ),
    sections = listOf(
        LegalSection(
            title = "How to request deletion",
            blocks = listOf(
                NumberedList(
                    listOf(
                        "Send an email to <a " +
                            "href=\"mailto:flashcardsdev@gmail.com?subject=Delete%20my%20Flashcards%20account\">flashcardsdev@gmail.com</a> " +
                            "with the subject \"Delete my Flashcards account\".",
                        "Send it from the Google account email address you use to sign in to the app, so we can confirm the account is yours.",
                        "We reply to confirm when your account and data have been deleted, within 30 days of your request.",
                    ),
                ),
            ),
        ),
        LegalSection(
            title = "What we delete",
            blocks = listOf(
                BulletList(
                    listOf(
                        "Your account: Google account user ID, email address, display name and profile photo URL.",
                        "Your study data: favorites, study session history, per-flashcard progress, XP, level and streak.",
                        "Content you created: your private flashcards and the problem reports you submitted.",
                    ),
                ),
            ),
        ),
        LegalSection(
            title = "What we keep",
            blocks = listOf(
                Paragraph(
                    "We do not keep any data linked to your account after deletion. We never store your voice answers or their transcripts, so we hold none to delete.",
                ),
                Paragraph("Settings stored only on your device, such as your daily goal, are removed when you uninstall the app."),
                Paragraph("<a href=\"/privacy\">Privacy Policy</a>"),
            ),
        ),
    ),
)
