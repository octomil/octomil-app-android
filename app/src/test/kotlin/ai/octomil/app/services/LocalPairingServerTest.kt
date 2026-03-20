package ai.octomil.app.services

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalPairingServerTest {

    private var server: LocalPairingServer? = null
    private val receivedCode = AtomicReference<String?>(null)
    private val receivedHost = AtomicReference<String?>(null)
    private val receivedModelName = AtomicReference<String?>(null)

    @Before
    fun setUp() {
        receivedCode.set(null)
        receivedHost.set(null)
        receivedModelName.set(null)
    }

    @After
    fun tearDown() {
        server?.stop()
        server = null
    }

    private fun createServer(latch: CountDownLatch? = null): LocalPairingServer {
        val s = LocalPairingServer { code, host, modelName ->
            receivedCode.set(code)
            receivedHost.set(host)
            receivedModelName.set(modelName)
            latch?.countDown()
        }
        server = s
        return s
    }

    /**
     * Send a raw HTTP request to the server and return the response status line + body.
     */
    private fun sendRequest(port: Int, method: String, path: String, body: String? = null): Pair<String, String> {
        val socket = Socket("127.0.0.1", port)
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        val request = StringBuilder()
        request.append("$method $path HTTP/1.1\r\n")
        request.append("Host: 127.0.0.1:$port\r\n")
        if (body != null) {
            request.append("Content-Type: application/json\r\n")
            request.append("Content-Length: ${body.length}\r\n")
        }
        request.append("\r\n")
        if (body != null) {
            request.append(body)
        }

        writer.print(request.toString())
        writer.flush()

        val statusLine = reader.readLine() ?: ""
        // Read headers until empty line
        val headers = mutableListOf<String>()
        var line = reader.readLine()
        while (!line.isNullOrEmpty()) {
            headers.add(line)
            line = reader.readLine()
        }

        // Read body based on Content-Length
        val contentLength = headers
            .find { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

        val responseBody = if (contentLength > 0) {
            val buf = CharArray(contentLength)
            reader.read(buf, 0, contentLength)
            String(buf)
        } else {
            ""
        }

        socket.close()
        return statusLine to responseBody
    }

    // =========================================================================
    // Server lifecycle
    // =========================================================================

    @Test
    fun `start assigns a valid port`() {
        val s = createServer()
        s.start()

        assertTrue("Port should be > 0 after start", s.port > 0)
        assertTrue("Port should be in valid range", s.port in 1..65535)
    }

    @Test
    fun `port is 0 before start`() {
        val s = createServer()
        assertEquals("Port should be 0 before start", 0, s.port)
    }

    @Test
    fun `stop resets port to 0`() {
        val s = createServer()
        s.start()
        assertTrue(s.port > 0)

        s.stop()
        assertEquals("Port should be 0 after stop", 0, s.port)
    }

    @Test
    fun `multiple start-stop cycles work`() {
        val s = createServer()

        s.start()
        val port1 = s.port
        assertTrue(port1 > 0)
        s.stop()
        assertEquals(0, s.port)

        s.start()
        val port2 = s.port
        assertTrue(port2 > 0)
        s.stop()
        assertEquals(0, s.port)
    }

    // =========================================================================
    // POST /pair — successful pairing
    // =========================================================================

    @Test
    fun `POST pair with code invokes callback and returns 200`() {
        val latch = CountDownLatch(1)
        val s = createServer(latch)
        s.start()

        val body = """{"code":"ABC123"}"""
        val (status, responseBody) = sendRequest(s.port, "POST", "/pair", body)

        assertTrue("Should wait for callback", latch.await(5, TimeUnit.SECONDS))
        assertTrue("Status should be 200", status.contains("200"))
        assertEquals("ABC123", receivedCode.get())

        val json = JSONObject(responseBody)
        assertEquals("ok", json.getString("status"))
    }

    @Test
    fun `POST pair with code and host passes host to callback`() {
        val latch = CountDownLatch(1)
        val s = createServer(latch)
        s.start()

        val body = """{"code":"XYZ789","host":"https://api.octomil.com/api/v1"}"""
        val (status, _) = sendRequest(s.port, "POST", "/pair", body)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(status.contains("200"))
        assertEquals("XYZ789", receivedCode.get())
        assertEquals("https://api.octomil.com/api/v1", receivedHost.get())
    }

    @Test
    fun `POST pair with code host and model_name passes all fields to callback`() {
        val latch = CountDownLatch(1)
        val s = createServer(latch)
        s.start()

        val body = """{"code":"CODE1","host":"https://custom.server.com","model_name":"phi-4-mini"}"""
        val (status, _) = sendRequest(s.port, "POST", "/pair", body)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(status.contains("200"))
        assertEquals("CODE1", receivedCode.get())
        assertEquals("https://custom.server.com", receivedHost.get())
        assertEquals("phi-4-mini", receivedModelName.get())
    }

    @Test
    fun `POST pair with only code leaves host and modelName as null`() {
        val latch = CountDownLatch(1)
        val s = createServer(latch)
        s.start()

        val body = """{"code":"ONLY_CODE"}"""
        sendRequest(s.port, "POST", "/pair", body)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals("ONLY_CODE", receivedCode.get())
        // JSONObject.optString returns null when key is absent
        // (the server uses optString with null default)
        assertNull(receivedHost.get())
        assertNull(receivedModelName.get())
    }

    // =========================================================================
    // Error cases
    // =========================================================================

    @Test
    fun `GET pair returns 404`() {
        val s = createServer()
        s.start()

        val (status, responseBody) = sendRequest(s.port, "GET", "/pair")

        assertTrue("Should return 404 for GET", status.contains("404"))
        assertEquals("Not Found", responseBody)
    }

    @Test
    fun `POST to unknown path returns 404`() {
        val s = createServer()
        s.start()

        val (status, responseBody) = sendRequest(s.port, "POST", "/unknown")

        assertTrue("Should return 404 for unknown path", status.contains("404"))
        assertEquals("Not Found", responseBody)
    }

    @Test
    fun `GET root returns 404`() {
        val s = createServer()
        s.start()

        val (status, _) = sendRequest(s.port, "GET", "/")

        assertTrue("Should return 404 for GET /", status.contains("404"))
    }

    @Test
    fun `POST pair with invalid JSON returns 400`() {
        val s = createServer()
        s.start()

        val body = "this is not json"
        val (status, responseBody) = sendRequest(s.port, "POST", "/pair", body)

        assertTrue("Should return 400 for invalid JSON", status.contains("400"))
        val json = JSONObject(responseBody)
        assertEquals("invalid request", json.getString("error"))
    }

    @Test
    fun `POST pair with empty body returns 400`() {
        val s = createServer()
        s.start()

        val (status, responseBody) = sendRequest(s.port, "POST", "/pair", "")

        assertTrue("Should return 400 for empty body", status.contains("400"))
        val json = JSONObject(responseBody)
        assertEquals("invalid request", json.getString("error"))
    }

    @Test
    fun `POST pair with JSON missing code field returns 400`() {
        val s = createServer()
        s.start()

        val body = """{"host":"https://api.octomil.com"}"""
        val (status, responseBody) = sendRequest(s.port, "POST", "/pair", body)

        assertTrue("Should return 400 when code is missing", status.contains("400"))
        val json = JSONObject(responseBody)
        assertEquals("invalid request", json.getString("error"))
    }

    // =========================================================================
    // Concurrent connections
    // =========================================================================

    @Test
    fun `server handles multiple sequential requests`() {
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        val s = LocalPairingServer { _, _, _ ->
            callCount.incrementAndGet()
        }
        server = s
        s.start()

        for (i in 1..5) {
            val body = """{"code":"SEQ_$i"}"""
            val (status, _) = sendRequest(s.port, "POST", "/pair", body)
            assertTrue(status.contains("200"))
        }

        // Allow a brief moment for all callbacks to execute
        Thread.sleep(200)
        assertEquals("All 5 requests should invoke callback", 5, callCount.get())
    }

    // =========================================================================
    // Golden harness routes
    // =========================================================================

    @Test
    fun `GET golden status returns 200 with default status when no provider`() {
        val s = createServer()
        s.start()

        val (status, responseBody) = sendRequest(s.port, "GET", "/golden/status")

        assertTrue("Should return 200", status.contains("200"))
        val json = JSONObject(responseBody)
        assertEquals("idle", json.getString("phase"))
        assertEquals(false, json.getBoolean("paired"))
        assertEquals(false, json.getBoolean("device_registered"))
        assertEquals(false, json.getBoolean("model_downloaded"))
        assertEquals(false, json.getBoolean("model_activated"))
        assertEquals(0, json.getInt("model_count"))
        assertTrue("active_model should be null", json.isNull("active_model"))
        assertTrue("active_version should be null", json.isNull("active_version"))
        assertTrue("last_error should be null", json.isNull("last_error"))
    }

    @Test
    fun `GET golden status returns 200 with custom provider status`() {
        val customStatus = JSONObject().apply {
            put("phase", "active")
            put("paired", true)
            put("device_registered", true)
            put("model_downloaded", true)
            put("model_activated", true)
            put("active_model", "phi-4-mini")
            put("active_version", "1.0.0")
            put("model_count", 1)
            put("last_error", JSONObject.NULL)
        }
        val s = LocalPairingServer(
            onPair = { _, _, _ -> },
            statusProvider = { customStatus },
        )
        server = s
        s.start()

        val (status, responseBody) = sendRequest(s.port, "GET", "/golden/status")

        assertTrue("Should return 200", status.contains("200"))
        val json = JSONObject(responseBody)
        assertEquals("active", json.getString("phase"))
        assertEquals(true, json.getBoolean("paired"))
        assertEquals("phi-4-mini", json.getString("active_model"))
        assertEquals("1.0.0", json.getString("active_version"))
        assertEquals(1, json.getInt("model_count"))
    }

    @Test
    fun `POST golden reset invokes reset handler and returns 200`() {
        val resetCalled = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val s = LocalPairingServer(
            onPair = { _, _, _ -> },
            resetHandler = {
                resetCalled.set(true)
                latch.countDown()
            },
        )
        server = s
        s.start()

        val (status, responseBody) = sendRequest(s.port, "POST", "/golden/reset")

        assertTrue("Should wait for reset", latch.await(5, TimeUnit.SECONDS))
        assertTrue("Should return 200", status.contains("200"))
        assertTrue("Reset handler should have been called", resetCalled.get())

        val json = JSONObject(responseBody)
        assertEquals("ok", json.getString("status"))
    }

    @Test
    fun `POST golden reset without handler returns noop`() {
        val s = createServer()
        s.start()

        val (status, responseBody) = sendRequest(s.port, "POST", "/golden/reset")

        assertTrue("Should return 200", status.contains("200"))
        val json = JSONObject(responseBody)
        assertEquals("noop", json.getString("status"))
    }

    // =========================================================================
    // Concurrent connections
    // =========================================================================

    @Test
    fun `server handles concurrent requests`() {
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        val allDone = CountDownLatch(10)
        val s = LocalPairingServer { _, _, _ ->
            callCount.incrementAndGet()
        }
        server = s
        s.start()

        val threads = (1..10).map { i ->
            Thread {
                try {
                    val body = """{"code":"CONC_$i"}"""
                    val (status, _) = sendRequest(s.port, "POST", "/pair", body)
                    assertTrue(status.contains("200"))
                } finally {
                    allDone.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        assertTrue("All requests should complete", allDone.await(10, TimeUnit.SECONDS))
        assertEquals("All 10 requests should invoke callback", 10, callCount.get())
    }
}
