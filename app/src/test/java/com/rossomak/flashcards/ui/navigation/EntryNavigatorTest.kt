package com.rossomak.flashcards.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class EntryNavigatorTest {

    private val navController: NavController = mockk(relaxed = true)
    private var isOnTop = true
    private val navigator = EntryNavigator(navController) { isOnTop }

    @Test
    fun `navigate reaches the controller while the entry is on top`() {
        navigator.navigate(ROUTE)

        verify(exactly = 1) { navController.navigate(ROUTE, any<NavOptionsBuilder.() -> Unit>()) }
    }

    @Test
    fun `navigate is dropped once the entry is no longer on top`() {
        isOnTop = false

        navigator.navigate(ROUTE)

        verify(exactly = 0) { navController.navigate(any<Any>(), any<NavOptionsBuilder.() -> Unit>()) }
    }

    @Test
    fun `popBackStack reaches the controller while the entry is on top`() {
        navigator.popBackStack()

        verify(exactly = 1) { navController.popBackStack() }
    }

    @Test
    fun `popBackStack is dropped once the entry is no longer on top`() {
        isOnTop = false

        navigator.popBackStack()

        verify(exactly = 0) { navController.popBackStack() }
    }

    @Test
    fun `a double tap navigates once when the first tap covers the entry`() {
        val coveringNavController: NavController = mockk(relaxed = true)
        var entryOnTop = true
        val coveringNavigator = EntryNavigator(coveringNavController) { entryOnTop }
        every { coveringNavController.navigate(any<Any>(), any<NavOptionsBuilder.() -> Unit>()) } answers {
            entryOnTop = false
        }

        coveringNavigator.navigate(ROUTE)
        coveringNavigator.navigate(ROUTE)

        verify(exactly = 1) { coveringNavController.navigate(ROUTE, any<NavOptionsBuilder.() -> Unit>()) }
    }

    @Test
    fun `navigate works again once the entry is back on top`() {
        isOnTop = false
        navigator.navigate(ROUTE)
        isOnTop = true

        navigator.navigate(ROUTE)

        verify(exactly = 1) { navController.navigate(ROUTE, any<NavOptionsBuilder.() -> Unit>()) }
    }

    @Test
    fun `navigateStacked runs both steps in order behind a single check`() {
        navigator.navigateStacked(BASE_ROUTE, {}, ROUTE)

        verifyOrder {
            navController.navigate(BASE_ROUTE, any<NavOptionsBuilder.() -> Unit>())
            navController.navigate(ROUTE)
        }
    }

    @Test
    fun `navigateStacked is dropped once the entry is no longer on top`() {
        isOnTop = false

        navigator.navigateStacked(BASE_ROUTE, {}, ROUTE)

        verify(exactly = 0) { navController.navigate(any<Any>(), any<NavOptionsBuilder.() -> Unit>()) }
        verify(exactly = 0) { navController.navigate(any<Any>()) }
    }

    // Objects, not strings: a String route would resolve to NavController's separate route-string overloads.
    private data object TestRoute

    private data object TestBaseRoute

    private companion object {
        val ROUTE: Any = TestRoute
        val BASE_ROUTE: Any = TestBaseRoute
    }
}
