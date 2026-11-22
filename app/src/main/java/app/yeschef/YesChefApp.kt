package app.yeschef

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.yeschef.data.sync.SyncScheduler
import coil.ImageLoader
import coil.ImageLoaderFactory

class YesChefApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.schedulePeriodic(this)

        // Sync whenever the app comes to the foreground...
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = SyncScheduler.requestSync(this@YesChefApp)
        })

        // ...and whenever a VPN (WireGuard) comes up, since that is when the server becomes reachable.
        val vpnRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
            vpnRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = SyncScheduler.requestSync(this@YesChefApp)
            },
        )
    }

    // Recipe images go through the same client, so the Start9 CA and token apply.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(container.httpClient)
            .crossfade(true)
            .build()
}
