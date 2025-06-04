package com.example.autowish

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BirthdayEntry::class], version = 3)
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS birthdays_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        birthDate TEXT NOT NULL,
                        personType TEXT NOT NULL,
                        department TEXT NOT NULL,
                        year TEXT,
                        groupId TEXT NOT NULL,
                        isHod INTEGER NOT NULL DEFAULT 0
                    )
                """)
                database.execSQL("""
                    INSERT INTO birthdays_new (id, name, phoneNumber, birthDate, personType, department, year, groupId, isHod)
                    SELECT id, name, phoneNumber, birthDate, personType, 'Computer Engineering', NULL, 'Default', 0 FROM birthdays
                """)
                database.execSQL("DROP TABLE birthdays")
                database.execSQL("ALTER TABLE birthdays_new RENAME TO birthdays")
            }
        }
    }
}