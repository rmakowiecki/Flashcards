package com.rossomak.flashcards.web.landing

import com.rossomak.flashcards.web.landing.ScreenshotVariant.Dark
import com.rossomak.flashcards.web.landing.ScreenshotVariant.Light
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.screenshot_01_home_dark
import com.rossomak.flashcards.web.resources.screenshot_01_home_light
import com.rossomak.flashcards.web.resources.screenshot_02_category_search_dark
import com.rossomak.flashcards.web.resources.screenshot_02_category_search_light
import com.rossomak.flashcards.web.resources.screenshot_03_category_selection_mode_dark
import com.rossomak.flashcards.web.resources.screenshot_03_category_selection_mode_light
import com.rossomak.flashcards.web.resources.screenshot_04_subcategory_details_dark
import com.rossomak.flashcards.web.resources.screenshot_04_subcategory_details_light
import com.rossomak.flashcards.web.resources.screenshot_05_preview_study_session_dark
import com.rossomak.flashcards.web.resources.screenshot_05_preview_study_session_light
import com.rossomak.flashcards.web.resources.screenshot_06_rated_voice_graded_dark
import com.rossomak.flashcards.web.resources.screenshot_06_rated_voice_graded_light
import com.rossomak.flashcards.web.resources.screenshot_07_fast_read_aloud_dark
import com.rossomak.flashcards.web.resources.screenshot_07_fast_read_aloud_light
import com.rossomak.flashcards.web.resources.screenshot_08_session_complete_dark
import com.rossomak.flashcards.web.resources.screenshot_08_session_complete_light
import org.jetbrains.compose.resources.DrawableResource

/** Which of a feature's two screenshots to show: the app in light or in dark mode. */
enum class ScreenshotVariant {
    Light,
    Dark,
    ;

    companion object {
        /** Follows the browser's `prefers-color-scheme`; nothing else on the site does. */
        fun forColorScheme(prefersDark: Boolean): ScreenshotVariant = if (prefersDark) Dark else Light
    }
}

/**
 * One feature page of the landing pager, in page order: its headline, its subline, a longer detail
 * paragraph for those who stop to read, and the app screenshot that shows it.
 */
enum class LandingFeature(
    val headline: String,
    val subline: String,
    val detail: String,
    private val lightScreenshot: DrawableResource,
    private val darkScreenshot: DrawableResource,
) {
    Home(
        headline = "Track your progress at a glance",
        subline = "Keep your streak going, hit your daily goal, and quickly jump back into your favorite topics",
        detail = "Favorite the topics you're working on and jump back into them from Home in one tap. Progress rings show how much of " +
            "each topic you've covered, and your daily goal and streak keep you coming back.",
        lightScreenshot = Res.drawable.screenshot_01_home_light,
        darkScreenshot = Res.drawable.screenshot_01_home_dark,
    ),
    CategorySearch(
        headline = "Find the topics that interest you",
        subline = "Search every topic and category as you type, and find exactly what you want to study in seconds",
        detail = "Study curated cards across 7 categories: Android, Kotlin, Swift, Apple Platforms, Python, Data Science Python and Machine Learning. Each " +
            "one breaks down into focused topics like Coroutines, Jetpack Compose, SwiftUI, Pandas, NumPy or PyTorch.",
        lightScreenshot = Res.drawable.screenshot_02_category_search_light,
        darkScreenshot = Res.drawable.screenshot_02_category_search_dark,
    ),
    CategorySelectionMode(
        headline = "Study exactly what you want",
        subline = "Pick the topics you want from any category and study them together in a single custom session",
        detail = "Mix topics from one category into a session built around what you need today, whether that's a " +
            "quick refresh before an interview or a deep pass over one area.",
        lightScreenshot = Res.drawable.screenshot_03_category_selection_mode_light,
        darkScreenshot = Res.drawable.screenshot_03_category_selection_mode_dark,
    ),
    SubcategoryDetails(
        headline = "Sort and filter cards in any topic",
        subline = "Order cards by difficulty and narrow them down by tags, so you can focus on exactly what you need to learn",
        detail = "Every card moves from new to mastered. Sort by difficulty and filter by tags to find exactly the cards you're " +
            "weak on, and see real code snippets in the answers, not just definitions.",
        lightScreenshot = Res.drawable.screenshot_04_subcategory_details_light,
        darkScreenshot = Res.drawable.screenshot_04_subcategory_details_dark,
    ),
    PreviewStudySession(
        headline = "See your session before you start",
        subline = "Adjust it on the spot: pick Fast or Rated mode, card count, difficulty, tag filters and the card order",
        detail = "Check what's coming before you commit. Change the mode, the number of cards, the difficulty, the " +
            "tags and the order, then start when the session looks right.",
        lightScreenshot = Res.drawable.screenshot_05_preview_study_session_light,
        darkScreenshot = Res.drawable.screenshot_05_preview_study_session_dark,
    ),
    RatedVoiceGraded(
        headline = "Test yourself in Rated mode",
        subline = "Think it through and rate yourself, or say it out loud and let AI grade it. Works in background and with BT earphones",
        detail = "Weaker cards come back until you nail them. Voice answers work hands-free with Bluetooth earphones, even with the app in the " +
            "background or the screen off, and your voice is disguised on the device before it leaves your phone.",
        lightScreenshot = Res.drawable.screenshot_06_rated_voice_graded_light,
        darkScreenshot = Res.drawable.screenshot_06_rated_voice_graded_dark,
    ),
    FastReadAloud(
        headline = "Listen and learn in Fast mode",
        subline = "Skip the grading: tap through cards, or let them play aloud hands-free, even with the app in background",
        detail = "No ratings, no pressure. Read-aloud playback keeps going with the screen off, and lock-screen and " +
            "headset controls keep you in charge while you go about your day.",
        lightScreenshot = Res.drawable.screenshot_07_fast_read_aloud_light,
        darkScreenshot = Res.drawable.screenshot_07_fast_read_aloud_dark,
    ),
    SessionComplete(
        headline = "Earn XP and level up as you learn",
        subline = "Study new cards, master them and finish sessions to earn XP and climb toward the next level",
        detail = "Earn XP for new cards, mastered cards and finished sessions. Your progress syncs when you're back online " +
            "and follows you across devices when you sign in with Google or GitHub.",
        lightScreenshot = Res.drawable.screenshot_08_session_complete_light,
        darkScreenshot = Res.drawable.screenshot_08_session_complete_dark,
    ),
    ;

    /** Features alternate sides: the first shows its phone on the end side, the next on the start side, and so on. */
    val phoneOnEnd: Boolean get() = ordinal % 2 == 0

    fun screenshot(variant: ScreenshotVariant): DrawableResource = when (variant) {
        Light -> lightScreenshot
        Dark -> darkScreenshot
    }
}
