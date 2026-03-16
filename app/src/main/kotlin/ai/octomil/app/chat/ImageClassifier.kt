package ai.octomil.app.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLite image classifier using EfficientNet-Lite0.
 *
 * Use [create] to obtain an instance — returns null if the model or label
 * assets are not bundled in this build. Callers must handle the null case
 * (e.g. show "image understanding not available").
 */
class ImageClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
) : Closeable {

    fun classify(bitmap: Bitmap, topK: Int = 5): List<Pair<String, Float>> {
        val resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val input = bitmapToByteBuffer(resized)
        val output = Array(1) { FloatArray(labels.size) }

        interpreter.run(input, output)

        return output[0]
            .mapIndexed { index, confidence -> labels[index] to confidence }
            .sortedByDescending { it.second }
            .take(topK)
    }

    fun classifyBase64(base64Data: String, topK: Int = 5): List<Pair<String, Float>> {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return emptyList()
        return classify(bitmap, topK)
    }

    override fun close() {
        interpreter.close()
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        bitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }

        buffer.rewind()
        return buffer
    }

    companion object {
        private const val TAG = "ImageClassifier"
        private const val MODEL_FILENAME = "efficientnet_lite0.tflite"
        private const val LABELS_FILENAME = "imagenet_labels.txt"
        private const val IMAGE_SIZE = 224

        /**
         * Returns an [ImageClassifier] if the TFLite model and labels are
         * present in app assets, or null if they are not bundled.
         */
        fun create(context: Context): ImageClassifier? {
            val assets = context.assets
            val model: MappedByteBuffer
            val labels: List<String>

            try {
                val fd = assets.openFd(MODEL_FILENAME)
                val channel = fd.createInputStream().channel
                model = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            } catch (_: Exception) {
                Log.i(TAG, "$MODEL_FILENAME not found in assets — classifier unavailable")
                return null
            }

            try {
                labels = assets.open(LABELS_FILENAME).bufferedReader().readLines()
            } catch (_: Exception) {
                Log.i(TAG, "$LABELS_FILENAME not found in assets — classifier unavailable")
                return null
            }

            return ImageClassifier(Interpreter(model), labels)
        }
    }
}
