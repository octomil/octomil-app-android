package ai.octomil.app.screens

import ai.octomil.app.OctomilApplication
import ai.octomil.app.ui.OctomilColors
import ai.octomil.client.OctomilClient
import ai.octomil.errors.OctomilErrorCode
import ai.octomil.errors.OctomilException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onModelClick: (modelId: String) -> Unit,
) {
    val app = OctomilApplication.instance
    val isInitialized = OctomilClient.isInitialized()
    val localPort = app.localServer?.port ?: 0
    val prefs = app.getSharedPreferences("octomil", android.content.Context.MODE_PRIVATE)
    val apiKey = prefs.getString("api_key", "") ?: ""
    val deviceName = prefs.getString("device_name", "") ?: ""

    val scope = rememberCoroutineScope()
    var isRegistering by remember { mutableStateOf(false) }
    var isRegistered by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
    ) {
        // Header
        item {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(
                    text = "octomil",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1).sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "on-device runtime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Status card
        item {
            StatusCard(
                isInitialized = isInitialized,
                isRegistered = isRegistered,
                isRegistering = isRegistering,
                errorMessage = errorMessage,
                localPort = localPort,
                deviceName = deviceName,
                onRegister = {
                    isRegistering = true
                    errorMessage = null
                    scope.launch {
                        try {
                            app.client?.initialize()
                                ?: throw OctomilException(
                                    OctomilErrorCode.DEVICE_NOT_REGISTERED,
                                    "Not configured \u2014 pair your device first",
                                )
                            isRegistered = true
                        } catch (e: Exception) {
                            errorMessage = e.message
                        }
                        isRegistering = false
                    }
                },
            )
        }

        // Models section
        item {
            SectionHeader("Models")
        }

        if (app.pairedModels.isEmpty()) {
            item {
                EmptyModelsCard()
            }
        } else {
            items(app.pairedModels, key = { it.name }) { model ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = ButtonDefaults.outlinedButtonBorder(enabled = false).copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (model.isChatModel) {
                                    Modifier.clickable { onModelClick(model.name) }
                                } else Modifier,
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Model icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SmallChip(text = "v${model.version}")
                                SmallChip(text = model.runtime)
                                model.capabilities.forEach { cap ->
                                    SmallChip(text = cap.code)
                                }
                            }
                        }

                        if (model.isChatModel) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Device section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader("Device")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailRow(
                        label = "Model",
                        value = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    )
                    DetailRow(
                        label = "Architecture",
                        value = System.getProperty("os.arch") ?: "unknown",
                    )
                    DetailRow(
                        label = "OS",
                        value = "Android ${android.os.Build.VERSION.RELEASE}",
                    )
                    DetailRow(
                        label = "Memory",
                        value = "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB",
                    )
                }
            }
        }
    }
}

// ── Components ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun StatusCard(
    isInitialized: Boolean,
    isRegistered: Boolean,
    isRegistering: Boolean,
    errorMessage: String?,
    localPort: Int,
    deviceName: String,
    onRegister: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = ButtonDefaults.outlinedButtonBorder(enabled = false).copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRegistered) OctomilColors.Emerald400
                            else if (isInitialized) OctomilColors.Amber400
                            else MaterialTheme.colorScheme.outline,
                        ),
                )
                Text(
                    text = if (isRegistered) "Connected"
                    else if (isInitialized) "Ready to register"
                    else "Not configured",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Device name + port
            if (deviceName.isNotBlank() || localPort > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (deviceName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = deviceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (localPort > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = ":$localPort",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Register button
            if (isInitialized && !isRegistered) {
                Button(
                    onClick = onRegister,
                    enabled = !isRegistering,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registering\u2026")
                    } else {
                        Text("Register Device")
                    }
                }
            }

            // Not configured hint
            if (!isInitialized) {
                Text(
                    text = "Pair your device or add credentials in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Error
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyModelsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = ButtonDefaults.outlinedButtonBorder(enabled = false).copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Memory,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No models deployed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "octomil deploy <model> --phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

