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
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class QueueDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeSpeeds = ConcurrentHashMap<Long, Long>()

    private val _globalSpeedBytesPerSec = MutableStateFlow(0L)
    val globalSpeedBytesPerSec: StateFlow<Long> = _globalSpeedBytesPerSec.asStateFlow()

    private val _isGlobalQueuePaused = MutableStateFlow(false)
    val isGlobalQueuePaused: StateFlow<Boolean> = _isGlobalQueuePaused.asStateFlow()

    @Volatile
    var currentSettings = AppSettings()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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
        activeSpeeds.clear()
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
        activeSpeeds.remove(id)
        recalculateGlobalSpeed()
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
        activeSpeeds.remove(id)
        recalculateGlobalSpeed()
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
                    val maxAllowed = currentSettings.maxConcurrentDownloads.coerceAtLeast(1)

                    val pendingItems = queue.filter { it.status == "PENDING" }
                    val availableSlots = (maxAllowed - currentlyDownloadingCount).coerceAtLeast(0)

                    for (i in 0 until availableSlots.coerceAtMost(pendingItems.size)) {
                        val itemToStart = pendingItems[i]
                        if (!activeJobs.containsKey(itemToStart.id)) {
                            val job = scope.launch {
                                executeRealDownloadTask(itemToStart)
                            }
                            activeJobs[itemToStart.id] = job
                        }
                    }
                }
            }
        }
    }

    private suspend fun executeRealDownloadTask(item: DownloadItem) {
        var currentItem = item.copy(status = "DOWNLOADING")
        downloadDao.updateDownload(currentItem)

        val targetDir = resolveTargetDirectory(item)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, item.fileName)

        var downloadedBytes = if (targetFile.exists()) targetFile.length() else 0L

        try {
            val requestBuilder = Request.Builder()
                .url(item.fileUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PulseDownloader/1.0")

            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    downloadedBytes = 0L
                    if (targetFile.exists()) targetFile.delete()
                    val retryResponse = client.newCall(
                        Request.Builder()
                            .url(item.fileUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PulseDownloader/1.0")
                            .build()
                    ).execute()

                    if (!retryResponse.isSuccessful) {
                        throw IOException("HTTP ${retryResponse.code}: ${retryResponse.message}")
                    }
                    streamResponseBodyToFile(retryResponse, item, currentItem, targetDir, targetFile, downloadedBytes)
                    return
                }
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            streamResponseBodyToFile(response, item, currentItem, targetDir, targetFile, downloadedBytes)

        } catch (e: Exception) {
            activeSpeeds.remove(item.id)
            recalculateGlobalSpeed()

            if (!scope.isActive) {
                currentItem = currentItem.copy(
                    status = "PAUSED",
                    downloadSpeedBytesPerSec = 0L
                )
            } else {
                currentItem = currentItem.copy(
                    status = "FAILED",
                    downloadSpeedBytesPerSec = 0L,
                    errorMessage = e.localizedMessage ?: e.message ?: "Error de descarga en la red"
                )
            }
            downloadDao.updateDownload(currentItem)
        } finally {
            activeJobs.remove(item.id)
            activeSpeeds.remove(item.id)
            recalculateGlobalSpeed()
        }
    }

    private suspend fun streamResponseBodyToFile(
        response: okhttp3.Response,
        item: DownloadItem,
        initialItem: DownloadItem,
        targetDir: File,
        targetFile: File,
        startDownloadedBytes: Long
    ) {
        var currentItem = initialItem
        val body = response.body ?: throw IOException("Cuerpo de respuesta de la red está vacío")

        val isPartial = response.code == 206
        val contentLength = body.contentLength()
        val totalSizeBytes = if (contentLength > 0) {
            if (isPartial) startDownloadedBytes + contentLength else contentLength
        } else {
            if (item.fileSizeBytes > 0) item.fileSizeBytes else -1L
        }

        var downloaded = if (isPartial) startDownloadedBytes else 0L
        val appendMode = isPartial && startDownloadedBytes > 0

        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(targetFile, appendMode)

        val buffer = ByteArray(32 * 1024)
        var bytesRead = inputStream.read(buffer)

        var lastUiUpdateTime = System.currentTimeMillis()
        var bytesSinceLastUiUpdate = 0L

        try {
            while (scope.isActive && bytesRead != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                bytesSinceLastUiUpdate += bytesRead

                val now = System.currentTimeMillis()
                val timeDelta = now - lastUiUpdateTime

                if (timeDelta >= 400) {
                    val speed = (bytesSinceLastUiUpdate * 1000L) / timeDelta.coerceAtLeast(1)
                    activeSpeeds[item.id] = speed
                    recalculateGlobalSpeed()

                    val remainingBytes = if (totalSizeBytes > downloaded) totalSizeBytes - downloaded else 0L
                    val eta = if (speed > 0) remainingBytes / speed else 0L

                    currentItem = currentItem.copy(
                        downloadedBytes = downloaded,
                        fileSizeBytes = if (totalSizeBytes > 0) totalSizeBytes else downloaded,
                        downloadSpeedBytesPerSec = speed,
                        etaSeconds = eta,
                        targetFolderPath = targetDir.absolutePath
                    )
                    downloadDao.updateDownload(currentItem)

                    lastUiUpdateTime = now
                    bytesSinceLastUiUpdate = 0L
                }

                bytesRead = inputStream.read(buffer)
            }

            outputStream.flush()

            if (!scope.isActive) {
                currentItem = currentItem.copy(
                    status = "PAUSED",
                    downloadSpeedBytesPerSec = 0L
                )
                downloadDao.updateDownload(currentItem)
            } else {
                val finalFileSize = if (targetFile.exists()) targetFile.length() else downloaded
                currentItem = currentItem.copy(
                    downloadedBytes = finalFileSize,
                    fileSizeBytes = finalFileSize,
                    status = "COMPLETED",
                    downloadSpeedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    completedAtTimestamp = System.currentTimeMillis(),
                    targetFolderPath = targetDir.absolutePath
                )
                downloadDao.updateDownload(currentItem)
            }
        } finally {
            activeSpeeds.remove(item.id)
            recalculateGlobalSpeed()
            try { inputStream.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
            try { body.close() } catch (_: Exception) {}
        }
    }

    private fun recalculateGlobalSpeed() {
        val totalSpeed = activeSpeeds.values.sum()
        _globalSpeedBytesPerSec.value = totalSpeed
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
