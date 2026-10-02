package com.rossomak.flashcards.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteCategory
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(
        HomeScreenState(
            favoriteItems = FakeFavorites.items,
            progressSummary = FakeFavorites.progressSummary,
        ),
    )
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            delay(FakeFavorites.progressResolveDelay)
            _state.update { it.copy(isProgressResolved = true) }
        }
    }
}

/**
 * Hardcoded Favorites standing in for real data, so the carousel can be reviewed on a device first.
 * Covers a very long title, a Category with no icon or color, and every progress state: the flag starts
 * unresolved and resolves after [progressResolveDelay], so the arrival of progress is visible.
 */
private object FakeFavorites {

    val progressResolveDelay = 2.seconds

    private const val ANDROID_ID = "android"
    private const val KOTLIN_ID = "kotlin"

    private val androidCategory = Category(
        id = ANDROID_ID,
        name = "Android",
        order = 0,
        subcategoryCount = 14,
        iconSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M12 2 2 22h20z\"/></svg>",
        color = "#2B6AA5",
        featuredSubcategoryNames = emptyList(),
    )
    private val kotlinCategory = Category(
        id = KOTLIN_ID,
        name = "Kotlin",
        order = 1,
        subcategoryCount = 9,
        iconSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"10\"/></svg>",
        color = "#7F52FF",
        featuredSubcategoryNames = emptyList(),
    )
    private val longTitleCategory = Category(
        id = "architecture",
        name = "Distributed Systems and Cloud-Native Architecture Patterns",
        order = 2,
        subcategoryCount = 1,
        iconSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"4\"/></svg>",
        color = "#C2410C",
        featuredSubcategoryNames = emptyList(),
    )
    private val uncuratedCategory = Category(
        id = "python",
        name = "Python",
        order = 3,
        subcategoryCount = 7,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = emptyList(),
    )

    private val compose = Subcategory(
        id = "compose",
        name = "Compose",
        categoryId = ANDROID_ID,
        categoryName = "Android",
        order = 0,
        cardCount = 30,
    )
    private val longTitleSubcategory = Subcategory(
        id = "structured-concurrency",
        name = "Structured Concurrency, Cancellation and Exception Handling in Kotlin Coroutines",
        categoryId = KOTLIN_ID,
        categoryName = "Kotlin",
        order = 1,
        cardCount = 40,
    )
    private val navigation = Subcategory(
        id = "navigation",
        name = "Navigation",
        categoryId = ANDROID_ID,
        categoryName = "Android",
        order = 2,
        cardCount = 12,
    )

    val items: List<FavoriteItem> = listOf(
        FavoriteSubcategory(compose, androidCategory, Instant.parse("2026-05-05T10:00:00Z")),
        FavoriteCategory(longTitleCategory, Instant.parse("2026-05-04T10:00:00Z")),
        FavoriteSubcategory(longTitleSubcategory, kotlinCategory, Instant.parse("2026-05-03T10:00:00Z")),
        FavoriteCategory(uncuratedCategory, Instant.parse("2026-05-02T10:00:00Z")),
        FavoriteSubcategory(navigation, androidCategory, Instant.parse("2026-05-01T10:00:00Z")),
    )

    /**
     * Compose is 83% studied, the long-title Subcategory is absent (a resolved zero), and Navigation
     * counts more Studied than it has cards, so the percentage clamps to 100%.
     */
    val progressSummary = ProgressSummary(
        subcategories = mapOf(
            compose.id to SubcategoryProgressSummary(masteredCount = 10, studiedCount = 25),
            navigation.id to SubcategoryProgressSummary(masteredCount = 12, studiedCount = 14),
        ),
    )
}
