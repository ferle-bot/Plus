package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.ui.PulseViewModel
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PulseViewModel) {
    var currentTab by remember { mutableStateOf(0) } // 0: Queue, 1: Grabber, 2: Files, 3: Drive/Remote
    var showSettingsSheet by remember { mutableStateOf(false) }

    val allDownloads by viewModel.allDownloads.collectAsState()
    val completedDownloads by viewModel.completedDownloads.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isExaminingUrl by viewModel.isExaminingUrl.collectAsState()
    val lastGrabResult by viewModel.lastGrabResult.collectAsState()
    val globalSpeedBytesPerSec by viewModel.globalSpeedBytesPerSec.collectAsState()
    val isGlobalQueuePaused by viewModel.isGlobalQueuePaused.collectAsState()
    val driveSyncState by viewModel.driveSyncState.collectAsState()
    val remoteLogs by viewModel.remoteLogs.collectAsState()
    val isRemoteRunning by viewModel.isRemoteServerRunning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PulseDownloader",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCanvas)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkCardBg,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(0, "Colas", Icons.Default.Download),
                    Triple(1, "Capturador", Icons.Default.Language),
                    Triple(2, "Archivos", Icons.Default.Folder),
                    Triple(3, "Drive & Remoto", Icons.Default.Cloud)
                )

                navItems.forEach { (index, title, icon) ->
                    val isSelected = currentTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = if (isSelected) NeonCyan else TextMuted
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonCyan else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        containerColor = DarkCanvas
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> QueueScreen(
                    downloads = allDownloads,
                    globalSpeedBytesPerSec = globalSpeedBytesPerSec,
                    isGlobalQueuePaused = isGlobalQueuePaused,
                    settings = settings,
                    onPauseSingle = viewModel::pauseSingle,
                    onResumeSingle = viewModel::resumeSingle,
                    onCancelSingle = viewModel::cancelSingle,
                    onPauseAll = viewModel::pauseAllQueue,
                    onResumeAll = viewModel::resumeAllQueue,
                    onClearCompleted = viewModel::clearCompleted,
                    onUpdateSpeedLimit = viewModel::updateSpeedLimit
                )
                1 -> LinkGrabberScreen(
                    isExamining = isExaminingUrl,
                    lastResult = lastGrabResult,
                    globalDownloadDir = settings.globalDownloadDirectory,
                    autoOrganizeMode = settings.autoOrganizeBy,
                    onExamineUrl = viewModel::examineUrl,
                    onAddLinksToQueue = { links, folder ->
                        viewModel.addGrabbedLinksToQueue(links, folder)
                        currentTab = 0 // Switch to Queue tab after adding!
                    }
                )
                2 -> StorageScreen(
                    completedItems = completedDownloads,
                    onTriggerDriveSync = viewModel::triggerDriveSync
                )
                3 -> DriveRemoteScreen(
                    settings = settings,
                    driveState = driveSyncState,
                    remoteLogs = remoteLogs,
                    isRemoteRunning = isRemoteRunning,
                    onTriggerDriveSync = viewModel::triggerDriveSync,
                    onUpdateDriveFolder = viewModel::setDriveFolder,
                    onToggleGoogleAccount = viewModel::toggleGoogleAccount,
                    onToggleRemoteServer = viewModel::toggleRemoteServer,
                    onInjectRemoteTestLink = viewModel::injectRemoteTestLink
                )
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = DarkCardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Ajustes de Descargas y Ancho de Banda",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Concurrency Slider
                Text(
                    text = "Límite de descargas simultáneas: ${settings.maxConcurrentDownloads}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Slider(
                    value = settings.maxConcurrentDownloads.toFloat(),
                    onValueChange = { viewModel.updateConcurrency(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Default Directory Input
                OutlinedTextField(
                    value = settings.globalDownloadDirectory,
                    onValueChange = viewModel::updateGlobalDirectory,
                    label = { Text("Directorio por defecto") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
