package ai.octomil.app.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Lightweight HTTP server for receiving pairing codes from the CLI
 * and exposing debug-only golden-path test endpoints.
 *
 * Routes:
 * - `POST /pair` — receive a pairing code from the CLI
 * - `GET /golden/status` — report current app state for test harness
 * - `POST /golden/reset` — clear credentials and cached models
 * - `POST /golden/test/all` — run all capability tests
 * - `POST /golden/test/chat` — run chat test
 * - `POST /golden/test/transcribe` — run transcription test
 * - `POST /golden/test/predict` — run prediction test
 */
class LocalPairingServer(
    private val onPair: (code: String, host: String?, modelName: String?) -> Unit,
    private val statusProvider: (() -> JSONObject)? = null,
    private val resetHandler: (() -> Unit)? = null,
    private val testHandler: GoldenTestRunner? = null,
) {
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var running = false

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun start() {
        serverSocket = ServerSocket(0) // OS picks an available port
        running = true
        Log.d(TAG, "Local pairing server started on port $port")

        thread(isDaemon = true, name = "local-pair-server") {
            while (running) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    thread(isDaemon = true) {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            val writer = PrintWriter(socket.getOutputStream(), true)

                            // Read request line
                            val requestLine = reader.readLine() ?: return@thread

                            // Read headers
                            var contentLength = 0
                            var line = reader.readLine()
                            while (!line.isNullOrEmpty()) {
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                                }
                                line = reader.readLine()
                            }

                            // Read body
                            val body = if (contentLength > 0) {
                                val buf = CharArray(contentLength)
                                reader.read(buf, 0, contentLength)
                                String(buf)
                            } else {
                                ""
                            }

                            when {
                                requestLine.startsWith("POST /pair") -> {
                                    try {
                                        val json = JSONObject(body)
                                        val code = json.getString("code")
                                        val host = json.optString("host", null)
                                        val modelName = json.optString("model_name", null)

                                        onPair(code, host, modelName)

                                        sendJson(writer, 200, """{"status":"ok"}""")
                                    } catch (e: Exception) {
                                        sendJson(writer, 400, """{"error":"invalid request"}""")
                                    }
                                }

                                requestLine.startsWith("GET /golden/status") -> {
                                    val provider = statusProvider
                                    if (provider != null) {
                                        sendJson(writer, 200, provider().toString())
                                    } else {
                                        sendJson(writer, 200, DEFAULT_STATUS.toString())
                                    }
                                }

                                requestLine.startsWith("POST /golden/reset") -> {
                                    val handler = resetHandler
                                    if (handler != null) {
                                        handler()
                                        sendJson(writer, 200, """{"status":"ok"}""")
                                    } else {
                                        sendJson(writer, 200, """{"status":"noop"}""")
                                    }
                                }

                                requestLine.startsWith("POST /golden/test/") -> {
                                    handleTestRoute(requestLine, body, writer)
                                }

                                else -> {
                                    writer.print("HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found")
                                }
                            }

                            writer.flush()
                            socket.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling connection", e)
                        }
                    }
                } catch (e: Exception) {
                    if (running) {
                        Log.e(TAG, "Server accept error", e)
                    }
                }
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    /**
     * Handle POST /golden/test/<route> — dispatches to the test runner.
     * Since tests are async (coroutines), we block the socket thread with runBlocking-style
     * approach via a CountDownLatch to keep the HTTP response synchronous.
     */
    private fun handleTestRoute(requestLine: String, body: String, writer: PrintWriter) {
        val runner = testHandler
        if (runner == null) {
            sendJson(writer, 501, """{"error":"test handler not configured"}""")
            return
        }

        // Extract route: POST /golden/test/<route> HTTP/1.1
        val route = requestLine
            .removePrefix("POST /golden/test/")
            .substringBefore(" ")
            .substringBefore("?")

        val bodyJson = if (body.isNotBlank()) {
            try { JSONObject(body) } catch (_: Exception) { null }
        } else null

        // Block the socket thread until the coroutine completes
        val latch = java.util.concurrent.CountDownLatch(1)
        var resultJson: JSONObject? = null

        testScope.launch {
            resultJson = when (route) {
                "all" -> runner.runAll()

                "chat" -> {
                    val model = resolveModel(bodyJson, ModelCapability.CHAT)
                    if (model == null) {
                        JSONObject().apply { put("error", "no chat model available") }
                    } else {
                        val prompt = bodyJson?.optString("prompt", "What is 2+2? Answer in one word.") ?: "What is 2+2? Answer in one word."
                        val maxTokens = bodyJson?.optInt("max_tokens", 32) ?: 32
                        runner.runChat(model, prompt, maxTokens)
                    }
                }

                "transcribe" -> {
                    val model = resolveModel(bodyJson, ModelCapability.TRANSCRIPTION)
                    if (model == null) {
                        JSONObject().apply { put("error", "no transcription model available") }
                    } else {
                        val fixture = bodyJson?.optString("fixture", "hello") ?: "hello"
                        val mode = bodyJson?.optString("mode", "speech_service") ?: "speech_service"
                        runner.runTranscribe(model, fixture, mode)
                    }
                }

                "predict" -> {
                    val cap = ModelCapability.KEYBOARD_PREDICTION
                    val model = resolveModel(bodyJson, cap)
                        ?: resolveModel(bodyJson, ModelCapability.TEXT_COMPLETION)
                    if (model == null) {
                        JSONObject().apply { put("error", "no prediction model available") }
                    } else {
                        val prefix = bodyJson?.optString("prefix", "The weather today is") ?: "The weather today is"
                        val n = bodyJson?.optInt("n", 3) ?: 3
                        runner.runPredict(model, prefix, n)
                    }
                }

                else -> JSONObject().apply { put("error", "unknown test route: $route") }
            }
            latch.countDown()
        }

        // Wait up to 60s for the test to complete
        if (!latch.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
            sendJson(writer, 504, """{"error":"test timed out"}""")
            return
        }

        sendJson(writer, 200, (resultJson ?: JSONObject().apply { put("error", "null result") }).toString())
    }

    private fun resolveModel(body: JSONObject?, capability: ModelCapability): PairedModel? {
        val models = testHandler?.getModels() ?: return null
        val explicitName = body?.optString("model", null)
        if (explicitName != null) {
            return models.firstOrNull { it.name == explicitName }
        }
        return models.firstOrNull { capability in it.capabilities }
    }

    private fun sendJson(writer: PrintWriter, code: Int, body: String) {
        val status = when (code) {
            200 -> "200 OK"
            400 -> "400 Bad Request"
            501 -> "501 Not Implemented"
            504 -> "504 Gateway Timeout"
            else -> "$code"
        }
        writer.print("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body")
    }

    companion object {
        private const val TAG = "LocalPairingServer"

        private val DEFAULT_STATUS = JSONObject().apply {
            put("phase", "idle")
            put("paired", false)
            put("device_registered", false)
            put("model_downloaded", false)
            put("model_activated", false)
            put("active_model", JSONObject.NULL)
            put("active_version", JSONObject.NULL)
            put("model_count", 0)
            put("last_error", JSONObject.NULL)
        }
    }
}
