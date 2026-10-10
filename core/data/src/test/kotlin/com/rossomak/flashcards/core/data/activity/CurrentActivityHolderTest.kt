package com.rossomak.flashcards.core.data.activity

import android.app.Activity
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test

class CurrentActivityHolderTest {

    private val holder = CurrentActivityHolder()
    private val activity: Activity = mockk()
    private val otherActivity: Activity = mockk()

    @Test
    fun `a resumed activity is returned`() {
        holder.onActivityResumed(activity)

        holder.currentActivity shouldBe activity
    }

    @Test
    fun `pausing the resumed activity clears it`() {
        holder.onActivityResumed(activity)

        holder.onActivityPaused(activity)

        holder.currentActivity shouldBe null
    }

    @Test
    fun `pausing a different activity does not clear the resumed one`() {
        holder.onActivityResumed(activity)

        holder.onActivityPaused(otherActivity)

        holder.currentActivity shouldBe activity
    }

    @Test
    fun `a later resume replaces the activity`() {
        holder.onActivityResumed(activity)

        holder.onActivityResumed(otherActivity)

        holder.currentActivity shouldBe otherActivity
    }
}
