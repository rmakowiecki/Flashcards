package com.rossomak.flashcards.feature.account

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.AuthProvider
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroup
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroupItem
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.account.AccountDestination.ContactSupport
import com.rossomak.flashcards.feature.account.AccountDestination.Login
import com.rossomak.flashcards.feature.account.AccountDestination.OpenSourceLicenses
import com.rossomak.flashcards.feature.account.AccountDialog.SignOut
import com.rossomak.flashcards.feature.account.AccountMessage.NoEmailApp
import com.rossomak.flashcards.feature.account.AccountMessage.OpenLinkFailed
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.account_app_version_label)

    val context = LocalContext.current

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            Login -> onNavigateToLogin()
            OpenSourceLicenses -> onNavigateToOpenSourceLicenses()
            is ContactSupport -> {
                val emailIntent = supportEmailIntent(context, destination.installationInfo, destination.uid)
                if (!context.tryStartActivity(emailIntent)) viewModel.onNoEmailAppFound()
            }
        }
    }

    val openLinkFailedMessage = stringResource(R.string.account_open_link_failed_message)
    val noEmailAppMessage = stringResource(R.string.account_no_email_app_message)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            OpenLinkFailed -> openLinkFailedMessage
            NoEmailApp -> noEmailAppMessage
        }
        snackbarScope.launch {
            snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val darkTheme = isSystemInDarkTheme()

    AccountContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onDialogEvent = viewModel::onDialogEvent,
        onManageAccountClick = {
            // The Manage link only shows for a known provider.
            state.provider?.let { provider ->
                val manageIntent = Intent(Intent.ACTION_VIEW, manageAccountUrl(provider, state.email).toUri())
                if (!context.tryStartActivity(manageIntent)) viewModel.onOpenLinkFailed()
            }
        },
        onContactSupportClick = viewModel::onContactSupportClick,
        onReportBugClick = {},
        onPrivacyPolicyClick = {
            if (!context.tryStartActivity(privacyPolicyIntent(surfaceColor, darkTheme))) viewModel.onOpenLinkFailed()
        },
        onOpenSourceLicensesClick = viewModel::onOpenSourceLicensesClick,
        onAppVersionCopy = { versionLabel ->
            clipboardScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, versionLabel))) }
        },
        onDeleteAccountClick = {},
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountContent(
    modifier: Modifier = Modifier,
    state: AccountScreenState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateBack: () -> Unit,
    onDialogEvent: (AccountDialogEvent) -> Unit,
    onManageAccountClick: () -> Unit,
    onContactSupportClick: () -> Unit,
    onReportBugClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onAppVersionCopy: (versionLabel: String) -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val appVersionLabel = state.appVersion?.let { appVersion ->
        stringResource(R.string.account_app_version_value_label, appVersion.name, appVersion.code)
    }

    AccountDialogHost(
        activeDialog = state.activeDialog,
        onDialogEvent = onDialogEvent,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AccountHeaderBar(
                state = state,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onSignOutClick = { onDialogEvent(Open(SignOut)) },
                onManageAccountClick = onManageAccountClick,
            )
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .flashcardsScrollFade(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = MaterialTheme.spacing.small),
        ) {
            FlashcardsOverlineLabel(text = stringResource(R.string.account_support_label))
            FlashcardsListGroup(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
                items = supportRows(
                    onContactSupportClick = onContactSupportClick,
                    onReportBugClick = onReportBugClick,
                ),
            )

            FlashcardsOverlineLabel(text = stringResource(R.string.account_about_label))
            FlashcardsListGroup(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
                items = aboutRows(
                    appVersionLabel = appVersionLabel,
                    onPrivacyPolicyClick = onPrivacyPolicyClick,
                    onOpenSourceLicensesClick = onOpenSourceLicensesClick,
                    onAppVersionCopy = onAppVersionCopy,
                ),
            )

            FlashcardsOverlineLabel(text = stringResource(R.string.account_danger_zone_label))
            FlashcardsListGroup(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.normal),
                items = dangerZoneRows(onDeleteAccountClick = onDeleteAccountClick),
            )
        }
    }
}

@Composable
private fun supportRows(
    onContactSupportClick: () -> Unit,
    onReportBugClick: () -> Unit,
): List<FlashcardsListGroupItem> = listOf(
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.account_contact_support_label),
        onClick = onContactSupportClick,
        leading = { FlashcardsIconTile(icon = Icons.Default.Email, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.account_report_bug_label),
        onClick = onReportBugClick,
        leading = { FlashcardsIconTile(icon = Icons.Default.BugReport, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
)

@Composable
private fun aboutRows(
    appVersionLabel: String?,
    onPrivacyPolicyClick: () -> Unit,
    onOpenSourceLicensesClick: () -> Unit,
    onAppVersionCopy: (versionLabel: String) -> Unit,
): List<FlashcardsListGroupItem> = listOf(
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.account_privacy_policy_label),
        onClick = onPrivacyPolicyClick,
        leading = { FlashcardsIconTile(icon = Icons.Default.PrivacyTip, contentDescription = null) },
        trailing = { ExternalLinkIcon() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.account_open_source_licenses_label),
        onClick = onOpenSourceLicensesClick,
        leading = { FlashcardsIconTile(icon = Icons.Default.Code, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.account_app_version_label),
        onClick = { appVersionLabel?.let(onAppVersionCopy) },
        secondaryText = appVersionLabel,
        leading = { FlashcardsIconTile(icon = Icons.Default.Info, contentDescription = null) },
        trailing = appVersionLabel?.let { { CopyIcon() } },
    ),
)

@Composable
private fun dangerZoneRows(onDeleteAccountClick: () -> Unit): List<FlashcardsListGroupItem> = listOf(
    FlashcardsListGroupItem.Custom(
        onClick = onDeleteAccountClick,
        content = {
            FlashcardsIconTile(
                icon = Icons.Default.DeleteForever,
                contentDescription = null,
                contentColor = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.account_delete_account_button),
                modifier = Modifier.padding(start = MaterialTheme.spacing.xxsmall),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ),
)

/**
 * Not decorative: the row is one merged node, so this description is what tells a screen reader that
 * activating the row copies the version.
 */
@Composable
private fun CopyIcon() {
    Icon(
        imageVector = Icons.Default.ContentCopy,
        contentDescription = stringResource(R.string.account_copy_version_cd),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Decorative: the row is the labeled, clickable node, and the system browser opens from it. */
@Composable
private fun ExternalLinkIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val PRIVACY_POLICY_URL = "https://flashcards-8ad6d.web.app/privacy"

/**
 * The launch intent for the hosted policy in a Custom Tab. The tab always follows the app's own
 * light or dark setting, with [surfaceColor] on its toolbar and navigation bar.
 */
private fun privacyPolicyIntent(surfaceColor: Int, darkTheme: Boolean): Intent {
    val colorSchemeParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(surfaceColor)
        .setNavigationBarColor(surfaceColor)
        .build()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorSchemeParams)
        .setColorScheme(if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        .build()
    return customTabsIntent.intent.apply { data = PRIVACY_POLICY_URL.toUri() }
}

private const val SUPPORT_EMAIL_ADDRESS = "flashcardsdev@gmail.com"

/** An email draft to support; the address goes in an extra so the URI needs no encoding. */
private fun supportEmailIntent(context: Context, installationInfo: InstallationInfo, uid: String?): Intent =
    Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL_ADDRESS))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.account_support_email_subject_label))
        putExtra(Intent.EXTRA_TEXT, supportEmailBody(context, installationInfo, uid))
    }

/**
 * The support details first, then two empty lines. Email apps put the caret at the end of the body, so
 * the User starts typing below the details; the account line is omitted when signed out.
 */
internal fun supportEmailBody(context: Context, installationInfo: InstallationInfo, uid: String?): String {
    val (appVersion, deviceInfo) = installationInfo
    return listOfNotNull(
        context.getString(R.string.account_support_email_details_label),
        context.getString(R.string.account_support_email_app_version_label, appVersion.name, appVersion.code),
        context.getString(R.string.account_support_email_device_label, deviceInfo.model),
        context.getString(R.string.account_support_email_android_version_label, deviceInfo.systemVersion),
        uid?.let { context.getString(R.string.account_support_email_account_id_label, it) },
        "",
        "",
    ).joinToString(separator = "\n")
}

@PreviewLightDark
@Composable
private fun AccountContentPreview() {
    FlashcardsTheme {
        AccountContent(
            state = AccountScreenState(
                displayName = "Ross Smith",
                email = "ross.smith@example.com",
                provider = AuthProvider.Google,
                appVersion = AppVersion(name = "1.4.0", code = 142L),
            ),
            onNavigateBack = {},
            onDialogEvent = {},
            onManageAccountClick = {},
            onContactSupportClick = {},
            onReportBugClick = {},
            onPrivacyPolicyClick = {},
            onOpenSourceLicensesClick = {},
            onAppVersionCopy = {},
            onDeleteAccountClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AccountContentEmptyPreview() {
    FlashcardsTheme {
        AccountContent(
            state = AccountScreenState(),
            onNavigateBack = {},
            onDialogEvent = {},
            onManageAccountClick = {},
            onContactSupportClick = {},
            onReportBugClick = {},
            onPrivacyPolicyClick = {},
            onOpenSourceLicensesClick = {},
            onAppVersionCopy = {},
            onDeleteAccountClick = {},
        )
    }
}
