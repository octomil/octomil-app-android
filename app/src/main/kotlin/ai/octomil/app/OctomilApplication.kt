package ai.octomil.app

import android.app.Application
import android.os.Build
import ai.octomil.Octomil
import ai.octomil.client.OctomilClient
import android.util.Log
import ai.octomil.config.OctomilConfig
import ai.octomil.generated.DeliveryMode
import ai.octomil.generated.ModelCapability
import ai.octomil.discovery.DiscoveryManager
import ai.octomil.manifest.AppManifest
import ai.octomil.manifest.AppModelEntry
import ai.octomil.app.models.PairedModel
import ai.octomil.app.services.LocalPairingServer
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class OctomilApplication : Application() {

    var client: OctomilClient? = null
        private set

    var localServer: LocalPairingServer? = null
        private set

    private var discoveryManager: DiscoveryManager? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val pairedModels = mutableStateListOf<PairedModel>()

    private fun loadPairedModels() {
        val prefs = getSharedPreferences("octomil", MODE_PRIVATE)
        val json = prefs.getString("paired_models", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val caps = mutableListOf<ModelCapability>()
                obj.optJSONArray("capabilities")?.let { capsArr ->
                    for (j in 0 until capsArr.length()) {
                        ModelCapability.fromCode(capsArr.getString(j))?.let { caps.add(it) }
                    }
                }
                pairedModels.add(PairedModel(
                    name = obj.getString("name"),
                    version = obj.getString("version"),
                    sizeBytes = obj.getLong("sizeBytes"),
                    sizeString = obj.getString("sizeString"),
                    runtime = obj.getString("runtime"),
                    modality = obj.optString("modality", null),
                    capabilities = caps,
                ))
            }
        } catch (_: Exception) { }
    }

    private fun savePairedModels() {
        val arr = JSONArray()
        for (m in pairedModels) {
            arr.put(JSONObject().apply {
                put("name", m.name)
                put("version", m.version)
                put("sizeBytes", m.sizeBytes)
                put("sizeString", m.sizeString)
                put("runtime", m.runtime)
                if (m.modality != null) put("modality", m.modality)
                if (m.capabilities.isNotEmpty()) {
                    put("capabilities", JSONArray(m.capabilities.map { it.code }))
                }
            })
        }
        getSharedPreferences("octomil", MODE_PRIVATE)
            .edit().putString("paired_models", arr.toString()).apply()
    }

    /** Callback invoked when a pairing code arrives via the local HTTP server. */
    var onPairingCodeReceived: ((code: String, host: String?, modelName: String?) -> Unit)? = null

    /** True when running in the `:speech` child process (ORT isolation). */
    private val isSpeechProcess: Boolean by lazy {
        val pid = android.os.Process.myPid()
        val procName = try {
            java.io.File("/proc/$pid/cmdline").readText().trim('\u0000')
        } catch (_: Exception) { "" }
        procName.endsWith(":speech")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // The :speech process only needs Octomil.init() for speech runtime —
        // skip everything else (server, client, llama.cpp, mDNS).
        if (isSpeechProcess) {
            Log.i("OctomilApp", "Speech process — minimal init")
            Octomil.init(this)
            return
        }

        // Eagerly load sherpa-onnx native library BEFORE HWUI render threads
        // are fully active. This isolates JNI_OnLoad from UI rendering.
        try {
            System.loadLibrary("sherpa-onnx-jni")
            Log.i("OctomilApp", "Pre-loaded sherpa-onnx-jni native library")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("OctomilApp", "Failed to pre-load sherpa-onnx-jni", e)
        }

        // Synchronous init — SDK wires all runtimes (llama.cpp, sherpa-onnx) internally
        Octomil.init(this)

        val prefs = getSharedPreferences("octomil", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val orgId = prefs.getString("org_id", "") ?: ""
        val serverUrl = prefs.getString("server_url", "https://api.octomil.com/api/v1") ?: "https://api.octomil.com/api/v1"

        // Initialize device name if not set
        if (prefs.getString("device_name", null) == null) {
            prefs.edit().putString("device_name", "${Build.MANUFACTURER} ${Build.MODEL}").apply()
        }

        // Only initialize the client if credentials are configured.
        // On first launch, api_key is empty — the client is created later
        // when the user pairs via saveCredentials().
        if (apiKey.isNotBlank()) {
            client = OctomilClient.Builder(this)
                .config(
                    OctomilConfig.Builder()
                        .deviceAccessToken(apiKey)
                        .orgId(orgId)
                        .serverUrl(serverUrl)
                        .build()
                )
                .build()
        }

        loadPairedModels()

        // Bootstrap catalog in background so capability-based routing is available
        val manifest = buildAppManifest()
        appScope.launch {
            try {
                Octomil.configure(this@OctomilApplication, manifest)
                Log.i("OctomilApp", "Octomil.configure() complete — ${manifest.models.size} model(s)")
            } catch (e: Exception) {
                Log.w("OctomilApp", "Octomil.configure() failed: ${e.message}")
            }
        }

        // Start server + mDNS off main thread to speed up cold launch
        Thread { startLocalServer() }.start()

        // Note: Speech recognition runs in a separate process (SpeechService)
        // to avoid Samsung HWUI crash when loading ONNX Runtime models.
    }

    fun addPairedModel(model: PairedModel) {
        if (pairedModels.none { it.name == model.name }) {
            pairedModels.add(model)
            savePairedModels()
        }
    }

    fun removePairedModel(name: String) {
        pairedModels.removeAll { it.name == name }
        savePairedModels()
    }

    fun clearPairedModels() {
        pairedModels.clear()
        savePairedModels()
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

    /**
     * Build an [AppManifest] from the currently paired models.
     *
     * Maps each [PairedModel] to an [AppModelEntry] with its first capability
     * (or CHAT as default). All paired models use [DeliveryMode.MANAGED] since
     * they were deployed from the server via `octomil deploy --phone`.
     */
    private fun buildAppManifest(): AppManifest {
        val entries = pairedModels.map { model ->
            AppModelEntry(
                id = model.name,
                capability = model.capabilities.firstOrNull() ?: ModelCapability.CHAT,
                delivery = DeliveryMode.MANAGED,
            )
        }
        return AppManifest(models = entries)
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
