package com.example.autowish

import androidx.room.*

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays")
    suspend fun getAll(): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays WHERE birthDate = :date")
    suspend fun getBirthdaysByDate(date: String): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays WHERE personType = :type")
    suspend fun getByPersonType(type: String): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays WHERE department = :department AND (:year IS NULL OR year = :year) AND (:personType IS NULL OR personType = :personType)")
    suspend fun getByDepartmentAndYear(department: String, year: String?, personType: String?): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays WHERE department = :department AND year = :year AND groupId = :groupId AND id != :excludeId")
    suspend fun getPeers(department: String, year: String?, groupId: String, excludeId: Int): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays WHERE department = :department AND isHod = 1")
    suspend fun getHodByDepartment(department: String): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays ORDER BY birthDate ASC")
    suspend fun getAllSortedAsc(): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays ORDER BY birthDate DESC")
    suspend fun getAllSortedDesc(): List<BirthdayEntry>

    @Query("SELECT * FROM birthdays WHERE LOWER(name) = LOWER(:name) AND phoneNumber = :phoneNumber")
    suspend fun getByNameAndPhone(name: String, phoneNumber: String): List<BirthdayEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BirthdayEntry)

    @Delete
    suspend fun delete(entry: BirthdayEntry)

    @Query("DELETE FROM birthdays WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM birthdays WHERE department = :department AND (:year IS NULL OR year = :year) AND (:personType IS NULL OR personType = :personType)")
    suspend fun deleteByDepartmentAndYear(department: String, year: String?, personType: String?)

    @Query("DELETE FROM birthdays")
    suspend fun deleteAll()
}