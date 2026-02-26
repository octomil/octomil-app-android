package ai.octomil.app.screens

import ai.octomil.app.OctomilApplication
import ai.octomil.app.viewmodels.PairViewModel
import ai.octomil.ui.PairingScreen
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairScreen(
    initialCode: String? = null,
    host: String? = null,
    onComplete: () -> Unit,
    viewModel: PairViewModel = viewModel(),
) {
    val session by viewModel.session.collectAsState()

    // Update server URL if host was provided via deep link
    LaunchedEffect(host) {
        if (!host.isNullOrBlank()) {
            val app = OctomilApplication.instance
            val prefs = app.getSharedPreferences("octomil", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "") ?: ""
            val orgId = prefs.getString("org_id", "") ?: ""
            app.saveCredentials(apiKey, orgId, host)
        }
    }

    // Auto-start pairing if code was provided
    LaunchedEffect(initialCode) {
        if (!initialCode.isNullOrBlank()) {
            viewModel.startPairing(initialCode)
        }
    }

    PairingScreen(
        session = session,
        onCodeScanned = { code -> viewModel.startPairing(code) },
        onManualCode = { code -> viewModel.startPairing(code) },
        onRetry = { viewModel.reset() },
    )
}
