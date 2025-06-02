package com.example.autowish

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BirthdayEntry::class], version = 2)
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS birthdays_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        birthDate TEXT NOT NULL,
                        personType TEXT NOT NULL
                    )
                """)
                database.execSQL("""
                    INSERT INTO birthdays_new (id, name, phoneNumber, birthDate, personType)
                    SELECT id, name, phoneNumber, birthDate, 'Student' FROM birthdays
                """)
                database.execSQL("DROP TABLE birthdays")
                database.execSQL("ALTER TABLE birthdays_new RENAME TO birthdays")
            }
        }
    }
}