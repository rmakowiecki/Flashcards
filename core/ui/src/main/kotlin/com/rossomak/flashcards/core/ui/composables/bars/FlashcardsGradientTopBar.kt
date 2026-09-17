package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors

/**
 * The small centred-title top bar for screens painted with [com.rossomak.flashcards.core.ui.theme.BrandColors.screenGradient],
 * full-bleed. Unlike [FlashcardsTopAppBar], this bar paints nothing itself — both its container and
 * scrolled-container colours are [Color.Transparent] — so the screen's own gradient shows through it,
 * and bleeds up behind the status bar, for free. Content colours come from the fixed
 * [com.rossomak.flashcards.core.ui.theme.BrandColors.onGradientContent] token rather than the theme,
 * same as every other on-gradient component.
 *
 * [navigationIcon] is a caller-supplied slot, not a fixed control: the Preview screen wants a close
 * (X), a future session-summary screen may want nothing at all. Hardcoding one shape would fork this
 * component on its second consumer. [actions] follows the same convention.
 *
 * [FlashcardsTopAppBar] is untouched by this — it exists for the large gradient-*painting* treatment
 * and keeps its own users; this component is for screens that paint the gradient themselves.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FlashcardsGradientTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val onGradientContent = MaterialTheme.brandColors.onGradientContent

    CenterAlignedTopAppBar(
        title = {
            // titleSmall, not the default titleLarge: this screen's title is a long "Category ·
            // Subcategory" string (see PreviewStudySessionScreen.screenTitle) and reads as an app
            // bar label, not a headline — titleLarge dwarfed the hero content beneath it.
            // basicMarquee no-ops when the text fits, and scrolls it only when it would otherwise
            // be ellipsized, so long "Category · Subcategory" titles stay fully readable.
            Text(
                text = title,
                modifier = Modifier.basicMarquee(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = onGradientContent,
            titleContentColor = onGradientContent,
            actionIconContentColor = onGradientContent,
            subtitleContentColor = onGradientContent
        ),
        scrollBehavior = scrollBehavior,
    )
}

@ShowkaseComposable(name = "Gradient top bar", group = "Bars")
@Preview
@Composable
fun FlashcardsGradientTopBarShowcase() {
    FlashcardsTheme {
        Box(modifier = Modifier.background(MaterialTheme.brandColors.screenGradient)) {
            FlashcardsGradientTopBar(
                title = "Study session",
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun FlashcardsGradientTopBarPreview() {
    FlashcardsGradientTopBarShowcase()
}
