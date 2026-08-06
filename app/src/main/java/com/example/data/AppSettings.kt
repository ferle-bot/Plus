package com.example.data

data class AppSettings(
    val maxConcurrentDownloads: Int = 3,
    val speedLimitKbps: Int = 0, // 0 = unlimited
    val autoOrganizeBy: String = "DATE", // "DATE", "TYPE", "DOMAIN", "CUSTOM"
    val globalDownloadDirectory: String = "Downloads/PulseDownloader",
    val googleDriveSyncEnabled: Boolean = true,
    val googleDriveUserEmail: String? = "fernando.garcia.langle@gmail.com",
    val googleDriveFolder: String = "PulseDownloader_Respaldo",
    val driveSyncIntervalDays: Int = 7,
    val lastDriveSyncTimestamp: Long = System.currentTimeMillis() - 86400000 * 2, // 2 days ago
    val remoteServerEnabled: Boolean = true,
    val remotePort: Int = 8080,
    val remotePinCode: String = "9482"
)
