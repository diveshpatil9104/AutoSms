package com.example.autowish

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_queue")
data class SmsQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val name: String,
    val personType: String,
    val type: String, // direct, peer, hod
    val retryCount: Int,
    val timestamp: Long
)