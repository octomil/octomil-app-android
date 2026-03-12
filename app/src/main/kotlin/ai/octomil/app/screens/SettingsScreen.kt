package ai.octomil.app.screens

import ai.octomil.app.OctomilApplication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val app = OctomilApplication.instance
    val prefs = app.getSharedPreferences("octomil", android.content.Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    var deviceToken by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var orgId by remember { mutableStateOf(prefs.getString("org_id", "") ?: "") }
    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", "https://api.octomil.com/api/v1") ?: "") }
    var deviceName by remember { mutableStateOf(prefs.getString("device_name", "") ?: "") }

    val snackbarHostState = remember { SnackbarHostState() }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            snackbarHostState.showSnackbar(statusMessage!!)
            statusMessage = null
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?") },
            text = { Text("This will remove all downloaded models.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        scope.launch {
                            try {
                                app.client?.clearCache()
                                app.clearPairedModels()
                                statusMessage = "Cache cleared"
                            } catch (e: Exception) {
                                statusMessage = "Failed: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // API Configuration
            Text("API Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = deviceToken,
                onValueChange = { deviceToken = it },
                label = { Text("Device Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = orgId,
                onValueChange = { orgId = it },
                label = { Text("Organization ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://api.octomil.com/api/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Button(
                onClick = {
                    if (deviceToken.isNotBlank() && orgId.isNotBlank()) {
                        app.saveCredentials(deviceToken, orgId, serverUrl.ifBlank { null })
                        statusMessage = "Client reconfigured"
                    }
                },
                enabled = deviceToken.isNotBlank() && orgId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save & Reconnect")
            }

            HorizontalDivider()

            // Device
            Text("Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = deviceName,
                onValueChange = {
                    deviceName = it
                    prefs.edit().putString("device_name", it).apply()
                },
                label = { Text("Device Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            HorizontalDivider()

            // Cache
            Text("Cache", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            OutlinedButton(
                onClick = { showClearCacheDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Clear Model Cache")
            }

            HorizontalDivider()

            // Device Info
            Text("Device Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeviceInfoRow("Chip", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    DeviceInfoRow("RAM", "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
                    DeviceInfoRow("OS", "Android ${android.os.Build.VERSION.RELEASE}")
                }
            }

            // About
            HorizontalDivider()
            Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeviceInfoRow("App Version", "1.0.0")
                    DeviceInfoRow("Platform", "Android")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
