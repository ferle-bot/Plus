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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GrabbedLink
import com.example.engine.WebGrabResult
import com.example.ui.components.FolderSelectorDialog
import com.example.ui.components.GrabbedLinkCard
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LinkGrabberScreen(
    isExamining: Boolean,
    lastResult: WebGrabResult?,
    globalDownloadDir: String,
    autoOrganizeMode: String,
    onExamineUrl: (String) -> Unit,
    onAddLinksToQueue: (links: List<GrabbedLink>, customFolder: String?) -> Unit
) {
    var urlInput by remember { mutableStateOf("https://instagram.com/fernando.garcia.langle") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    // Multi-selection state
    val selectedIndices = remember(lastResult) {
        mutableStateListOf<Int>().apply {
            lastResult?.links?.indices?.let { addAll(it) }
        }
    }

    var showFolderDialog by remember { mutableStateOf(false) }

    val currentLinks = lastResult?.links ?: emptyList()

    val filteredLinks = currentLinks.filter { link ->
        when (selectedCategoryFilter) {
            "IMAGE" -> link.mediaType == "IMAGE"
            "VIDEO" -> link.mediaType == "VIDEO"
            "AUDIO" -> link.mediaType == "AUDIO"
            "ARCHIVE" -> link.mediaType == "ARCHIVE"
            else -> true
        }
    }

    if (showFolderDialog) {
        val selectedLinks = currentLinks.filterIndexed { index, _ -> selectedIndices.contains(index) }
        FolderSelectorDialog(
            selectedCount = selectedLinks.size,
            currentOrganizeMode = autoOrganizeMode,
            currentGlobalDir = globalDownloadDir,
            onConfirm = { folderPath ->
                onAddLinksToQueue(selectedLinks, folderPath)
                showFolderDialog = false
            },
            onDismiss = { showFolderDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // URL Search Input
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                color = DarkCardBg
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("Pega enlace web (ej. instagram.com/usuario)", color = TextMuted) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                onExamineUrl(urlInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isExamining
                    ) {
                        if (isExamining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DarkSurface,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Examinar",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Examinar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset Quick Buttons
            Text(
                text = "Sitios populares y pruebas rápidas:",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            val presets = listOf(
                "📸 Mi Instagram" to "https://instagram.com/fernando.garcia.langle",
                "🖼️ Unsplash 4K" to "https://unsplash.com/wallpapers",
                "📦 Archivo Directo" to "https://archive.org/download/sample_pack.zip"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { (title, url) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                urlInput = url
                                onExamineUrl(url)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isExamining) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonCyan)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Examinando sitio web y extrayendo enlaces multimedia...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else if (lastResult != null) {
                // Results Header
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                    color = DarkCardBg
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = lastResult.pageTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${lastResult.totalLinksFound} enlaces encontrados en ${lastResult.domain}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan
                                )
                            }

                            Row {
                                TextButton(
                                    onClick = {
                                        selectedIndices.clear()
                                        selectedIndices.addAll(currentLinks.indices)
                                    }
                                ) {
                                    Text("Todos", fontSize = 12.sp, color = NeonCyan)
                                }
                                TextButton(
                                    onClick = { selectedIndices.clear() }
                                ) {
                                    Text("Ninguno", fontSize = 12.sp, color = TextMuted)
                                }
                            }
                        }

                        // Category Filter Chips
                        Spacer(modifier = Modifier.height(8.dp))
                        val categories = listOf(
                            "ALL" to "Todos (${currentLinks.size})",
                            "IMAGE" to "Fotos (${currentLinks.count { it.mediaType == "IMAGE" }})",
                            "VIDEO" to "Videos (${currentLinks.count { it.mediaType == "VIDEO" }})",
                            "ARCHIVE" to "Zips (${currentLinks.count { it.mediaType == "ARCHIVE" }})"
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories) { (cat, label) ->
                                val isSelected = selectedCategoryFilter == cat
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkSurface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonCyan else DarkBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedCategoryFilter = cat }
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        color = if (isSelected) NeonCyan else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Link Cards List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(filteredLinks) { link ->
                        val index = currentLinks.indexOf(link)
                        val isSelected = selectedIndices.contains(index)
                        GrabbedLinkCard(
                            link = link,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) selectedIndices.remove(index) else selectedIndices.add(index)
                            }
                        )
                    }
                }
            } else {
                // Empty Initial State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Examinador de Enlaces Web",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pega cualquier URL de Instagram, galería web o archivo para capturar todos sus medios.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        // Floating Action Bottom Bar to Add Selected to Queue
        if (selectedIndices.isNotEmpty() && lastResult != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, NeonCyan, RoundedCornerShape(20.dp)),
                color = DarkCardBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedIndices.size} Enlaces Seleccionados",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        val totalMb = currentLinks
                            .filterIndexed { idx, _ -> selectedIndices.contains(idx) }
                            .sumOf { it.estimatedSizeBytes } / (1024f * 1024f)
                        Text(
                            text = "Tamaño total est: ${String.format("%.1f MB", totalMb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }

                    Button(
                        onClick = { showFolderDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Descargar Todos", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
