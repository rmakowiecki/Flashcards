package com.rossomak.flashcards.core.ui.composables.common

/**
 * Which background a design-system component is drawn on. Shared across component families
 * (buttons, badges, progress bars, …) that all need the same axis — a component cannot read the
 * surface behind it, so the caller declares it. See ADR-0034.
 */
enum class FlashcardsComponentStyle {
    /** Default — a plain themed surface. */
    OnSurface,

    /**
     * For use on a fixed brand gradient (e.g.
     * [com.rossomak.flashcards.core.designsystem.theme.BrandColors.topBarGradient],
     * [com.rossomak.flashcards.core.designsystem.theme.BrandColors.ctaButtonGradient]). Those gradients
     * never flip to a dark variant, so this style's colors are theme-independent too.
     */
    OnGradient,
}
