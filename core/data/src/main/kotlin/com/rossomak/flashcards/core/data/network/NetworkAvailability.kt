package com.rossomak.flashcards.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** A one-off answer to "is there a network that can reach the internet right now?". */
fun interface NetworkAvailability {
    fun isInternetAvailable(): Boolean
}

/**
 * True when the active network declares [NetworkCapabilities.NET_CAPABILITY_INTERNET]. Validation is
 * not required: a network the system has not validated yet still counts, so a slow validation never
 * reads as offline.
 */
class DefaultNetworkAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkAvailability {

    override fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
