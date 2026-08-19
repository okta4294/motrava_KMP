package com.myapp.motrava.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val body: String,
    val timestamp: Long,
    val dataPayload: String? = null, // JSON string if needed
    val isRead: Boolean = false
)
