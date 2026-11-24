package com.rossomak.flashcards.web.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.rossomak.flashcards.web.browser.openInNewTab
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.navigation.WebRoute
import com.rossomak.flashcards.web.theme.WebColors

private val focusedLinkStyle = SpanStyle(background = WebColors.focusedLinkBackground)

private val webLinkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline), focusedStyle = focusedLinkStyle)

private fun linkStyles(color: Color): TextLinkStyles = if (color.isSpecified) {
    TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline), focusedStyle = focusedLinkStyle)
} else {
    webLinkStyles
}

/** Text from a small HTML fragment ([parseInlineMarkup]). Site-path links open in place; others go to the browser. */
@Composable
fun HtmlText(html: String, style: TextStyle, onNavigate: OnNavigate, modifier: Modifier = Modifier, linkColor: Color = Color.Unspecified) {
    val uriHandler = LocalUriHandler.current
    val text = remember(html, onNavigate, uriHandler, linkColor) {
        parseInlineMarkup(
            markup = html,
            linkStyles = linkStyles(linkColor),
            linkInteractionListener = { link -> openLink(link, onNavigate, uriHandler) },
        )
    }
    Text(text = text, style = style, modifier = modifier)
}

/** A one-line link to another page of the site. */
@Composable
fun PageLink(label: String, route: WebRoute, style: TextStyle, onNavigate: OnNavigate, modifier: Modifier = Modifier) {
    val text = remember(label, route, onNavigate) {
        AnnotatedString.Builder().apply {
            withLink(LinkAnnotation.Clickable(tag = route.path, styles = webLinkStyles) { onNavigate(route) }) {
                append(label)
            }
        }.toAnnotatedString()
    }
    Text(text = text, style = style, modifier = modifier)
}

/** [prefix] followed by a link labelled [label] that opens [url] in a new tab. */
@Composable
fun NewTabLink(label: String, url: String, style: TextStyle, modifier: Modifier = Modifier, prefix: String = "") {
    val text = remember(prefix, label, url) {
        AnnotatedString.Builder().apply {
            append(prefix)
            withLink(LinkAnnotation.Clickable(tag = url, styles = webLinkStyles) { openInNewTab(url) }) {
                append(label)
            }
        }.toAnnotatedString()
    }
    Text(text = text, style = style, modifier = modifier)
}

private fun openLink(link: LinkAnnotation, onNavigate: OnNavigate, uriHandler: UriHandler) {
    val url = (link as? LinkAnnotation.Url)?.url ?: return
    if (url.startsWith("/")) onNavigate(WebRoute.fromPath(url)) else uriHandler.openUri(url)
}
