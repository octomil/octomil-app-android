package ai.octomil.app.chat

import ai.octomil.android.LocalAttachment
import ai.octomil.chat.ThreadMessage
import ai.octomil.responses.ContentPart
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
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
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }

    // Camera capture
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { viewModel.attachImage(it) }
    }

    // Auto-scroll on new messages or streaming text
    LaunchedEffect(messages.size, streamingText) {
        val totalItems = messages.size + (if (streamingText.isNotEmpty()) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
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
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
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
                                "Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onBack) { Text("Go Back") }
                        }
                    }
                }

                else -> {
                    // Chat messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(messages) { message ->
                            ChatBubble(message)
                        }
                        // Streaming response
                        if (streamingText.isNotEmpty()) {
                            item {
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
                        }
                    }

                    // Input bar
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
                            val photoFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        onClearAttachment = { viewModel.clearAttachment() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ThreadMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Render image content parts
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
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Text content
                val textContent = message.content
                if (!textContent.isNullOrBlank()) {
                    Text(
                        text = textContent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Metrics chip for completed assistant messages
        val m = message.metrics
        if (!isUser && m != null) {
            Text(
                text = "TTFT ${m.ttftMs}ms · ${String.format("%.1f", m.decodeTokensPerSec)} tok/s · ${m.totalTokens} tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    pendingAttachment: LocalAttachment? = null,
    onAttachGallery: () -> Unit = {},
    onAttachCamera: () -> Unit = {},
    onClearAttachment: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Column {
            // Pending attachment chip
            if (pendingAttachment != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = pendingAttachment.contentUri,
                        contentDescription = "Pending attachment",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pendingAttachment.displayName ?: "Image",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClearAttachment) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onAttachCamera,
                    enabled = !isGenerating,
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Take photo")
                }
                IconButton(
                    onClick = onAttachGallery,
                    enabled = !isGenerating,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Choose from gallery")
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if ((text.isNotBlank() || pendingAttachment != null) && !isGenerating) {
                                onSend(text)
                                text = ""
                            }
                        },
                    ),
                    singleLine = true,
                    enabled = !isGenerating,
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isGenerating) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank() || pendingAttachment != null) {
                                onSend(text)
                                text = ""
                            }
                        },
                        enabled = text.isNotBlank() || pendingAttachment != null,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
