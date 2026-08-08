package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.DownloadItem
import com.example.engine.GrabbedLink
import com.example.engine.WebGrabResult
import com.example.repository.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PulseViewModel(application: Application) : AndroidViewModel(application) {
    val repository = DownloadRepository(application)

    val activeQueue: StateFlow<List<DownloadItem>> = repository.activeQueue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadItem>> = repository.completedDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDownloads: StateFlow<List<DownloadItem>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = repository.settings
    val isExaminingUrl: StateFlow<Boolean> = repository.isExaminingUrl
    val lastGrabResult: StateFlow<WebGrabResult?> = repository.lastGrabResult

    val globalSpeedBytesPerSec: StateFlow<Long> = repository.queueManager.globalSpeedBytesPerSec
    val isGlobalQueuePaused: StateFlow<Boolean> = repository.queueManager.isGlobalQueuePaused

    val driveSyncState = repository.driveBackupEngine.syncState
    val remoteLogs = repository.remoteServerEngine.remoteLogs
    val isRemoteServerRunning = repository.remoteServerEngine.isServerRunning

    init {
        // ViewModel initialized cleanly
    }

    fun examineUrl(url: String) {
        viewModelScope.launch {
            repository.examineWebPage(url)
        }
    }

    fun addGrabbedLinksToQueue(links: List<GrabbedLink>, customFolder: String? = null) {
        viewModelScope.launch {
            repository.addGrabbedLinksToQueue(links, customFolder)
        }
    }

    fun pauseSingle(id: Long) {
        viewModelScope.launch {
            repository.pauseSingle(id)
        }
    }

    fun resumeSingle(id: Long) {
        viewModelScope.launch {
            repository.resumeSingle(id)
        }
    }

    fun cancelSingle(id: Long) {
        viewModelScope.launch {
            repository.cancelSingle(id)
        }
    }

    fun pauseAllQueue() {
        repository.pauseAll()
    }

    fun resumeAllQueue() {
        repository.resumeAll()
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompleted()
        }
    }

    fun updateConcurrency(maxConcurrent: Int) {
        val updated = settings.value.copy(maxConcurrentDownloads = maxConcurrent)
        repository.updateSettings(updated)
    }

    fun updateSpeedLimit(limitKbps: Int) {
        val updated = settings.value.copy(speedLimitKbps = limitKbps)
        repository.updateSettings(updated)
    }

    fun updateAutoOrganize(mode: String) {
        val updated = settings.value.copy(autoOrganizeBy = mode)
        repository.updateSettings(updated)
    }

    fun updateGlobalDirectory(dir: String) {
        val updated = settings.value.copy(globalDownloadDirectory = dir)
        repository.updateSettings(updated)
    }

    fun triggerDriveSync() {
        viewModelScope.launch {
            repository.runGoogleDriveSyncNow(completedDownloads.value)
        }
    }

    fun setDriveFolder(folderName: String) {
        val updated = settings.value.copy(googleDriveFolder = folderName)
        repository.updateSettings(updated)
    }

    fun toggleGoogleAccount(email: String?) {
        val updated = settings.value.copy(googleDriveUserEmail = email)
        repository.updateSettings(updated)
    }

    fun toggleRemoteServer(enable: Boolean) {
        repository.remoteServerEngine.toggleServer(enable)
        val updated = settings.value.copy(remoteServerEnabled = enable)
        repository.updateSettings(updated)
    }

    fun injectRemoteTestLink() {
        viewModelScope.launch {
            repository.remoteServerEngine.addRemoteLog(
                clientIp = "192.168.1.105 (Chrome Browser Extension)",
                action = "ADD_TO_QUEUE",
                details = "Enviado desde PC: IG_Profile_Media_Pack.zip (32 MB)"
            )
            val testLink = GrabbedLink(
                url = "https://instagram.com/media/ig_remote_extension_media.zip",
                title = "Enlace Remoto desde Extensión Chrome",
                suggestedFileName = "IG_Remote_Extension_Media.zip",
                mediaType = "ARCHIVE",
                estimatedSizeBytes = 32_000_000L,
                thumbnailUrl = "https://picsum.photos/id/1069/800/800",
                sourcePageUrl = "https://instagram.com/fernando.garcia.langle",
                domain = "instagram.com"
            )
            repository.addGrabbedLinksToQueue(listOf(testLink))
        }
    }
}
