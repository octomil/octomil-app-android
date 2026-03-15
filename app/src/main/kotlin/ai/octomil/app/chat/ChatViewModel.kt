package ai.octomil.app.chat

import ai.octomil.ModelResolver
import ai.octomil.chat.GenerateConfig
import ai.octomil.chat.LLMRuntime
import ai.octomil.chat.LLMRuntimeRegistry
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the chat screen — manages LLM lifecycle and streaming generation.
 *
 * Preloads the model eagerly on construction so TTFT is minimised.
 * Tracks generation metrics: TTFT, decode tok/s, total latency.
 */
class ChatViewModel(
    application: Application,
    private val modelName: String,
) : AndroidViewModel(application) {

    // ── State ──

    sealed class UiState {
        data class Loading(val message: String = "Loading model…") : UiState()
        object Ready : UiState()
        object Generating : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Chat history ──

    data class ChatMessage(
        val role: String, // "user" or "assistant"
        val content: String,
        val metrics: GenerationMetrics? = null,
    )

    data class GenerationMetrics(
        val ttftMs: Long,
        val decodeTokensPerSec: Double,
        val totalTokens: Int,
        val totalLatencyMs: Long,
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    // ── Internal ──

    private var runtime: LLMRuntime? = null
    private var generationJob: Job? = null

    init {
        preload()
    }

    private fun preload() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Resolving model…")
                val context = getApplication<Application>()
                val modelFile = ModelResolver.paired().resolve(context, modelName)
                    ?: throw IllegalStateException("Model '$modelName' not found on device")

                _uiState.value = UiState.Loading("Loading ${modelFile.name}…")
                val factory = LLMRuntimeRegistry.factory
                    ?: throw IllegalStateException("No LLMRuntime factory registered")

                val llmRuntime = factory(modelFile)
                runtime = llmRuntime

                // Eager load if the runtime supports it
                if (llmRuntime is LlamaCppRuntime) {
                    llmRuntime.loadModel()
                }

                _uiState.value = UiState.Ready
                Log.i(TAG, "Model preloaded: $modelName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload model", e)
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val currentRuntime = runtime ?: return

        // Cancel any active generation
        cancelGeneration()

        // Add user message
        val updated = _messages.value + ChatMessage(role = "user", content = text)
        _messages.value = updated
        _streamingText.value = ""
        _uiState.value = UiState.Generating

        generationJob = viewModelScope.launch {
            try {
                val prompt = buildPrompt(updated)
                val config = GenerateConfig(maxTokens = 1024, temperature = 0.7f)

                val startTime = System.nanoTime()
                var ttftNanos = 0L
                var tokenCount = 0
                val buffer = StringBuilder()

                currentRuntime.generate(prompt, config).collect { token ->
                    if (tokenCount == 0) {
                        ttftNanos = System.nanoTime() - startTime
                    }
                    tokenCount++
                    buffer.append(token)
                    _streamingText.value = buffer.toString()
                }

                val totalNanos = System.nanoTime() - startTime
                val ttftMs = ttftNanos / 1_000_000
                val totalMs = totalNanos / 1_000_000
                val decodeMs = totalMs - ttftMs
                val decodeTokensPerSec = if (decodeMs > 0 && tokenCount > 1) {
                    (tokenCount - 1).toDouble() / (decodeMs / 1000.0)
                } else {
                    0.0
                }

                val metrics = GenerationMetrics(
                    ttftMs = ttftMs,
                    decodeTokensPerSec = decodeTokensPerSec,
                    totalTokens = tokenCount,
                    totalLatencyMs = totalMs,
                )

                // Add assistant message with metrics
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant",
                    content = buffer.toString(),
                    metrics = metrics,
                )
                _streamingText.value = ""
                _uiState.value = UiState.Ready

                Log.i(TAG, "Generation done: ${metrics.totalTokens} tokens, " +
                    "TTFT=${metrics.ttftMs}ms, " +
                    "decode=${String.format("%.1f", metrics.decodeTokensPerSec)} tok/s, " +
                    "total=${metrics.totalLatencyMs}ms")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled — keep partial text as assistant message
                val partial = _streamingText.value
                if (partial.isNotBlank()) {
                    _messages.value = _messages.value + ChatMessage(
                        role = "assistant",
                        content = "$partial [cancelled]",
                    )
                }
                _streamingText.value = ""
                _uiState.value = UiState.Ready
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                _uiState.value = UiState.Error(e.message ?: "Generation failed")
                _streamingText.value = ""
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    private fun buildPrompt(messages: List<ChatMessage>): String {
        // Simple multi-turn prompt format (works with most GGUF chat models)
        return buildString {
            for (msg in messages) {
                when (msg.role) {
                    "user" -> append("<|user|>\n${msg.content}\n")
                    "assistant" -> append("<|assistant|>\n${msg.content}\n")
                }
            }
            append("<|assistant|>\n")
        }
    }

    override fun onCleared() {
        cancelGeneration()
        runtime?.close()
        runtime = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
