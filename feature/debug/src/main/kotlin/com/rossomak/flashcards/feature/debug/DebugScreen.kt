package com.rossomak.flashcards.feature.debug

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroup
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroupItem
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.showcase.Showcase
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The hub for every debug-only tool, and the whole reason this module exists.
 *
 * It is the tab's start destination rather than the voice harness itself: debug affordances used
 * to be scattered — the harness on its own tab, onboarding replay and the component showcase
 * buried in Settings, which ships in release. Collecting them here means release builds lose the
 * entry points by dropping one `debugImplementation`, and Settings stops carrying rows a user must
 * never see.
 */
@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
    buildInfo: BuildInfo,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToVoiceDebug: () -> Unit,
) {
    val context = LocalContext.current

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            DebugDestination.Onboarding -> onNavigateToOnboarding()
        }
    }

    val showcaseIntent = remember { Showcase.intentOrNull(context) }

    DebugContent(
        modifier = modifier,
        buildInfo = buildInfo,
        showcaseIntent = showcaseIntent,
        onVoiceDebugClick = onNavigateToVoiceDebug,
        onReplayOnboardingClick = viewModel::onReplayOnboardingClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugContent(
    modifier: Modifier = Modifier,
    buildInfo: BuildInfo,
    showcaseIntent: Intent?,
    onVoiceDebugClick: () -> Unit,
    onReplayOnboardingClick: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DebugTopAppBar(buildInfo = buildInfo, scrollBehavior = scrollBehavior)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.spacing.normal,
                    vertical = MaterialTheme.spacing.small,
                ),
        ) {
            FlashcardsListGroup(
                items = buildList {
                    add(
                        FlashcardsListGroupItem.Row(
                            title = stringResource(R.string.debug_voice_harness_label),
                            onClick = onVoiceDebugClick,
                            secondaryText = stringResource(R.string.debug_voice_harness_message),
                            leading = {
                                FlashcardsIconTile(
                                    icon = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                )
                            },
                            trailing = { FlashcardsChevron() },
                        ),
                    )
                    add(
                        FlashcardsListGroupItem.Row(
                            title = stringResource(R.string.debug_replay_onboarding_label),
                            onClick = onReplayOnboardingClick,
                            secondaryText = stringResource(R.string.debug_replay_onboarding_message),
                            leading = {
                                FlashcardsIconTile(
                                    icon = Icons.Default.Refresh,
                                    contentDescription = null,
                                )
                            },
                            trailing = { FlashcardsChevron() },
                        ),
                    )
                    // Absent when Showkase is not on the classpath, which is every non-debug build
                    // of :core:ui — the row would open nothing.
                    if (showcaseIntent != null) {
                        add(
                            FlashcardsListGroupItem.Row(
                                title = stringResource(R.string.debug_showcase_label),
                                onClick = { context.startActivity(showcaseIntent) },
                                secondaryText = stringResource(R.string.debug_showcase_message),
                                leading = {
                                    FlashcardsIconTile(
                                        icon = Icons.Default.Widgets,
                                        contentDescription = null,
                                    )
                                },
                                trailing = { FlashcardsChevron() },
                            ),
                        )
                    }
                },
            )
        }
    }
}

/**
 * [TwoRowsTopAppBar] rather than a flexible app bar: its subtitle slot is told which row it renders
 * in, so the build line shows only while the bar is expanded and the collapsed bar keeps the title
 * alone — the flexible bars repeat the subtitle in both rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugTopAppBar(buildInfo: BuildInfo, scrollBehavior: TopAppBarScrollBehavior) {
    TwoRowsTopAppBar(
        title = { Text(text = stringResource(R.string.debug_title)) },
        subtitle = { expanded ->
            if (expanded) {
                Text(
                    text = stringResource(
                        R.string.debug_build_info_label,
                        buildInfo.versionName,
                        buildInfo.versionCode,
                        buildInfo.gitShortSha,
                    ),
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@PreviewLightDark
@Composable
private fun DebugContentPreview() {
    FlashcardsTheme {
        DebugContent(
            buildInfo = BuildInfo(
                versionName = "0.1.1234-profiling",
                versionCode = 1234,
                gitShortSha = "7f57c64",
            ),
            showcaseIntent = null,
            onVoiceDebugClick = {},
            onReplayOnboardingClick = {},
        )
    }
}
