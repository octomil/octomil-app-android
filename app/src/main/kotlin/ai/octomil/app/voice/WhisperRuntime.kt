package ai.octomil.app.voice

import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper around WhisperContext for on-device batch speech-to-text.
 *
 * All operations run on a single IO thread. Designed to be loaded on demand
 * and unloaded immediately after transcription to free ~148MB native memory.
 */
class WhisperRuntime(private val modelFile: File) {
    private var ctx: WhisperContext? = null
    private val loaded = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun loadModel() = withContext(dispatcher) {
        ctx = WhisperContext.createContextFromFile(modelFile.absolutePath)
        loaded.set(true)
    }

    suspend fun transcribe(samples: FloatArray): String = withContext(dispatcher) {
        check(loaded.get()) { "Whisper model not loaded" }
        ctx!!.transcribeData(samples, printTimestamp = false)
    }

    suspend fun release() = withContext(dispatcher) {
        ctx?.release()
        ctx = null
        loaded.set(false)
    }
}
