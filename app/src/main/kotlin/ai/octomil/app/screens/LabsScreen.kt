package ai.octomil.app.screens

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.octomil.app.keyboard.TokenSuggestionFilter
import ai.octomil.app.voice.AudioRecorder
import ai.octomil.app.voice.WhisperRuntime
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "LabsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Labs") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
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
            // Start recording after permission granted
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Whisper Transcription",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // Load button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        status = "loading whisper-base..."
                        try {
                            val t0 = System.currentTimeMillis()
                            val modelsDir = context.filesDir.resolve("octomil_models/whisper-base/1.0.0")
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
                enabled = whisperRuntime == null && !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Load whisper-base")
            }

            // Record button
            Button(
                onClick = {
                    if (isRecording) {
                        // Stop early
                        capturedSamples = recorder.stopRecording()
                        isRecording = false
                        status = "recorded ${capturedSamples!!.size} samples (${durationMs}ms)"
                    } else {
                        // Request permission and start
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = whisperRuntime != null && !isTranscribing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (isRecording) "Stop (${durationMs}ms)" else "Record 3 sec",
                )
            }

            // Transcribe button
            Button(
                onClick = {
                    scope.launch {
                        isTranscribing = true
                        status = "transcribing..."
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
                },
                enabled = capturedSamples != null && whisperRuntime != null && !isTranscribing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isTranscribing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Transcribe")
            }

            // Status
            Text(
                "Status: $status",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Transcription result
            if (transcriptionResult.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        transcriptionResult,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Next-Token Prediction",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Input text") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // Load button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        status = "loading smollm2-135m..."
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
                enabled = predictionHandle == null && !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Load smollm2-135m")
            }

            // Predict button
            Button(
                onClick = {
                    scope.launch {
                        isPredicting = true
                        status = "predicting..."
                        try {
                            val t0 = System.currentTimeMillis()
                            val raw = engine!!.predictNext(predictionHandle!!, inputText, k = 8)
                            val elapsed = System.currentTimeMillis() - t0
                            suggestions = TokenSuggestionFilter.process(raw)
                            status = "predicted (${elapsed}ms), ${raw.size} raw tokens → ${suggestions.size} suggestions"
                        } catch (e: Exception) {
                            Log.e(TAG, "Prediction failed", e)
                            status = "prediction failed: ${e.message}"
                        } finally {
                            isPredicting = false
                        }
                    }
                },
                enabled = predictionHandle != null && inputText.isNotBlank() && !isPredicting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isPredicting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Predict next")
            }

            // Suggestion chips
            if (suggestions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        AssistChip(
                            onClick = {
                                inputText = if (inputText.endsWith(" ")) {
                                    "$inputText$suggestion"
                                } else {
                                    "$inputText $suggestion"
                                }
                                suggestions = emptyList()
                            },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }

            // Unload button
            OutlinedButton(
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
                enabled = predictionHandle != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Unload")
            }

            // Status
            Text(
                "Status: $status",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
