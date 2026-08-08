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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.engine.DriveSyncState
import com.example.engine.RemoteLogMessage
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriveRemoteScreen(
    settings: AppSettings,
    driveState: DriveSyncState,
    remoteLogs: List<RemoteLogMessage>,
    isRemoteRunning: Boolean,
    onTriggerDriveSync: () -> Unit,
    onUpdateDriveFolder: (String) -> Unit,
    onToggleGoogleAccount: (String?) -> Unit,
    onToggleRemoteServer: (Boolean) -> Unit,
    onInjectRemoteTestLink: () -> Unit
) {
    var folderInput by remember { mutableStateOf(settings.googleDriveFolder) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var customEmailInput by remember { mutableStateOf("") }

    if (showAccountDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            containerColor = DarkCardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vincular Cuenta de Google Drive",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Selecciona o escribe el correo de Google Drive donde deseas guardar los respaldos y probar sincronización:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    val accountPresets = listOf(
                        "Cuenta Hermano (Pruebas)" to "hermano.prueba@gmail.com",
                        "Fernando García Langle" to "fernando.garcia.langle@gmail.com",
                        "Cuenta Personal Google" to "usuario.google@gmail.com"
                    )

                    accountPresets.forEach { (label, email) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (settings.googleDriveUserEmail == email) NeonCyan else DarkBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onToggleGoogleAccount(email)
                                    showAccountDialog = false
                                },
                            color = DarkSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                    Text(text = email, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                                if (settings.googleDriveUserEmail == email) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "O introduce otro correo de Google:", style = MaterialTheme.typography.labelSmall, color = TextMuted)

                    OutlinedTextField(
                        value = customEmailInput,
                        onValueChange = { customEmailInput = it },
                        placeholder = { Text("ejemplo.hermano@gmail.com", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customEmailInput.isNotBlank()) {
                            onToggleGoogleAccount(customEmailInput.trim())
                        }
                        showAccountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Conectar Cuenta", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // GOOGLE DRIVE BACKUP SECTION
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
                color = DarkCardBg
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Google Drive",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "RESPALDO EN GOOGLE DRIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Sincronización Semanal",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (settings.googleDriveUserEmail != null) NeonEmerald.copy(alpha = 0.15f) else DarkSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (settings.googleDriveUserEmail != null) NeonEmerald else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (settings.googleDriveUserEmail != null) "Conectado" else "Sin Sesión",
                                    fontSize = 11.sp,
                                    color = if (settings.googleDriveUserEmail != null) NeonEmerald else TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // User Account Details
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = DarkSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = settings.googleDriveUserEmail ?: "Cuenta de Google",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Subida automática semanal a tu nube",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                            }

                            Button(
                                onClick = { showAccountDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.googleDriveUserEmail != null) DarkCardBg else NeonCyan,
                                    contentColor = if (settings.googleDriveUserEmail != null) TextSecondary else DarkSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (settings.googleDriveUserEmail != null) "Cambiar Cuenta" else "Acceder Google",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Drive Folder Selector
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = {
                            folderInput = it
                            onUpdateDriveFolder(it)
                        },
                        label = { Text("Carpeta de Destino en Google Drive") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Drive Progress & Trigger
                    if (driveState.isSyncing) {
                        Column {
                            Text(
                                text = driveState.currentFileName,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { driveState.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape),
                                color = NeonCyan,
                                trackColor = DarkSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Text(
                        text = driveState.lastSyncMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onTriggerDriveSync,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !driveState.isSyncing && settings.googleDriveUserEmail != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (driveState.isSyncing) "Sincronizando..." else "Sincronizar con Drive Ahora",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // REMOTE QUEUE & BROWSER EXTENSION COMPANION SECTION
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
                color = DarkCardBg
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(NeonPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = "Remoto Extension",
                                    tint = NeonPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GESTIÓN REMOTA Y EXTENSIÓN CHROME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Servidor de Cola Remota",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                        }

                        Switch(
                            checked = isRemoteRunning,
                            onCheckedChange = onToggleRemoteServer,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkSurface,
                                checkedTrackColor = NeonPurple
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            color = DarkSurface
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "Puerto API Local", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    text = "http://localhost:${settings.remotePort}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = NeonCyan
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            color = DarkSurface
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "PIN Emparejamiento", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    text = settings.remotePinCode,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = NeonPurple
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onInjectRemoteTestLink,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simular Enlace Recibido desde Extensión PC", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Historial de conexión remota:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        remoteLogs.take(4).forEach { log ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = DarkSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sdf.format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${log.clientIp} • ${log.action}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = log.details,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
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
