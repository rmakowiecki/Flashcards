package com.rossomak.flashcards.core.domain.model

/**
 * A Category or Subcategory the launcher can pin or list as a dynamic shortcut. [id] is
 * deterministic — `"category:{id}"` for a Category, `"subcategory:{id}"` for a Subcategory — so a
 * target already pinned and later re-synced as a dynamic shortcut updates in place instead of
 * duplicating, since pinned and dynamic shortcuts share one app-wide id namespace.
 *
 * [route] is the ADR-0003 tab-prefixed route string (`/study/category/{id}` or
 * `/study/category/{id}/subcategory/{id}`), carried in the shortcut's Intent via
 * `AppShortcutsRepository.EXTRA_ROUTE` under action `AppShortcutsRepository.ACTION_OPEN_ROUTE`.
 *
 * [iconSvg] and [color] mirror the owning [Category]'s [Category.iconSvg]/[Category.color] — both
 * nullable, since a Category may have neither set; shortcut icon rasterization must handle null.
 */
data class ShortcutTarget(
    val id: String,
    val name: String,
    val route: String,
    val iconSvg: String?,
    val color: String?,
)
