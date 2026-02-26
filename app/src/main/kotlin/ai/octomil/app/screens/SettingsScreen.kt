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
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var orgId by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    var showSavedSnackbar by remember { mutableStateOf(false) }
    var showClearedSnackbar by remember { mutableStateOf(false) }

    LaunchedEffect(showSavedSnackbar) {
        if (showSavedSnackbar) {
            snackbarHostState.showSnackbar("Credentials saved")
            showSavedSnackbar = false
        }
    }
    LaunchedEffect(showClearedSnackbar) {
        if (showClearedSnackbar) {
            snackbarHostState.showSnackbar("Cache cleared")
            showClearedSnackbar = false
        }
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
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("edg_...") },
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
                    if (apiKey.isNotBlank() && orgId.isNotBlank()) {
                        app.saveCredentials(apiKey, orgId, serverUrl.ifBlank { null })
                        showSavedSnackbar = true
                    }
                },
                enabled = apiKey.isNotBlank() && orgId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save Credentials")
            }

            HorizontalDivider()

            // Device
            Text("Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Authenticated")
                Text(
                    text = if (app.client.isAuthenticated) "Yes" else "No",
                    fontWeight = FontWeight.Medium,
                    color = if (app.client.isAuthenticated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { scope.launch { try { app.client.register() } catch (_: Exception) {} } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Register Device")
            }

            OutlinedButton(
                onClick = { scope.launch { app.client.logout() } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Logout")
            }

            HorizontalDivider()

            // Cache
            Text("Cache", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            OutlinedButton(
                onClick = {
                    app.client.clearCache()
                    showClearedSnackbar = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
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
                    DeviceInfoRow("Manufacturer", android.os.Build.MANUFACTURER)
                    DeviceInfoRow("Model", android.os.Build.MODEL)
                    DeviceInfoRow("Android", android.os.Build.VERSION.RELEASE)
                    DeviceInfoRow("SDK", android.os.Build.VERSION.SDK_INT.toString())
                    DeviceInfoRow("Architecture", System.getProperty("os.arch") ?: "unknown")
                    DeviceInfoRow("SOC", android.os.Build.SOC_MODEL)
                    val localPort = app.localServer?.port ?: 0
                    if (localPort > 0) {
                        DeviceInfoRow("Local Server", "Port $localPort")
                    }
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
                    DeviceInfoRow("SDK Version", "1.0.0")
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
