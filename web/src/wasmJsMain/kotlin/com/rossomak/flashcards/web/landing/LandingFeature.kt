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

/** A feature's light or dark screenshot. */
enum class ScreenshotVariant {
    Light,
    Dark,
    ;

    companion object {
        fun forColorScheme(prefersDark: Boolean): ScreenshotVariant = if (prefersDark) Dark else Light
    }
}

/** One feature page, in page order. */
enum class LandingFeature(
    val headline: String,
    val body: String,
    private val lightScreenshot: DrawableResource,
    private val darkScreenshot: DrawableResource,
) {
    Home(
        headline = "Track your progress at a glance",
        body = "Favorite your topics and jump back in from Home in one tap. Hit your daily goal, keep your streak going, and watch the " +
            "progress rings fill.",
        lightScreenshot = Res.drawable.screenshot_01_home_light,
        darkScreenshot = Res.drawable.screenshot_01_home_dark,
    ),
    CategorySearch(
        headline = "Find the topics that interest you",
        body = "Search every category and topic as you type, from Android and Kotlin to Swift, Python and Machine Learning, and find what " +
            "to study in seconds.",
        lightScreenshot = Res.drawable.screenshot_02_category_search_light,
        darkScreenshot = Res.drawable.screenshot_02_category_search_dark,
    ),
    CategorySelectionMode(
        headline = "Study exactly what you want",
        body = "Pick topics from a category and mix them into one custom session built around today: a quick refresh before an " +
            "interview or a deep pass over one area.",
        lightScreenshot = Res.drawable.screenshot_03_category_selection_mode_light,
        darkScreenshot = Res.drawable.screenshot_03_category_selection_mode_dark,
    ),
    SubcategoryDetails(
        headline = "Sort and filter cards in any topic",
        body = "Sort cards by difficulty and filter by tags to zero in on the cards you're weak on. Every card moves from new to " +
            "mastered, and answers show real code, not just definitions.",
        lightScreenshot = Res.drawable.screenshot_04_subcategory_details_light,
        darkScreenshot = Res.drawable.screenshot_04_subcategory_details_dark,
    ),
    PreviewStudySession(
        headline = "See your session before you start",
        body = "Check your session before you commit: pick Fast or Rated mode, card count, difficulty, tags and order, then start " +
            "when it looks right.",
        lightScreenshot = Res.drawable.screenshot_05_preview_study_session_light,
        darkScreenshot = Res.drawable.screenshot_05_preview_study_session_dark,
    ),
    RatedVoiceGraded(
        headline = "Test yourself in Rated mode",
        body = "Rate yourself, or say answers out loud and let AI grade them. Weaker cards come back until you nail them. Works " +
            "hands-free over Bluetooth and in the background, with your voice disguised on-device.",
        lightScreenshot = Res.drawable.screenshot_06_rated_voice_graded_light,
        darkScreenshot = Res.drawable.screenshot_06_rated_voice_graded_dark,
    ),
    FastReadAloud(
        headline = "Listen and learn in Fast mode",
        body = "Skip the grading: tap through cards, or let them play aloud hands-free. Playback keeps going with the screen off, " +
            "and lock-screen and headset controls keep you in charge.",
        lightScreenshot = Res.drawable.screenshot_07_fast_read_aloud_light,
        darkScreenshot = Res.drawable.screenshot_07_fast_read_aloud_dark,
    ),
    SessionComplete(
        headline = "Earn XP and level up as you learn",
        body = "Earn XP for new cards, mastered cards and finished sessions, and climb toward the next level. Progress syncs when " +
            "you're back online and follows you across devices with Google or GitHub sign-in.",
        lightScreenshot = Res.drawable.screenshot_08_session_complete_light,
        darkScreenshot = Res.drawable.screenshot_08_session_complete_dark,
    ),
    ;

    /** The phone alternates sides, starting on the end side. */
    val phoneOnEnd: Boolean get() = ordinal % 2 == 0

    fun screenshot(variant: ScreenshotVariant): DrawableResource = when (variant) {
        Light -> lightScreenshot
        Dark -> darkScreenshot
    }
}
