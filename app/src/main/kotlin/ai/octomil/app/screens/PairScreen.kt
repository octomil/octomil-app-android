package ai.octomil.app.screens

import ai.octomil.app.OctomilApplication
import ai.octomil.app.models.PairedModel
import ai.octomil.app.models.formatBytes
import ai.octomil.api.OctomilApiFactory
import ai.octomil.client.OctomilClient
import ai.octomil.config.OctomilConfig
import ai.octomil.pairing.ui.PairingScreen
import ai.octomil.pairing.ui.PairingState
import ai.octomil.pairing.ui.PairingViewModel
import ai.octomil.tryitout.TryItOutActivity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairScreen(
    initialCode: String? = null,
    host: String? = null,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val app = OctomilApplication.instance

    // Update server URL if host was provided via deep link
    LaunchedEffect(host) {
        if (!host.isNullOrBlank()) {
            val prefs = app.getSharedPreferences("octomil", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "") ?: ""
            val orgId = prefs.getString("org_id", "") ?: ""
            app.saveCredentials(apiKey, orgId, host)
        }
    }

    val code = initialCode ?: ""
    val serverHost = host ?: "https://api.octomil.com/api/v1"

    if (code.isNotBlank()) {
        val prefs = app.getSharedPreferences("octomil", android.content.Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val orgId = prefs.getString("org_id", "") ?: ""

        val config = OctomilConfig.Builder()
            .deviceAccessToken(apiKey)
            .orgId(orgId)
            .serverUrl(serverHost)
            .build()
        val api = OctomilApiFactory.create(config)

        val viewModel: PairingViewModel = viewModel(
            factory = PairingViewModel.Factory(
                api = api,
                context = context,
                token = code,
                host = serverHost,
            )
        )

        PairingScreen(
            viewModel = viewModel,
            onTryItOut = {
                val state = viewModel.state.value
                if (state is PairingState.Success) {
                    // Store the paired model
                    app.addPairedModel(
                        PairedModel(
                            name = state.modelName,
                            version = state.modelVersion,
                            sizeBytes = state.sizeBytes,
                            sizeString = formatBytes(state.sizeBytes),
                            runtime = state.runtime,
                            modality = state.modality,
                        )
                    )

                    val intent = TryItOutActivity.createIntent(
                        context = context,
                        modelName = state.modelName,
                        modelVersion = state.modelVersion,
                        sizeBytes = state.sizeBytes,
                        runtime = state.runtime,
                        modality = state.modality,
                    )
                    (context as? ComponentActivity)?.startActivity(intent)
                }
                onComplete()
            },
            onOpenDashboard = onComplete,
        )
    } else if (OctomilClient.isInitialized()) {
        // Ready to pair — matching iOS empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Ready to Pair",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    text = "Scan a QR code or run\noctomil deploy <model> --phone\nto pair.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        // Not configured — matching iOS empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Not Configured",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    text = "Set your device token in Settings before pairing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
