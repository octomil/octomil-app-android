package ai.octomil.app.speech

import ai.octomil.Octomil
import ai.octomil.speech.SpeechSession
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "SpeechService"

/**
 * Runs ONNX Runtime speech recognition in a separate process to avoid
 * Samsung HWUI crash (pthread_mutex_lock on destroyed mutex in hwuiTask).
 *
 * Declared with `android:process=":speech"` in the manifest.
 */
class SpeechService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: SpeechSession? = null
    private var callback: ISpeechCallback? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize Octomil SDK in this process (separate from main app process)
        Octomil.init(this)
        Log.i(TAG, "SpeechService created in process ${android.os.Process.myPid()}")
    }

    private val binder = object : ISpeechService.Stub() {

        override fun createSession(modelName: String, cb: ISpeechCallback) {
            callback = cb
            scope.launch {
                try {
                    Log.i(TAG, "Creating session for '$modelName'...")
                    val s = Octomil.audio.streamingSession(modelName)
                    session = s
                    Log.i(TAG, "Session created successfully")

                    // Start collecting transcript updates
                    launch {
                        s.transcript.collect { text ->
                            try {
                                cb.onTranscriptUpdate(text)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send transcript update", e)
                            }
                        }
                    }

                    cb.onSessionReady()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create session", e)
                    try {
                        cb.onError(e.message ?: "Unknown error")
                    } catch (_: Exception) { }
                }
            }
        }

        override fun feedAudio(samples: FloatArray) {
            val s = session ?: return
            s.feed(samples)
        }

        override fun finalizeSession(): String {
            val s = session ?: return ""
            return runBlocking {
                s.finalize()
            }
        }

        override fun releaseSession() {
            session?.release()
            session = null
            callback = null
            Log.i(TAG, "Session released")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        session?.release()
        session = null
        scope.cancel()
        Log.i(TAG, "SpeechService destroyed")
        super.onDestroy()
    }
}
