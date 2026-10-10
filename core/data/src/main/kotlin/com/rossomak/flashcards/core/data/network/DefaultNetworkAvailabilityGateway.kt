package com.rossomak.flashcards.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * True when the active network declares [NetworkCapabilities.NET_CAPABILITY_INTERNET]. Validation is
 * not required: a network the system has not validated yet still counts, so a slow validation never
 * reads as offline.
 */
class DefaultNetworkAvailabilityGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkAvailabilityGateway {

    override fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
