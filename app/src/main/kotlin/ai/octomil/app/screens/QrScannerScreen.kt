package ai.octomil.app.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Full-screen QR code scanner using CameraX + ML Kit.
 *
 * Scans for QR codes containing octomil pairing URLs and extracts
 * the `code`/`token` and `host`/`server` parameters.
 */
@Composable
fun QrScannerScreen(
    onCodeScanned: (code: String, host: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Track whether we've already fired the callback to avoid duplicates
    var scanned by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // Camera preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val scanner = BarcodeScanning.getClient()
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { imageAnalysis ->
                                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                        processImage(imageProxy, scanner) { code, host ->
                                            if (!scanned) {
                                                scanned = true
                                                onCodeScanned(code, host)
                                            }
                                        }
                                    }
                                }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        } catch (e: Exception) {
                            Log.e("QrScanner", "Camera init failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Viewfinder overlay
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Semi-transparent overlay with cutout hint
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(50),
                    ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close scanner",
                    tint = Color.White,
                )
            }

            // Bottom hint
            Text(
                text = "Point at the QR code from\noctomil deploy <model> --phone",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            // Permission denied state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Camera permission is required to scan QR codes.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Access")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Process a camera frame for QR codes via ML Kit.
 *
 * Extracts `code`/`token` and `host`/`server` params from recognized
 * octomil pairing URLs (octomil://, https://octomil.com/pair, https://app.octomil.com/pair).
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onResult: (code: String, host: String?) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                if (barcode.valueType != Barcode.TYPE_URL && barcode.valueType != Barcode.TYPE_TEXT) continue
                val url = barcode.rawValue ?: continue
                val parsed = parsePairingUrl(url) ?: continue
                onResult(parsed.first, parsed.second)
                break
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
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
private fun parsePairingUrl(url: String): Pair<String, String?>? {
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
