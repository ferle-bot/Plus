package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_items ORDER BY id DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE status IN ('PENDING', 'DOWNLOADING', 'PAUSED') ORDER BY id ASC")
    fun getActiveQueue(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY completedAtTimestamp DESC")
    fun getCompletedDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadItem>): List<Long>

    @Update
    suspend fun updateDownload(item: DownloadItem)

    @Query("UPDATE download_items SET status = :status, downloadSpeedBytesPerSec = 0 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE download_items SET status = :status WHERE status = :fromStatus")
    suspend fun updateAllStatus(fromStatus: String, status: String)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM download_items WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("UPDATE download_items SET driveSynced = 1, driveFileId = :driveFileId WHERE id = :id")
    suspend fun markDriveSynced(id: Long, driveFileId: String)
}
