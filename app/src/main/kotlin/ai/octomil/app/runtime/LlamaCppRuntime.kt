package ai.octomil.app.runtime

import ai.octomil.chat.GenerateConfig
import ai.octomil.chat.LLMRuntime
import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.ProgressListener
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Bridges the llama.cpp [InferenceEngine] to the Octomil SDK's [LLMRuntime] interface.
 *
 * Supports both chat generation and handle-based next-token prediction.
 *
 * Create via [factory]:
 * ```kotlin
 * LLMRuntimeRegistry.factory = LlamaCppRuntime.factory(context)
 * ```
 */
class LlamaCppRuntime(
    private val engine: InferenceEngine,
    private val modelFile: File,
) : LLMRuntime {

    private var progressListener: ((Float) -> Unit)? = null

    override suspend fun load() {
        engine.setProgressListener(ProgressListener { progress ->
            progressListener?.invoke(progress)
        })
        try {
            engine.loadModel(modelFile.absolutePath)

            // Auto-load mmproj if present alongside model
            val mmprojFile = modelFile.parentFile?.listFiles()?.firstOrNull { f ->
                f.name.contains("mmproj", ignoreCase = true) && f.extension == "gguf"
            }
            if (mmprojFile != null) {
                engine.loadMmproj(mmprojFile.absolutePath)
            }
        } finally {
            engine.setProgressListener(null)
        }
    }

    override fun setLoadProgressListener(listener: ((Float) -> Unit)?) {
        progressListener = listener
    }

    override fun generate(prompt: String, config: GenerateConfig): Flow<String> =
        engine.sendUserPrompt(prompt, config.maxTokens)

    override fun generateMultimodal(
        text: String,
        mediaData: ByteArray,
        config: GenerateConfig,
    ): Flow<String> = engine.sendMultimodalPrompt(text, mediaData, config.maxTokens)

    override fun supportsVision(): Boolean = engine.supportsVision()
    override fun supportsAudio(): Boolean = engine.supportsAudio()

    // ── Next-token prediction ──

    override fun supportsPrediction(): Boolean = true

    override suspend fun loadPredictionHandle(modelPath: String): Long =
        engine.loadModelHandle(modelPath)

    override suspend fun predictNext(handle: Long, text: String, k: Int): List<Pair<String, Float>> =
        engine.predictNext(handle, text, k).map { it.text to it.probability }

    override suspend fun unloadPredictionHandle(handle: Long) =
        engine.unloadHandle(handle)

    override fun close() {
        engine.cleanUp()
        engine.destroy()
    }

    companion object {
        /** Create a factory for [LLMRuntimeRegistry]. */
        fun factory(context: Context): (File) -> LLMRuntime = { modelFile ->
            val engine = AiChat.getInferenceEngine(context)
            LlamaCppRuntime(engine, modelFile)
        }
    }
}
