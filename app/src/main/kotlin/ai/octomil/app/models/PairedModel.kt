package ai.octomil.app.models

import ai.octomil.*
import java.io.File

data class PairedModel(
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val sizeString: String,
    val runtime: String,
    val tokensPerSecond: Double? = null,
    val modality: String? = null,
    val capabilities: List<ModelCapability> = emptyList(),
    /** Absolute path to the model directory on disk (filesDir/octomil_models/{name}/{version}). */
    val modelPath: String? = null,
) {
    /** Whether this model can be opened in the chat UI. */
    val isChatModel: Boolean
        get() = capabilities.isEmpty() ||
            capabilities.contains(ModelCapability.CHAT)

    /** Whether the model directory still exists on disk. */
    val isAvailableOnDisk: Boolean
        get() {
            val path = modelPath ?: return false
            val dir = File(path)
            return dir.exists() && dir.isDirectory
        }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
}
