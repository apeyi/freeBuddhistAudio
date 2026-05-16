package com.fba.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val catNum: String,
    val title: String,
    val speaker: String,
    val imageUrl: String,
    val filePath: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val downloadedAt: Long = 0,
)

enum class DownloadStatus {
    PENDING, DOWNLOADING, COMPLETE, FAILED
}
