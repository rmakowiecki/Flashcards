package com.rossomak.flashcards.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.HtmlText
import com.rossomak.flashcards.web.components.RevealWhenScrolledIn
import com.rossomak.flashcards.web.components.WebPage
import com.rossomak.flashcards.web.content.LegalBlock
import com.rossomak.flashcards.web.content.LegalBlock.BulletList
import com.rossomak.flashcards.web.content.LegalBlock.NumberedList
import com.rossomak.flashcards.web.content.LegalBlock.Paragraph
import com.rossomak.flashcards.web.content.LegalDocument
import com.rossomak.flashcards.web.content.LegalSection
import com.rossomak.flashcards.web.navigation.OnNavigate

/** A legal page (privacy policy, account deletion): the title, then its sections, on the site's reading card. */
@Composable
fun LegalDocumentPage(modifier: Modifier = Modifier, document: LegalDocument, onNavigate: OnNavigate) {
    WebPage(modifier = modifier, onNavigate = onNavigate) {
        Text(
            text = document.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = document.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.spacing.xxsmall),
        )
        Column(
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            document.intro.forEach { html -> BodyText(html = html, onNavigate = onNavigate) }
        }
        document.sections.forEach { section -> LegalSectionContent(section = section, onNavigate = onNavigate) }
    }
}

/** A section: its divider and heading reveal once, as they first scroll into the window; its text never moves. */
@Composable
private fun LegalSectionContent(section: LegalSection, onNavigate: OnNavigate) {
    RevealWhenScrolledIn {
        HorizontalDivider(
            modifier = Modifier.padding(top = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.normal),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
    Column(
        modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        section.blocks.forEach { block -> LegalBlockContent(block = block, onNavigate = onNavigate) }
    }
}

@Composable
private fun LegalBlockContent(block: LegalBlock, onNavigate: OnNavigate) {
    when (block) {
        is Paragraph -> BodyText(html = block.html, onNavigate = onNavigate)
        is BulletList -> block.items.forEach { item -> ListItem(marker = BULLET, html = item, onNavigate = onNavigate) }
        is NumberedList -> block.items.forEachIndexed { index, item ->
            ListItem(marker = "${index + 1}.", html = item, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun ListItem(marker: String, html: String, onNavigate: OnNavigate) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
        Text(text = marker, style = MaterialTheme.typography.bodyLarge)
        BodyText(html = html, onNavigate = onNavigate)
    }
}

@Composable
private fun BodyText(html: String, onNavigate: OnNavigate) {
    HtmlText(html = html, style = MaterialTheme.typography.bodyLarge, onNavigate = onNavigate, linkColor = MaterialTheme.colorScheme.primary)
}

private const val BULLET = "•"
