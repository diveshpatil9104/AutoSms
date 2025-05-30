package com.example.autowish

import androidx.room.*

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays")
    suspend fun getAll(): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays WHERE birthDate = :date")
    suspend fun getBirthdaysByDate(date: String): List<BirthdayEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BirthdayEntry)
}