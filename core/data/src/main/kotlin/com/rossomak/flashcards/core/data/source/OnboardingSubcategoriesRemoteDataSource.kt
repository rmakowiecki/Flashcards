package com.rossomak.flashcards.core.data.source

import com.google.firebase.firestore.FirebaseFirestore
import com.rossomak.flashcards.core.data.model.OnboardingSubcategoriesDocument
import com.rossomak.flashcards.core.data.model.OnboardingSubcategoryDto
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Reads the single, publicly-readable `onboarding/subcategories` document — a small, admin-picked
 * subset of `subcategories` (`scripts/seed/curated_onboarding_topics.py` is the allowlist source
 * of truth), readable before any auth exists (`firestore.rules`: `allow read: if true`). Every
 * curated entry is denormalized (its parent Category's `iconSvg` joined in) and trimmed down to
 * exactly what onboarding needs, and packed into one document's `subcategories` map so the whole
 * curated list costs a single Firestore read.
 */
class OnboardingSubcategoriesRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    suspend fun getOnboardingSubcategories(): List<OnboardingSubcategoryDto> {
        val document = firestore.collection(COLLECTION_ONBOARDING)
            .document(DOCUMENT_SUBCATEGORIES)
            .get()
            .await()
            .toObject(OnboardingSubcategoriesDocument::class.java)
            // A missing singleton doc is a backend misconfiguration, not an empty curated list —
            // throwing here surfaces the loader's retry state instead of a false "loaded, nothing to show".
            ?: error("Missing required onboarding/subcategories document")
        return document.subcategories.values.sortedBy { it.order }
    }

    private companion object {
        const val COLLECTION_ONBOARDING = "onboarding"
        const val DOCUMENT_SUBCATEGORIES = "subcategories"
    }
}
