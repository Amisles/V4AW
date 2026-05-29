package org.amisles.v4aw.model

import android.annotation.SuppressLint
import androidx.room.Entity
import androidx.room.PrimaryKey

@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
