package com.myapp.motrava.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    fun markAsRead(id: Long)
    
    @Query("UPDATE notifications SET isRead = 1")
    fun markAllAsRead()
    
    @Query("DELETE FROM notifications WHERE id = :id")
    fun deleteNotification(id: Long)

    @Query("DELETE FROM notifications")
    fun clearAll()

    @Query("SELECT * FROM notifications WHERE dataPayload = :payload AND isRead = 0 LIMIT 1")
    fun getUnreadByPayload(payload: String): NotificationEntity?
}
