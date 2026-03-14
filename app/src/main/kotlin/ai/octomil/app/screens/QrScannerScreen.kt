package ai.octomil.app.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.tasks.await

/**
 * QR code scanner using Google Code Scanner (Play Services).
 *
 * Bypasses CameraX entirely — handles camera preview and permissions
 * internally via Play Services. Works on all devices with Google Play.
 */
@Composable
fun QrScannerScreen(
    onCodeScanned: (code: String, host: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current as? android.app.Activity
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (context == null) {
            error = "Camera unavailable: Activity context required"
            return@LaunchedEffect
        }

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        // Ensure the scanner module is downloaded before attempting to scan.
        // Without this, getClient() can NPE if the module isn't available yet.
        val scanner = GmsBarcodeScanning.getClient(context, options)
        try {
            val moduleInstallClient = ModuleInstall.getClient(context)
            val installRequest = ModuleInstallRequest.newBuilder()
                .addApi(scanner)
                .build()
            moduleInstallClient.installModules(installRequest).await()
        } catch (e: Exception) {
            Log.w("QrScanner", "Module install check failed: ${e.message}")
            // Continue anyway — module may already be available
        }

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val url = barcode.rawValue
                Log.d("QrScanner", "Scanned: type=${barcode.valueType} raw='$url'")
                if (url != null) {
                    val parsed = parsePairingUrl(url)
                    Log.d("QrScanner", "Parsed: code=${parsed?.first} host=${parsed?.second}")
                    if (parsed != null) {
                        onCodeScanned(parsed.first, parsed.second)
                    } else {
                        error = "Not an Octomil pairing code: $url"
                    }
                } else {
                    error = "Empty QR code"
                }
            }
            .addOnCanceledListener {
                onDismiss()
            }
            .addOnFailureListener { e ->
                Log.e("QrScanner", "Scan failed", e)
                error = e.message ?: "Scanner failed"
            }
    }

    // Show status while scanner is open (it overlays its own UI)
    if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Text("Scan failed", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismiss) { Text("Go Back") }
            }
        }
    }
}

/**
 * Parse a pairing URL and extract (code, host?) pair.
 *
 * Supported formats (path-based preferred, query-based legacy):
 * - octomil://pair/CODE
 * - https://octomil.com/pair/CODE
 * - https://app.octomil.com/pair/CODE?host=...
 * - octomil://pair?code=X or octomil://pair?token=X&host=Y
 * - https://octomil.com/pair?token=X&host=Y
 */
internal fun parsePairingUrl(url: String): Pair<String, String?>? {
    val uri = try {
        android.net.Uri.parse(url)
    } catch (_: Exception) {
        return null
    }

    // Check scheme and path
    val isOctomilScheme = uri.scheme == "octomil" && uri.host == "pair"
    val isHttpsPairing = uri.scheme == "https" &&
        (uri.host == "octomil.com" || uri.host == "app.octomil.com") &&
        uri.path?.startsWith("/pair") == true

    if (!isOctomilScheme && !isHttpsPairing) return null

    // Try path-based format first: /pair/CODE
    val pathSegments = uri.pathSegments
    val pathCode = if (isOctomilScheme) {
        // octomil://pair/CODE → pathSegments = ["CODE"]
        pathSegments.firstOrNull()
    } else {
        // https://octomil.com/pair/CODE → pathSegments = ["pair", "CODE"]
        if (pathSegments.size >= 2 && pathSegments[0] == "pair") pathSegments[1] else null
    }

    // Fall back to query params: ?code=X or ?token=X
    val code = pathCode?.takeIf { it.isNotBlank() }
        ?: uri.getQueryParameter("code")
        ?: uri.getQueryParameter("token")
        ?: return null

    if (code.isBlank()) return null

    val host = uri.getQueryParameter("host")
        ?: uri.getQueryParameter("server")

    return code to host
}
