package com.rossomak.flashcards.feature.account

import android.content.Intent
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheetState
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.flashcardsListScrollFade
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListRow
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupContainer
import com.rossomak.flashcards.core.ui.composables.lists.flashcardsListGroupItems
import com.rossomak.flashcards.core.ui.composables.rememberFlashcardsBottomSheetState
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.account.OpenSourceLicensesMessage.OpenLinkFailed
import kotlinx.coroutines.launch

/**
 * Every library the release build ships, read from the list the build generated into
 * [librariesResId]. The id lives in the app module, so the nav graph passes it in.
 */
@Composable
fun OpenSourceLicensesScreen(
    modifier: Modifier = Modifier,
    viewModel: OpenSourceLicensesViewModel = hiltViewModel(),
    @RawRes librariesResId: Int,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraries = produceLibraries(librariesResId).value?.libraries
    val selectedLibrary = libraries?.firstOrNull { library -> library.uniqueId == state.selectedLibraryId }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val context = LocalContext.current
    val openLinkFailedMessage = stringResource(R.string.account_open_link_failed_message)

    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            OpenLinkFailed -> openLinkFailedMessage
        }
        snackbarScope.launch {
            snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        }
    }

    OpenSourceLicensesContent(
        modifier = modifier,
        libraries = libraries,
        selectedLibrary = selectedLibrary,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onLibraryClick = { library -> viewModel.onLibraryClick(library.uniqueId) },
        onDetailDismiss = viewModel::onDetailDismiss,
        onLinkClick = { url ->
            if (!context.tryStartActivity(Intent(Intent.ACTION_VIEW, url.toUri()))) viewModel.onOpenLinkFailed()
        },
    )
}

/**
 * [libraries] is `null` while the list is still being read. A non-null [selectedLibrary] opens its
 * detail sheet.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSourceLicensesContent(
    modifier: Modifier = Modifier,
    libraries: List<Library>?,
    selectedLibrary: Library?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateBack: () -> Unit,
    onLibraryClick: (Library) -> Unit,
    onDetailDismiss: () -> Unit,
    onLinkClick: (url: String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    // The sheet is a plain, unaligned sibling of Scaffold: see FlashcardsBottomSheet for why.
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(text = stringResource(R.string.open_source_licenses_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(CoreUiR.string.common_navigate_back_cd),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (libraries == null) {
                    CircularProgressIndicator()
                } else {
                    LibraryList(
                        modifier = Modifier.align(Alignment.TopCenter),
                        listState = listState,
                        libraries = libraries,
                        onLibraryClick = onLibraryClick,
                    )
                }
            }
        }
        LibraryDetailSheetHost(
            selectedLibrary = selectedLibrary,
            onDismiss = onDetailDismiss,
            onLinkClick = onLinkClick,
        )
    }
}

/**
 * Opens the detail sheet for [selectedLibrary] and slides it out when that becomes `null`. The
 * sheet's content stays on the last library while it slides out, rather than blanking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryDetailSheetHost(
    selectedLibrary: Library?,
    onDismiss: () -> Unit,
    onLinkClick: (url: String) -> Unit,
) {
    var shownLibrary by remember { mutableStateOf(selectedLibrary) }
    if (selectedLibrary != null) shownLibrary = selectedLibrary
    val sheetState = rememberFlashcardsBottomSheetState(initiallyExpanded = selectedLibrary != null)
    LaunchedEffect(selectedLibrary != null) {
        if (selectedLibrary != null) sheetState.sheetState.show() else sheetState.sheetState.hide()
    }
    shownLibrary?.let { library ->
        LibraryDetailSheet(
            library = library,
            sheetState = sheetState,
            onDismissRequest = onDismiss,
            onLinkClick = onLinkClick,
        )
    }
}

private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

@Composable
private fun LibraryList(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    libraries: List<Library>,
    onLibraryClick: (Library) -> Unit,
) {
    val separator = stringResource(CoreUiR.string.common_middle_dot_separator)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(all = MaterialTheme.spacing.normal)
            .flashcardsListGroupContainer(listState)
            .flashcardsListScrollFade(listState),
    ) {
        flashcardsListGroupItems(
            items = libraries,
            key = { library -> library.uniqueId },
        ) { library, rowModifier ->
            FlashcardsListRow(
                modifier = rowModifier,
                title = library.name,
                secondaryText = library.summary(separator),
                onClick = { onLibraryClick(library) },
                trailing = { FlashcardsChevron() },
            )
        }
    }
}

/** `"3.6.3 · Apache License 2.0"`, dropping whichever part the library does not declare. */
private fun Library.summary(separator: String): String =
    listOfNotNull(
        artifactVersion,
        licenses.joinToString(separator = ", ") { license -> license.name }.takeIf { it.isNotEmpty() },
    ).joinToString(separator = separator)

/**
 * A library's detail: what it is, then each of its licenses. A license the build bundled the text
 * for is shown in full; one it did not (the Google and Firebase terms) offers its web page instead.
 */
@Composable
private fun LibraryDetailSheet(
    library: Library,
    sheetState: FlashcardsBottomSheetState,
    onDismissRequest: () -> Unit,
    onLinkClick: (url: String) -> Unit,
) {
    FlashcardsBottomSheet(
        state = sheetState,
        onDismissRequest = onDismissRequest,
    ) {
        LibraryDetail(library = library, onLinkClick = onLinkClick)
    }
}

@Composable
private fun LibraryDetail(
    library: Library,
    onLinkClick: (url: String) -> Unit,
) {
    val separator = stringResource(CoreUiR.string.common_middle_dot_separator)
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
        Text(text = library.name, style = MaterialTheme.typography.titleLarge)
        library.summary(separator).takeIf { it.isNotEmpty() }?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        library.description.nonBlank()?.let { description ->
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        library.licenses.forEach { license -> LicenseSection(license = license, onLinkClick = onLinkClick) }
        LibraryLinks(library = library, onLinkClick = onLinkClick)
    }
}

@Composable
private fun LicenseSection(
    license: License,
    onLinkClick: (url: String) -> Unit,
) {
    val licenseText = license.licenseContent.nonBlank()
    val licenseUrl = license.url.nonBlank()
    Text(
        text = license.name,
        modifier = Modifier.padding(top = MaterialTheme.spacing.xsmall),
        style = MaterialTheme.typography.titleSmall,
    )
    if (licenseText != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaterialTheme.cornerRadius.medium),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = licenseText,
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else if (licenseUrl != null) {
        FlashcardsTextButton(
            text = stringResource(R.string.open_source_licenses_license_button),
            onClick = { onLinkClick(licenseUrl) },
        )
    }
}

@Composable
private fun LibraryLinks(
    library: Library,
    onLinkClick: (url: String) -> Unit,
) {
    val website = library.website.nonBlank()
    val source = library.scm?.url.nonBlank()
    if (website == null && source == null) return
    Column {
        website?.let { url ->
            FlashcardsTextButton(
                text = stringResource(R.string.open_source_licenses_website_button),
                onClick = { onLinkClick(url) },
            )
        }
        source?.let { url ->
            FlashcardsTextButton(
                text = stringResource(R.string.open_source_licenses_source_button),
                onClick = { onLinkClick(url) },
            )
        }
    }
}

private val previewApacheLicense = License(
    name = "Apache License 2.0",
    url = "https://spdx.org/licenses/Apache-2.0.html",
    licenseContent = "Apache License\nVersion 2.0, January 2004\nhttp://www.apache.org/licenses/",
    hash = "Apache-2.0",
)

private val previewSdkLicense = License(
    name = "Android Software Development Kit License",
    url = "https://developer.android.com/studio/terms.html",
    hash = "ASDKL",
)

private val previewLibraries = listOf(
    previewLibrary(name = "Coil", uniqueId = "io.coil-kt.coil3:coil", version = "3.6.3", license = previewApacheLicense),
    previewLibrary(name = "Hilt Android", uniqueId = "com.google.dagger:hilt-android", version = "2.60.1", license = previewApacheLicense),
    previewLibrary(name = "firebase-auth", uniqueId = "com.google.firebase:firebase-auth", version = "24.2.0", license = previewSdkLicense),
)

private fun previewLibrary(name: String, uniqueId: String, version: String, license: License) = Library(
    uniqueId = uniqueId,
    artifactVersion = version,
    name = name,
    description = "Image loading for Android and Compose Multiplatform.",
    website = "https://coil-kt.github.io/coil/",
    developers = emptyList(),
    organization = null,
    scm = null,
    licenses = setOf(license),
)

@PreviewLightDark
@Composable
private fun OpenSourceLicensesContentPreview() {
    FlashcardsTheme {
        OpenSourceLicensesContent(
            libraries = previewLibraries,
            selectedLibrary = null,
            onNavigateBack = {},
            onLibraryClick = {},
            onDetailDismiss = {},
            onLinkClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun OpenSourceLicensesDetailPreview() {
    FlashcardsTheme {
        OpenSourceLicensesContent(
            libraries = previewLibraries,
            selectedLibrary = previewLibraries.first(),
            onNavigateBack = {},
            onLibraryClick = {},
            onDetailDismiss = {},
            onLinkClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun OpenSourceLicensesLoadingPreview() {
    FlashcardsTheme {
        OpenSourceLicensesContent(
            libraries = null,
            selectedLibrary = null,
            onNavigateBack = {},
            onLibraryClick = {},
            onDetailDismiss = {},
            onLinkClick = {},
        )
    }
}
