package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FolderSelectorDialog(
    selectedCount: Int,
    currentOrganizeMode: String,
    currentGlobalDir: String,
    onConfirm: (targetFolderPath: String) -> Unit,
    onDismiss: () -> Unit
) {
    var organizeMode by remember { mutableStateOf(currentOrganizeMode) }
    var customDirInput by remember { mutableStateOf(currentGlobalDir) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCardBg,
        title = {
            Column {
                Text(
                    text = "Confirmar Descarga ($selectedCount enlaces)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Selecciona la carpeta de destino y regla de organización:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column {
                // Rule Selection Options
                val options = listOf(
                    "DATE" to "Por Fecha (ej: PulseDownloader/2026-08-06)",
                    "TYPE" to "Por Categoría (ej: Imágenes, Videos, Zips)",
                    "DOMAIN" to "Por Sitio Web (ej: Sitios/instagram_com)",
                    "CUSTOM" to "Carpeta Personalizada"
                )

                options.forEach { (mode, label) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (organizeMode == mode) NeonCyan else DarkBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { organizeMode = mode },
                        color = DarkSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = organizeMode == mode,
                                onClick = { organizeMode = mode },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (organizeMode == mode) TextPrimary else TextSecondary
                            )
                        }
                    }
                }

                if (organizeMode == "CUSTOM") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customDirInput,
                        onValueChange = { customDirInput = it },
                        label = { Text("Directorio de destino") },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPath = if (organizeMode == "CUSTOM") customDirInput else "Auto ($organizeMode)"
                    onConfirm(finalPath)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkSurface)
            ) {
                Text("Iniciar Descarga", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextMuted)
            }
        }
    )
}
