package com.rossomak.flashcards.core.ui.composables.banners

/**
 * Color coding of a [FlashcardsInfoBanner] drawn on a themed surface. Each tone maps onto a
 * valence pair in [com.rossomak.flashcards.core.designsystem.theme.SemanticColors] — the same pairs the
 * study screen's rating buttons use — but keeps banner vocabulary so a call site reads as intent
 * ("this tip is a caveat") rather than as a color.
 *
 * Ignored for [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient],
 * which is always the translucent white treatment.
 */
enum class FlashcardsInfoBannerTone {
    /** Green — something earned, completed, or safe. */
    Positive,

    /** Amber — a neutral tip or explanation. The default, and the most common banner by far. */
    Info,

    /** Red — a caveat or restriction the reader should notice before acting. */
    Warning,
}
