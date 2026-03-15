package ai.octomil.app.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLite image classifier using EfficientNet-Lite0.
 * Classifies images and returns top-K label/confidence pairs.
 *
 * Usage:
 * ```
 * val classifier = ImageClassifier(context)
 * val labels = classifier.classify(bitmap, topK = 5)
 * classifier.close()
 * ```
 */
class ImageClassifier(context: Context) : Closeable {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val model = loadModelFile(context, MODEL_FILENAME)
        interpreter = Interpreter(model)
        labels = context.assets.open(LABELS_FILENAME).bufferedReader().readLines()
    }

    /**
     * Classify a bitmap image.
     * @return top-K labels with confidence scores, sorted descending by confidence
     */
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

    /**
     * Classify a base64-encoded image.
     * @return top-K labels with confidence scores, sorted descending by confidence
     */
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
        private const val MODEL_FILENAME = "efficientnet_lite0.tflite"
        private const val LABELS_FILENAME = "imagenet_labels.txt"
        private const val IMAGE_SIZE = 224
    }
}

private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
    val fd = context.assets.openFd(filename)
    val channel = fd.createInputStream().channel
    return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
}
