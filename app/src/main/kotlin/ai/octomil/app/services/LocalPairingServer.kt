package ai.octomil.app.services

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Lightweight HTTP server for receiving pairing codes from the CLI.
 *
 * Listens on a random port and accepts POST /pair requests:
 * ```json
 * {"code": "ABC123", "host": "https://api.octomil.com/api/v1", "model_name": "phi-4-mini"}
 * ```
 */
class LocalPairingServer(
    private val onPair: (code: String, host: String?, modelName: String?) -> Unit,
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

                            if (!requestLine.startsWith("POST /pair")) {
                                writer.print("HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found")
                                writer.flush()
                                socket.close()
                                return@thread
                            }

                            // Read body
                            val body = if (contentLength > 0) {
                                val buf = CharArray(contentLength)
                                reader.read(buf, 0, contentLength)
                                String(buf)
                            } else {
                                ""
                            }

                            try {
                                val json = JSONObject(body)
                                val code = json.getString("code")
                                val host = json.optString("host", null)
                                val modelName = json.optString("model_name", null)

                                onPair(code, host, modelName)

                                val response = """{"status":"ok"}"""
                                writer.print("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.length}\r\n\r\n$response")
                            } catch (e: Exception) {
                                val error = """{"error":"invalid request"}"""
                                writer.print("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Length: ${error.length}\r\n\r\n$error")
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

    companion object {
        private const val TAG = "LocalPairingServer"
    }
}
