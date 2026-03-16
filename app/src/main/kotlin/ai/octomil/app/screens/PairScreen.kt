package ai.octomil.app.screens

import android.util.Log
import ai.octomil.app.OctomilApplication
import ai.octomil.app.models.PairedModel
import ai.octomil.app.models.formatBytes
import ai.octomil.api.OctomilApiFactory
import ai.octomil.config.AuthConfig
import ai.octomil.config.OctomilConfig
import ai.octomil.pairing.ui.PairingScreen
import ai.octomil.pairing.ui.PairingState
import ai.octomil.pairing.ui.PairingViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairScreen(
    initialCode: String? = null,
    host: String? = null,
    onComplete: () -> Unit,
    onNavigateToChat: (modelName: String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = OctomilApplication.instance

    // Persist server URL if host was provided via deep link (only when creds exist)
    LaunchedEffect(host) {
        if (!host.isNullOrBlank()) {
            val prefs = app.getSharedPreferences("octomil", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "") ?: ""
            val orgId = prefs.getString("org_id", "") ?: ""
            if (apiKey.isNotBlank() && orgId.isNotBlank()) {
                app.saveCredentials(apiKey, orgId, host)
            } else {
                prefs.edit().putString("server_url", host).apply()
            }
        }
    }

    var activeCode by rememberSaveable { mutableStateOf(initialCode ?: "") }
    var activeHost by rememberSaveable { mutableStateOf(host ?: "https://api.octomil.com") }
    var showScanner by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialCode, host) {
        if (!initialCode.isNullOrBlank()) activeCode = initialCode
        if (!host.isNullOrBlank()) activeHost = host
    }

    Log.d("PairScreen", "State: activeCode='$activeCode' activeHost='$activeHost' showScanner=$showScanner initialCode=$initialCode host=$host")

    if (showScanner) {
        QrScannerScreen(
            onCodeScanned = { code, scannedHost ->
                Log.d("PairScreen", "QR scanned: code='$code' host=$scannedHost")
                activeCode = code
                if (!scannedHost.isNullOrBlank()) activeHost = scannedHost
                showScanner = false
            },
            onDismiss = { showScanner = false },
        )
    } else if (activeCode.isNotBlank()) {
        Log.d("PairScreen", "Creating OctomilConfig for pairing: code='$activeCode' host='$activeHost'")
        val config = try {
            OctomilConfig.Builder()
                .auth(AuthConfig.OrgApiKey(
                    apiKey = activeCode,
                    orgId = "pairing",
                    serverUrl = activeHost,
                ))
                .modelId("pairing")
                .build()
        } catch (e: Exception) {
            Log.e("PairScreen", "OctomilConfig.build() failed", e)
            null
        }
        val api = if (config != null) OctomilApiFactory.create(config) else null

        if (api == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        "Failed to initialize pairing",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Code: $activeCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { activeCode = "" },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Try Again")
                    }
                }
            }
        } else {
            val viewModel: PairingViewModel = viewModel(
                factory = PairingViewModel.Factory(
                    api = api,
                    context = context,
                    token = activeCode,
                    host = activeHost,
                ),
            )

            val currentState by viewModel.state.collectAsState()
            LaunchedEffect(currentState) {
                val s = currentState
                if (s is PairingState.Success) {
                    app.addPairedModel(
                        PairedModel(
                            name = s.modelName,
                            version = s.modelVersion,
                            sizeBytes = s.sizeBytes,
                            sizeString = formatBytes(s.sizeBytes),
                            runtime = s.runtime,
                            modality = s.modality,
                        ),
                    )
                }
            }

            PairingScreen(
                viewModel = viewModel,
                onTryItOut = {
                    val state = viewModel.state.value
                    if (state is PairingState.Success) {
                        onNavigateToChat(state.modelName)
                    } else {
                        onComplete()
                    }
                },
                onOpenDashboard = onComplete,
            )
        }
    } else {
        // Empty state — scan prompt
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(40.dp),
                ) {
                    // Icon container
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Ready to Pair",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Scan the QR code from the CLI\nor deploy a model with --phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showScanner = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR Code")
                    }
                }
            }
        }
    }
}
