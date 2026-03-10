package ai.octomil.app

import android.app.Application
import android.os.Build
import ai.octomil.client.OctomilClient
import ai.octomil.config.OctomilConfig
import ai.octomil.discovery.DiscoveryManager
import ai.octomil.app.services.LocalPairingServer
import java.util.UUID

class OctomilApplication : Application() {

    lateinit var client: OctomilClient
        private set

    var localServer: LocalPairingServer? = null
        private set

    private var discoveryManager: DiscoveryManager? = null

    /** Callback invoked when a pairing code arrives via the local HTTP server. */
    var onPairingCodeReceived: ((code: String, host: String?, modelName: String?) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        val prefs = getSharedPreferences("octomil", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val orgId = prefs.getString("org_id", "") ?: ""
        val serverUrl = prefs.getString("server_url", "https://api.octomil.com/api/v1") ?: "https://api.octomil.com/api/v1"

        client = OctomilClient.Builder(this)
            .config(
                OctomilConfig.Builder()
                    .deviceAccessToken(apiKey)
                    .orgId(orgId)
                    .serverUrl(serverUrl)
                    .build()
            )
            .build()

        startLocalServer()
    }

    fun saveCredentials(apiKey: String, orgId: String, serverUrl: String? = null) {
        val prefs = getSharedPreferences("octomil", MODE_PRIVATE)
        prefs.edit()
            .putString("api_key", apiKey)
            .putString("org_id", orgId)
            .apply()

        if (serverUrl != null) {
            prefs.edit().putString("server_url", serverUrl).apply()
        }

        val url = serverUrl ?: prefs.getString("server_url", "https://api.octomil.com/api/v1")!!
        client = OctomilClient.Builder(this)
            .config(
                OctomilConfig.Builder()
                    .deviceAccessToken(apiKey)
                    .orgId(orgId)
                    .serverUrl(url)
                    .build()
            )
            .build()
    }

    private fun startLocalServer() {
        val server = LocalPairingServer { code, host, modelName ->
            onPairingCodeReceived?.invoke(code, host, modelName)
        }
        server.start()
        localServer = server

        // Advertise via mDNS using the SDK's DiscoveryManager
        discoveryManager = DiscoveryManager(this).also {
            it.startDiscoverable(
                deviceId = loadDeviceId(),
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }

    private fun loadDeviceId(): String {
        val prefs = getSharedPreferences("octomil", MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    override fun onTerminate() {
        super.onTerminate()
        localServer?.stop()
        discoveryManager?.stopDiscoverable()
    }

    companion object {
        lateinit var instance: OctomilApplication
            private set
    }
}
