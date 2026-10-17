package ai.octomil.app.services

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Lightweight HTTP server for receiving pairing codes from the CLI
 * and exposing debug-only golden-path test endpoints.
 *
 * Routes:
 * - `POST /pair` — receive a pairing code from the CLI (requires `X-Octomil-Pin`)
 * - `GET /golden/status` — report current app state for test harness
 * - `POST /golden/reset` — clear credentials and cached models (requires `X-Octomil-Pin`)
 */
class LocalPairingServer(
    private val onPair: (code: String, host: String?, modelName: String?) -> Unit,
    private val statusProvider: (() -> JSONObject)? = null,
    private val resetHandler: (() -> Unit)? = null,
) {
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false

    /** Per-session PIN the user reads off the device and gives to the sender. */
    val pin: String = "%06d".format(SecureRandom().nextInt(1_000_000))

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
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    thread(isDaemon = true) {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            val writer = PrintWriter(socket.getOutputStream(), true)

                            // Read request line
                            val requestLine = reader.readLine() ?: return@thread

                            // Read headers
                            var contentLength = 0
                            var suppliedPin: String? = null
                            var headerCount = 0
                            var line = reader.readLine()
                            while (!line.isNullOrEmpty()) {
                                if (++headerCount > MAX_HEADERS) {
                                    reject(socket, writer, 400, """{"error":"invalid request"}""")
                                    return@thread
                                }
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                                } else if (line.startsWith("X-Octomil-Pin:", ignoreCase = true)) {
                                    suppliedPin = line.substringAfter(":").trim()
                                }
                                line = reader.readLine()
                            }

                            if (contentLength < 0 || contentLength > MAX_BODY_CHARS) {
                                reject(socket, writer, 413, """{"error":"payload too large"}""")
                                return@thread
                            }

                            val needsPin = requestLine.startsWith("POST /pair") ||
                                requestLine.startsWith("POST /golden/reset")
                            if (needsPin && !pinMatches(suppliedPin)) {
                                reject(socket, writer, 401, """{"error":"unauthorized"}""")
                                return@thread
                            }

                            // Read body
                            val body = if (contentLength > 0) {
                                val buf = CharArray(contentLength)
                                var read = 0
                                while (read < contentLength) {
                                    val n = reader.read(buf, read, contentLength - read)
                                    if (n < 0) break
                                    read += n
                                }
                                String(buf, 0, read)
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

    private fun pinMatches(supplied: String?): Boolean =
        supplied != null &&
            MessageDigest.isEqual(supplied.toByteArray(), pin.toByteArray())

    private fun reject(socket: java.net.Socket, writer: PrintWriter, code: Int, body: String) {
        sendJson(writer, code, body)
        writer.flush()
        socket.close()
    }

    private fun sendJson(writer: PrintWriter, code: Int, body: String) {
        val status = when (code) {
            200 -> "200 OK"
            400 -> "400 Bad Request"
            401 -> "401 Unauthorized"
            413 -> "413 Payload Too Large"
            else -> "$code"
        }
        writer.print("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body")
    }

    companion object {
        private const val TAG = "LocalPairingServer"
        private const val SOCKET_TIMEOUT_MS = 10_000
        private const val MAX_BODY_CHARS = 8 * 1024
        private const val MAX_HEADERS = 64

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
