package com.myapp.motrava.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [NotificationEntity::class, LocationPointEntity::class],
    version = 2,
    exportSchema = false
)
@ConstructedBy(MotravaDatabaseConstructor::class)
abstract class MotravaDatabase : RoomDatabase() {
    abstract val notificationDao: NotificationDao
    abstract val locationPointDao: LocationPointDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object MotravaDatabaseConstructor : RoomDatabaseConstructor<MotravaDatabase> {
    override fun initialize(): MotravaDatabase
}
