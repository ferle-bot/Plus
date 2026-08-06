package com.example.engine

import com.example.data.DownloadDao
import com.example.data.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DriveSyncState(
    val isSyncing: Boolean = false,
    val progressPercent: Float = 0f,
    val currentFileName: String = "",
    val totalFilesSynced: Int = 0,
    val totalBytesUploaded: Long = 0L,
    val lastSyncMessage: String = "Listo para respaldar"
)

class GoogleDriveBackupEngine(
    private val downloadDao: DownloadDao
) {
    private val _syncState = MutableStateFlow(DriveSyncState())
    val syncState: StateFlow<DriveSyncState> = _syncState.asStateFlow()

    suspend fun performWeeklyBackup(
        userEmail: String,
        targetDriveFolder: String,
        completedItems: List<DownloadItem>
    ) = withContext(Dispatchers.IO) {
        val unSyncedItems = completedItems.filter { !it.driveSynced }
        if (unSyncedItems.isEmpty()) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncMessage = "Todos los archivos ya están respaldados en Google Drive."
            )
            return@withContext
        }

        _syncState.value = DriveSyncState(
            isSyncing = true,
            progressPercent = 0f,
            currentFileName = "Iniciando conexión con Google Drive ($userEmail)...",
            lastSyncMessage = "Conectando a Google Drive..."
        )

        delay(1200) // Auth verification delay

        var bytesUploaded = 0L
        unSyncedItems.forEachIndexed { index, item ->
            val progress = (index + 1).toFloat() / unSyncedItems.size.toFloat()
            _syncState.value = _syncState.value.copy(
                isSyncing = true,
                progressPercent = progress,
                currentFileName = "Subiendo a Drive: ${item.fileName} -> /$targetDriveFolder",
                lastSyncMessage = "Subiendo ${index + 1} de ${unSyncedItems.size} archivos..."
            )

            // Simulate file chunk upload to Drive
            delay(1500)
            bytesUploaded += item.fileSizeBytes

            val driveId = "drive_file_${System.currentTimeMillis()}_${item.id}"
            downloadDao.markDriveSynced(item.id, driveId)
        }

        _syncState.value = DriveSyncState(
            isSyncing = false,
            progressPercent = 1f,
            currentFileName = "Completado",
            totalFilesSynced = unSyncedItems.size,
            totalBytesUploaded = bytesUploaded,
            lastSyncMessage = "Respaldo semanal en Google Drive completado exitosamente (${unSyncedItems.size} archivos)."
        )
    }
}
