package com.slimvideo.app.model

import android.net.Uri
import androidx.media3.common.MimeTypes
import java.util.UUID

/**
 * Supported video encoding codecs
 */
enum class VideoCodec(val displayName: String, val shortName: String, val mimeType: String) {
    H264("H.264 (AVC) • Universal Compatibility", "H.264", MimeTypes.VIDEO_H264),
    H265("H.265 (HEVC) • 40% Smaller & VPU Accelerated", "H.265 (HEVC)", MimeTypes.VIDEO_H265)
}

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
    val fps: Int = 30,
    val hasAudio: Boolean = true
) {
    val durationSeconds: Long get() = (durationMs / 1000).coerceAtLeast(1)
    val durationSecondsDouble: Double get() = (durationMs.toDouble() / 1000.0).coerceAtLeast(0.1)
    val isPortrait: Boolean get() = height > width
}

/**
 * Compression mode presets
 */
enum class CompressionPreset(val title: String, val subtitle: String) {
    AUTO("Smart Auto", "Crisp 720p HD • Ideal balance (~10–12 MB)"),
    ULTRA("Ultra Saver", "480p • Maximum space saved (~4–5 MB)"),
    BALANCED("Balanced", "720p HD @ 380 kbps (~12–14 MB)"),
    HIGH("High Quality", "1080p FHD @ 1 Mbps (~25–30 MB)"),
    TARGET_SIZE("Target Size", "Set exact target file size in MB"),
    CUSTOM("Custom", "Manual resolution and bitrate tuning")
}

/**
 * Video compression configuration parameters
 */
data class CompressionConfig(
    val preset: CompressionPreset = CompressionPreset.AUTO,
    val codec: VideoCodec = VideoCodec.H264,
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
    val processedMs: Long = 0,
    val currentOutputSizeBytes: Long = 0L,
    val encodingFps: Float = 0f
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
    val height: Int,
    val codecUsed: String = "H.264"
) {
    val savedBytes: Long get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0)
    val savedPercent: Float get() = if (originalSizeBytes > 0) {
        ((savedBytes.toFloat() / originalSizeBytes) * 100f).coerceIn(0f, 100f)
    } else 0f
}

/**
 * Batch processing item status
 */
enum class BatchItemStatus {
    PENDING,
    COMPRESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Individual item in a batch compression queue
 */
data class BatchItem(
    val id: String = UUID.randomUUID().toString(),
    val metadata: VideoMetadata,
    val config: CompressionConfig,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val progress: CompressionProgress = CompressionProgress(),
    val result: CompressionResult? = null,
    val error: String? = null
)

/**
 * Real-time progress across an entire batch queue
 */
data class BatchProgress(
    val totalCount: Int,
    val currentIndex: Int, // 1-based index (e.g. 1 of 15)
    val completedCount: Int,
    val failedCount: Int,
    val overallPercent: Int,
    val currentItemProgress: CompressionProgress,
    val activeVideoName: String,
    val totalOriginalBytes: Long,
    val totalCompressedBytesSoFar: Long
)

/**
 * Final result for a completed batch job
 */
data class BatchResult(
    val items: List<BatchItem>,
    val totalOriginalBytes: Long,
    val totalCompressedBytes: Long,
    val totalDurationMs: Long,
    val successfulCount: Int,
    val failedCount: Int
) {
    val totalSavedBytes: Long get() = (totalOriginalBytes - totalCompressedBytes).coerceAtLeast(0)
    val totalSavedPercent: Float get() = if (totalOriginalBytes > 0) {
        ((totalSavedBytes.toFloat() / totalOriginalBytes.toFloat()) * 100f).coerceIn(0f, 100f)
    } else 0f
}

/**
 * Application screen states
 */
sealed interface AppState {
    data object Idle : AppState
    data class Analyzing(val current: Int = 1, val total: Int = 1) : AppState
    
    // Single Video States
    data class Ready(val metadata: VideoMetadata) : AppState
    data class Compressing(val metadata: VideoMetadata, val progress: CompressionProgress) : AppState
    data class Completed(val result: CompressionResult) : AppState

    // Batch Video States (2 to 20+ videos)
    data class BatchReady(
        val items: List<VideoMetadata>,
        val config: CompressionConfig
    ) : AppState
    data class BatchCompressing(
        val batchProgress: BatchProgress,
        val currentMetadata: VideoMetadata
    ) : AppState
    data class BatchCompleted(
        val batchResult: BatchResult
    ) : AppState

    data class Error(val message: String, val lastMetadata: VideoMetadata? = null) : AppState
}
