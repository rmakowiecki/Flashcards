package com.rossomak.flashcards.ui.permission

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import com.rossomak.flashcards.core.data.permission.toManifestPermission
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.repository.PermissionRepository
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents

/**
 * The app's single runtime-permission launcher: launches the system prompt for every request the
 * [permissionRepository] emits and reports the outcome back to it. The rationale flag is read right
 * after the result, when it tells a soft denial apart from one the system will no longer prompt for.
 * The pending permission is saveable so a rotation mid-prompt still reports against it.
 */
@Composable
fun PermissionLauncherHost(permissionRepository: PermissionRepository) {
    val activity = LocalActivity.current
    var pendingPermission by rememberSaveable { mutableStateOf<AppPermission?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val permission = pendingPermission ?: return@rememberLauncherForActivityResult
        pendingPermission = null
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, permission.toManifestPermission())
        } ?: false
        permissionRepository.onPermissionResult(
            permission = permission,
            isGranted = isGranted,
            shouldShowRationale = shouldShowRationale,
        )
    }
    observeAsEvents(permissionRepository.permissionRequests) { permission ->
        pendingPermission = permission
        launcher.launch(permission.toManifestPermission())
    }
}
