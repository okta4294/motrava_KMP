package com.myapp.motrava.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_points")
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val heading: Float,
    val accuracy: Float,
    val altitude: Double,
    val battery: Int,
    val timestamp: String,
    val isSynced: Boolean = false
)
