package ai.octomil.app.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "SpeechServiceClient"

/**
 * Client-side bridge to [SpeechService] running in the `:speech` process.
 *
 * Exposes the same contract as [ai.octomil.speech.SpeechSession] but
 * routes every call over AIDL/Binder IPC so that ONNX Runtime never
 * loads in the main (HWUI) process.
 *
 * Usage:
 * ```
 * val client = SpeechServiceClient(context)
 * client.connect()
 * client.createSession(speechModelName)
 * client.feed(samples)
 * val transcript = client.transcript.value   // live updates
 * val final = client.finalizeSession()
 * client.releaseSession()
 * client.disconnect()
 * ```
 */
class SpeechServiceClient(private val context: Context) {

    private var service: ISpeechService? = null
    private var bound = false

    private val _transcript = MutableStateFlow("")
    /** Live transcript updates from the speech process. */
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = ISpeechService.Stub.asInterface(binder)
            _connected.value = true
            Log.i(TAG, "Connected to SpeechService")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _connected.value = false
            Log.w(TAG, "Disconnected from SpeechService")
        }
    }

    /** Bind to the speech service. Returns immediately; use [connected] to observe. */
    fun connect() {
        if (bound) return
        val intent = Intent(context, SpeechService::class.java)
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            Log.e(TAG, "Failed to bind to SpeechService")
        }
    }

    /** Unbind from the speech service. */
    fun disconnect() {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: Exception) { }
            bound = false
            service = null
            _connected.value = false
        }
    }

    /**
     * Create a streaming session in the speech process.
     * Suspends until the session is ready or an error occurs.
     */
    suspend fun createSession(modelName: String) = suspendCoroutine { cont ->
        val svc = service
        if (svc == null) {
            cont.resumeWithException(IllegalStateException("SpeechService not connected"))
            return@suspendCoroutine
        }

        _transcript.value = ""

        svc.createSession(modelName, object : ISpeechCallback.Stub() {
            override fun onSessionReady() {
                Log.i(TAG, "Session ready")
                cont.resume(Unit)
            }

            override fun onTranscriptUpdate(text: String) {
                _transcript.value = text
            }

            override fun onError(message: String) {
                Log.e(TAG, "Session error: $message")
                cont.resumeWithException(RuntimeException(message))
            }
        })
    }

    /** Feed 16kHz mono float PCM samples to the active session. */
    fun feed(samples: FloatArray) {
        service?.feedAudio(samples)
    }

    /** Finalize the session and return the final transcript. Blocks the calling thread. */
    fun finalizeSession(): String {
        return service?.finalizeSession() ?: ""
    }

    /** Release the active session and free native resources. */
    fun releaseSession() {
        _transcript.value = ""
        try {
            service?.releaseSession()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release session", e)
        }
    }
}
