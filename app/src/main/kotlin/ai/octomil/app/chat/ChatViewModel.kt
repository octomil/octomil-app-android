package ai.octomil.app.chat

import ai.octomil.ModelResolver
import ai.octomil.android.AttachmentResolver
import ai.octomil.android.LocalAttachment
import ai.octomil.chat.ChatThread
import ai.octomil.chat.ContentPartValidation
import ai.octomil.chat.GenerateConfig
import ai.octomil.chat.GenerationMetrics
import ai.octomil.chat.LLMRuntime
import ai.octomil.chat.LLMRuntimeRegistry
import ai.octomil.chat.ThreadMessage
import ai.octomil.responses.ContentPart
import ai.octomil.app.OctomilApplication
import ai.octomil.runtime.ModelKeepAliveService
import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.ProgressListener
import java.time.Instant
import java.util.UUID
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
        data class Generating(val phase: String = "Generating…") : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Chat history ──

    private var thread: ChatThread? = null

    private fun ensureThread(): ChatThread {
        return thread ?: ChatThread(
            id = "thread_${UUID.randomUUID()}",
            model = modelName,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString(),
        ).also { thread = it }
    }

    private val _messages = MutableStateFlow<List<ThreadMessage>>(emptyList())
    val messages: StateFlow<List<ThreadMessage>> = _messages.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    // ── Multimodal ──

    private val attachmentResolver = AttachmentResolver(application)

    private val _pendingAttachment = MutableStateFlow<LocalAttachment?>(null)
    val pendingAttachment: StateFlow<LocalAttachment?> = _pendingAttachment.asStateFlow()

    // ── Internal ──

    private var runtime: LLMRuntime? = null
    private var generationJob: Job? = null

    init {
        preload()
    }

    private fun preload() {
        viewModelScope.launch {
            try {
                val app = getApplication<OctomilApplication>()

                // Check if we already have a loaded runtime for this model
                val cached = app.getCachedRuntime(modelName)
                if (cached != null) {
                    runtime = cached
                    ModelKeepAliveService.start(app, modelName)
                    _uiState.value = UiState.Ready
                    Log.i(TAG, "Using cached runtime for: $modelName")
                    return@launch
                }

                var modelFile = ModelResolver.paired().resolve(app, modelName)
                    ?: throw IllegalStateException(
                        "Model '$modelName' not found on device. " +
                        "Re-run: octomil deploy $modelName --phone"
                    )

                // Look for mmproj file alongside model (same dir, *mmproj* pattern)
                val mmprojFile = modelFile.parentFile?.listFiles()?.firstOrNull { f ->
                    f.name.contains("mmproj", ignoreCase = true) && f.extension == "gguf"
                }

                // If the resolver returned the mmproj, pick the actual model file instead
                if (modelFile.name.contains("mmproj", ignoreCase = true)) {
                    modelFile = modelFile.parentFile?.listFiles()?.firstOrNull { f ->
                        f.isFile && !f.name.contains("mmproj", ignoreCase = true) && f.extension == "gguf"
                    } ?: throw IllegalStateException(
                        "Model directory only contains mmproj — no main model GGUF found"
                    )
                }
                if (mmprojFile != null) {
                    Log.i(TAG, "Found mmproj: ${mmprojFile.name}")
                }

                _uiState.value = UiState.Loading("Loading model…")
                val factory = LLMRuntimeRegistry.factory
                    ?: throw IllegalStateException("No LLMRuntime factory registered")

                val llmRuntime = factory(modelFile)

                // Eager load if the runtime supports it.
                // Show progress percentage from native callback.
                if (llmRuntime is LlamaCppRuntime) {
                    llmRuntime.setProgressListener(ProgressListener { progress ->
                        val pct = (progress * 100).toInt()
                        _uiState.value = UiState.Loading("Loading model… $pct%")
                    })
                    try {
                        llmRuntime.loadModel()
                    } finally {
                        llmRuntime.setProgressListener(null)
                    }
                }

                runtime = llmRuntime
                app.cacheRuntime(modelName, llmRuntime)

                // Keep process alive when backgrounded (image picker, camera)
                ModelKeepAliveService.start(app, modelName)

                _uiState.value = UiState.Ready
                Log.i(TAG, "Model preloaded: $modelName" +
                    if (llmRuntime is LlamaCppRuntime) {
                        " vision=${llmRuntime.supportsVision()} audio=${llmRuntime.supportsAudio()}"
                    } else "")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload model: ${e::class.simpleName}: ${e.message}", e)
                _uiState.value = UiState.Error(
                    "${e::class.simpleName}: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && _pendingAttachment.value == null) return
        val currentRuntime = runtime ?: return

        // Cancel any active generation
        cancelGeneration()

        // Capture and clear attachment before launching coroutine
        val attachment = _pendingAttachment.value
        if (attachment != null) _pendingAttachment.value = null

        _streamingText.value = ""
        _uiState.value = UiState.Generating(
            if (attachment != null) "Processing image…" else "Generating…"
        )

        val t = ensureThread()

        generationJob = viewModelScope.launch {
            try {
                // --- multimodal attachment handling (suspend) ---
                val contentParts: List<ContentPart>?

                if (attachment != null) {
                    _uiState.value = UiState.Generating("Processing image…")
                    val resolvedImage = attachmentResolver.resolve(attachment)
                    val parts = buildList {
                        if (text.isNotBlank()) add(ContentPart.Text(text))
                        add(resolvedImage)
                    }
                    ContentPartValidation.validate(parts)
                    contentParts = parts
                } else {
                    contentParts = null
                }

                // Add user message
                val userMsg = ThreadMessage(
                    id = "msg_${UUID.randomUUID()}",
                    threadId = t.id,
                    role = "user",
                    content = if (contentParts != null) {
                        ContentPartValidation.deriveContent(contentParts) ?: text
                    } else {
                        text
                    },
                    contentParts = contentParts,
                    createdAt = Instant.now().toString(),
                )
                _messages.value = _messages.value + userMsg

                // Determine if this is a multimodal turn
                val hasMedia = contentParts?.any { it !is ContentPart.Text } == true
                val runtimeSupportsMedia = currentRuntime.supportsVision() || currentRuntime.supportsAudio()

                if (hasMedia && !runtimeSupportsMedia) {
                    _messages.value = _messages.value + ThreadMessage(
                        id = "msg_${UUID.randomUUID()}",
                        threadId = t.id,
                        role = "assistant",
                        content = "Image understanding isn't available on this build yet. " +
                            "Your image has been saved with the message — it will be " +
                            "processed once a vision-capable model is available.",
                        createdAt = Instant.now().toString(),
                    )
                    _uiState.value = UiState.Ready
                    return@launch
                }

                val config = GenerateConfig(maxTokens = 1024, temperature = 0.7f)
                _uiState.value = UiState.Generating("Generating response…")

                val startTime = System.nanoTime()
                var ttftNanos = 0L
                var tokenCount = 0
                val buffer = StringBuilder()

                val tokenFlow = if (hasMedia && runtimeSupportsMedia) {
                    // Extract raw media bytes from the first media part
                    val mediaPart = contentParts!!.first { it !is ContentPart.Text }
                    val mediaBytes = when (mediaPart) {
                        is ContentPart.Image -> mediaPart.data?.let { Base64.decode(it, Base64.DEFAULT) }
                        is ContentPart.Audio -> mediaPart.data?.let { Base64.decode(it, Base64.DEFAULT) }
                        is ContentPart.Video -> mediaPart.data?.let { Base64.decode(it, Base64.DEFAULT) }
                        else -> null
                    } ?: throw IllegalStateException("Media part has no data")

                    // Build prompt with media marker before the text (VLMs expect image first)
                    val marker = "<__media__>"
                    val prompt = if (text.isNotBlank()) "$marker\n$text" else marker
                    currentRuntime.generateMultimodal(prompt, mediaBytes, config)
                } else {
                    currentRuntime.generate(text, config)
                }

                tokenFlow.collect { token ->
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
                _messages.value = _messages.value + ThreadMessage(
                    id = "msg_${UUID.randomUUID()}",
                    threadId = t.id,
                    role = "assistant",
                    content = buffer.toString(),
                    metrics = metrics,
                    createdAt = Instant.now().toString(),
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
                    _messages.value = _messages.value + ThreadMessage(
                        id = "msg_${UUID.randomUUID()}",
                        threadId = t.id,
                        role = "assistant",
                        content = "$partial [cancelled]",
                        createdAt = Instant.now().toString(),
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

    fun attachImage(uri: Uri) {
        val app = getApplication<Application>()
        val mediaType = app.contentResolver.getType(uri) ?: "image/jpeg"
        val displayName = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        }
        _pendingAttachment.value = LocalAttachment(
            contentUri = uri,
            mediaType = mediaType,
            displayName = displayName,
        )
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    override fun onCleared() {
        cancelGeneration()
        // Stop the keep-alive service when leaving chat
        ModelKeepAliveService.stop(getApplication())
        // Don't close the runtime — it's cached in OctomilApplication
        // for instant reuse when navigating back to chat.
        runtime = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
