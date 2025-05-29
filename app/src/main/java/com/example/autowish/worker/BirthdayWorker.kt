package com.example.autowish.worker

import android.content.Context
import android.telephony.SmsManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.autowish.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BirthdayWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(applicationContext).birthdayDao()

            // Get current month and day as MM-dd (e.g., "05-29")
            val monthDay = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())

            // Fetch birthdays matching today's month and day, ignoring year
            val birthdayPeople = dao.getBirthdaysByMonthDay(monthDay)

            val smsManager = SmsManager.getDefault()
            for (person in birthdayPeople) {
                val message = "Happy Birthday, ${person.name}! 🎉"
                smsManager.sendTextMessage(person.phone, null, message, null, null)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
