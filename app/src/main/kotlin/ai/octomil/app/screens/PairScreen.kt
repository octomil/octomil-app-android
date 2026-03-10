package ai.octomil.app.screens

import ai.octomil.app.OctomilApplication
import ai.octomil.api.OctomilApiFactory
import ai.octomil.config.OctomilConfig
import ai.octomil.pairing.ui.PairingScreen
import ai.octomil.pairing.ui.PairingState
import ai.octomil.pairing.ui.PairingViewModel
import ai.octomil.tryitout.TryItOutActivity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairScreen(
    initialCode: String? = null,
    host: String? = null,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current

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

    val code = initialCode ?: ""
    val serverHost = host ?: "https://api.octomil.com/api/v1"

    if (code.isNotBlank()) {
        val prefs = OctomilApplication.instance
            .getSharedPreferences("octomil", android.content.Context.MODE_PRIVATE)
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
    }
}
