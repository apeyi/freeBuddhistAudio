package com.fba.app.data.local

import androidx.room.ColumnInfo
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
    /** "remastered" or "original" for audio downloads; "" for transcript-only downloads. */
    @ColumnInfo(defaultValue = "") val audioVersion: String = "",
) {
    /** A COMPLETE row with no audio file is a transcript-only download. */
    val isTranscriptOnly: Boolean get() = status == DownloadStatus.COMPLETE && filePath.isBlank()
}

enum class DownloadStatus {
    PENDING, DOWNLOADING, COMPLETE, FAILED
}
