package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DownloadItem
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FolderGroup(
    val folderName: String,
    val folderPath: String,
    val totalSizeBytes: Long,
    val items: List<DownloadItem>
)

@Composable
fun StorageScreen(
    completedItems: List<DownloadItem>,
    onTriggerDriveSync: () -> Unit
) {
    var organizeViewMode by remember { mutableStateOf("DATE") }

    // Group completed items based on mode
    val groups = remember(completedItems, organizeViewMode) {
        when (organizeViewMode) {
            "TYPE" -> {
                completedItems.groupBy { item ->
                    when (item.mediaType) {
                        "IMAGE" -> "Imágenes (JPG, PNG, WEBP)"
                        "VIDEO" -> "Videos (MP4, MKV)"
                        "AUDIO" -> "Música y Audios"
                        "ARCHIVE" -> "Archivos Comprimidos (ZIP, RAR)"
                        else -> "Documentos y Otros"
                    }
                }.map { (name, list) ->
                    FolderGroup(
                        folderName = name,
                        folderPath = "PulseDownloader/$name",
                        totalSizeBytes = list.sumOf { it.fileSizeBytes },
                        items = list
                    )
                }
            }
            "DOMAIN" -> {
                completedItems.groupBy { it.domain.ifBlank { "Directo" } }
                    .map { (domain, list) ->
                        FolderGroup(
                            folderName = "Sitio: $domain",
                            folderPath = "PulseDownloader/Sitios/$domain",
                            totalSizeBytes = list.sumOf { it.fileSizeBytes },
                            items = list
                        )
                    }
            }
            else -> { // "DATE"
                val sdf = SimpleDateFormat("yyyy-MM-DD", Locale.getDefault())
                completedItems.groupBy { item ->
                    if (item.completedAtTimestamp > 0) {
                        sdf.format(Date(item.completedAtTimestamp))
                    } else "2026-08-06"
                }.map { (dateStr, list) ->
                    FolderGroup(
                        folderName = "Fecha: $dateStr",
                        folderPath = "PulseDownloader/$dateStr",
                        totalSizeBytes = list.sumOf { it.fileSizeBytes },
                        items = list
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // View Mode Switcher Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
            color = DarkCardBg
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ORGANIZACIÓN AUTOMÁTICA DE ARCHIVOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Explorar Carpetas Estructuradas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                val modes = listOf(
                    "DATE" to "Por Fecha",
                    "TYPE" to "Por Categoría",
                    "DOMAIN" to "Por Sitio Web"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { (mode, label) ->
                        val isSelected = organizeViewMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) NeonCyan else DarkBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { organizeViewMode = mode },
                            color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurface
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) NeonCyan else TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aún no hay archivos completados",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Los archivos completados se organizarán automáticamente aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(groups) { group ->
                    FolderGroupCard(group = group, onTriggerDriveSync = onTriggerDriveSync)
                }
            }
        }
    }
}

@Composable
fun FolderGroupCard(
    group: FolderGroup,
    onTriggerDriveSync: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val totalMb = group.totalSizeBytes / (1024f * 1024f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        color = DarkCardBg
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = group.folderName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "${group.items.size} archivos • ${String.format("%.1f MB", totalMb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", color = TextMuted, fontSize = 12.sp)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.items.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                            color = DarkSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
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
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = String.format("%.1f MB", item.fileSizeBytes / (1024f * 1024f)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = onTriggerDriveSync,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = "Drive Sync",
                                            tint = if (item.driveSynced) NeonEmerald else TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
