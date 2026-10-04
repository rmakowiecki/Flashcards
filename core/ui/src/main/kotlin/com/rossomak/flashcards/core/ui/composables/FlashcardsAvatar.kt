package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Share of the avatar's diameter taken by the generic person icon. */
private const val AVATAR_ICON_FRACTION = 0.6f

/** Splits a display name into words on any run of whitespace or Unicode separator (NBSP included). */
private val AVATAR_NAME_WORD_SEPARATOR = Regex("[\\p{Z}\\s]+")

/** A URL no loader can resolve offline, to showcase the failed-load fallback. */
private const val SHOWCASE_BROKEN_PHOTO_URL = "file:///flashcards/missing-avatar.jpg"

private const val SHOWCASE_TWO_WORD_NAME = "Jane Doe"

private const val SHOWCASE_ONE_WORD_NAME = "Plato"

/**
 * Circular user avatar: the [photoUrl] loaded with Coil's default singleton loader and cropped to
 * the circle, else the initials of [displayName] (see [avatarInitials]), else a generic person icon.
 * The fallback shows while the photo is loading and when the load fails, so the circle is never empty.
 *
 * [style] follows the shared on-surface/on-gradient axis (ADR-0034): [OnSurface] is the tonal
 * container with no border, [OnGradient] is the translucent container with the
 * [com.rossomak.flashcards.core.ui.theme.AppSizes.onGradientBorder] hairline drawn over every
 * variant, the photo included, so the edge stays crisp on the brand gradient.
 *
 * [contentDescription] is applied once to the whole avatar; null marks it decorative and hides it
 * (initials included) from accessibility services.
 */
@Composable
fun FlashcardsAvatar(
    photoUrl: String?,
    displayName: String?,
    size: FlashcardsAvatarSize,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
) {
    val brandColors = MaterialTheme.brandColors
    val containerColor = when (style) {
        OnSurface -> brandColors.tonalButtonContainer
        OnGradient -> brandColors.onGradientContainer
    }
    val contentColor = when (style) {
        OnSurface -> brandColors.onTonalButtonContainer
        OnGradient -> brandColors.onGradientContent
    }
    val border = when (style) {
        OnSurface -> null
        OnGradient -> BorderStroke(width = MaterialTheme.sizes.onGradientBorder, color = brandColors.onGradientBorder)
    }
    val description = contentDescription
    val resolvedPhotoUrl = photoUrl?.takeIf { it.isNotBlank() }
    val initials = remember(displayName) { avatarInitials(displayName) }
    var isPhotoLoaded by remember(resolvedPhotoUrl) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .size(size.diameter)
            .clearAndSetSemantics {
                description?.let {
                    this.contentDescription = it
                    role = Role.Image
                }
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!isPhotoLoaded) {
                AvatarFallback(initials = initials, size = size)
            }
            if (resolvedPhotoUrl != null) {
                // No placeholder or error painter: until Success the image draws nothing, so the
                // fallback underneath is what shows while loading and after a failure.
                AsyncImage(
                    model = resolvedPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onState = { state -> isPhotoLoaded = state is AsyncImagePainter.State.Success },
                )
            }
        }
    }
}

@Composable
private fun AvatarFallback(
    initials: String?,
    size: FlashcardsAvatarSize,
) {
    if (initials == null) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(AVATAR_ICON_FRACTION),
        )
    } else {
        // Initials are decorative and must fit the circle: ignore the user's font scale, which at
        // large settings would overflow the Small avatar.
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
            Text(
                text = initials,
                style = size.initialsStyle,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The initials shown when there is no photo: the first letter of the first and the last word of
 * [displayName], uppercased; one letter for a one-word name; null for a null or blank name.
 * Letters are taken by code point and never filtered by character class, so an emoji, digit or
 * symbol that opens a word is shown as is.
 */
internal fun avatarInitials(displayName: String?): String? {
    val words = displayName.orEmpty().split(AVATAR_NAME_WORD_SEPARATOR).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    val firstInitial = words.first().firstLetterUppercased()
    return when (words.size) {
        1 -> firstInitial
        else -> firstInitial + words.last().firstLetterUppercased()
    }
}

private fun String.firstLetterUppercased(): String = substring(0, offsetByCodePoints(0, 1)).uppercase()

@Composable
private fun AvatarShowcaseMatrix(style: FlashcardsComponentStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal)) {
        AvatarShowcaseRow(photoUrl = null, displayName = SHOWCASE_TWO_WORD_NAME, style = style)
        AvatarShowcaseRow(photoUrl = null, displayName = SHOWCASE_ONE_WORD_NAME, style = style)
        AvatarShowcaseRow(photoUrl = SHOWCASE_BROKEN_PHOTO_URL, displayName = SHOWCASE_TWO_WORD_NAME, style = style)
        AvatarShowcaseRow(photoUrl = null, displayName = null, style = style)
    }
}

@Composable
private fun AvatarShowcaseRow(
    photoUrl: String?,
    displayName: String?,
    style: FlashcardsComponentStyle,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlashcardsAvatarSize.entries.forEach { avatarSize ->
            FlashcardsAvatar(
                photoUrl = photoUrl,
                displayName = displayName,
                size = avatarSize,
                contentDescription = null,
                style = style,
            )
        }
    }
}

@ShowkaseComposable(name = "Avatar — on surface", group = "Icons")
@Composable
fun FlashcardsAvatarOnSurfaceShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.padding(MaterialTheme.spacing.normal)) {
                AvatarShowcaseMatrix(style = OnSurface)
            }
        }
    }
}

@ShowkaseComposable(name = "Avatar — on gradient", group = "Icons")
@Preview
@Composable
fun FlashcardsAvatarOnGradientShowcase() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.brandColors.screenGradientBase) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.brandColors.screenGradient)
                    .padding(MaterialTheme.spacing.normal),
            ) {
                AvatarShowcaseMatrix(style = OnGradient)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsAvatarOnSurfacePreview() {
    FlashcardsAvatarOnSurfaceShowcase()
}
