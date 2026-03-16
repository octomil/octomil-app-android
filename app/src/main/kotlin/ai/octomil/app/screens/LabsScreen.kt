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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.octomil.app.keyboard.TokenSuggestionFilter
import ai.octomil.app.ui.OctomilColors
import ai.octomil.app.voice.AudioRecorder
import ai.octomil.app.voice.WhisperRuntime
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { WhisperCard() }
            item { PredictionCard() }
        }
    }
}

// ── Whisper Card ──

@Composable
private fun WhisperCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var whisperRuntime by remember { mutableStateOf<WhisperRuntime?>(null) }
    var status by remember { mutableStateOf("idle") }
    var transcriptionResult by remember { mutableStateOf("") }
    var capturedSamples by remember { mutableStateOf<FloatArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(false) }

    val recorder = remember { AudioRecorder() }
    val durationMs by recorder.durationMs.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            isRecording = true
            recorder.startRecording()
            scope.launch {
                delay(3000)
                if (isRecording) {
                    capturedSamples = recorder.stopRecording()
                    isRecording = false
                    status = "recorded ${capturedSamples!!.size} samples (${durationMs}ms)"
                }
            }
        } else {
            status = "microphone permission denied"
        }
    }

    val isModelLoaded = whisperRuntime != null
    val hasRecording = capturedSamples != null

    LabCard(
        icon = { LabCardIcon(Icons.Outlined.GraphicEq, OctomilColors.Cyan400) },
        title = "Whisper Transcription",
        status = status,
    ) {
        // Load model
        LabButton(
            text = "Load whisper-tiny",
            onClick = {
                scope.launch {
                    isLoading = true
                    status = "loading whisper-tiny\u2026"
                    try {
                        val t0 = System.currentTimeMillis()
                        val modelsDir = context.filesDir.resolve("octomil_models/whisper-tiny/1.0.0")
                        val file = modelsDir.listFiles()?.firstOrNull { it.extension == "bin" }
                            ?: error("Whisper model not found in ${modelsDir.absolutePath}")
                        val runtime = WhisperRuntime(file)
                        runtime.loadModel()
                        whisperRuntime = runtime
                        val elapsed = System.currentTimeMillis() - t0
                        status = "loaded (${elapsed}ms)"
                    } catch (e: Exception) {
                        Log.e(TAG, "Whisper load failed", e)
                        status = "load failed: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isModelLoaded && !isLoading,
            isLoading = isLoading,
        )

        // Record
        LabButton(
            text = if (isRecording) "Stop (${durationMs}ms)" else "Record 3 sec",
            onClick = {
                if (isRecording) {
                    capturedSamples = recorder.stopRecording()
                    isRecording = false
                    status = "recorded ${capturedSamples!!.size} samples (${durationMs}ms)"
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = isModelLoaded && !isTranscribing,
            variant = if (isRecording) LabButtonVariant.Destructive else LabButtonVariant.Secondary,
        )

        // Transcribe
        LabButton(
            text = "Transcribe",
            onClick = {
                scope.launch {
                    isTranscribing = true
                    status = "transcribing\u2026"
                    withContext(NonCancellable) {
                        try {
                            val t0 = System.currentTimeMillis()
                            val result = whisperRuntime!!.transcribe(capturedSamples!!)
                            val elapsed = System.currentTimeMillis() - t0
                            transcriptionResult = result.trim()
                            whisperRuntime!!.release()
                            whisperRuntime = null
                            capturedSamples = null
                            status = "transcribed (${elapsed}ms), model released"
                        } catch (e: Exception) {
                            Log.e(TAG, "Transcription failed", e)
                            status = "transcription failed: ${e.message}"
                            whisperRuntime?.release()
                            whisperRuntime = null
                        } finally {
                            isTranscribing = false
                        }
                    }
                }
            },
            enabled = hasRecording && isModelLoaded && !isTranscribing,
            isLoading = isTranscribing,
        )

        // Result
        if (transcriptionResult.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    transcriptionResult,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var engine by remember { mutableStateOf<InferenceEngine?>(null) }
    var predictionHandle by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf("idle") }
    var inputText by remember { mutableStateOf("The weather today is") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isPredicting by remember { mutableStateOf(false) }

    val isModelLoaded = predictionHandle != null

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

        // Load
        LabButton(
            text = "Load smollm2-135m",
            onClick = {
                scope.launch {
                    isLoading = true
                    status = "loading smollm2-135m\u2026"
                    try {
                        val t0 = System.currentTimeMillis()
                        val eng = AiChat.getInferenceEngine(context)
                        engine = eng
                        val modelsDir = context.filesDir.resolve("octomil_models/smollm2-135m/1.0.0")
                        val file = modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }
                            ?: error("SmolLM2 model not found in ${modelsDir.absolutePath}")
                        val handle = eng.loadModelHandle(file.absolutePath)
                        predictionHandle = handle
                        val elapsed = System.currentTimeMillis() - t0
                        status = "loaded handle=$handle (${elapsed}ms)"
                    } catch (e: Exception) {
                        Log.e(TAG, "Prediction model load failed", e)
                        status = "load failed: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isModelLoaded && !isLoading,
            isLoading = isLoading,
        )

        // Predict
        LabButton(
            text = "Predict next",
            onClick = {
                scope.launch {
                    isPredicting = true
                    status = "predicting\u2026"
                    try {
                        val t0 = System.currentTimeMillis()
                        val raw = engine!!.predictNext(predictionHandle!!, inputText, k = 8)
                        val elapsed = System.currentTimeMillis() - t0
                        suggestions = TokenSuggestionFilter.process(raw)
                        status = "predicted (${elapsed}ms), ${raw.size} raw \u2192 ${suggestions.size} suggestions"
                    } catch (e: Exception) {
                        Log.e(TAG, "Prediction failed", e)
                        status = "prediction failed: ${e.message}"
                    } finally {
                        isPredicting = false
                    }
                }
            },
            enabled = isModelLoaded && inputText.isNotBlank() && !isPredicting,
            isLoading = isPredicting,
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
                            inputText = if (inputText.endsWith(" ")) {
                                "$inputText$suggestion"
                            } else {
                                "$inputText $suggestion"
                            }
                            suggestions = emptyList()
                        },
                        label = { Text(suggestion) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }

        // Unload
        LabButton(
            text = "Unload",
            onClick = {
                scope.launch {
                    try {
                        engine!!.unloadHandle(predictionHandle!!)
                        status = "unloaded handle=$predictionHandle"
                        predictionHandle = null
                        suggestions = emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Unload failed", e)
                        status = "unload failed: ${e.message}"
                    }
                }
            },
            enabled = isModelLoaded,
            variant = LabButtonVariant.Outline,
        )
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
