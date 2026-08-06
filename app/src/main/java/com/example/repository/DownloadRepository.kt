package com.example.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.DownloadDao
import com.example.data.DownloadItem
import com.example.engine.GoogleDriveBackupEngine
import com.example.engine.GrabbedLink
import com.example.engine.LinkGrabberEngine
import com.example.engine.QueueDownloadManager
import com.example.engine.RemoteQueueServerEngine
import com.example.engine.WebGrabResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    val downloadDao: DownloadDao = database.downloadDao()

    val linkGrabberEngine = LinkGrabberEngine()
    val queueManager = QueueDownloadManager(context, downloadDao)
    val driveBackupEngine = GoogleDriveBackupEngine(downloadDao)
    val remoteServerEngine = RemoteQueueServerEngine()

    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()
    val activeQueue: Flow<List<DownloadItem>> = downloadDao.getActiveQueue()
    val completedDownloads: Flow<List<DownloadItem>> = downloadDao.getCompletedDownloads()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _lastGrabResult = MutableStateFlow<WebGrabResult?>(null)
    val lastGrabResult: StateFlow<WebGrabResult?> = _lastGrabResult.asStateFlow()

    private val _isExaminingUrl = MutableStateFlow(false)
    val isExaminingUrl: StateFlow<Boolean> = _isExaminingUrl.asStateFlow()

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        queueManager.updateSettings(newSettings)
    }

    suspend fun examineWebPage(url: String): WebGrabResult {
        _isExaminingUrl.value = true
        return try {
            val result = linkGrabberEngine.examinePageUrl(url)
            _lastGrabResult.value = result
            result
        } finally {
            _isExaminingUrl.value = false
        }
    }

    suspend fun addGrabbedLinksToQueue(
        links: List<GrabbedLink>,
        customFolderPath: String? = null
    ) {
        val currentFolder = customFolderPath ?: settings.value.globalDownloadDirectory
        val items = links.map { link ->
            DownloadItem(
                fileName = link.suggestedFileName,
                fileUrl = link.url,
                mediaType = link.mediaType,
                fileSizeBytes = link.estimatedSizeBytes,
                downloadedBytes = 0L,
                status = "PENDING",
                sourceUrl = link.sourcePageUrl,
                domain = link.domain,
                targetFolderPath = currentFolder,
                thumbnailUrl = link.thumbnailUrl
            )
        }
        downloadDao.insertAll(items)
    }

    suspend fun pauseSingle(id: Long) {
        queueManager.pauseSingleDownload(id)
    }

    suspend fun resumeSingle(id: Long) {
        queueManager.resumeSingleDownload(id)
    }

    suspend fun cancelSingle(id: Long) {
        queueManager.cancelSingleDownload(id)
    }

    fun pauseAll() {
        queueManager.pauseAllQueue()
    }

    fun resumeAll() {
        queueManager.resumeAllQueue()
    }

    suspend fun clearCompleted() {
        downloadDao.clearCompleted()
    }

    suspend fun runGoogleDriveSyncNow(completedItems: List<DownloadItem>) {
        val current = settings.value
        driveBackupEngine.performWeeklyBackup(
            userEmail = current.googleDriveUserEmail ?: "user@gmail.com",
            targetDriveFolder = current.googleDriveFolder,
            completedItems = completedItems
        )
        updateSettings(current.copy(lastDriveSyncTimestamp = System.currentTimeMillis()))
    }
}
