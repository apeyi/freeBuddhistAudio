package com.fba.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_listened")
data class RecentlyListenedEntity(
    @PrimaryKey val catNum: String,
    val title: String,
    val speaker: String,
    val imageUrl: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val trackIndex: Int = 0,
    val totalDurationSeconds: Int = 0,
    val listenedAt: Long = System.currentTimeMillis(),
)
