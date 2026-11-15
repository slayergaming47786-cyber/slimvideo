package com.slimvideo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slimvideo.app.model.CompressionConfig
import com.slimvideo.app.model.CompressionPreset
import com.slimvideo.app.model.VideoMetadata
import com.slimvideo.app.ui.theme.*
import com.slimvideo.app.utils.VideoAnalyzer

@Composable
fun CompressionControls(
    metadata: VideoMetadata,
    onStartCompress: (CompressionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf(CompressionPreset.AUTO) }
    var targetSizeMB by remember { mutableFloatStateOf(10f) }

    // Custom Mode states
    var customMaxDim by remember { mutableIntStateOf(1280) } // 0 = original, 1920, 1280, 960, 854
    var customVideoBitrateKbps by remember { mutableFloatStateOf(450f) }
    var customFps by remember { mutableIntStateOf(30) }
    var customAudioBitrateKbps by remember { mutableIntStateOf(64) }

    val config = remember(
        metadata,
        selectedPreset,
        targetSizeMB,
        customMaxDim,
        customVideoBitrateKbps,
        customFps,
        customAudioBitrateKbps
    ) {
        if (selectedPreset == CompressionPreset.CUSTOM) {
            val (w, h) = if (customMaxDim == 0) {
                Pair(VideoAnalyzer.makeEven(metadata.width), VideoAnalyzer.makeEven(metadata.height))
            } else {
                VideoAnalyzer.calculateScaledDimensions(metadata.width, metadata.height, customMaxDim)
            }
            CompressionConfig(
                preset = CompressionPreset.CUSTOM,
                targetWidth = w,
                targetHeight = h,
                targetVideoBitrateBps = (customVideoBitrateKbps * 1000).toInt(),
                targetAudioBitrateBps = customAudioBitrateKbps * 1000,
                targetFps = customFps
            )
        } else {
            VideoAnalyzer.getPresetConfig(metadata, selectedPreset, targetSizeMB.toInt())
        }
    }

    val estimatedBytes = remember(config, metadata) {
        VideoAnalyzer.estimateOutputSizeBytes(config, metadata.durationSeconds)
    }

    val savedBytes = (metadata.sizeBytes - estimatedBytes).coerceAtLeast(0L)
    val savedPercent = if (metadata.sizeBytes > 0) {
        ((savedBytes.toFloat() / metadata.sizeBytes.toFloat()) * 100f).coerceIn(0f, 100f)
    } else 0f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Select Compression Preset",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp)
        )

        // Preset Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetOptionCard(
                preset = CompressionPreset.AUTO,
                title = "⚡ Smart Auto (Recommended)",
                description = "Crisp 720p HD • Ideal balance (~10–12 MB)",
                isSelected = selectedPreset == CompressionPreset.AUTO,
                onClick = { selectedPreset = CompressionPreset.AUTO }
            )

            PresetOptionCard(
                preset = CompressionPreset.ULTRA,
                title = "📉 Ultra Space Saver",
                description = "480p • Maximum compression (~4–5 MB)",
                isSelected = selectedPreset == CompressionPreset.ULTRA,
                onClick = { selectedPreset = CompressionPreset.ULTRA }
            )

            PresetOptionCard(
                preset = CompressionPreset.BALANCED,
                title = "⚖️ Balanced",
                description = "720p HD @ 380 kbps (~12–14 MB)",
                isSelected = selectedPreset == CompressionPreset.BALANCED,
                onClick = { selectedPreset = CompressionPreset.BALANCED }
            )

            PresetOptionCard(
                preset = CompressionPreset.HIGH,
                title = "⭐ High Quality",
                description = "1080p FHD @ 1 Mbps (~28–32 MB)",
                isSelected = selectedPreset == CompressionPreset.HIGH,
                onClick = { selectedPreset = CompressionPreset.HIGH }
            )

            PresetOptionCard(
                preset = CompressionPreset.TARGET_SIZE,
                title = "🎯 Target Size (Exact MB)",
                description = "Choose an exact output size in megabytes",
                isSelected = selectedPreset == CompressionPreset.TARGET_SIZE,
                onClick = { selectedPreset = CompressionPreset.TARGET_SIZE }
            )

            PresetOptionCard(
                preset = CompressionPreset.CUSTOM,
                title = "⚙️ Custom Settings",
                description = "Customize bitrate, resolution, FPS & audio quality",
                isSelected = selectedPreset == CompressionPreset.CUSTOM,
                onClick = { selectedPreset = CompressionPreset.CUSTOM }
            )
        }

        // Target Size Slider (Only shown when Target Size is selected)
        if (selectedPreset == CompressionPreset.TARGET_SIZE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Output Size",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${targetSizeMB.toInt()} MB",
                            color = CyanAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = targetSizeMB,
                        onValueChange = { targetSizeMB = it },
                        valueRange = 4f..100f,
                        steps = 95,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccent,
                            activeTrackColor = CyanAccent,
                            inactiveTrackColor = BorderColor
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "4 MB (WhatsApp min)", color = TextMuted, fontSize = 10.sp)
                        Text(text = "100 MB", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        // Custom Settings Panel (Only shown when Custom is selected)
        if (selectedPreset == CompressionPreset.CUSTOM) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, PurpleAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Custom Compression Controls",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 1. Resolution Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Target Resolution", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = "${config.targetWidth}×${config.targetHeight}",
                                color = CyanAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ResolutionChip(
                                label = "Original",
                                isSelected = customMaxDim == 0,
                                onClick = { customMaxDim = 0 }
                            )
                            ResolutionChip(
                                label = "1080p FHD",
                                isSelected = customMaxDim == 1920,
                                onClick = { customMaxDim = 1920 }
                            )
                            ResolutionChip(
                                label = "720p HD",
                                isSelected = customMaxDim == 1280,
                                onClick = { customMaxDim = 1280 }
                            )
                            ResolutionChip(
                                label = "540p",
                                isSelected = customMaxDim == 960,
                                onClick = { customMaxDim = 960 }
                            )
                            ResolutionChip(
                                label = "480p",
                                isSelected = customMaxDim == 854,
                                onClick = { customMaxDim = 854 }
                            )
                        }
                    }

                    // 2. Video Bitrate Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Video Bitrate", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = if (customVideoBitrateKbps >= 1000f) {
                                    String.format(java.util.Locale.US, "%.1f Mbps", customVideoBitrateKbps / 1000f)
                                } else {
                                    "${customVideoBitrateKbps.toInt()} kbps"
                                },
                                color = CyanAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = customVideoBitrateKbps,
                            onValueChange = { customVideoBitrateKbps = it },
                            valueRange = 100f..4000f,
                            steps = 77,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanAccent,
                                activeTrackColor = CyanAccent,
                                inactiveTrackColor = BorderColor
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "100 kbps (Max compression)", color = TextMuted, fontSize = 10.sp)
                            Text(text = "4000 kbps (High bitrate)", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    // 3. Frame Rate (FPS)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Frame Rate (FPS)", color = TextSecondary, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FpsChip(
                                label = "24 FPS (Cinema)",
                                isSelected = customFps == 24,
                                onClick = { customFps = 24 },
                                modifier = Modifier.weight(1f)
                            )
                            FpsChip(
                                label = "30 FPS (Standard)",
                                isSelected = customFps == 30,
                                onClick = { customFps = 30 },
                                modifier = Modifier.weight(1f)
                            )
                            FpsChip(
                                label = "60 FPS (Smooth)",
                                isSelected = customFps == 60,
                                onClick = { customFps = 60 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 4. Audio Quality
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Audio Quality", color = TextSecondary, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FpsChip(
                                label = "48 kbps",
                                isSelected = customAudioBitrateKbps == 48,
                                onClick = { customAudioBitrateKbps = 48 },
                                modifier = Modifier.weight(1f)
                            )
                            FpsChip(
                                label = "64 kbps",
                                isSelected = customAudioBitrateKbps == 64,
                                onClick = { customAudioBitrateKbps = 64 },
                                modifier = Modifier.weight(1f)
                            )
                            FpsChip(
                                label = "96 kbps",
                                isSelected = customAudioBitrateKbps == 96,
                                onClick = { customAudioBitrateKbps = 96 },
                                modifier = Modifier.weight(1f)
                            )
                            FpsChip(
                                label = "128 kbps",
                                isSelected = customAudioBitrateKbps == 128,
                                onClick = { customAudioBitrateKbps = 128 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Estimated Output Stats Box (Real-time update)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Estimated Output",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "~${VideoAnalyzer.formatBytes(estimatedBytes)}",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${config.targetWidth}×${config.targetHeight} @ ${config.targetFps}fps • ${(config.targetVideoBitrateBps / 1000)} kbps",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldGreen.copy(alpha = 0.15f))
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f%% SAVED", savedPercent),
                            color = EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "-${VideoAnalyzer.formatBytes(savedBytes)}",
                            color = EmeraldGreen.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Main CTA Compress Button
        Button(
            onClick = { onStartCompress(config) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Compress Video (Hardware Accelerated)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PresetOptionCard(
    preset: CompressionPreset,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CyanAccent else BorderColor
    val bgColor = if (isSelected) DarkSurfaceVariant else DarkSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = if (isSelected) CyanAccent else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
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

@Composable
private fun ResolutionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) CyanAccent else DarkSurface)
            .border(
                1.dp,
                if (isSelected) CyanAccent else BorderColor,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) DarkBackground else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun FpsChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PurpleAccent else DarkSurface)
            .border(
                1.dp,
                if (isSelected) PurpleAccent else BorderColor,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
