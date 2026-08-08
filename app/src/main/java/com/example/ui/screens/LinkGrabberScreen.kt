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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BatchLinkParser
import com.example.engine.BatchParseResult
import com.example.engine.CapturedLinkItem
import com.example.engine.GrabbedLink
import com.example.engine.WebGrabResult
import com.example.ui.components.FolderSelectorDialog
import com.example.ui.components.GrabbedLinkCard
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LinkGrabberScreen(
    isExamining: Boolean,
    lastResult: WebGrabResult?,
    globalDownloadDir: String,
    autoOrganizeMode: String,
    capturedLinks: List<CapturedLinkItem>,
    isMonitoringBackground: Boolean,
    onToggleBackgroundMonitoring: (Boolean) -> Unit,
    onAddCapturedToQueue: (List<CapturedLinkItem>, String?) -> Unit,
    onRemoveCapturedItem: (Long) -> Unit,
    onClearAllCaptured: () -> Unit,
    onExamineUrl: (String) -> Unit,
    onAddLinksToQueue: (links: List<GrabbedLink>, customFolder: String?) -> Unit
) {
    var subTab by remember { mutableStateOf(0) } // 0: Sniffer, 1: Batch & Split, 2: Background History
    val clipboardManager = LocalClipboardManager.current

    // State for Sniffer
    var urlInput by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    // State for Batch Text
    var batchTextInput by remember { mutableStateOf("") }
    var batchResult by remember { mutableStateOf<BatchParseResult?>(null) }

    // Multi-selection state for Sniffer
    val selectedIndices = remember(lastResult) {
        mutableStateListOf<Int>().apply {
            lastResult?.links?.indices?.let { addAll(it) }
        }
    }

    // Selection state for Captured Links
    val selectedCapturedIds = remember(capturedLinks) {
        mutableStateListOf<Long>().apply {
            addAll(capturedLinks.map { it.id })
        }
    }

    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingFolderAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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
        FolderSelectorDialog(
            selectedCount = 1,
            currentOrganizeMode = autoOrganizeMode,
            currentGlobalDir = globalDownloadDir,
            onConfirm = { folderPath ->
                pendingFolderAction?.invoke()
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

            // Sub-Tab Switcher Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                color = DarkCardBg
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabs = listOf(
                        "Extractor Único" to Icons.Default.Language,
                        "Lote / Divididos" to Icons.Default.FolderZip,
                        "Segundo Plano (${capturedLinks.size})" to Icons.Default.History
                    )

                    tabs.forEachIndexed { index, (label, icon) ->
                        val isSelected = subTab == index
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { subTab = index }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) NeonCyan else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) NeonCyan else TextMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (subTab) {
                0 -> {
                    // TAB 0: SINGLE WEB & MEDIA SNIFFER
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
                                placeholder = { Text("Pega enlace de TikTok, Instagram o Web...", color = TextMuted) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (urlInput.isNotEmpty()) {
                                            IconButton(
                                                onClick = { urlInput = "" },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Limpiar enlace",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipText = clipboardManager.getText()?.text.orEmpty()
                                                if (clipText.isNotBlank()) {
                                                    urlInput = clipText.trim()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Pegar enlace",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
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
                                    text = "Examinando servidor de contenido y extrayendo videos HD...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else if (lastResult != null) {
                        if (lastResult.errorMessage != null || lastResult.totalLinksFound == 0) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                                color = DarkCardBg
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Resultado del Análisis",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = lastResult.errorMessage ?: "No se encontraron enlaces o archivos multimedia en esta URL.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        } else {
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
                        }
                    } else {
                        // Preset Quick Buttons
                        Text(
                            text = "Sitios populares y pruebas rápidas:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val presets = listOf(
                            "📄 PDF de prueba" to "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
                            "🖼️ Imagen de prueba" to "https://picsum.photos/1024/768.jpg",
                            "📦 Archivo de prueba (1MB)" to "https://httpbin.org/bytes/1048576"
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
                    }
                }

                1 -> {
                    // TAB 1: BATCH LINK PARSER & SPLIT ARCHIVE GROUPING
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                        color = DarkCardBg
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pegar Texto Múltiple o Archivos Divididos",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                IconButton(onClick = {
                                    val clipText = clipboardManager.getText()?.text.orEmpty()
                                    if (clipText.isNotBlank()) {
                                        batchTextInput = clipText
                                        batchResult = BatchLinkParser.parseTextBlob(clipText)
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Pegar", tint = NeonCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = batchTextInput,
                                onValueChange = {
                                    batchTextInput = it
                                    batchResult = BatchLinkParser.parseTextBlob(it)
                                },
                                placeholder = {
                                    Text(
                                        "Pega aquí un bloque de texto con múltiples enlaces (ejemplo:\nhttps://site.com/juego.part1.rar\nhttps://site.com/juego.part2.rar)",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                },
                                maxLines = 6,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val result = batchResult
                    if (result != null && result.totalUrlsFound > 0) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            // SPLIT ARCHIVES SECTION
                            if (result.splitPackages.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "PAQUETES DE ARCHIVOS DIVIDIDOS DETECTADOS (${result.splitPackages.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonPurple,
                                        letterSpacing = 1.sp
                                    )
                                }

                                items(result.splitPackages) { pkg ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                        color = DarkCardBg
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, tint = NeonPurple)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(text = pkg.packageName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                                        Text(text = "${pkg.totalPartsFound} partes encontradas", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                                    }
                                                }

                                                Button(
                                                    onClick = {
                                                        onAddLinksToQueue(pkg.items, null)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Descargar Paquete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            if (pkg.missingPartNumbers.isNotEmpty()) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF331500)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Atención: Faltan las partes ${pkg.missingPartNumbers.joinToString(", ")}",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFFFFCC80)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF002211)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Paquete Completo: Todas las partes consecutivas están presentes.",
                                                            fontSize = 11.sp,
                                                            color = NeonEmerald
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // STANDALONE LINKS SECTION
                            if (result.standaloneLinks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "ENLACES INDEPENDIENTES EXTRAÍDOS (${result.standaloneLinks.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        letterSpacing = 1.sp
                                    )
                                }

                                items(result.standaloneLinks) { link ->
                                    GrabbedLinkCard(
                                        link = link,
                                        isSelected = true,
                                        onToggleSelect = {}
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pega cualquier lista de enlaces para extraerlos e identificar paquetes rar/zip divididos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }

                2 -> {
                    // TAB 2: BACKGROUND LINK CAPTURE HISTORY
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                        color = DarkCardBg
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.History, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "Capturador en Segundo Plano", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                    Text(text = "Monitorea enlaces copiados continuamente", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }

                            Switch(
                                checked = isMonitoringBackground,
                                onCheckedChange = onToggleBackgroundMonitoring,
                                colors = SwitchDefaults.colors(checkedThumbColor = DarkSurface, checkedTrackColor = NeonCyan)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (capturedLinks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HISTORIAL DE ENLACES CAPTURADOS (${capturedLinks.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )

                            Row {
                                TextButton(onClick = {
                                    val selected = capturedLinks.filter { selectedCapturedIds.contains(it.id) }
                                    onAddCapturedToQueue(selected, null)
                                }) {
                                    Text("Descargar Seleccionados (${selectedCapturedIds.size})", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = onClearAllCaptured) {
                                    Text("Limpiar", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val sdf = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(capturedLinks) { captured ->
                                val isSelected = selectedCapturedIds.contains(captured.id)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, if (isSelected) NeonCyan.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isSelected) selectedCapturedIds.remove(captured.id) else selectedCapturedIds.add(captured.id)
                                        },
                                    color = DarkCardBg
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) NeonCyan else DarkSurface)
                                                .border(1.dp, if (isSelected) NeonCyan else TextMuted, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = DarkSurface, modifier = Modifier.size(14.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = NeonCyan.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = captured.domain,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 10.sp,
                                                        color = NeonCyan,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = sdf.format(Date(captured.capturedAtTimestamp)),
                                                    fontSize = 10.sp,
                                                    color = TextMuted
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = captured.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimary,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = captured.url,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted,
                                                maxLines = 1
                                            )
                                        }

                                        IconButton(onClick = { onRemoveCapturedItem(captured.id) }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar", tint = TextMuted, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aún no se han capturado enlaces. Copia cualquier URL en tu dispositivo para guardarla automáticamente.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Bar for Sniffer Tab
        if (subTab == 0 && selectedIndices.isNotEmpty() && lastResult != null) {
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
                        val selectedLinks = currentLinks.filterIndexed { idx, _ -> selectedIndices.contains(idx) }
                        Text(
                            text = "Añadir a la cola de descargas",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }

                    Button(
                        onClick = {
                            val selectedLinks = currentLinks.filterIndexed { idx, _ -> selectedIndices.contains(idx) }
                            onAddLinksToQueue(selectedLinks, null)
                        },
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
