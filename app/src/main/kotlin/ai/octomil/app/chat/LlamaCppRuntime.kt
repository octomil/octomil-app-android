package ai.octomil.app.chat

import ai.octomil.chat.GenerateConfig
import ai.octomil.chat.LLMRuntime
import android.content.Context
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.ProgressListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [LLMRuntime] backed by llama.cpp via the AiChat InferenceEngine.
 *
 * Wraps the reference llama.android InferenceEngine behind the SDK's
 * [LLMRuntime] interface for on-device GGUF model inference.
 */
class LlamaCppRuntime(
    private val modelFile: File,
    private val mmprojFile: File? = null,
    context: Context,
) : LLMRuntime {

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private val modelLoaded = AtomicBoolean(false)
    private val mmprojLoaded = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    /** Set a listener for model loading progress (0.0 to 1.0). */
    fun setProgressListener(listener: ProgressListener?) {
        engine.setProgressListener(listener)
    }

    /**
     * Eagerly load the model. Call this before [generate] to avoid
     * cold-start latency on first prompt.
     */
    suspend fun loadModel() {
        check(!closed.get()) { "Runtime is closed" }
        Log.i(TAG, "Loading model: ${modelFile.absolutePath}")
        engine.loadModel(modelFile.absolutePath)
        engine.state.first { state ->
            when (state) {
                is InferenceEngine.State.ModelReady -> true
                is InferenceEngine.State.Error -> throw state.exception
                else -> false
            }
        }
        modelLoaded.set(true)
        Log.i(TAG, "Model loaded successfully")

        // Load mmproj if available
        if (mmprojFile != null && mmprojFile.exists()) {
            try {
                Log.i(TAG, "Loading mmproj: ${mmprojFile.absolutePath}")
                engine.loadMmproj(mmprojFile.absolutePath)
                mmprojLoaded.set(true)
                Log.i(TAG, "Mmproj loaded. vision=${engine.supportsVision()}, audio=${engine.supportsAudio()}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load mmproj (multimodal disabled): ${e.message}")
            }
        }
    }

    override fun generate(prompt: String, config: GenerateConfig): Flow<String> {
        check(!closed.get()) { "Runtime is closed" }

        if (!modelLoaded.get()) {
            return flow {
                loadModel()
                engine.sendUserPrompt(prompt, config.maxTokens).collect { emit(it) }
            }
        }
        return engine.sendUserPrompt(prompt, config.maxTokens)
    }

    override fun generateMultimodal(
        text: String,
        mediaData: ByteArray,
        config: GenerateConfig,
    ): Flow<String> {
        check(!closed.get()) { "Runtime is closed" }
        check(mmprojLoaded.get()) { "Mmproj not loaded — multimodal unavailable" }
        return engine.sendMultimodalPrompt(text, mediaData, config.maxTokens)
    }

    override fun supportsVision(): Boolean = mmprojLoaded.get() && engine.supportsVision()

    override fun supportsAudio(): Boolean = mmprojLoaded.get() && engine.supportsAudio()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            Log.i(TAG, "Closing runtime")
            engine.cleanUp()
        }
    }

    companion object {
        private const val TAG = "LlamaCppRuntime"
    }
}
