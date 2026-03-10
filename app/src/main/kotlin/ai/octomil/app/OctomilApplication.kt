package ai.octomil.app

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import ai.octomil.client.OctomilClient
import ai.octomil.config.OctomilConfig
import ai.octomil.app.services.LocalPairingServer
import java.util.UUID

class OctomilApplication : Application() {

    lateinit var client: OctomilClient
        private set

    var localServer: LocalPairingServer? = null
        private set

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

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

        // Advertise via mDNS once server is ready
        val port = server.port
        if (port > 0) {
            advertiseMdns(port)
        }
    }

    private fun advertiseMdns(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            serviceType = "_octomil._tcp"
            setPort(port)
            setAttribute("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
            setAttribute("platform", "android")
            setAttribute("device_id", loadDeviceId())
        }

        nsdManager = (getSystemService(Context.NSD_SERVICE) as NsdManager).also { mgr ->
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    android.util.Log.d("Octomil", "mDNS registered: ${info.serviceName} on port $port")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    android.util.Log.e("Octomil", "mDNS registration failed: $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = listener
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
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
        registrationListener?.let { listener ->
            nsdManager?.unregisterService(listener)
        }
    }

    companion object {
        lateinit var instance: OctomilApplication
            private set
    }
}
