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

    fun selectVideo(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.value = AppState.Analyzing
                val metadata = VideoAnalyzer.analyze(getApplication(), uri)
                _state.value = AppState.Ready(metadata)
            } catch (e: Exception) {
                _state.value = AppState.Error(
                    e.message ?: "Failed to read video file. Please ensure it is a valid video."
                )
            }
        }
    }

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
                        e.message ?: "Video compression failed. Please check device hardware encoder."
                    )
                }
            }
        }
    }

    fun cancelCompress() {
        compressionJob?.cancel()
        engine.cancel()
        val current = _state.value
        if (current is AppState.Compressing) {
            _state.value = AppState.Ready(current.metadata)
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
