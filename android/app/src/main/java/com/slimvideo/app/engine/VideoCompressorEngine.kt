package com.slimvideo.app.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
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

    /**
     * Run Hardware-Accelerated Video Compression
     * Note: Media3 Transformer MUST be instantiated, started, polled, and cancelled on the Main Looper!
     */
    suspend fun compress(
        metadata: VideoMetadata,
        config: CompressionConfig,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionResult = withContext(Dispatchers.Main) {
        val cacheDir = File(context.cacheDir, "exports")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val tempOutputFile = File(cacheDir, "slim_compressed_${System.currentTimeMillis()}.mp4")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        // 1. Build hardware encoder settings with requested bitrate & fallback
        val videoSettings = VideoEncoderSettings.Builder()
            .setBitrate(config.targetVideoBitrateBps)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(videoSettings)
            .setEnableFallback(true)
            .build()

        // 2. Setup video resolution scaling effect (even dimensions strictly required)
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
            .setRemoveAudio(false)
            .build()

        // 3. Setup Transformer on the Main Looper
        suspendCancellableCoroutine { continuation ->
            var isFinished = false
            val startTime = System.currentTimeMillis()

            val transformer = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
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

            // Cancellation must run on the Main Looper
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

            // Polling progress on Main Looper (Transformer.getProgress REQUIRES Main Thread!)
            launch(Dispatchers.Main) {
                val progressHolder = ProgressHolder()
                while (!isFinished && isActive) {
                    delay(200)
                    if (isFinished) break

                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val percent = progressHolder.progress.coerceIn(0, 99)
                        val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)
                        val processedMs = ((metadata.durationMs * percent) / 100).coerceAtLeast(0)

                        val speed = if (elapsedSec > 0 && processedMs > 0) {
                            processedMs.toFloat() / (elapsedSec * 1000f)
                        } else 1.0f

                        val remainingSec = if (speed > 0.1f) {
                            ((metadata.durationMs - processedMs) / (speed * 1000f)).toInt()
                        } else 0

                        val cleanSpeed = String.format(Locale.US, "%.1f", speed).toFloatOrNull() ?: 1.0f

                        onProgress(
                            CompressionProgress(
                                percent = percent,
                                speedMultiplier = cleanSpeed,
                                etaSeconds = remainingSec.coerceAtLeast(0),
                                elapsedSeconds = elapsedSec,
                                processedMs = processedMs
                            )
                        )
                    }
                }
            }
        }

        // 4. Send 100% progress
        onProgress(
            CompressionProgress(
                percent = 100,
                speedMultiplier = 1.0f,
                etaSeconds = 0,
                elapsedSeconds = 0,
                processedMs = metadata.durationMs
            )
        )

        // 5. Automatically save output to Android MediaStore Gallery / Movies / SlimVideo
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
            compressedSizeBytes = tempOutputFile.length(),
            durationMs = metadata.durationMs,
            width = targetW,
            height = targetH
        )
    }

    fun cancel() {
        mainHandler.post {
            try {
                activeTransformer?.cancel()
            } catch (_: Exception) {}
            activeTransformer = null
        }
    }
}
