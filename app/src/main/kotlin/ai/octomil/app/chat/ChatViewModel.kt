package ai.octomil.app.chat

import ai.octomil.*
import ai.octomil.app.OctomilApplication
import ai.octomil.app.keyboard.PredictionState
import ai.octomil.app.speech.SpeechServiceClient
import ai.octomil.app.voice.AudioRecorder
import ai.octomil.app.voice.VoiceState
import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the chat screen — manages LLM lifecycle and streaming generation.
 *
 * Uses [Octomil.responses] for all inference. The SDK owns runtime creation,
 * model loading, and caching internally — no engine-specific code here.
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

    // ── Voice (Speech-to-Text) ──

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val recorder = AudioRecorder()
    val recordingDurationMs: StateFlow<Long> = recorder.durationMs

    private val speechClient = SpeechServiceClient(application)

    // ── Keyboard Prediction (SmolLM2) ──

    private val _predictionState = MutableStateFlow<PredictionState>(PredictionState.Idle)
    val predictionState: StateFlow<PredictionState> = _predictionState.asStateFlow()

    private var predictionJob: Job? = null

    // ── Internal ──

    private var generationJob: Job? = null
    private var previousResponseId: String? = null
    private var modelReady = false

    init {
        preload()
    }

    /**
     * Warm up the model by triggering runtime resolution and model loading
     * through [Octomil.responses]. The SDK creates, loads, and caches the
     * runtime internally — no engine objects in app code.
     */
    private fun preload() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading("Loading model…")

                // Trigger model loading via a minimal inference call.
                // The SDK resolves the model file, creates the LlamaCppRuntime,
                // and loads weights. Subsequent calls reuse the cached runtime.
                val warmupRequest = ResponseRequest(
                    model = modelName,
                    input = listOf(InputItem.text("hi")),
                    maxOutputTokens = 1,
                )
                Octomil.responses.create(warmupRequest)
                modelReady = true

                ModelKeepAliveService.start(
                    getApplication<OctomilApplication>(),
                    modelName,
                )
                _uiState.value = UiState.Ready
                Log.i(TAG, "Model preloaded: $modelName")
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
                // --- build input items ---
                val inputItems = mutableListOf<InputItem>()

                if (attachment != null) {
                    _uiState.value = UiState.Generating("Processing image…")
                    val resolvedImage = attachmentResolver.resolve(attachment)
                    val parts = buildList<ContentPart> {
                        if (resolvedImage is ContentPartImage) add(resolvedImage)
                        if (text.isNotBlank()) add(ContentPartText(text))
                    }
                    inputItems.add(InputItemUser(parts))
                } else {
                    inputItems.add(InputItem.text(text))
                }

                // Add user message to chat history
                val userMsg = ThreadMessage(
                    id = "msg_${UUID.randomUUID()}",
                    threadId = t.id,
                    role = "user",
                    content = text,
                    createdAt = Instant.now().toString(),
                )
                _messages.value = _messages.value + userMsg

                _uiState.value = UiState.Generating("Generating response…")

                val request = ResponseRequest(
                    model = modelName,
                    input = inputItems,
                    maxOutputTokens = 1024,
                    temperature = 0.7f,
                    previousResponseId = previousResponseId,
                )

                val startTime = System.nanoTime()
                var ttftNanos = 0L
                var tokenCount = 0
                val buffer = StringBuilder()

                Octomil.responses.stream(request).collect { event ->
                    when (event) {
                        is ResponseStreamEventTextDelta -> {
                            if (tokenCount == 0) {
                                ttftNanos = System.nanoTime() - startTime
                            }
                            tokenCount++
                            buffer.append(event.delta)
                            _streamingText.value = buffer.toString()
                        }
                        is ResponseStreamEventDone -> {
                            previousResponseId = event.response.id
                        }
                        else -> { /* tool calls, errors */ }
                    }
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

    // ── Voice Flow ──

    fun startRecording() {
        _voiceState.value = VoiceState.Recording
        recorder.startRecording()
    }

    fun stopAndTranscribe() {
        viewModelScope.launch {
            try {
                val samples = recorder.stopRecording()

                _voiceState.value = VoiceState.Transcribing

                // Transcribe via separate-process SpeechService (avoids Samsung HWUI crash)
                val speechModel = OctomilApplication.instance.pairedModels
                    .first { ModelCapability.TRANSCRIPTION in it.capabilities }
                    .name
                speechClient.connect()
                speechClient.createSession(speechModel)
                speechClient.feed(samples)
                val text = withContext(Dispatchers.IO) {
                    speechClient.finalizeSession()
                }
                speechClient.releaseSession()

                _voiceState.value = VoiceState.Idle
                _transcribedText.value = text.trim()
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _voiceState.value = VoiceState.Error(e.message ?: "Transcription failed")
            }
        }
    }

    fun dismissVoiceError() {
        _voiceState.value = VoiceState.Idle
    }

    fun consumeTranscribedText() {
        _transcribedText.value = ""
    }

    // ── Keyboard Prediction Flow ──

    fun onTextChanged(text: String) {
        if (text.isBlank()) {
            _predictionState.value = PredictionState.Idle
            return
        }

        predictionJob?.cancel()
        predictionJob = viewModelScope.launch {
            delay(200) // debounce
            try {
                val suggestions = Octomil.text.predict(prefix = text)
                _predictionState.value = if (suggestions.isNotEmpty()) {
                    PredictionState.Ready(suggestions)
                } else {
                    PredictionState.Idle
                }
            } catch (e: Exception) {
                Log.w(TAG, "Prediction failed: ${e.message}")
                _predictionState.value = PredictionState.Idle
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
        predictionJob?.cancel()

        // Disconnect from speech service
        speechClient.disconnect()

        // Stop the keep-alive service when leaving chat
        ModelKeepAliveService.stop(getApplication())
        super.onCleared()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
