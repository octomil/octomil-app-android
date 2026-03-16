package ai.octomil.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records 16kHz mono PCM audio, capped at [MAX_DURATION_MS] to prevent OOM on 3GB devices.
 *
 * 30s * 16000 samples/s = 480,000 floats = ~1.8MB — safe for Galaxy A17.
 */
class AudioRecorder {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val MAX_DURATION_MS = 30_000L
        private const val MAX_SAMPLES = (SAMPLE_RATE * MAX_DURATION_MS / 1000).toInt()
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val buffer = ShortArray(MAX_SAMPLES)
    private var samplesRecorded = 0

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (_isRecording.value) return

        samplesRecorded = 0
        _durationMs.value = 0

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        recorder?.startRecording()
        _isRecording.value = true

        recordingThread = Thread {
            val readBuf = ShortArray(1024)
            while (_isRecording.value && samplesRecorded < MAX_SAMPLES) {
                val read = recorder?.read(readBuf, 0, readBuf.size) ?: break
                if (read > 0) {
                    val toCopy = minOf(read, MAX_SAMPLES - samplesRecorded)
                    System.arraycopy(readBuf, 0, buffer, samplesRecorded, toCopy)
                    samplesRecorded += toCopy
                    _durationMs.value = (samplesRecorded.toLong() * 1000) / SAMPLE_RATE
                }
            }

            // Auto-stop at max duration
            if (samplesRecorded >= MAX_SAMPLES) {
                Log.i(TAG, "Auto-stopped at ${MAX_DURATION_MS}ms")
                _isRecording.value = false
            }
        }.also { it.start() }
    }

    /**
     * Stops recording and returns normalized float samples in [-1, 1].
     */
    fun stopRecording(): FloatArray {
        _isRecording.value = false
        recordingThread?.join(1000)
        recordingThread = null

        recorder?.stop()
        recorder?.release()
        recorder = null

        val result = FloatArray(samplesRecorded) { buffer[it] / 32768f }
        Log.i(TAG, "Recorded ${samplesRecorded} samples (${_durationMs.value}ms)")
        return result
    }
}
