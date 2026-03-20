package ai.octomil.app.services

import ai.octomil.*
import ai.octomil.app.OctomilApplication
import ai.octomil.app.models.PairedModel
import ai.octomil.app.speech.SpeechServiceClient
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "GoldenTestRunner"

/**
 * Exercises each model capability through the same code paths the app uses.
 * Debug-only — invoked by `POST /golden/test/*` endpoints on the local pairing server.
 */
class GoldenTestRunner(
    private val context: Context,
    private val speechClient: SpeechServiceClient,
) {
    fun getModels(): List<PairedModel> = OctomilApplication.instance.pairedModels.toList()

    // ---- Run All ----

    suspend fun runAll(): JSONObject {
        val models = getModels()
        val results = JSONArray()
        var passed = 0; var failed = 0; var skipped = 0

        // Group by capability
        val byCapability = models.groupBy { it.capabilities.firstOrNull() }

        // Chat
        byCapability[ModelCapability.CHAT]?.firstOrNull()?.let { model ->
            val r = runChat(model, "What is 2+2? Answer in one word.", 32)
            results.put(r)
            when {
                r.optBoolean("skipped") -> skipped++
                r.optBoolean("passed") -> passed++
                else -> failed++
            }
        }

        // Transcription
        byCapability[ModelCapability.TRANSCRIPTION]?.firstOrNull()?.let { model ->
            val r = runTranscribe(model, "hello", "speech_service")
            results.put(r)
            when {
                r.optBoolean("skipped") -> skipped++
                r.optBoolean("passed") -> passed++
                else -> failed++
            }
        }

        // Prediction
        val predModel = byCapability[ModelCapability.KEYBOARD_PREDICTION]?.firstOrNull()
            ?: byCapability[ModelCapability.TEXT_COMPLETION]?.firstOrNull()
        predModel?.let { model ->
            val r = runPredict(model, "The weather today is", 3)
            results.put(r)
            when {
                r.optBoolean("skipped") -> skipped++
                r.optBoolean("passed") -> passed++
                else -> failed++
            }
        }

        val total = passed + failed + skipped
        return JSONObject().apply {
            put("results", results)
            put("summary", JSONObject().apply {
                put("total", total)
                put("passed", passed)
                put("failed", failed)
                put("skipped", skipped)
            })
        }
    }

    // ---- Chat ----

    suspend fun runChat(model: PairedModel, prompt: String, maxTokens: Int): JSONObject {
        val startMs = System.nanoTime() / 1_000_000
        var firstTokenMs: Long? = null
        var tokenCount = 0
        val buffer = StringBuilder()

        try {
            val request = ResponseRequest(
                model = model.name,
                input = listOf(InputItem.text(prompt)),
                maxOutputTokens = maxTokens,
                temperature = 0.7f,
            )

            Octomil.responses.stream(request).collect { event ->
                when (event) {
                    is ResponseStreamEvent.TextDelta -> {
                        if (tokenCount == 0) {
                            firstTokenMs = System.nanoTime() / 1_000_000
                        }
                        tokenCount++
                        buffer.append(event.delta)
                    }
                    else -> {}
                }
            }

            val totalMs = System.nanoTime() / 1_000_000 - startMs
            val ttftMs = firstTokenMs?.let { it - startMs }
            val text = buffer.toString().trim()

            return result(
                model = model.name,
                capability = "chat",
                passed = text.isNotEmpty(),
                assertion = "non_empty_text",
                metrics = JSONObject().apply {
                    put("total_ms", totalMs)
                    if (ttftMs != null) put("ttft_ms", ttftMs)
                    put("token_count", tokenCount)
                },
                output = JSONObject().apply { put("text", text) },
            )
        } catch (e: Exception) {
            val totalMs = System.nanoTime() / 1_000_000 - startMs
            return result(
                model = model.name,
                capability = "chat",
                passed = false,
                error = e.message,
                metrics = JSONObject().apply { put("total_ms", totalMs) },
            )
        }
    }

    // ---- Transcription (via SpeechServiceClient — real :speech process path) ----

    suspend fun runTranscribe(model: PairedModel, fixture: String, mode: String): JSONObject {
        val startMs = System.nanoTime() / 1_000_000

        // Load WAV fixture from assets
        val samples: FloatArray
        try {
            val assetName = if (fixture == "hello") "hello_16khz.wav" else "$fixture.wav"
            val wavBytes = context.assets.open(assetName).use { it.readBytes() }
            samples = wavToFloatSamples(wavBytes)
        } catch (e: Exception) {
            return result(
                model = model.name,
                capability = "transcription",
                skipped = true,
                error = "fixture not found: $fixture (${e.message})",
            )
        }

        try {
            // Connect to speech service
            speechClient.connect()

            // Wait for connection (up to 5s)
            val connected = withTimeoutOrNull(5000L) {
                speechClient.connected.first { it }
            }
            if (connected == null) {
                return result(
                    model = model.name,
                    capability = "transcription",
                    passed = false,
                    error = "SpeechService connection timeout",
                )
            }

            // Create session
            speechClient.createSession(model.name)

            // Feed samples
            speechClient.feed(samples)

            // Small delay for processing
            delay(500)

            // Finalize
            val text = withContext(Dispatchers.IO) {
                speechClient.finalizeSession()
            }.trim()

            speechClient.releaseSession()

            val totalMs = System.nanoTime() / 1_000_000 - startMs

            return result(
                model = model.name,
                capability = "transcription",
                passed = text.isNotEmpty(),
                assertion = "non_empty_text",
                metrics = JSONObject().apply { put("total_ms", totalMs) },
                output = JSONObject().apply { put("text", text) },
            )
        } catch (e: Exception) {
            val totalMs = System.nanoTime() / 1_000_000 - startMs
            Log.e(TAG, "Transcription test failed", e)
            return result(
                model = model.name,
                capability = "transcription",
                passed = false,
                error = e.message,
                metrics = JSONObject().apply { put("total_ms", totalMs) },
            )
        }
    }

    // ---- Prediction ----

    suspend fun runPredict(model: PairedModel, prefix: String, n: Int): JSONObject {
        val startMs = System.nanoTime() / 1_000_000

        try {
            val suggestions = Octomil.text.predict(
                prefix = prefix,
                maxSuggestions = n,
            )

            val totalMs = System.nanoTime() / 1_000_000 - startMs
            val passed = suggestions.isNotEmpty() && suggestions.any { it.isNotBlank() }

            return result(
                model = model.name,
                capability = "prediction",
                passed = passed,
                assertion = "non_empty_predictions",
                metrics = JSONObject().apply {
                    put("total_ms", totalMs)
                    put("prediction_count", suggestions.size)
                },
                output = JSONObject().apply {
                    put("predictions", JSONArray(suggestions))
                },
            )
        } catch (e: Exception) {
            val totalMs = System.nanoTime() / 1_000_000 - startMs
            return result(
                model = model.name,
                capability = "prediction",
                passed = false,
                error = e.message,
                metrics = JSONObject().apply { put("total_ms", totalMs) },
            )
        }
    }

    // ---- Helpers ----

    private fun result(
        model: String,
        capability: String,
        passed: Boolean = false,
        skipped: Boolean = false,
        error: String? = null,
        assertion: String? = null,
        metrics: JSONObject = JSONObject(),
        output: JSONObject = JSONObject(),
    ): JSONObject = JSONObject().apply {
        put("model", model)
        put("capability", capability)
        put("passed", passed)
        put("skipped", skipped)
        put("error", error ?: JSONObject.NULL)
        put("assertion", assertion ?: JSONObject.NULL)
        put("metrics", metrics)
        put("output", output)
    }

    /** Convert 16-bit PCM WAV bytes to normalized float samples (skip 44-byte header). */
    private fun wavToFloatSamples(wav: ByteArray): FloatArray {
        if (wav.size <= 44) return FloatArray(0)
        val pcm = ByteBuffer.wrap(wav, 44, wav.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = (wav.size - 44) / 2
        val samples = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = pcm.short.toFloat() / 32768f
        }
        return samples
    }
}
