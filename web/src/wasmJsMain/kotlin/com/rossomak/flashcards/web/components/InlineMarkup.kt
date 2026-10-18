package com.rossomak.flashcards.web.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import com.rossomak.flashcards.core.designsystem.theme.EmphasizedSpanStyle

/**
 * Parses the two inline tags the site's copy uses, `<strong>` and `<a href="...">`, into an
 * [AnnotatedString]. Compose's own `AnnotatedString.fromHtml` exists only on Android, so the web
 * target carries this small parser instead. Any other tag, or an unclosed one, throws: the copy is
 * written in this repository, so a typo should fail a test rather than render as raw markup.
 */
fun parseInlineMarkup(markup: String, linkStyles: TextLinkStyles? = null, linkInteractionListener: LinkInteractionListener? = null): AnnotatedString = buildAnnotatedString {
    val openTags = ArrayDeque<String>()
    var cursor = 0
    for (tag in TAG.findAll(markup)) {
        append(markup.substring(cursor, tag.range.first))
        cursor = tag.range.last + 1
        val (closing, name, href) = tag.destructured
        when {
            closing.isNotEmpty() -> {
                require(openTags.removeLastOrNull() == name) { "Unexpected </$name> in: $markup" }
                pop()
            }
            name == STRONG && href.isEmpty() -> {
                openTags.addLast(name)
                pushStyle(EmphasizedSpanStyle)
            }
            name == ANCHOR && href.isNotEmpty() -> {
                openTags.addLast(name)
                pushLink(LinkAnnotation.Url(url = href, styles = linkStyles, linkInteractionListener = linkInteractionListener))
            }
            else -> throw IllegalArgumentException("Unsupported tag ${tag.value} in: $markup")
        }
    }
    require(openTags.isEmpty()) { "Unclosed <${openTags.last()}> in: $markup" }
    append(markup.substring(cursor))
}

private const val STRONG = "strong"
private const val ANCHOR = "a"

/** `<strong>`, `</strong>`, `<a href="...">` and `</a>`; groups are the slash, the name and the href. */
private val TAG = Regex("""<(/?)(\w+)(?:\s+href="([^"]*)")?\s*>""")
