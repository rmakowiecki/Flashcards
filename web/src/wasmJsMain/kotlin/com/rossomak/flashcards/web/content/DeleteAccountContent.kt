package com.rossomak.flashcards.web.content

import com.rossomak.flashcards.web.content.LegalBlock.BulletList
import com.rossomak.flashcards.web.content.LegalBlock.NumberedList
import com.rossomak.flashcards.web.content.LegalBlock.Paragraph

/** The account deletion steps the Play Console's data deletion question links to. */
val DeleteAccountDocument = LegalDocument(
    title = "Delete your Flashcards account",
    subtitle = "For the Flashcards Android app by rmakowiecki",
    intro = listOf(
        "You can delete your Flashcards account and all data linked to it at any time, in the app or, without the app, by email.",
    ),
    sections = listOf(
        LegalSection(
            title = "Delete in the app",
            blocks = listOf(
                NumberedList(
                    listOf(
                        "Open Settings and tap your avatar.",
                        "Tap Delete account and confirm.",
                    ),
                ),
                Paragraph(
                    "Your account and data are deleted as soon as you confirm. If deletion fails, the app tells you and you can try again.",
                ),
            ),
        ),
        LegalSection(
            title = "Request deletion by email",
            blocks = listOf(
                NumberedList(
                    listOf(
                        "Send an email to <a " +
                            "href=\"mailto:flashcardsdev@gmail.com?subject=Delete%20my%20Flashcards%20account\">flashcardsdev@gmail.com</a> " +
                            "with the subject \"Delete my Flashcards account\".",
                        "Send it from the email address of the Google or GitHub account you sign in to the app with, so we can " +
                            "confirm the account is yours.",
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
                        "Your account: the Google or GitHub account's user ID, email address, display name and profile photo URL.",
                        "Your study data: favorites, study session history, per-flashcard progress, XP, level and streak.",
                        "Your reports: the problem reports you submitted about flashcards and the bug reports you sent.",
                    ),
                ),
            ),
        ),
        LegalSection(
            title = "What we keep",
            blocks = listOf(
                Paragraph(
                    "We keep no account or study data after deletion. We never store your voice answers or their transcripts, so we hold none to delete.",
                ),
                Paragraph("Support emails you sent us stay in our inbox until you ask us to delete them."),
                Paragraph("Settings stored only on your device, such as your daily goal, are removed when you uninstall the app."),
                Paragraph("<a href=\"/privacy\">Privacy Policy</a>"),
            ),
        ),
    ),
)
