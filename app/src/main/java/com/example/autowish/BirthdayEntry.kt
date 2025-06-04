package com.example.autowish

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "birthdays")
data class BirthdayEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val birthDate: String, // MM-DD format
    val personType: String, // "Student" or "Staff"
    val department: String, // One of the 6 supported departments
    val year: String?, // 2nd, 3rd, 4th for students; null for staff
    val groupId: String, // Group identifier
    val isHod: Boolean // True for HOD staff; false for students
)