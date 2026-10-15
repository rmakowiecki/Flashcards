package com.rossomak.flashcards.core.ui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.rossomak.flashcards.core.domain.model.StudyMode

/** The one icon every screen uses for a Study Mode. */
val StudyMode.icon: ImageVector
    get() = when (this) {
        StudyMode.Rated -> Icons.Default.Star
        StudyMode.Fast -> Icons.Default.Bolt
    }
