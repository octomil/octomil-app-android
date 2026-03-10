package ai.octomil.app.models

data class PairedModel(
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val sizeString: String,
    val runtime: String,
    val tokensPerSecond: Double? = null,
    val modality: String? = null,
)

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
}
