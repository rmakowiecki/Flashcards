package com.rossomak.flashcards.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.components.HtmlText
import com.rossomak.flashcards.web.components.PageLink
import com.rossomak.flashcards.web.components.WebPage
import com.rossomak.flashcards.web.content.LegalBlock
import com.rossomak.flashcards.web.content.LegalBlock.BulletList
import com.rossomak.flashcards.web.content.LegalBlock.NumberedList
import com.rossomak.flashcards.web.content.LegalBlock.Paragraph
import com.rossomak.flashcards.web.content.LegalDocument
import com.rossomak.flashcards.web.content.LegalSection
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.navigation.WebRoute
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA

/** A legal page (privacy policy, account deletion): a link home, the title, then its sections. */
@Composable
fun LegalDocumentPage(modifier: Modifier = Modifier, document: LegalDocument, onNavigate: OnNavigate) {
    WebPage(modifier = modifier) {
        PageLink(label = "Flashcards", route = WebRoute.Home, style = MaterialTheme.typography.titleMediumEmphasized, onNavigate = onNavigate)
        Spacer(Modifier.height(MaterialTheme.spacing.large))
        Text(
            text = document.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = document.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA),
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

@Composable
private fun LegalSectionContent(section: LegalSection, onNavigate: OnNavigate) {
    HorizontalDivider(
        modifier = Modifier.padding(top = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.normal),
        color = MaterialTheme.brandColors.onGradientBorder,
    )
    Text(
        text = section.title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
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
    HtmlText(html = html, style = MaterialTheme.typography.bodyLarge, onNavigate = onNavigate)
}

private const val BULLET = "•"
