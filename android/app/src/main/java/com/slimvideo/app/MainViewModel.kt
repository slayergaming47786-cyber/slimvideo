package com.slimvideo.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slimvideo.app.engine.VideoCompressorEngine
import com.slimvideo.app.model.*
import com.slimvideo.app.utils.VideoAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = VideoCompressorEngine(application)

    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var compressionJob: Job? = null

    /**
     * Handle single video selection
     */
    fun selectVideo(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.value = AppState.Analyzing(current = 1, total = 1)
                val metadata = VideoAnalyzer.analyze(getApplication(), uri)
                _state.value = AppState.Ready(metadata)
            } catch (e: Exception) {
                _state.value = AppState.Error(
                    message = e.message ?: "Failed to read video file. Please ensure it is a valid video format."
                )
            }
        }
    }

    /**
     * Handle batch video selection (2 to 20+ videos)
     */
    fun selectVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            selectVideo(uris.first())
            return
        }

        viewModelScope.launch {
            try {
                _state.value = AppState.Analyzing(current = 1, total = uris.size)
                val metadataList = mutableListOf<VideoMetadata>()

                for ((index, uri) in uris.withIndex()) {
                    _state.value = AppState.Analyzing(current = index + 1, total = uris.size)
                    try {
                        val meta = VideoAnalyzer.analyze(getApplication(), uri)
                        metadataList.add(meta)
                    } catch (_: Exception) {
                        // Skip corrupted/unreadable item in batch selection
                    }
                }

                if (metadataList.isEmpty()) {
                    _state.value = AppState.Error(
                        message = "Could not read any of the selected video files. Please check file permissions or format."
                    )
                } else if (metadataList.size == 1) {
                    _state.value = AppState.Ready(metadataList.first())
                } else {
                    val defaultConfig = VideoAnalyzer.getPresetConfig(
                        meta = metadataList.first(),
                        preset = CompressionPreset.AUTO,
                        codec = VideoCodec.H264
                    )
                    _state.value = AppState.BatchReady(
                        items = metadataList,
                        config = defaultConfig
                    )
                }
            } catch (e: Exception) {
                _state.value = AppState.Error(
                    message = e.message ?: "Error analyzing selected batch videos."
                )
            }
        }
    }

    /**
     * Remove an individual item from the Batch Ready queue
     */
    fun removeBatchItem(index: Int) {
        val current = _state.value
        if (current is AppState.BatchReady) {
            val updated = current.items.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            if (updated.isEmpty()) {
                _state.value = AppState.Idle
            } else if (updated.size == 1) {
                _state.value = AppState.Ready(updated.first())
            } else {
                _state.value = current.copy(items = updated)
            }
        }
    }

    /**
     * Add more videos to an existing Batch Ready queue
     */
    fun addVideosToBatch(newUris: List<Uri>) {
        val current = _state.value
        if (current !is AppState.BatchReady) {
            selectVideos(newUris)
            return
        }

        viewModelScope.launch {
            val existing = current.items.toMutableList()
            for (uri in newUris) {
                try {
                    val meta = VideoAnalyzer.analyze(getApplication(), uri)
                    if (existing.none { it.uri == uri }) {
                        existing.add(meta)
                    }
                } catch (_: Exception) {}
            }
            _state.value = current.copy(items = existing)
        }
    }

    /**
     * Update configuration applied to the Batch
     */
    fun updateBatchConfig(newConfig: CompressionConfig) {
        val current = _state.value
        if (current is AppState.BatchReady) {
            _state.value = current.copy(config = newConfig)
        }
    }

    /**
     * Start single video compression
     */
    fun startCompress(config: CompressionConfig) {
        val currentState = _state.value
        if (currentState !is AppState.Ready) return

        val metadata = currentState.metadata

        compressionJob = viewModelScope.launch {
            try {
                _state.value = AppState.Compressing(
                    metadata = metadata,
                    progress = CompressionProgress(percent = 0)
                )

                val result = engine.compress(
                    metadata = metadata,
                    config = config,
                    onProgress = { progress ->
                        _state.value = AppState.Compressing(
                            metadata = metadata,
                            progress = progress
                        )
                    }
                )

                _state.value = AppState.Completed(result)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _state.value = AppState.Ready(metadata)
                } else {
                    _state.value = AppState.Error(
                        message = e.message ?: "Video compression failed. Please check device hardware encoder.",
                        lastMetadata = metadata
                    )
                }
            }
        }
    }

    /**
     * Start batch compression: processes 1 to 20+ videos automatically one after another
     */
    fun startBatchCompress(batchConfig: CompressionConfig) {
        val current = _state.value
        if (current !is AppState.BatchReady) return

        val itemsToCompress = current.items
        if (itemsToCompress.isEmpty()) return

        compressionJob = viewModelScope.launch {
            val totalCount = itemsToCompress.size
            val processedItems = mutableListOf<BatchItem>()
            var totalOriginalBytes = 0L
            var totalCompressedBytesSoFar = 0L
            var completedCount = 0
            var failedCount = 0

            for (item in itemsToCompress) {
                totalOriginalBytes += item.sizeBytes
            }

            for ((index, meta) in itemsToCompress.withIndex()) {
                val currentIndex1Based = index + 1

                // Compute optimized config for this specific video based on chosen preset & codec
                val itemConfig = if (batchConfig.preset == CompressionPreset.CUSTOM) {
                    batchConfig
                } else {
                    VideoAnalyzer.getPresetConfig(
                        meta = meta,
                        preset = batchConfig.preset,
                        targetSizeMB = batchConfig.targetSizeMB,
                        codec = batchConfig.codec
                    )
                }

                // Initial batch progress update
                val initialOverallPercent = ((index.toFloat() / totalCount.toFloat()) * 100f).toInt()
                _state.value = AppState.BatchCompressing(
                    batchProgress = BatchProgress(
                        totalCount = totalCount,
                        currentIndex = currentIndex1Based,
                        completedCount = completedCount,
                        failedCount = failedCount,
                        overallPercent = initialOverallPercent,
                        currentItemProgress = CompressionProgress(percent = 0),
                        activeVideoName = meta.name,
                        totalOriginalBytes = totalOriginalBytes,
                        totalCompressedBytesSoFar = totalCompressedBytesSoFar
                    ),
                    currentItem = meta
                )

                try {
                    val result = engine.compress(
                        metadata = meta,
                        config = itemConfig,
                        onProgress = { itemProgress ->
                            val currentItemContribution = itemProgress.percent.toFloat() / 100f
                            val overall = (((index + currentItemContribution) / totalCount) * 100f)
                                .toInt().coerceIn(0, 99)

                            _state.value = AppState.BatchCompressing(
                                batchProgress = BatchProgress(
                                    totalCount = totalCount,
                                    currentIndex = currentIndex1Based,
                                    completedCount = completedCount,
                                    failedCount = failedCount,
                                    overallPercent = overall,
                                    currentItemProgress = itemProgress,
                                    activeVideoName = meta.name,
                                    totalOriginalBytes = totalOriginalBytes,
                                    totalCompressedBytesSoFar = totalCompressedBytesSoFar + itemProgress.currentOutputSizeBytes
                                ),
                                currentItem = meta
                            )
                        }
                    )

                    totalCompressedBytesSoFar += result.compressedSizeBytes
                    completedCount++
                    processedItems.add(
                        BatchItem(
                            metadata = meta,
                            config = itemConfig,
                            status = BatchItemStatus.COMPLETED,
                            result = result
                        )
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        _state.value = AppState.BatchReady(itemsToCompress, batchConfig)
                        return@launch
                    }
                    failedCount++
                    processedItems.add(
                        BatchItem(
                            metadata = meta,
                            config = itemConfig,
                            status = BatchItemStatus.FAILED,
                            error = e.message ?: "Failed hardware transcode"
                        )
                    )
                }
            }

            // All videos finished in batch queue!
            var totalDurationMs = 0L
            for (item in itemsToCompress) {
                totalDurationMs += item.durationMs
            }

            val batchResult = BatchResult(
                items = processedItems,
                totalOriginalBytes = totalOriginalBytes,
                totalCompressedBytes = totalCompressedBytesSoFar,
                totalDurationMs = totalDurationMs,
                successfulCount = completedCount,
                failedCount = failedCount
            )

            _state.value = AppState.BatchCompleted(batchResult)
        }
    }

    fun cancelCompress() {
        compressionJob?.cancel()
        engine.cancel()
        val current = _state.value
        when (current) {
            is AppState.Compressing -> _state.value = AppState.Ready(current.metadata)
            is AppState.BatchCompressing -> {
                // Return to BatchReady
                _state.value = AppState.Idle
            }
            else -> _state.value = AppState.Idle
        }
    }

    fun retry() {
        val current = _state.value
        if (current is AppState.Error && current.lastMetadata != null) {
            _state.value = AppState.Ready(current.lastMetadata)
        } else {
            _state.value = AppState.Idle
        }
    }

    fun reset() {
        compressionJob?.cancel()
        engine.cancel()
        _state.value = AppState.Idle
    }
}
