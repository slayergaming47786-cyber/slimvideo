package com.slimvideo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slimvideo.app.model.CompressionResult
import com.slimvideo.app.ui.theme.*
import com.slimvideo.app.utils.MediaStoreHelper
import com.slimvideo.app.utils.VideoAnalyzer
import java.io.File

@Composable
fun ResultCard(
    result: CompressionResult,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    listOf(EmeraldGreen.copy(alpha = 0.5f), CyanAccent.copy(alpha = 0.2f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success Checkmark Badge
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen.copy(alpha = 0.15f))
                    .border(1.5.dp, EmeraldGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Compression Finished!",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Saved automatically to Gallery (Movies/SlimVideo)",
                    color = EmeraldGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // Before vs After Comparison Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Original Size", color = TextMuted, fontSize = 11.sp)
                            Text(
                                text = VideoAnalyzer.formatBytes(result.originalSizeBytes),
                                color = RoseRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(BorderColor)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Compressed Size", color = TextMuted, fontSize = 11.sp)
                            Text(
                                text = VideoAnalyzer.formatBytes(result.compressedSizeBytes),
                                color = EmeraldGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Total Saved Highlight
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldGreen.copy(alpha = 0.15f))
                            .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(
                                java.util.Locale.US,
                                "🎉 %.1f%% SPACE SAVED (-%s)",
                                result.savedPercent,
                                VideoAnalyzer.formatBytes(result.savedBytes)
                            ),
                            color = EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Play Video Button
                Button(
                    onClick = { MediaStoreHelper.openVideo(context, result.outputUri) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play Video",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Share to WhatsApp / Telegram
                OutlinedButton(
                    onClick = {
                        val file = File(result.outputFilePath)
                        MediaStoreHelper.shareVideo(context, file)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(CyanAccent, PurpleAccent))
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share Video (WhatsApp / Social)",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                // Compress Another Video
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Compress Another Video",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
