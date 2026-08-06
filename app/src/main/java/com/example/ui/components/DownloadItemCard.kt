package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.DownloadItem
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DownloadItemCard(
    item: DownloadItem,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = if (item.fileSizeBytes > 0) {
        (item.downloadedBytes.toFloat() / item.fileSizeBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(targetValue = progressFraction, label = "progress")

    val speedMb = item.downloadSpeedBytesPerSec / (1024f * 1024f)
    val speedText = if (speedMb >= 1.0f) {
        String.format("%.1f MB/s", speedMb)
    } else {
        String.format("%.0f KB/s", item.downloadSpeedBytesPerSec / 1024f)
    }

    val downloadedMb = item.downloadedBytes / (1024f * 1024f)
    val totalMb = item.fileSizeBytes / (1024f * 1024f)
    val sizeText = String.format("%.1f MB / %.1f MB", downloadedMb, totalMb)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        color = DarkCardBg
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media Thumbnail or Icon
                if (!item.thumbnailUrl.isNull_or_blank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (item.mediaType) {
                            "IMAGE" -> Icons.Default.Image
                            "VIDEO" -> Icons.Default.Movie
                            "AUDIO" -> Icons.Default.MusicNote
                            "ARCHIVE" -> Icons.Default.Archive
                            else -> Icons.Default.Description
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonCyan.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = item.domain.ifBlank { "sitio web" },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = item.targetFolderPath.substringAfterLast("/"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        "DOWNLOADING" -> {
                            IconButton(
                                onClick = { onPause(item.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pausar",
                                    tint = StatusAmber
                                )
                            }
                        }
                        "PAUSED" -> {
                            IconButton(
                                onClick = { onResume(item.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reanudar",
                                    tint = NeonEmerald
                                )
                            }
                        }
                        "COMPLETED" -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completado",
                                tint = StatusGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (item.status != "COMPLETED") {
                        IconButton(
                            onClick = { onCancel(item.id) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar",
                                tint = TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(DarkSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = when (item.status) {
                                    "COMPLETED" -> listOf(StatusGreen, NeonEmerald)
                                    "PAUSED" -> listOf(StatusAmber, Color(0xFFFFD166))
                                    else -> listOf(NeonCyan, NeonEmerald)
                                }
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed, ETA & Drive Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (item.status) {
                        "DOWNLOADING" -> "$sizeText (${(progressFraction * 100).toInt()}%)"
                        "PAUSED" -> "Pausado - $sizeText"
                        "COMPLETED" -> "Completado - ${String.format("%.1f MB", totalMb)}"
                        else -> "En cola - $sizeText"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.status == "DOWNLOADING") {
                        Text(
                            text = "$speedText • ETA: ${item.etaSeconds}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (item.driveSynced) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Drive Respaldo",
                            tint = NeonEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean {
    return this == null || this.isBlank()
}
