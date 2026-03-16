package ai.octomil.app.chat

import ai.octomil.android.LocalAttachment
import ai.octomil.app.keyboard.PredictionState
import ai.octomil.app.voice.VoiceState
import ai.octomil.chat.ThreadMessage
import ai.octomil.responses.ContentPart
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val pendingAttachment by viewModel.pendingAttachment.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val recordingDurationMs by viewModel.recordingDurationMs.collectAsState()
    val transcribedText by viewModel.transcribedText.collectAsState()
    val predictionState by viewModel.predictionState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { viewModel.attachImage(it) }
    }

    // Camera requires runtime permission on Android 6+
    val launchCamera = {
        val photoFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", photoFile
        )
        cameraUri = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
    }

    // Audio permission for voice recording
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    // Auto-scroll on new messages, streaming text, or processing indicator
    val isGenerating = uiState is ChatViewModel.UiState.Generating
    LaunchedEffect(messages.size, streamingText, isGenerating) {
        val hasExtra = streamingText.isNotEmpty() || isGenerating
        val totalItems = messages.size + (if (hasExtra) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Chat",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                is ChatViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is ChatViewModel.UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Text(
                                "Something went wrong",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(onClick = onBack) { Text("Go back") }
                        }
                    }
                }

                else -> {
                    // Messages
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatBubble(message)
                        }
                        if (streamingText.isNotEmpty()) {
                            item(key = "streaming") {
                                ChatBubble(
                                    ThreadMessage(
                                        id = "msg_streaming",
                                        threadId = "",
                                        role = "assistant",
                                        content = streamingText,
                                        createdAt = "",
                                    ),
                                )
                            }
                        } else if (uiState is ChatViewModel.UiState.Generating) {
                            item(key = "processing") {
                                ProcessingIndicator(
                                    phase = (uiState as ChatViewModel.UiState.Generating).phase,
                                )
                            }
                        }
                    }

                    // Input
                    ChatInputBar(
                        isGenerating = uiState is ChatViewModel.UiState.Generating,
                        onSend = { viewModel.sendMessage(it) },
                        onCancel = { viewModel.cancelGeneration() },
                        pendingAttachment = pendingAttachment,
                        onAttachGallery = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onAttachCamera = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                launchCamera()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onClearAttachment = { viewModel.clearAttachment() },
                        // Voice
                        voiceState = voiceState,
                        recordingDurationMs = recordingDurationMs,
                        onMicTap = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.startRecording()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopRecording = { viewModel.stopAndTranscribe() },
                        transcribedText = transcribedText,
                        onConsumeTranscription = { viewModel.consumeTranscribedText() },
                        // Prediction
                        predictionState = predictionState,
                        onTextChanged = { viewModel.onTextChanged(it) },
                    )
                }
            }
        }
    }
}

// ── Bubbles ──

private val UserBubbleShape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
private val AssistantBubbleShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

@Composable
private fun ChatBubble(message: ThreadMessage) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = if (isUser) UserBubbleShape else AssistantBubbleShape,
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(2.dp)) {
                // Images
                message.contentParts?.filterIsInstance<ContentPart.Image>()?.forEach { imagePart ->
                    val imageData = imagePart.data
                    if (imageData != null) {
                        val bytes = Base64.decode(imageData, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(bitmap)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Attached image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }

                // Text
                val textContent = message.content
                if (!textContent.isNullOrBlank()) {
                    Text(
                        text = textContent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Metrics (subtle, below assistant bubble)
        val m = message.metrics
        if (!isUser && m != null) {
            Text(
                text = "${String.format("%.1f", m.decodeTokensPerSec)} tok/s · ${m.totalTokens} tok · ${m.ttftMs}ms TTFT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

// ── Processing Indicator ──

@Composable
private fun ProcessingIndicator(phase: String) {
    val transition = rememberInfiniteTransition(label = "processing")
    val dot0 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "dot0",
    )
    val dot1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "dot1",
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "dot2",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            shape = AssistantBubbleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
                    Box(Modifier.size(6.dp).background(dotColor.copy(alpha = dot0), CircleShape))
                    Box(Modifier.size(6.dp).background(dotColor.copy(alpha = dot1), CircleShape))
                    Box(Modifier.size(6.dp).background(dotColor.copy(alpha = dot2), CircleShape))
                }
                Text(
                    text = phase,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Input Bar ──

@Composable
private fun ChatInputBar(
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    pendingAttachment: LocalAttachment?,
    onAttachGallery: () -> Unit,
    onAttachCamera: () -> Unit,
    onClearAttachment: () -> Unit,
    // Voice
    voiceState: VoiceState,
    recordingDurationMs: Long,
    onMicTap: () -> Unit,
    onStopRecording: () -> Unit,
    transcribedText: String,
    onConsumeTranscription: () -> Unit,
    // Prediction
    predictionState: PredictionState,
    onTextChanged: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var showAttachOptions by remember { mutableStateOf(false) }
    val canSend = (text.isNotBlank() || pendingAttachment != null) && !isGenerating

    val isRecording = voiceState is VoiceState.Recording
    val isVoiceBusy = voiceState is VoiceState.LoadingModel || voiceState is VoiceState.Transcribing

    // Insert transcribed text into input field
    LaunchedEffect(transcribedText) {
        if (transcribedText.isNotBlank()) {
            text = if (text.isBlank()) transcribedText else "$text $transcribedText"
            onConsumeTranscription()
            onTextChanged(text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Voice state indicator (loading model / transcribing)
        AnimatedVisibility(
            visible = isVoiceBusy,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = when (voiceState) {
                        is VoiceState.LoadingModel -> "Loading voice model..."
                        is VoiceState.Transcribing -> "Transcribing..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Voice error
        if (voiceState is VoiceState.Error) {
            Text(
                text = voiceState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Prediction chips
        AnimatedVisibility(
            visible = predictionState is PredictionState.Ready,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            val suggestions = (predictionState as? PredictionState.Ready)?.suggestions ?: emptyList()
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions) { word ->
                    SuggestionChip(
                        onClick = {
                            // Append word with space
                            text = if (text.endsWith(" ") || text.isEmpty()) {
                                "$text$word "
                            } else {
                                "$text $word "
                            }
                            onTextChanged(text)
                        },
                        label = { Text(word) },
                    )
                }
            }
        }

        // Prediction loading indicator
        if (predictionState is PredictionState.Loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp),
            )
        }

        // Pending attachment preview
        AnimatedVisibility(
            visible = pendingAttachment != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            if (pendingAttachment != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        AsyncImage(
                            model = pendingAttachment.contentUri,
                            contentDescription = "Pending attachment",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        // Dismiss overlay
                        IconButton(
                            onClick = onClearAttachment,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    CircleShape,
                                ),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = pendingAttachment.displayName ?: "Photo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Attach options row
        AnimatedVisibility(
            visible = showAttachOptions,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AssistChip(
                    onClick = {
                        showAttachOptions = false
                        onAttachCamera()
                    },
                    label = { Text("Camera") },
                    leadingIcon = {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                AssistChip(
                    onClick = {
                        showAttachOptions = false
                        onAttachGallery()
                    },
                    label = { Text("Gallery") },
                    leadingIcon = {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }

        // Main input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Attach toggle
            IconButton(
                onClick = { showAttachOptions = !showAttachOptions },
                enabled = !isGenerating && !isRecording && !isVoiceBusy,
            ) {
                Icon(
                    if (showAttachOptions) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Attach",
                    tint = if (isGenerating || isRecording || isVoiceBusy) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (isRecording) {
                // Recording indicator replaces the text field
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // Pulsing red dot
                    val transition = rememberInfiniteTransition(label = "rec")
                    val alpha by transition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween<Float>(500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ), label = "rec_dot",
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color.Red.copy(alpha = alpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val secs = recordingDurationMs / 1000
                    val tenths = (recordingDurationMs % 1000) / 100
                    Text(
                        text = "Recording ${secs}.${tenths}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                // Text field
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onTextChanged(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Message",
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                onSend(text)
                                text = ""
                                showAttachOptions = false
                            }
                        },
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    maxLines = 4,
                    enabled = !isGenerating && !isVoiceBusy,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Mic / Stop recording / Send / Cancel
            if (isRecording) {
                // Stop recording button
                FilledIconButton(
                    onClick = onStopRecording,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop recording")
                }
            } else if (isGenerating) {
                FilledIconButton(
                    onClick = onCancel,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop generation")
                }
            } else if (canSend) {
                FilledIconButton(
                    onClick = {
                        onSend(text)
                        text = ""
                        showAttachOptions = false
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            } else {
                // Mic button (shown when text field is empty and not generating)
                IconButton(
                    onClick = onMicTap,
                    enabled = !isVoiceBusy,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = if (isVoiceBusy) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
