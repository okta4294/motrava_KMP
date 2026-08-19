package com.myapp.motrava.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationPointDao {
    @Insert
    suspend fun insert(point: LocationPointEntity): Long

    @Query("SELECT * FROM location_points WHERE tripId = :tripId AND isSynced = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getUnsyncedByTrip(tripId: String, limit: Int = 50): List<LocationPointEntity>

    @Query("SELECT DISTINCT tripId FROM location_points WHERE isSynced = 0")
    suspend fun getTripsWithUnsyncedPoints(): List<String>

    @Query("UPDATE location_points SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM location_points WHERE tripId = :tripId AND isSynced = 1")
    suspend fun deleteSyncedByTrip(tripId: String)
}
