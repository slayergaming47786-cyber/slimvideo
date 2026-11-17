package com.slimvideo.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.slimvideo.app.model.CompressionConfig
import com.slimvideo.app.model.CompressionPreset
import com.slimvideo.app.model.VideoCodec
import com.slimvideo.app.model.VideoMetadata
import com.slimvideo.app.ui.theme.*
import com.slimvideo.app.utils.VideoAnalyzer

@Composable
fun BatchReadyCard(
    items: List<VideoMetadata>,
    initialConfig: CompressionConfig,
    onRemoveItem: (Int) -> Unit,
    onAddVideos: (List<Uri>) -> Unit,
    onStartBatchCompress: (CompressionConfig) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf(initialConfig.preset) }
    var selectedCodec by remember { mutableStateOf(initialConfig.codec) }
    var targetSizeMB by remember { mutableFloatStateOf(10f) }

    val multiplePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onAddVideos(uris)
        }
    }

    val totalOriginalBytes = remember(items) {
        items.sumOf { it.sizeBytes }
    }

    val totalDurationSecondsDouble = remember(items) {
        items.sumOf { it.durationSecondsDouble }
    }

    val batchConfig = remember(selectedPreset, selectedCodec, targetSizeMB) {
        if (items.isNotEmpty()) {
            VideoAnalyzer.getPresetConfig(
                meta = items.first(),
                preset = selectedPreset,
                targetSizeMB = targetSizeMB.toInt(),
                codec = selectedCodec
            )
        } else initialConfig
    }

    val estimatedTotalBytes = remember(batchConfig, totalDurationSecondsDouble) {
        VideoAnalyzer.estimateOutputSizeBytes(batchConfig, totalDurationSecondsDouble)
    }

    val totalSavedBytes = (totalOriginalBytes - estimatedTotalBytes).coerceAtLeast(0L)
    val totalSavedPercent = if (totalOriginalBytes > 0) {
        ((totalSavedBytes.toFloat() / totalOriginalBytes.toFloat()) * 100f).coerceIn(0f, 100f)
    } else 0f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Queue Summary Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(
                    1.5.dp,
                    Brush.horizontalGradient(listOf(CyanAccent.copy(alpha = 0.5f), PurpleAccent.copy(alpha = 0.3f))),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyanAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Batch Queue (${items.size} Videos)",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total Size: ${VideoAnalyzer.formatBytes(totalOriginalBytes)}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Clear / Reset
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Queue",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Estimated Savings Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldGreen.copy(alpha = 0.15f))
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estimated Output: ~${VideoAnalyzer.formatBytes(estimatedTotalBytes)}",
                        color = EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.0f%% SAVED", totalSavedPercent),
                        color = EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // List of videos in queue (Up to 20+ videos)
        Text(
            text = "Videos in Queue",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { index, meta ->
                BatchQueueItemRow(
                    index = index + 1,
                    metadata = meta,
                    onDelete = { onRemoveItem(index) }
                )
            }
        }

        // Add More Videos button
        OutlinedButton(
            onClick = { multiplePickerLauncher.launch("video/*") },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(CyanAccent.copy(alpha = 0.5f), PurpleAccent.copy(alpha = 0.5f)))
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Add More Videos to Batch",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Codec Selector
        Text(
            text = "Batch Encoding Hardware Codec",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedCodec == VideoCodec.H264) DarkSurfaceVariant else DarkSurface)
                    .border(
                        1.dp,
                        if (selectedCodec == VideoCodec.H264) CyanAccent else BorderColor,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { selectedCodec = VideoCodec.H264 }
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "H.264 (Universal)",
                        color = if (selectedCodec == VideoCodec.H264) CyanAccent else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Plays everywhere",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedCodec == VideoCodec.H265) DarkSurfaceVariant else DarkSurface)
                    .border(
                        1.dp,
                        if (selectedCodec == VideoCodec.H265) CyanAccent else BorderColor,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { selectedCodec = VideoCodec.H265 }
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "⚡ H.265 (HEVC)",
                        color = if (selectedCodec == VideoCodec.H265) CyanAccent else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "40% smaller • VPU Turbo",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Batch Presets
        Text(
            text = "Batch Compression Preset",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BatchPresetItem(
                title = "⚡ Smart Auto (720p HD)",
                subtitle = "Crisp balance • ~10–12 MB per video",
                isSelected = selectedPreset == CompressionPreset.AUTO,
                onClick = { selectedPreset = CompressionPreset.AUTO }
            )
            BatchPresetItem(
                title = "📉 Ultra Space Saver (480p)",
                subtitle = "Maximum compression • ~4–5 MB per video",
                isSelected = selectedPreset == CompressionPreset.ULTRA,
                onClick = { selectedPreset = CompressionPreset.ULTRA }
            )
            BatchPresetItem(
                title = "⚖️ Balanced (720p HD)",
                subtitle = "High clarity • ~12–14 MB per video",
                isSelected = selectedPreset == CompressionPreset.BALANCED,
                onClick = { selectedPreset = CompressionPreset.BALANCED }
            )
            BatchPresetItem(
                title = "⭐ High Quality (1080p FHD)",
                subtitle = "Full HD • ~28–32 MB per video",
                isSelected = selectedPreset == CompressionPreset.HIGH,
                onClick = { selectedPreset = CompressionPreset.HIGH }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Start Batch Compression Button
        Button(
            onClick = { onStartBatchCompress(batchConfig) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(CyanAccent, Color(0xFF0284C7))
                    )
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Batch Compression (${items.size} Videos)",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun BatchQueueItemRow(
    index: Int,
    metadata: VideoMetadata,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Video Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(metadata.uri)
                        .videoFrameMillis(1000)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${index}. ${metadata.name}",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${VideoAnalyzer.formatBytes(metadata.sizeBytes)} • ${VideoAnalyzer.formatDuration(metadata.durationMs)} • ${metadata.width}×${metadata.height}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Remove Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove video",
                    tint = RoseRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BatchPresetItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) DarkSurfaceVariant else DarkSurface)
            .border(
                1.dp,
                if (isSelected) CyanAccent else BorderColor,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = if (isSelected) CyanAccent else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(CyanAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
