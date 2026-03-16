package ai.octomil.app.screens

import android.util.Log
import ai.octomil.app.OctomilApplication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Connection section
            SettingsSectionHeader("Connection")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = deviceToken,
                        onValueChange = { deviceToken = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = settingsFieldColors(),
                    )

                    if (orgId.isNotBlank()) {
                        OutlinedTextField(
                            value = orgId,
                            onValueChange = {},
                            label = { Text("Organization") },
                            singleLine = true,
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = settingsFieldColors(),
                        )
                    }

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://api.octomil.com/api/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = settingsFieldColors(),
                    )

                    var isSaving by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            if (deviceToken.isNotBlank()) {
                                if (orgId.isNotBlank()) {
                                    app.saveCredentials(deviceToken, orgId, serverUrl.ifBlank { null })
                                    statusMessage = "Client reconfigured"
                                } else {
                                    isSaving = true
                                    scope.launch {
                                        val (fetchedOrgId, error) = fetchOrgId(
                                            apiKey = deviceToken,
                                            serverUrl = serverUrl.ifBlank { "https://api.octomil.com/api/v1" },
                                        )
                                        isSaving = false
                                        if (fetchedOrgId != null) {
                                            orgId = fetchedOrgId
                                            app.saveCredentials(deviceToken, fetchedOrgId, serverUrl.ifBlank { null })
                                            statusMessage = "Connected (org: $fetchedOrgId)"
                                        } else {
                                            statusMessage = error ?: "Could not fetch org ID"
                                        }
                                    }
                                }
                            }
                        },
                        enabled = deviceToken.isNotBlank() && !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting\u2026")
                        } else {
                            Text("Save & Connect")
                        }
                    }
                }
            }

            // Device section
            Spacer(modifier = Modifier.height(4.dp))
            SettingsSectionHeader("Device")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = {
                            deviceName = it
                            prefs.edit().putString("device_name", it).apply()
                        },
                        label = { Text("Device Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = settingsFieldColors(),
                    )
                }
            }

            // Storage section
            Spacer(modifier = Modifier.height(4.dp))
            SettingsSectionHeader("Storage")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    ) {
                        Text("Clear Model Cache")
                    }
                }
            }

            // About section
            Spacer(modifier = Modifier.height(4.dp))
            SettingsSectionHeader("About")

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
                    SettingsDetailRow("Version", "1.0.0")
                    SettingsDetailRow("Chip", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    SettingsDetailRow("Memory", "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
                    SettingsDetailRow("OS", "Android ${android.os.Build.VERSION.RELEASE}")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsDetailRow(label: String, value: String) {
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

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/**
 * Fetch org_id from the server using the API key.
 */
private suspend fun fetchOrgId(apiKey: String, serverUrl: String): Pair<String?, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/auth/me")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                Log.d("Settings", "/auth/me 200: $body")
                val orgIdMatch = Regex(""""org_id"\s*:\s*"([^"]+)"""").find(body)
                val orgId = orgIdMatch?.groupValues?.get(1)
                if (orgId != null) orgId to null
                else null to "Server returned 200 but no org_id in response"
            } else {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                Log.e("Settings", "/auth/me failed: HTTP $code \u2014 $errorBody")
                null to "HTTP $code: ${errorBody ?: conn.responseMessage}"
            }
        } catch (e: Exception) {
            Log.e("Settings", "/auth/me exception", e)
            null to "Connection failed: ${e.message}"
        }
    }
}
