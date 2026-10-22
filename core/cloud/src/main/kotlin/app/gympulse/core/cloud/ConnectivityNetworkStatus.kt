package app.gympulse.core.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.gympulse.core.data.cloud.NetworkStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Netz-Prüfung über den ConnectivityManager: aktives Netz mit Internet-Zugang. */
class ConnectivityNetworkStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkStatus {
    override fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
