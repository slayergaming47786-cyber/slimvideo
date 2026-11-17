package com.slimvideo.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slimvideo.app.model.AppState
import com.slimvideo.app.ui.components.*
import com.slimvideo.app.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure hardware window acceleration
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        handleShareIntent(intent)

        setContent {
            SlimVideoTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type ?: ""

        if (Intent.ACTION_SEND == action && type.startsWith("video/")) {
            val videoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            videoUri?.let { viewModel.selectVideo(it) }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type.startsWith("video/")) {
            val videoUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!videoUris.isNullOrEmpty()) {
                viewModel.selectVideos(videoUris)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Header()

            // Main Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (val currentState = state) {
                    is AppState.Idle -> {
                        Dropzone(
                            onVideoSelected = { uri -> viewModel.selectVideo(uri) },
                            onBatchSelected = { uris -> viewModel.selectVideos(uris) }
                        )
                    }

                    is AppState.Analyzing -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = CyanAccent,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (currentState.total > 1) {
                                        "Analyzing Video ${currentState.current} of ${currentState.total}..."
                                    } else {
                                        "Analyzing Video..."
                                    },
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Reading resolution, bitrate, audio tracks & VPU capabilities",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Single Video Ready
                    is AppState.Ready -> {
                        VideoInfoCard(
                            metadata = currentState.metadata,
                            onChangeVideo = { viewModel.reset() }
                        )
                        CompressionControls(
                            metadata = currentState.metadata,
                            onStartCompress = { config -> viewModel.startCompress(config) }
                        )
                    }

                    // Single Video Compressing
                    is AppState.Compressing -> {
                        VideoInfoCard(
                            metadata = currentState.metadata,
                            onChangeVideo = {}
                        )
                        CompressingDialog(
                            progress = currentState.progress,
                            onCancel = { viewModel.cancelCompress() }
                        )
                    }

                    // Single Video Completed
                    is AppState.Completed -> {
                        ResultCard(
                            result = currentState.result,
                            onReset = { viewModel.reset() }
                        )
                    }

                    // Batch Videos Ready (2 to 20+ videos)
                    is AppState.BatchReady -> {
                        BatchReadyCard(
                            items = currentState.items,
                            initialConfig = currentState.config,
                            onRemoveItem = { index -> viewModel.removeBatchItem(index) },
                            onAddVideos = { uris -> viewModel.addVideosToBatch(uris) },
                            onStartBatchCompress = { config -> viewModel.startBatchCompress(config) },
                            onReset = { viewModel.reset() }
                        )
                    }

                    // Batch Videos Compressing
                    is AppState.BatchCompressing -> {
                        BatchCompressingDialog(
                            batchProgress = currentState.batchProgress,
                            currentItem = currentState.currentMetadata,
                            onCancel = { viewModel.cancelCompress() }
                        )
                    }

                    // Batch Videos Completed
                    is AppState.BatchCompleted -> {
                        BatchResultCard(
                            batchResult = currentState.batchResult,
                            onReset = { viewModel.reset() }
                        )
                    }

                    is AppState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkSurface)
                                .border(1.dp, RoseRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = RoseRed,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Compression Notice",
                                    color = RoseRed,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentState.message,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.retry() },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Try Again", color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "100% Offline • MediaCodec GPU/VPU Turbo • Zero Data Transfer",
                    color = TextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
