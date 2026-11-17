package com.slimvideo.app.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.slimvideo.app.model.CompressionConfig
import com.slimvideo.app.model.CompressionPreset
import com.slimvideo.app.model.VideoCodec
import com.slimvideo.app.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoAnalyzer {

    fun makeEven(num: Int): Int {
        val safe = num.coerceAtLeast(16)
        return if (safe % 2 == 0) safe else safe - 1
    }

    /**
     * Calculates scaled dimensions maintaining exact aspect ratio,
     * ensuring dimensions never exceed original and are strictly even.
     */
    fun calculateScaledDimensions(
        origW: Int,
        origH: Int,
        maxDimension: Int
    ): Pair<Int, Int> {
        val safeW = origW.coerceAtLeast(16)
        val safeH = origH.coerceAtLeast(16)
        val isPortrait = safeH > safeW
        val longSide = if (isPortrait) safeH else safeW

        if (longSide <= maxDimension) {
            return Pair(makeEven(safeW), makeEven(safeH))
        }

        val scale = maxDimension.toFloat() / longSide.toFloat()
        val scaledW = makeEven((safeW * scale).toInt())
        val scaledH = makeEven((safeH * scale).toInt())
        return Pair(scaledW, scaledH)
    }

    suspend fun analyze(context: Context, uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        var name = "video.mp4"
        var sizeBytes = 0L

        // Query content resolver for display name and size
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        // Fallback size if query returned 0
        if (sizeBytes <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    sizeBytes = pfd.statSize
                }
            } catch (_: Exception) {}
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = (durationStr?.toLongOrNull() ?: 1000L).coerceAtLeast(500L)

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            var width = widthStr?.toIntOrNull() ?: 1280

            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            var height = heightStr?.toIntOrNull() ?: 720

            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // If video has 90 or 270 degree rotation flag, visual dimensions are flipped
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitrateBps = bitrateStr?.toLongOrNull() ?: if (durationMs > 0 && sizeBytes > 0) {
                (sizeBytes * 8 * 1000) / durationMs
            } else 0L

            // Check if audio track exists
            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val hasAudio = hasAudioStr == null || hasAudioStr.equals("yes", ignoreCase = true)

            // Extract capture frame rate if available
            val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val fps = (fpsStr?.toFloatOrNull()?.toInt() ?: 30).coerceIn(15, 60)

            VideoMetadata(
                uri = uri,
                name = name,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                width = makeEven(width),
                height = makeEven(height),
                bitrateBps = bitrateBps,
                fps = fps,
                hasAudio = hasAudio
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Compute optimized compression parameters for a preset
     */
    fun getPresetConfig(
        meta: VideoMetadata,
        preset: CompressionPreset,
        targetSizeMB: Int = 10,
        codec: VideoCodec = VideoCodec.H264
    ): CompressionConfig {
        val durationSec = meta.durationSecondsDouble.coerceAtLeast(1.0)
        val isHevc = codec == VideoCodec.H265
        // HEVC gives ~30-40% higher efficiency at the same visual quality
        val codecFactor = if (isHevc) 0.72f else 1.0f

        return when (preset) {
            CompressionPreset.AUTO -> {
                // Crisp 720p HD (~10-12 MB for 4 min video)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 1280)
                val baseVideoBps = (320_000 * codecFactor).toInt().coerceAtLeast(100_000)
                CompressionConfig(
                    preset = CompressionPreset.AUTO,
                    codec = codec,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = baseVideoBps,
                    targetAudioBitrateBps = if (meta.hasAudio) 64_000 else 0,
                    targetFps = 30
                )
            }
            CompressionPreset.ULTRA -> {
                // 480p SD (~4-5 MB for 4 min video)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 854)
                val baseVideoBps = (140_000 * codecFactor).toInt().coerceAtLeast(80_000)
                CompressionConfig(
                    preset = CompressionPreset.ULTRA,
                    codec = codec,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = baseVideoBps,
                    targetAudioBitrateBps = if (meta.hasAudio) 44_000 else 0,
                    targetFps = 24
                )
            }
            CompressionPreset.BALANCED -> {
                // 720p HD @ 380 kbps (~12-14 MB for 4 min video)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 1280)
                val baseVideoBps = (380_000 * codecFactor).toInt().coerceAtLeast(120_000)
                CompressionConfig(
                    preset = CompressionPreset.BALANCED,
                    codec = codec,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = baseVideoBps,
                    targetAudioBitrateBps = if (meta.hasAudio) 64_000 else 0,
                    targetFps = 30
                )
            }
            CompressionPreset.HIGH -> {
                // 1080p FHD @ 1 Mbps (~28-32 MB for 4 min video)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 1920)
                val baseVideoBps = (1_000_000 * codecFactor).toInt().coerceAtLeast(300_000)
                CompressionConfig(
                    preset = CompressionPreset.HIGH,
                    codec = codec,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = baseVideoBps,
                    targetAudioBitrateBps = if (meta.hasAudio) 96_000 else 0,
                    targetFps = 30
                )
            }
            CompressionPreset.TARGET_SIZE -> {
                val totalBits = (targetSizeMB.toDouble() * 1024.0 * 1024.0 * 8.0)
                val totalBitrate = (totalBits / durationSec).toInt()
                val audioBitrate = if (meta.hasAudio) {
                    if (totalBitrate > 350_000) 64_000 else 44_000
                } else 0
                val videoBitrate = (totalBitrate - audioBitrate).coerceAtLeast(80_000)

                val maxDim = when {
                    videoBitrate >= 600_000 -> 1280
                    videoBitrate >= 280_000 -> 960
                    else -> 854
                }
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, maxDim)
                CompressionConfig(
                    preset = CompressionPreset.TARGET_SIZE,
                    codec = codec,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = videoBitrate,
                    targetAudioBitrateBps = audioBitrate,
                    targetFps = if (videoBitrate >= 600_000) 30 else 24,
                    targetSizeMB = targetSizeMB
                )
            }
            CompressionPreset.CUSTOM -> {
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 1280)
                CompressionConfig(
                    preset = CompressionPreset.CUSTOM,
                    codec = codec,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = (450_000 * codecFactor).toInt(),
                    targetAudioBitrateBps = if (meta.hasAudio) 64_000 else 0,
                    targetFps = 30
                )
            }
        }
    }

    /**
     * Highly accurate file size estimation including MP4 container & index table overhead
     */
    fun estimateOutputSizeBytes(config: CompressionConfig, durationSecondsDouble: Double): Long {
        val totalBps = config.targetVideoBitrateBps + config.targetAudioBitrateBps
        // 2.5% overhead for MP4 container metadata (moov, mvhd, trak, stbl)
        val bytes = ((totalBps.toDouble() * durationSecondsDouble) / 8.0) * 1.025
        return bytes.toLong().coerceAtLeast(10_240L)
    }

    fun estimateOutputSizeBytes(config: CompressionConfig, durationSeconds: Long): Long {
        return estimateOutputSizeBytes(config, durationSeconds.toDouble())
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val k = 1024.0
        val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = (Math.log(bytes.toDouble()) / Math.log(k)).toInt().coerceIn(0, sizes.size - 1)
        val value = bytes / Math.pow(k, i.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, sizes[i])
    }

    fun formatDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }
}
