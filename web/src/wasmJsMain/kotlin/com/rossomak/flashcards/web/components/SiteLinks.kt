package com.rossomak.flashcards.web.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.navigation.WebRoute
import com.rossomak.flashcards.web.theme.WEB_MUTED_CONTENT_ALPHA

/** The contact address on the closing page and the legal pages' footer. */
private const val CONTACT_LINK = """<a href="mailto:flashcardsdev@gmail.com">Contact</a>"""

/**
 * The site's quiet links: the Privacy Policy and the account deletion page, and with [showContact]
 * the contact address. Small, muted and centered, wrapping onto a second line when narrow.
 */
@Composable
fun SiteLinks(modifier: Modifier = Modifier, showContact: Boolean, onNavigate: OnNavigate) {
    val style = MaterialTheme.typography.bodyLarge
    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = WEB_MUTED_CONTENT_ALPHA)) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        ) {
            PageLink(label = "Privacy Policy", route = WebRoute.PrivacyPolicy, style = style, onNavigate = onNavigate)
            PageLink(label = "Delete your account", route = WebRoute.DeleteAccount, style = style, onNavigate = onNavigate)
            if (showContact) HtmlText(html = CONTACT_LINK, style = style, onNavigate = onNavigate)
        }
    }
}
