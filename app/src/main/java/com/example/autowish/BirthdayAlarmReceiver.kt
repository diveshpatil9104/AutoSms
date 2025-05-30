package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class BirthdayAlarmReceiver : BroadcastReceiver() {
    private val TAG = "BirthdayAlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received at ${Date()}")

        val db = BirthdayDatabase.getInstance(context)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())

        CoroutineScope(Dispatchers.IO).launch {
            val list = db.birthdayDao().getBirthdaysByDate(today)
            Log.d(TAG, "Found ${list.size} birthdays for $today")
            list.forEach {
                Log.d(TAG, "Sending SMS to ${it.phoneNumber} for ${it.name}")
                sendSMS(context, it.phoneNumber, it.message)
            }
            // Reschedule alarm for next day
            AlarmUtils.scheduleDailyAlarm(context)
        }
    }
}