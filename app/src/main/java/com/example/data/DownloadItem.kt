package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileUrl: String,
    val mediaType: String, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT", "ARCHIVE", "OTHER"
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "PENDING", // "PENDING", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val downloadSpeedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val sourceUrl: String = "",
    val domain: String = "",
    val targetFolderPath: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val completedAtTimestamp: Long = 0L,
    val driveSynced: Boolean = false,
    val driveFileId: String? = null,
    val errorMessage: String? = null,
    val thumbnailUrl: String? = null
)
