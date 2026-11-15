package com.slimvideo.app.model

import android.net.Uri

/**
 * Metadata extracted from chosen input video file
 */
data class VideoMetadata(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrateBps: Long = 0,
    val fps: Int = 30
) {
    val durationSeconds: Long get() = (durationMs / 1000).coerceAtLeast(1)
    val isPortrait: Boolean get() = height > width
}

/**
 * Compression mode presets
 */
enum class CompressionPreset(val title: String, val subtitle: String) {
    AUTO("Smart Auto", "Crisp 720p HD • Ideal balance (~10–12 MB)"),
    ULTRA("Ultra Saver", "480p • Maximum space saved (~4–5 MB)"),
    BALANCED("Balanced", "720p HD @ 400 kbps (~12–14 MB)"),
    HIGH("High Quality", "1080p FHD @ 1 Mbps (~25–30 MB)"),
    TARGET_SIZE("Target Size", "Set exact target file size in MB"),
    CUSTOM("Custom", "Manual resolution and bitrate tuning")
}

/**
 * Video compression configuration parameters
 */
data class CompressionConfig(
    val preset: CompressionPreset = CompressionPreset.AUTO,
    val targetWidth: Int = 1280,
    val targetHeight: Int = 720,
    val targetVideoBitrateBps: Int = 350_000,
    val targetAudioBitrateBps: Int = 64_000,
    val targetFps: Int = 30,
    val targetSizeMB: Int = 10
)

/**
 * Real-time progress updates during hardware encoding
 */
data class CompressionProgress(
    val percent: Int = 0,
    val speedMultiplier: Float = 1.0f,
    val etaSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val processedMs: Long = 0
)

/**
 * Final result after compression and gallery save
 */
data class CompressionResult(
    val inputUri: Uri,
    val outputUri: Uri,
    val outputFilePath: String,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int
) {
    val savedBytes: Long get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0)
    val savedPercent: Float get() = if (originalSizeBytes > 0) {
        ((savedBytes.toFloat() / originalSizeBytes) * 100f).coerceIn(0f, 100f)
    } else 0f
}

/**
 * Application screen states
 */
sealed interface AppState {
    data object Idle : AppState
    data object Analyzing : AppState
    data class Ready(val metadata: VideoMetadata) : AppState
    data class Compressing(val metadata: VideoMetadata, val progress: CompressionProgress) : AppState
    data class Completed(val result: CompressionResult) : AppState
    data class Error(val message: String, val lastMetadata: VideoMetadata? = null) : AppState
}
