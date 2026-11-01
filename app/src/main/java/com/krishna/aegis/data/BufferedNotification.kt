package com.krishna.aegis.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buffered_notifications")
data class BufferedNotification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appTitle: String,
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
