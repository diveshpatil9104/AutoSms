package com.example.autowish.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays ORDER BY date ASC")
    fun getAllBirthdays(): Flow<List<BirthdayEntry>>

    // Query to match birthdays by month and day only (ignore year)
    @Query("SELECT * FROM birthdays WHERE strftime('%m-%d', date) = :monthDay")
    suspend fun getBirthdaysByMonthDay(monthDay: String): List<BirthdayEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BirthdayEntry)

    @Update
    suspend fun update(entry: BirthdayEntry)

    @Delete
    suspend fun delete(entry: BirthdayEntry)
}