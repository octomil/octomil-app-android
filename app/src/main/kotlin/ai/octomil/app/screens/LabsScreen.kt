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
import ai.octomil.app.keyboard.TokenSuggestionFilter
import ai.octomil.app.ui.OctomilColors
import ai.octomil.app.voice.AudioRecorder
import ai.octomil.speech.SpeechSession
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.launch

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

@Composable
private fun SpeechRecognitionCard() {
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<SpeechSession?>(null) }
    var status by remember { mutableStateOf("idle") }
    var transcriptionResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    val recorder = remember { AudioRecorder() }
    val durationMs by recorder.durationMs.collectAsState()

    // Collect live transcript from active session
    val liveTranscript by session?.transcript?.collectAsState() ?: remember { mutableStateOf("") }

    fun stopRecording() {
        val currentSession = session ?: return
        recorder.stopStreaming()
        isRecording = false
        status = "finalizing\u2026"
        scope.launch {
            try {
                val t0 = System.currentTimeMillis()
                val finalText = currentSession.finalize()
                val elapsed = System.currentTimeMillis() - t0
                transcriptionResult = finalText
                status = "done (${elapsed}ms finalize)"
            } catch (e: Exception) {
                Log.e(TAG, "Finalize failed", e)
                status = "finalize failed: ${e.message}"
            } finally {
                currentSession.release()
                session = null
            }
        }
    }

    fun startRecording() {
        scope.launch {
            isLoading = true
            status = "loading model\u2026"
            try {
                val newSession = Octomil.audio.streamingSession("sherpa-zipformer-en-20m")
                session = newSession
                isLoading = false
                isRecording = true
                status = "recording\u2026"
                transcriptionResult = ""

                recorder.startStreaming { chunk ->
                    newSession.feed(chunk)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Speech session failed", e)
                status = "error: ${e.message}"
                isLoading = false
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

        // Live transcript while recording
        val displayText = if (isRecording) liveTranscript else transcriptionResult
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

    // Auto-load model if needed, then predict
    fun predictNext(text: String) {
        scope.launch {
            if (predictionHandle == null) {
                isLoading = true
                status = "loading smollm2-135m\u2026"
                try {
                    val eng = AiChat.getInferenceEngine(context)
                    engine = eng
                    val modelsDir = context.filesDir.resolve("octomil_models/smollm2-135m/1.0.0")
                    val file = modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }
                        ?: error("SmolLM2 model not found in ${modelsDir.absolutePath}")
                    predictionHandle = eng.loadModelHandle(file.absolutePath)
                    status = "loaded"
                } catch (e: Exception) {
                    Log.e(TAG, "Prediction model load failed", e)
                    status = "load failed: ${e.message}"
                    isLoading = false
                    return@launch
                }
                isLoading = false
            }
            isPredicting = true
            status = "predicting\u2026"
            try {
                val t0 = System.currentTimeMillis()
                val raw = engine!!.predictNext(predictionHandle!!, text, k = 8)
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
                            if (isModelLoaded) predictNext(newText)
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
