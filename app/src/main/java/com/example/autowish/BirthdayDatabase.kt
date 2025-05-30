package com.example.autowish


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BirthdayEntry::class], version = 1)
abstract class BirthdayDatabase : RoomDatabase() {
    abstract fun birthdayDao(): BirthdayDao

    companion object {
        @Volatile private var INSTANCE: BirthdayDatabase? = null

        fun getInstance(context: Context): BirthdayDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    BirthdayDatabase::class.java,
                    "birthday_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}