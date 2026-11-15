package com.rossomak.flashcards.presentation.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.window.lockPortraitOnPhones
import com.rossomak.flashcards.presentation.main.DebugThemeOverrides

/** Hosts the card animation harness in its own window, so it looks exactly as a session does in the app. */
class CardAnimationsDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockPortraitOnPhones()
        enableEdgeToEdge()
        setContent {
            FlashcardsTheme {
                DebugThemeOverrides {
                    CardAnimationsDebugScreen(onNavigateBack = ::finish)
                }
            }
        }
    }
}
