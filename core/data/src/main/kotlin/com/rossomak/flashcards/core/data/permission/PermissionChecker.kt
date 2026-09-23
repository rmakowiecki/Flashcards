package com.rossomak.flashcards.core.data.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.rossomak.flashcards.core.domain.model.AppPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Seam over the OS grant check, so the gateway's status logic is testable without Android. */
fun interface PermissionChecker {
    fun isGranted(permission: AppPermission): Boolean
}

class DefaultPermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PermissionChecker {

    override fun isGranted(permission: AppPermission): Boolean =
        ContextCompat.checkSelfPermission(context, permission.toManifestPermission()) ==
            PackageManager.PERMISSION_GRANTED
}
