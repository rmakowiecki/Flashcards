package com.rossomak.flashcards.web.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.web.content.DeleteAccountDocument
import com.rossomak.flashcards.web.navigation.OnNavigate

/** `/delete-account`: how to ask for Account Deletion without the app. */
@Composable
fun DeleteAccountPage(modifier: Modifier = Modifier, onNavigate: OnNavigate) {
    LegalDocumentPage(modifier = modifier, document = DeleteAccountDocument, onNavigate = onNavigate)
}
