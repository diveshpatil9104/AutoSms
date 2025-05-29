package com.example.autowish.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autowish.data.AppDatabase
import com.example.autowish.data.BirthdayEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class BirthdayViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).birthdayDao()

    val birthdays = dao.getAllBirthdays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBirthday(entry: BirthdayEntry) {
        viewModelScope.launch {
            dao.insert(entry)
        }
    }

    fun importFromExcel(context: Context, fileUri: Uri) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(fileUri)
                val reader = BufferedReader(InputStreamReader(inputStream))

                val entries = mutableListOf<BirthdayEntry>()

                reader.useLines { lines ->
                    lines.drop(1).forEach { line -> // Assuming first line is header
                        val parts = line.split(",")

                        if (parts.size >= 3) {
                            val name = parts[0].trim()
                            val date = parts[1].trim()
                            val phone = parts[2].trim()

                            if (name.isNotEmpty() && date.isNotEmpty() && phone.isNotEmpty()) {
                                entries.add(BirthdayEntry(name = name, date = date, phone = phone))
                            }
                        }
                    }
                }

                // Insert all entries into database
                entries.forEach { dao.insert(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}