package com.slimvideo.app.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.slimvideo.app.model.CompressionConfig
import com.slimvideo.app.model.CompressionProgress
import com.slimvideo.app.model.CompressionResult
import com.slimvideo.app.model.VideoMetadata
import com.slimvideo.app.utils.MediaStoreHelper
import com.slimvideo.app.utils.VideoAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class VideoCompressorEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SlimVideo::CompressionEngineWakeLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes safety timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    /**
     * Run Hardware-Accelerated Video Compression (Single Video)
     * Utilizes full potential of CPU, GPU, and VPU silicon.
     */
    suspend fun compress(
        metadata: VideoMetadata,
        config: CompressionConfig,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionResult = withContext(Dispatchers.Main) {
        acquireWakeLock()

        val cacheDir = File(context.cacheDir, "exports")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val tempOutputFile = File(cacheDir, "slim_compressed_${System.currentTimeMillis()}.mp4")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        try {
            // 1. Build hardware encoder settings with requested bitrate & fallback
            val videoSettings = VideoEncoderSettings.Builder()
                .setBitrate(config.targetVideoBitrateBps)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoSettings)
                .setEnableFallback(true) // Automatically falls back if hardware encoder needs adaptation
                .build()

            // 2. Setup video resolution scaling effect (even dimensions strictly required by VPU/GPU)
            val targetW = VideoAnalyzer.makeEven(config.targetWidth)
            val targetH = VideoAnalyzer.makeEven(config.targetHeight)

            val presentation = Presentation.createForWidthAndHeight(
                targetW,
                targetH,
                Presentation.LAYOUT_SCALE_TO_FIT
            )

            val effects = Effects(
                emptyList(),
                listOf(presentation)
            )

            val mediaItem = MediaItem.fromUri(metadata.uri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .setRemoveAudio(!metadata.hasAudio)
                .build()

            // 3. Choose Hardware Codec (H.264 Universal or H.265 Next-Gen HEVC)
            val targetVideoMime = config.codec.mimeType

            // 4. Setup Transformer on the Main Looper
            suspendCancellableCoroutine { continuation ->
                var isFinished = false
                val startTime = System.currentTimeMillis()
                var smoothedSpeed = 1.0f

                val transformerBuilder = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .setVideoMimeType(targetVideoMime)

                if (metadata.hasAudio) {
                    transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
                }

                val transformer = transformerBuilder
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            isFinished = true
                            activeTransformer = null
                            if (continuation.isActive) {
                                continuation.resume(tempOutputFile)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            isFinished = true
                            activeTransformer = null
                            if (continuation.isActive) {
                                val userFriendlyMsg = exportException.message 
                                    ?: exportException.cause?.message 
                                    ?: "Hardware encoding failed on this device"
                                continuation.resumeWithException(
                                    Exception(userFriendlyMsg, exportException)
                                )
                            }
                        }
                    })
                    .build()

                activeTransformer = transformer

                continuation.invokeOnCancellation {
                    isFinished = true
                    mainHandler.post {
                        try {
                            transformer.cancel()
                        } catch (_: Exception) {}
                        activeTransformer = null
                    }
                    if (tempOutputFile.exists()) tempOutputFile.delete()
                }

                // Start hardware accelerated export
                try {
                    transformer.start(editedMediaItem, tempOutputFile.absolutePath)
                } catch (e: Exception) {
                    activeTransformer = null
                    continuation.resumeWithException(e)
                    return@suspendCancellableCoroutine
                }

                // Polling progress on Main Looper with exponential smoothing
                launch(Dispatchers.Main) {
                    val progressHolder = ProgressHolder()
                    while (!isFinished && isActive) {
                        delay(200)
                        if (isFinished) break

                        val progressState = transformer.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val percent = progressHolder.progress.coerceIn(0, 99)
                            val now = System.currentTimeMillis()
                            val elapsedSec = ((now - startTime) / 1000.0).coerceAtLeast(0.2)
                            val processedMs = ((metadata.durationMs * percent) / 100).coerceAtLeast(0)

                            val instantSpeed = if (elapsedSec > 0.3 && processedMs > 0) {
                                (processedMs / 1000.0) / elapsedSec
                            } else 1.0

                            // Exponential Moving Average (alpha = 0.25) to prevent jittery numbers
                            smoothedSpeed = (0.75f * smoothedSpeed + 0.25f * instantSpeed.toFloat())
                                .coerceIn(0.1f, 100.0f)

                            val remainingSec = if (smoothedSpeed > 0.1f) {
                                val remainingMs = (metadata.durationMs - processedMs).coerceAtLeast(0)
                                ((remainingMs / 1000.0) / smoothedSpeed).toInt()
                            } else 0

                            val cleanSpeed = String.format(Locale.US, "%.1f", smoothedSpeed).toFloatOrNull() ?: 1.0f
                            val currentOutputSize = if (tempOutputFile.exists()) tempOutputFile.length() else 0L
                            val encodingFps = (smoothedSpeed * metadata.fps).coerceAtLeast(0f)

                            onProgress(
                                CompressionProgress(
                                    percent = percent,
                                    speedMultiplier = cleanSpeed,
                                    etaSeconds = remainingSec.coerceAtLeast(0),
                                    elapsedSeconds = elapsedSec.toInt(),
                                    processedMs = processedMs,
                                    currentOutputSizeBytes = currentOutputSize,
                                    encodingFps = encodingFps
                                )
                            )
                        }
                    }
                }
            }

            // 5. Send final 100% progress
            val finalOutputSize = tempOutputFile.length()
            onProgress(
                CompressionProgress(
                    percent = 100,
                    speedMultiplier = 1.0f,
                    etaSeconds = 0,
                    elapsedSeconds = 0,
                    processedMs = metadata.durationMs,
                    currentOutputSizeBytes = finalOutputSize,
                    encodingFps = metadata.fps.toFloat()
                )
            )

            // 6. Automatically save output to Android MediaStore Gallery / Movies / SlimVideo
            val savedUri = MediaStoreHelper.saveVideoToGallery(
                context = context,
                sourceFile = tempOutputFile,
                baseName = metadata.name
            )

            CompressionResult(
                inputUri = metadata.uri,
                outputUri = savedUri,
                outputFilePath = tempOutputFile.absolutePath,
                originalSizeBytes = metadata.sizeBytes,
                compressedSizeBytes = finalOutputSize,
                durationMs = metadata.durationMs,
                width = targetW,
                height = targetH,
                codecUsed = config.codec.shortName
            )
        } finally {
            releaseWakeLock()
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                activeTransformer?.cancel()
            } catch (_: Exception) {}
            activeTransformer = null
            releaseWakeLock()
        }
    }
}
