package com.example.engine

import android.content.Context
import com.example.data.AppSettings
import com.example.data.DownloadDao
import com.example.data.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class QueueDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _globalSpeedBytesPerSec = MutableStateFlow(0L)
    val globalSpeedBytesPerSec: StateFlow<Long> = _globalSpeedBytesPerSec.asStateFlow()

    private val _isGlobalQueuePaused = MutableStateFlow(false)
    val isGlobalQueuePaused: StateFlow<Boolean> = _isGlobalQueuePaused.asStateFlow()

    @Volatile
    var currentSettings = AppSettings()

    private val client = OkHttpClient()

    init {
        startQueueMonitor()
    }

    fun updateSettings(settings: AppSettings) {
        currentSettings = settings
    }

    fun pauseAllQueue() {
        _isGlobalQueuePaused.value = true
        activeJobs.forEach { (id, job) ->
            job.cancel()
        }
        activeJobs.clear()
        _globalSpeedBytesPerSec.value = 0L
        scope.launch {
            downloadDao.updateAllStatus("DOWNLOADING", "PAUSED")
        }
    }

    fun resumeAllQueue() {
        _isGlobalQueuePaused.value = false
        scope.launch {
            downloadDao.updateAllStatus("PAUSED", "PENDING")
        }
    }

    fun pauseSingleDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            downloadDao.updateStatus(id, "PAUSED")
        }
    }

    fun resumeSingleDownload(id: Long) {
        scope.launch {
            downloadDao.updateStatus(id, "PENDING")
        }
    }

    fun cancelSingleDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            downloadDao.deleteDownload(id)
        }
    }

    private fun startQueueMonitor() {
        scope.launch {
            while (isActive) {
                delay(1000)
                if (_isGlobalQueuePaused.value) {
                    _globalSpeedBytesPerSec.value = 0L
                    continue
                }

                downloadDao.getActiveQueue().collect { queue ->
                    val currentlyDownloadingCount = activeJobs.size
                    val maxAllowed = currentSettings.maxConcurrentDownloads

                    val pendingItems = queue.filter { it.status == "PENDING" }

                    val availableSlots = (maxAllowed - currentlyDownloadingCount).coerceAtLeast(0)

                    for (i in 0 until availableSlots.coerceAtMost(pendingItems.size)) {
                        val itemToStart = pendingItems[i]
                        if (!activeJobs.containsKey(itemToStart.id)) {
                            val job = scope.launch {
                                executeDownloadTask(itemToStart)
                            }
                            activeJobs[itemToStart.id] = job
                        }
                    }
                }
            }
        }
    }

    private suspend fun executeDownloadTask(item: DownloadItem) {
        var currentItem = item.copy(status = "DOWNLOADING")
        downloadDao.updateDownload(currentItem)

        val targetDir = resolveTargetDirectory(item)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, item.fileName)

        val totalSize = if (item.fileSizeBytes > 0) item.fileSizeBytes else 12_500_000L
        var downloaded = item.downloadedBytes.coerceAtLeast(0L)

        val speedLimitBytes = if (currentSettings.speedLimitKbps > 0) currentSettings.speedLimitKbps * 1024L else 0L

        val chunkSize = 512 * 1024L // 512 KB per step
        var lastTime = System.currentTimeMillis()

        try {
            while (downloaded < totalSize && scope.isActive) {
                val now = System.currentTimeMillis()
                val elapsedSec = ((now - lastTime) / 1000.0).coerceAtLeast(0.1)

                // Simulated / throttled speed
                var targetSpeedBytes = if (speedLimitBytes > 0) {
                    speedLimitBytes
                } else {
                    (3_500_000..9_800_000).random().toLong() // 3.5 MB/s to 9.8 MB/s
                }

                val bytesAdded = (targetSpeedBytes * elapsedSec).toLong().coerceAtLeast(256 * 1024L)
                downloaded = (downloaded + bytesAdded).coerceAtMost(totalSize)

                val remainingBytes = totalSize - downloaded
                val eta = if (targetSpeedBytes > 0) (remainingBytes / targetSpeedBytes) else 0L

                currentItem = currentItem.copy(
                    downloadedBytes = downloaded,
                    fileSizeBytes = totalSize,
                    downloadSpeedBytesPerSec = targetSpeedBytes,
                    etaSeconds = eta,
                    targetFolderPath = targetDir.absolutePath
                )
                downloadDao.updateDownload(currentItem)

                // Calculate global speed
                recalculateGlobalSpeed()

                lastTime = now
                delay(400) // update UI smoothly 2.5 times per sec
            }

            if (downloaded >= totalSize) {
                // Ensure sample file exists on storage
                if (!targetFile.exists()) {
                    try {
                        targetFile.writeText("PulseDownloader Completed File: ${item.fileName}\nSource: ${item.fileUrl}\n")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                currentItem = currentItem.copy(
                    downloadedBytes = totalSize,
                    status = "COMPLETED",
                    downloadSpeedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    completedAtTimestamp = System.currentTimeMillis()
                )
                downloadDao.updateDownload(currentItem)
            }
        } catch (e: Exception) {
            currentItem = currentItem.copy(
                status = "FAILED",
                downloadSpeedBytesPerSec = 0L,
                errorMessage = e.message ?: "Error de red"
            )
            downloadDao.updateDownload(currentItem)
        } finally {
            activeJobs.remove(item.id)
            recalculateGlobalSpeed()
        }
    }

    private fun recalculateGlobalSpeed() {
        var totalSpeed = 0L
        activeJobs.keys.forEach { id ->
            // Active jobs sum
            totalSpeed += (2_500_000..6_000_000).random().toLong()
        }
        _globalSpeedBytesPerSec.value = if (activeJobs.isEmpty()) 0L else totalSpeed
    }

    private fun resolveTargetDirectory(item: DownloadItem): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val subPath = when (currentSettings.autoOrganizeBy) {
            "DATE" -> {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                "PulseDownloader/$dateStr"
            }
            "TYPE" -> {
                val typeSub = when (item.mediaType) {
                    "IMAGE" -> "Imagenes"
                    "VIDEO" -> "Videos"
                    "AUDIO" -> "Musica"
                    "ARCHIVE" -> "Archivos_ZIP"
                    "DOCUMENT" -> "Documentos"
                    else -> "Otros"
                }
                "PulseDownloader/$typeSub"
            }
            "DOMAIN" -> {
                val cleanDomain = if (item.domain.isBlank()) "web" else item.domain.replace(".", "_")
                "PulseDownloader/Sitios/$cleanDomain"
            }
            else -> currentSettings.globalDownloadDirectory
        }
        return File(baseDir, subPath)
    }
}
