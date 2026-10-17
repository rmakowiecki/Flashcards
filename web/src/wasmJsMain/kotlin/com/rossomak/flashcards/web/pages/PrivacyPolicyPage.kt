package com.rossomak.flashcards.web.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.web.content.PrivacyPolicyDocument
import com.rossomak.flashcards.web.navigation.OnNavigate

/** `/privacy`: the Privacy Policy. */
@Composable
fun PrivacyPolicyPage(modifier: Modifier = Modifier, onNavigate: OnNavigate) {
    LegalDocumentPage(modifier = modifier, document = PrivacyPolicyDocument, onNavigate = onNavigate)
}
