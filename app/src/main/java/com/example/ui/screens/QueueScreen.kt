package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.DownloadItem
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.SpeedGraphHeader
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun QueueScreen(
    downloads: List<DownloadItem>,
    globalSpeedBytesPerSec: Long,
    isGlobalQueuePaused: Boolean,
    settings: AppSettings,
    onPauseSingle: (Long) -> Unit,
    onResumeSingle: (Long) -> Unit,
    onCancelSingle: (Long) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onUpdateSpeedLimit: (Int) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredList = when (selectedFilter) {
        "DOWNLOADING" -> downloads.filter { it.status == "DOWNLOADING" }
        "PAUSED" -> downloads.filter { it.status == "PAUSED" || it.status == "PENDING" }
        "COMPLETED" -> downloads.filter { it.status == "COMPLETED" }
        else -> downloads
    }

    val activeCount = downloads.count { it.status == "DOWNLOADING" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Speed & Queue Header Card
        SpeedGraphHeader(
            globalSpeedBytesPerSec = globalSpeedBytesPerSec,
            activeCount = activeCount,
            maxConcurrent = settings.maxConcurrentDownloads,
            isGlobalPaused = isGlobalQueuePaused,
            speedLimitKbps = settings.speedLimitKbps,
            driveSyncEnabled = settings.googleDriveSyncEnabled,
            onTogglePauseAll = {
                if (isGlobalQueuePaused) onResumeAll() else onPauseAll()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Speed Limiter Chips Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Límite Ancho Banda:",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.width(8.dp))

            val speedOptions = listOf(
                0 to "Sin límite",
                1024 to "1 MB/s",
                5120 to "5 MB/s",
                10240 to "10 MB/s"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(speedOptions) { (kbps, label) ->
                    val isSelected = settings.speedLimitKbps == kbps
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                1.dp,
                                if (isSelected) NeonCyan else DarkBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onUpdateSpeedLimit(kbps) }
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NeonCyan else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Bar & Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf(
                "ALL" to "Todas (${downloads.size})",
                "DOWNLOADING" to "En Curso (${downloads.count { it.status == "DOWNLOADING" }})",
                "PAUSED" to "En Cola (${downloads.count { it.status == "PAUSED" || it.status == "PENDING" }})",
                "COMPLETED" to "Completadas (${downloads.count { it.status == "COMPLETED" }})"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filters) { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) DarkCardBg else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (isSelected) NeonCyan else DarkBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onClearCompleted,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Limpiar completadas", fontSize = 12.sp, color = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Download List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(DarkCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "La cola de descargas está vacía",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ve a 'Capturador' para examinar enlaces de cualquier página web.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = onPauseSingle,
                        onResume = onResumeSingle,
                        onCancel = onCancelSingle
                    )
                }
            }
        }
    }
}
