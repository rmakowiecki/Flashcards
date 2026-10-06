package com.rossomak.flashcards.feature.account

/**
 * [selectedLibraryId] is the id rather than the library: it survives process death, the library
 * does not. A non-null id opens that library's detail sheet.
 */
data class OpenSourceLicensesScreenState(val selectedLibraryId: String? = null)
