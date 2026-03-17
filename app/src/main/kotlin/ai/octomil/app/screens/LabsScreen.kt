package ai.octomil.app.screens

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ai.octomil.Octomil
import ai.octomil.manifest.ModelRef
import ai.octomil.text.TextPredictionRequest
import ai.octomil.app.speech.SpeechServiceClient
import ai.octomil.app.ui.OctomilColors
import ai.octomil.app.voice.AudioRecorder
import ai.octomil.app.voice.WhisperRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LabsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Labs",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { SpeechRecognitionCard() }
            item { PredictionCard() }
        }
    }
}

// ── Speech Recognition Card ──

private enum class SpeechMode(val label: String) {
    STREAMING("Zipformer 20M (live)"),
    BATCH("Whisper Tiny (batch)"),
}

@Composable
private fun SpeechRecognitionCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Shared state
    var mode by remember { mutableStateOf(SpeechMode.STREAMING) }
    var status by remember { mutableStateOf("idle") }
    var transcriptionResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    val recorder = remember { AudioRecorder() }
    val durationMs by recorder.durationMs.collectAsState()

    // Streaming mode state (sherpa-onnx via SpeechService)
    val speechClient = remember { SpeechServiceClient(context) }
    var hasSession by remember { mutableStateOf(false) }
    val liveTranscript by speechClient.transcript.collectAsState()

    // Batch mode state (whisper.cpp)
    var whisperRuntime by remember { mutableStateOf<WhisperRuntime?>(null) }

    DisposableEffect(Unit) {
        speechClient.connect()
        onDispose {
            if (hasSession) speechClient.releaseSession()
            speechClient.disconnect()
            scope.launch { whisperRuntime?.release() }
        }
    }

    fun stopRecording() {
        isRecording = false
        when (mode) {
            SpeechMode.STREAMING -> {
                recorder.stopStreaming()
                if (!hasSession) {
                    status = "idle"
                    isLoading = false
                    return
                }
                status = "finalizing\u2026"
                scope.launch {
                    try {
                        val t0 = System.currentTimeMillis()
                        val finalText = withContext(Dispatchers.IO) {
                            speechClient.finalizeSession()
                        }
                        val elapsed = System.currentTimeMillis() - t0
                        transcriptionResult = finalText
                        status = "done (${elapsed}ms finalize)"
                    } catch (e: Exception) {
                        Log.e(TAG, "Finalize failed", e)
                        status = "finalize failed: ${e.message}"
                    } finally {
                        speechClient.releaseSession()
                        hasSession = false
                    }
                }
            }
            SpeechMode.BATCH -> {
                val samples = recorder.stopRecording()
                val rt = whisperRuntime ?: return
                status = "transcribing ${samples.size} samples\u2026"
                scope.launch {
                    try {
                        val t0 = System.currentTimeMillis()
                        val text = rt.transcribe(samples)
                        val elapsed = System.currentTimeMillis() - t0
                        transcriptionResult = text
                        status = "done (${elapsed}ms)"
                    } catch (e: Exception) {
                        Log.e(TAG, "Whisper transcribe failed", e)
                        status = "error: ${e.message}"
                    }
                }
            }
        }
    }

    fun startRecording() {
        scope.launch {
            isLoading = true
            isRecording = true
            transcriptionResult = ""

            when (mode) {
                SpeechMode.STREAMING -> {
                    status = "loading zipformer\u2026"

                    // Start recording immediately — buffer while model loads
                    val pendingChunks = mutableListOf<FloatArray>()
                    val sessionReady = java.util.concurrent.atomic.AtomicBoolean(false)

                    recorder.startStreaming { chunk ->
                        if (sessionReady.get()) {
                            speechClient.feed(chunk)
                        } else {
                            synchronized(pendingChunks) { pendingChunks.add(chunk) }
                        }
                    }

                    try {
                        speechClient.createSession("sherpa-zipformer-en-20m")
                        hasSession = true

                        synchronized(pendingChunks) {
                            for (chunk in pendingChunks) speechClient.feed(chunk)
                            pendingChunks.clear()
                            sessionReady.set(true)
                        }

                        isLoading = false
                        status = "recording (live)\u2026"
                    } catch (e: Exception) {
                        Log.e(TAG, "Speech session failed", e)
                        recorder.stopStreaming()
                        status = "error: ${e.message}"
                        isLoading = false
                        isRecording = false
                    }
                }
                SpeechMode.BATCH -> {
                    status = "loading whisper-tiny\u2026"
                    try {
                        if (whisperRuntime == null) {
                            val modelsDir = context.filesDir.resolve("octomil_models/whisper-tiny/1.0.0")
                            val file = modelsDir.listFiles()?.firstOrNull { it.extension == "bin" }
                                ?: error("Whisper model not found in ${modelsDir.absolutePath}")
                            val rt = WhisperRuntime(file)
                            rt.loadModel()
                            whisperRuntime = rt
                        }
                        isLoading = false
                        status = "recording (batch)\u2026"
                        recorder.startRecording()
                    } catch (e: Exception) {
                        Log.e(TAG, "Whisper load failed", e)
                        status = "error: ${e.message}"
                        isLoading = false
                        isRecording = false
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            status = "microphone permission denied"
        }
    }

    LabCard(
        icon = { LabCardIcon(Icons.Outlined.GraphicEq, OctomilColors.Cyan400) },
        title = "Speech Recognition",
        status = status,
    ) {
        // Model selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeechMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { if (!isRecording && !isLoading) mode = m },
                    label = { Text(m.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isRecording && !isLoading,
                )
            }
        }

        LabButton(
            text = if (isLoading) "Loading model\u2026"
                   else if (isRecording) "Stop (${durationMs}ms)"
                   else "Record",
            onClick = {
                if (isRecording) {
                    stopRecording()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = !isLoading,
            isLoading = isLoading,
            variant = if (isRecording) LabButtonVariant.Destructive else LabButtonVariant.Primary,
        )

        // Live transcript (streaming) or final result (batch)
        val displayText = if (mode == SpeechMode.STREAMING && isRecording) liveTranscript else transcriptionResult
        if (displayText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    displayText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Prediction Card ──

@Composable
private fun PredictionCard() {
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("idle") }
    var inputText by remember { mutableStateOf("The weather today is") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isPredicting by remember { mutableStateOf(false) }

    // Auto-load model if needed, then predict — OctomilText manages lifecycle
    fun predictNext(text: String) {
        scope.launch {
            isPredicting = true
            status = "predicting\u2026"
            try {
                val t0 = System.currentTimeMillis()
                val result = Octomil.text.predictions.create(
                    TextPredictionRequest(
                        model = ModelRef.Id("smollm2-135m"),
                        input = text,
                    )
                )
                suggestions = result.predictions.map { it.text }
                val elapsed = System.currentTimeMillis() - t0
                status = "predicted (${elapsed}ms), ${suggestions.size} suggestions"
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed", e)
                status = "prediction failed: ${e.message}"
            } finally {
                isPredicting = false
            }
        }
    }

    LabCard(
        icon = { LabCardIcon(Icons.Outlined.Psychology, OctomilColors.Indigo400) },
        title = "Next-Token Prediction",
        status = status,
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Input text") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )

        // Single button: auto-loads model on first tap, then predicts
        LabButton(
            text = if (isLoading) "Loading model\u2026" else "Predict next",
            onClick = { predictNext(inputText) },
            enabled = inputText.isNotBlank() && !isLoading && !isPredicting,
            isLoading = isLoading || isPredicting,
        )

        // Suggestion chips
        if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            val newText = if (inputText.endsWith(" ")) {
                                "$inputText$suggestion"
                            } else {
                                "$inputText $suggestion"
                            }
                            inputText = newText
                            suggestions = emptyList()
                            // Auto-predict next token
                            predictNext(newText)
                        },
                        label = { Text(suggestion) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }

    }
}

// ── Shared components ──

@Composable
private fun LabCard(
    icon: @Composable () -> Unit,
    title: String,
    status: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                icon()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun LabCardIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

private enum class LabButtonVariant { Primary, Secondary, Outline, Destructive }

@Composable
private fun LabButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    variant: LabButtonVariant = LabButtonVariant.Primary,
) {
    when (variant) {
        LabButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text)
            }
        }
        LabButtonVariant.Secondary -> {
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(text)
            }
        }
        LabButtonVariant.Outline -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(text)
            }
        }
        LabButtonVariant.Destructive -> {
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                // Recording indicator dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text)
            }
        }
    }
}
