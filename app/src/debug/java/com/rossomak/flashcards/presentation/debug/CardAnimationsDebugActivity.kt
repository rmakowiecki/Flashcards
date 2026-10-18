package com.rossomak.flashcards.presentation.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/** Hosts the card animation harness in its own window, so it looks exactly as a session does in the app. */
class CardAnimationsDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashcardsTheme(dynamicColor = false) {
                CardAnimationsDebugScreen(onNavigateBack = ::finish)
            }
        }
    }
}
