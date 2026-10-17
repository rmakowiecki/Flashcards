package com.rossomak.flashcards.web.content

/**
 * A legal page's copy as data, so the page composable only lays it out. Text is a small HTML
 * fragment (see [com.rossomak.flashcards.web.components.HtmlText]) so bold lead-ins and links stay
 * inline with the sentence they belong to.
 */
data class LegalDocument(
    val title: String,
    val subtitle: String,
    val intro: List<String>,
    val sections: List<LegalSection>,
)

data class LegalSection(val title: String, val blocks: List<LegalBlock>)

sealed interface LegalBlock {
    data class Paragraph(val html: String) : LegalBlock

    data class BulletList(val items: List<String>) : LegalBlock

    data class NumberedList(val items: List<String>) : LegalBlock
}
