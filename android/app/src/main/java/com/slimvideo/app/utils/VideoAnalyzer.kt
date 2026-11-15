package com.slimvideo.app.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.slimvideo.app.model.CompressionConfig
import com.slimvideo.app.model.CompressionPreset
import com.slimvideo.app.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoAnalyzer {

    fun makeEven(num: Int): Int {
        return if (num % 2 == 0) num else num - 1
    }

    fun calculateScaledDimensions(
        origW: Int,
        origH: Int,
        maxDimension: Int
    ): Pair<Int, Int> {
        val isPortrait = origH > origW
        val longSide = if (isPortrait) origH else origW

        if (longSide <= maxDimension) {
            return Pair(makeEven(origW), makeEven(origH))
        }

        val scale = maxDimension.toFloat() / longSide.toFloat()
        val scaledW = makeEven((origW * scale).toInt())
        val scaledH = makeEven((origH * scale).toInt())
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
            val durationMs = durationStr?.toLongOrNull() ?: 1000L

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

            VideoMetadata(
                uri = uri,
                name = name,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                width = makeEven(width),
                height = makeEven(height),
                bitrateBps = bitrateBps,
                fps = 30
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun getPresetConfig(
        meta: VideoMetadata,
        preset: CompressionPreset,
        targetSizeMB: Int = 10
    ): CompressionConfig {
        val durationSec = meta.durationSeconds.coerceAtLeast(1L)
        val isPortrait = meta.isPortrait

        return when (preset) {
            CompressionPreset.AUTO -> {
                // ~10-12 MB for 4 min video (crisp 720p HD)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 1280)
                CompressionConfig(
                    preset = CompressionPreset.AUTO,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = 320_000,
                    targetAudioBitrateBps = 64_000,
                    targetFps = 30
                )
            }
            CompressionPreset.ULTRA -> {
                // ~4-5 MB for 4 min video (480p)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 854)
                CompressionConfig(
                    preset = CompressionPreset.ULTRA,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = 140_000,
                    targetAudioBitrateBps = 44_000,
                    targetFps = 24
                )
            }
            CompressionPreset.BALANCED -> {
                // ~12-14 MB for 4 min video (720p HD @ 380 kbps)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 1280)
                CompressionConfig(
                    preset = CompressionPreset.BALANCED,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = 380_000,
                    targetAudioBitrateBps = 64_000,
                    targetFps = 30
                )
            }
            CompressionPreset.HIGH -> {
                // ~28-32 MB for 4 min video (1080p FHD @ 1 Mbps)
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, 1920)
                CompressionConfig(
                    preset = CompressionPreset.HIGH,
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = 1_000_000,
                    targetAudioBitrateBps = 96_000,
                    targetFps = 30
                )
            }
            CompressionPreset.TARGET_SIZE -> {
                val totalBits = targetSizeMB.toLong() * 1024L * 1024L * 8L
                val totalBitrate = (totalBits / durationSec).toInt()
                val audioBitrate = if (totalBitrate > 350_000) 64_000 else 44_000
                val videoBitrate = (totalBitrate - audioBitrate).coerceAtLeast(80_000)

                val maxDim = when {
                    videoBitrate >= 600_000 -> 1280
                    videoBitrate >= 280_000 -> 960
                    else -> 854
                }
                val (w, h) = calculateScaledDimensions(meta.width, meta.height, maxDim)
                CompressionConfig(
                    preset = CompressionPreset.TARGET_SIZE,
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
                    targetWidth = w,
                    targetHeight = h,
                    targetVideoBitrateBps = 450_000,
                    targetAudioBitrateBps = 64_000,
                    targetFps = 30
                )
            }
        }
    }

    fun estimateOutputSizeBytes(config: CompressionConfig, durationSeconds: Long): Long {
        val totalBps = config.targetVideoBitrateBps + config.targetAudioBitrateBps
        val bytes = ((totalBps.toLong() * durationSeconds) / 8L) * 1.02 // 2% container overhead
        return bytes.toLong()
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
