package ai.octomil.app.services

import android.util.Log
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
 */
class LocalPairingServer(
    private val onPair: (code: String, host: String?, modelName: String?) -> Unit,
    private val statusProvider: (() -> JSONObject)? = null,
    private val resetHandler: (() -> Unit)? = null,
) {
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

    private fun sendJson(writer: PrintWriter, code: Int, body: String) {
        val status = when (code) {
            200 -> "200 OK"
            400 -> "400 Bad Request"
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
