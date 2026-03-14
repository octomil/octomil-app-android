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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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

            var isSaving by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (deviceToken.isNotBlank()) {
                        if (orgId.isNotBlank()) {
                            app.saveCredentials(deviceToken, orgId, serverUrl.ifBlank { null })
                            statusMessage = "Client reconfigured"
                        } else {
                            // Auto-fetch org ID from server using the API key
                            isSaving = true
                            scope.launch {
                                val fetchedOrgId = fetchOrgId(
                                    apiKey = deviceToken,
                                    serverUrl = serverUrl.ifBlank { "https://api.octomil.com/api/v1" },
                                )
                                isSaving = false
                                if (fetchedOrgId != null) {
                                    orgId = fetchedOrgId
                                    app.saveCredentials(deviceToken, fetchedOrgId, serverUrl.ifBlank { null })
                                    statusMessage = "Connected (org: $fetchedOrgId)"
                                } else {
                                    statusMessage = "Could not fetch org ID — enter it manually"
                                }
                            }
                        }
                    }
                },
                enabled = deviceToken.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Text("Save & Reconnect")
                }
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

/**
 * Fetch org_id from the server using the API key.
 * Calls GET /api/v1/me which returns the authenticated user's org.
 */
private suspend fun fetchOrgId(apiKey: String, serverUrl: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/me")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                // Parse org_id from JSON response
                val orgIdMatch = Regex(""""org_id"\s*:\s*"([^"]+)"""").find(body)
                orgIdMatch?.groupValues?.get(1)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
