package com.krishna.aegis.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: BufferedNotification): Long

    @Query("SELECT * FROM buffered_notifications WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByPackage(packageName: String, limit: Int = 10): List<BufferedNotification>

    @Query("SELECT * FROM buffered_notifications WHERE content LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchNotifications(query: String, limit: Int = 10): List<BufferedNotification>

    @Query("SELECT * FROM buffered_notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(limit: Int = 20): List<BufferedNotification>

    @Query("DELETE FROM buffered_notifications WHERE timestamp < :expirationTimestamp")
    suspend fun purgeOlderThan(expirationTimestamp: Long)
}
