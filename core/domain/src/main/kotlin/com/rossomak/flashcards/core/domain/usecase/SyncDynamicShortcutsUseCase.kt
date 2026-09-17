package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.model.ShortcutRoute
import com.rossomak.flashcards.core.domain.model.ShortcutTarget
import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

/**
 * Keeps the launcher's dynamic shortcut set mirroring the User's favorites, for the caller's
 * lifetime — never completes on its own; the caller launches it in its own scope and cancels it
 * (e.g. by scope teardown) to stop syncing. [ObserveFavoriteItemsUseCase] already resolves ids to
 * full [FavoriteItem]s sorted most-recently-favorited first; that order is preserved into the
 * [ShortcutTarget] list unchanged — [AppShortcutsRepository] treats list order as launcher display
 * rank and owns truncation to the platform's max dynamic shortcut count.
 */
class SyncDynamicShortcutsUseCase @Inject constructor(
    private val observeFavoriteItems: ObserveFavoriteItemsUseCase,
    private val appShortcutsRepository: AppShortcutsRepository,
) : NoParamUseCase<Unit> {

    override suspend operator fun invoke() {
        observeFavoriteItems()
            .map { favorites -> favorites.map { it.toShortcutTarget() } }
            .collect { shortcuts -> appShortcutsRepository.syncDynamicShortcuts(shortcuts) }
    }

    private fun FavoriteItem.toShortcutTarget(): ShortcutTarget = when (this) {
        is FavoriteItem.FavoriteCategory -> ShortcutTarget(
            id = "category:${category.id}",
            name = category.name,
            route = ShortcutRoute.category(category.id),
            iconSvg = category.iconSvg,
            color = category.color,
        )
        is FavoriteItem.FavoriteSubcategory -> ShortcutTarget(
            id = "subcategory:${subcategory.id}",
            name = subcategory.name,
            route = ShortcutRoute.subcategory(subcategory.categoryId, subcategory.id),
            iconSvg = parentCategory.iconSvg,
            color = parentCategory.color,
        )
    }
}
