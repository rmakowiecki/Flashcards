package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Keep
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface

private const val TITLE_ONE_LINE_FROM_FRACTION = 0.5f

/** [FlashcardsCollapsingTopAppBar] for a plain text [title] and [subtitle]; only an expanded [FlashcardsTopAppBarSize.Large] title wraps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsTopAppBar(
    title: String,
    size: FlashcardsTopAppBarSize,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
    subtitle: String? = null,
    subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed = Keep,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    backdrop: @Composable FlashcardsTopAppBarBackdropScope.() -> Unit = {},
) {
    FlashcardsCollapsingTopAppBar(
        size = size,
        scrollBehavior = scrollBehavior,
        title = { FlashcardsTopAppBarTitle(text = title, expandedMaxLines = size.expandedTitleMaxLines) },
        modifier = modifier,
        style = style,
        navigationIcon = navigationIcon,
        subtitle = subtitle?.let { { Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        subtitleWhenCollapsed = subtitleWhenCollapsed,
        actions = { actions() },
        backdrop = backdrop,
    )
}

@Composable
private fun FlashcardsTopAppBarTitle(text: String, expandedMaxLines: Int) {
    val collapsedFraction = LocalTopAppBarTitleFraction.current
    val wraps by remember(collapsedFraction) { derivedStateOf { collapsedFraction() < TITLE_ONE_LINE_FROM_FRACTION } }
    Text(text = text, maxLines = if (wraps) expandedMaxLines else 1, overflow = TextOverflow.Ellipsis)
}

/** The back arrow for a [FlashcardsTopAppBar]'s `navigationIcon`. */
@Composable
fun FlashcardsTopAppBarBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.common_navigate_back_cd),
        )
    }
}
