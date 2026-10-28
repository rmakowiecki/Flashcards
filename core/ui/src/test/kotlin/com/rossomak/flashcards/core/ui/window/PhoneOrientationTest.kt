package com.rossomak.flashcards.core.ui.window

import android.content.pm.ActivityInfo
import io.kotest.matchers.shouldBe
import org.junit.Test

class PhoneOrientationTest {

    @Test
    fun `a phone is locked to portrait`() {
        orientationForSmallestWidth(PHONE_SMALLEST_WIDTH_DP) shouldBe ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    @Test
    fun `a screen just below the large-screen width is locked to portrait`() {
        orientationForSmallestWidth(LARGE_SCREEN_SMALLEST_WIDTH_DP - 1) shouldBe ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    @Test
    fun `a screen at the large-screen width follows the user's rotation`() {
        orientationForSmallestWidth(LARGE_SCREEN_SMALLEST_WIDTH_DP) shouldBe ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    @Test
    fun `a tablet follows the user's rotation`() {
        orientationForSmallestWidth(TABLET_SMALLEST_WIDTH_DP) shouldBe ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    @Test
    fun `an undefined smallest width counts as a phone`() {
        orientationForSmallestWidth(UNDEFINED_SMALLEST_WIDTH_DP) shouldBe ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private companion object {
        const val PHONE_SMALLEST_WIDTH_DP = 411
        const val TABLET_SMALLEST_WIDTH_DP = 800
        const val UNDEFINED_SMALLEST_WIDTH_DP = 0
    }
}
