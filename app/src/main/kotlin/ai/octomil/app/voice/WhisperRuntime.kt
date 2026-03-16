package ai.octomil.app.voice

import java.io.File

/**
 * Legacy whisper.cpp wrapper — disabled after migration to Octomil SDK speech API.
 *
 * Speech transcription now goes through [ai.octomil.Octomil.audio] which uses
 * sherpa-onnx streaming recognizer under the hood. This stub is kept temporarily
 * to avoid breaking any remaining references. Remove once fully validated.
 */
@Deprecated("Use Octomil.audio API instead", ReplaceWith("Octomil.audio"))
class WhisperRuntime(private val modelFile: File) {

    suspend fun loadModel() {
        error("WhisperRuntime disabled — use Octomil.audio API instead")
    }

    suspend fun transcribe(samples: FloatArray): String {
        error("WhisperRuntime disabled — use Octomil.audio API instead")
    }

    suspend fun release() {
        // no-op
    }
}
