package com.rossomak.flashcards.core.ui.theme

import androidx.compose.ui.graphics.Color

private val HEX_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")

/** Fully opaque alpha channel, ORed onto the parsed RGB bits — category tint is never transparent. */
private const val OPAQUE_ALPHA_MASK = 0xFF000000.toInt()

/**
 * Parses a `#RRGGBB` hex string — [Category.color][com.rossomak.flashcards.core.domain.model.Category.color]
 * when present — into a Compose [Color]. This is a strict parser: category color is hand-curated,
 * authored data (not user input), so a malformed value throws [IllegalArgumentException] rather
 * than degrading to a fallback. [Category.color] itself is nullable (icon/color art can lag a
 * category's creation), so callers use [toCategoryColorOrNull] for graceful degradation rather than this
 * function handling null/fallback itself.
 *
 * Implemented as plain hex parsing rather than `android.graphics.Color.parseColor()` — the latter
 * is a stubbed Android-framework call that throws "not mocked" in this project's plain-JVM,
 * no-Robolectric unit tests (see TESTING.md), which would make this function's own fail-loud
 * contract untestable.
 */
fun String.toCategoryColor(): Color {
    require(HEX_COLOR_PATTERN.matches(this)) { "Category color must be #RRGGBB hex, was \"$this\"" }
    val rgb = substring(1).toInt(radix = 16)
    return Color(rgb or OPAQUE_ALPHA_MASK)
}

/**
 * Null-tolerant [toCategoryColor]: `null` (no curated color yet) and a malformed value both give `null`,
 * so the caller falls back to its own default tint.
 */
fun String?.toCategoryColorOrNull(): Color? = this?.let { runCatching { it.toCategoryColor() }.getOrNull() }
