package com.rossomak.flashcards.feature.debug.backdropblur

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.backdrop.LocalBackdropBlurEnabled
import com.rossomak.flashcards.core.ui.backdrop.LocalBackdropBlurRadius
import com.rossomak.flashcards.core.ui.backdrop.blurBackdropScope
import com.rossomak.flashcards.core.ui.backdrop.blurBackdropSource
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatar
import com.rossomak.flashcards.core.ui.composables.FlashcardsAvatarSize
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyStateTone
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconCircle
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownTone
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledIconButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsOutlinedButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalIconButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCard
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.debug.R
import kotlin.math.roundToInt

/** Every blurring component over drifting blobs; its state is screen-local, so no ViewModel. */
@Composable
fun BackdropBlurDebugScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
) {
    val themeBlurRadiusDp = MaterialTheme.sizes.backdropBlurRadius.value
    var isBlobsOn by rememberSaveable { mutableStateOf(true) }
    var isBlurOn by rememberSaveable { mutableStateOf(true) }
    var blurRadiusDp by rememberSaveable { mutableStateOf(themeBlurRadiusDp) }

    BackdropBlurDebugContent(
        modifier = modifier,
        state = BackdropBlurDebugScreenState(isBlobsOn = isBlobsOn, isBlurOn = isBlurOn, blurRadiusDp = blurRadiusDp),
        onNavigateBack = onNavigateBack,
        onBlobsChange = { isBlobsOn = it },
        onBlurChange = { isBlurOn = it },
        onBlurRadiusChange = { blurRadiusDp = it },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackdropBlurDebugContent(
    modifier: Modifier = Modifier,
    state: BackdropBlurDebugScreenState,
    onNavigateBack: () -> Unit,
    onBlobsChange: (Boolean) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
) {
    val onGradientContent = MaterialTheme.brandColors.onGradientContent

    CompositionLocalProvider(
        LocalBackdropBlurEnabled provides state.isBlurOn,
        LocalBackdropBlurRadius provides state.blurRadiusDp.dp,
    ) {
        Box(modifier = modifier.fillMaxSize().blurBackdropScope()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blurBackdropSource()
                    .background(MaterialTheme.brandColors.screenGradient),
            ) {
                if (state.isBlobsOn) DriftingBlobs()
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text(text = stringResource(R.string.backdrop_blur_debug_title)) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.debug_voice_harness_back_cd),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            navigationIconContentColor = onGradientContent,
                            titleContentColor = onGradientContent,
                        ),
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(MaterialTheme.spacing.normal),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    Controls(
                        state = state,
                        onBlobsChange = onBlobsChange,
                        onBlurChange = onBlurChange,
                        onBlurRadiusChange = onBlurRadiusChange,
                    )
                    Gallery()
                }
            }
        }
    }
}

@Composable
private fun Controls(
    state: BackdropBlurDebugScreenState,
    onBlobsChange: (Boolean) -> Unit,
    onBlurChange: (Boolean) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
        SwitchRow(label = stringResource(R.string.backdrop_blur_debug_blobs_label), checked = state.isBlobsOn, onCheckedChange = onBlobsChange)
        SwitchRow(label = stringResource(R.string.backdrop_blur_debug_blur_label), checked = state.isBlurOn, onCheckedChange = onBlurChange)
        Text(
            text = stringResource(R.string.backdrop_blur_debug_radius_label, state.blurRadiusDp.roundToInt()),
            color = MaterialTheme.brandColors.onGradientContent,
        )
        Slider(
            value = state.blurRadiusDp,
            onValueChange = onBlurRadiusChange,
            valueRange = MIN_BLUR_RADIUS_DP..MAX_BLUR_RADIUS_DP,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MaterialTheme.brandColors.onGradientContent)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.brandColors.onGradientContent)
}

@Composable
private fun Gallery() {
    CardsAndBanners()
    BadgesAndIcons()
    Buttons()
    EmptyStates()
}

@Composable
private fun CardsAndBanners() {
    val sectionSpacing = Arrangement.spacedBy(MaterialTheme.spacing.small)

    SectionTitle(stringResource(R.string.backdrop_blur_debug_cards_section_title))
    Column(verticalArrangement = sectionSpacing) {
        FlashcardsLevelCard(
            state = FlashcardsLevelCardState.Content(
                level = 12,
                xpIntoCurrentLevel = 2_840L,
                xpForNextLevel = 5_000L,
                photoUrl = null,
                displayName = "Jane Doe",
            ),
            style = OnGradient,
        )
        FlashcardsXpBreakdownRow(
            label = "Card Mastery",
            value = "11 cards × 100 = +1,100 XP",
            icon = Icons.Filled.WorkspacePremium,
            modifier = Modifier.fillMaxWidth(),
        )
        FlashcardsXpBreakdownRow(
            label = "De-mastery",
            value = "4 cards × 80 = −320 XP",
            icon = Icons.AutoMirrored.Filled.TrendingDown,
            modifier = Modifier.fillMaxWidth(),
            tone = FlashcardsXpBreakdownTone.Loss,
        )
        FlashcardsInfoBanner(
            text = "Voice answering is on for this session",
            icon = Icons.Filled.Info,
            style = OnGradient,
        )
    }
}

@Composable
private fun BadgesAndIcons() {
    SectionTitle(stringResource(R.string.backdrop_blur_debug_badges_section_title))
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlashcardsMetadataBadge(label = "12 min", icon = Icons.Filled.Schedule, style = OnGradient)
        FlashcardsIconCircle(icon = Icons.Filled.Celebration, contentDescription = null, style = OnGradient)
        FlashcardsAvatar(
            photoUrl = null,
            displayName = "Jane Doe",
            size = FlashcardsAvatarSize.Medium,
            contentDescription = null,
            style = OnGradient,
        )
    }
}

@Composable
private fun Buttons() {
    val sectionSpacing = Arrangement.spacedBy(MaterialTheme.spacing.small)

    SectionTitle(stringResource(R.string.backdrop_blur_debug_buttons_section_title))
    Column(verticalArrangement = sectionSpacing) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            FlashcardsTonalButton(text = "Edit", onClick = {}, icon = Icons.Filled.Edit, style = OnGradient)
            FlashcardsTonalButton(text = "Edit", onClick = {}, icon = Icons.Filled.Edit, style = OnGradient, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            FlashcardsOutlinedButton(text = "Skip card", onClick = {}, style = OnGradient)
            FlashcardsOutlinedButton(text = "Skip card", onClick = {}, style = OnGradient, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            FlashcardsFilledButton(text = "Start", onClick = {}, style = OnGradient)
            FlashcardsFilledButton(text = "Start", onClick = {}, style = OnGradient, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            FlashcardsTonalIconButton(icon = Icons.Filled.PlayArrow, contentDescription = "Play", onClick = {}, style = OnGradient)
            FlashcardsTonalIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                onClick = {},
                style = OnGradient,
                enabled = false,
            )
            FlashcardsFilledIconButton(icon = Icons.Filled.PlayArrow, contentDescription = "Play", onClick = {}, style = OnGradient)
            FlashcardsFilledIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                onClick = {},
                style = OnGradient,
                enabled = false,
            )
        }
    }
}

@Composable
private fun EmptyStates() {
    val sectionSpacing = Arrangement.spacedBy(MaterialTheme.spacing.small)

    SectionTitle(stringResource(R.string.backdrop_blur_debug_empty_states_section_title))
    Column(verticalArrangement = sectionSpacing) {
        FlashcardsEmptyState(
            icon = Icons.Filled.Info,
            title = "Nothing here yet",
            supportingText = "Cards you favourite will show up here.",
            modifier = Modifier.fillMaxWidth(),
            style = OnGradient,
        )
        FlashcardsEmptyState(
            icon = Icons.Filled.WarningAmber,
            title = "Could not load",
            supportingText = "Check your connection and try again.",
            modifier = Modifier.fillMaxWidth(),
            tone = FlashcardsEmptyStateTone.Error,
            style = OnGradient,
        )
    }
}

private data class Blob(
    val color: Color,
    val diameter: Dp,
    val startFraction: Pair<Float, Float>,
    val endFraction: Pair<Float, Float>,
    val periodMillis: Int,
)

private val Blobs = listOf(
    Blob(Color.White.copy(alpha = 0.55f), 160.dp, 0.05f to 0.10f, 0.70f to 0.35f, 5_200),
    Blob(Color(0xFFFF6FB5).copy(alpha = 0.70f), 200.dp, 0.80f to 0.30f, 0.10f to 0.65f, 6_800),
    Blob(Color(0xFF4DD0E1).copy(alpha = 0.65f), 140.dp, 0.30f to 0.85f, 0.75f to 0.55f, 4_400),
)

/** Translucent discs for the blur to have motion to blur. */
@Composable
private fun DriftingBlobs() {
    val transition = rememberInfiniteTransition(label = "drifting blobs")
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        Blobs.forEachIndexed { index, blob ->
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(blob.periodMillis, easing = LinearEasing), RepeatMode.Reverse),
                label = "blob $index",
            )
            Box(
                modifier = Modifier
                    .size(blob.diameter)
                    .graphicsLayer {
                        translationX = widthPx * (blob.startFraction.first + (blob.endFraction.first - blob.startFraction.first) * progress)
                        translationY = heightPx * (blob.startFraction.second + (blob.endFraction.second - blob.startFraction.second) * progress)
                    }
                    .background(blob.color, CircleShape),
            )
        }
    }
}

private const val MIN_BLUR_RADIUS_DP = 0f
private const val MAX_BLUR_RADIUS_DP = 60f

@PreviewLightDark
@Composable
private fun BackdropBlurDebugContentPreview() {
    FlashcardsTheme {
        BackdropBlurDebugContent(
            state = BackdropBlurDebugScreenState(blurRadiusDp = 20f),
            onNavigateBack = {},
            onBlobsChange = {},
            onBlurChange = {},
            onBlurRadiusChange = {},
        )
    }
}
