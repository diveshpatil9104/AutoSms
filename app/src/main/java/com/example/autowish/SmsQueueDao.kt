package com.example.autowish

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SmsQueueDao {
    @Insert
    suspend fun insert(entry: SmsQueueEntry)

    @Query("SELECT * FROM sms_queue WHERE retryCount < :maxRetries LIMIT :limit")
    suspend fun getPendingSms(limit: Int, maxRetries: Int = 5): List<SmsQueueEntry>

    @Query("DELETE FROM sms_queue WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM sms_queue")
    suspend fun getQueueSize(): Int
}