package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class BirthdayAlarmReceiver : BroadcastReceiver() {
    private val TAG = "BirthdayAlarmReceiver"
    private val TEST_MODE = false // Set to false for production
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_DATE = "lastSentDate"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received at ${Date()}")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())
        val lastSentDate = prefs.getString(PREF_SENT_DATE, "")

        // Skip sending if already sent today (in test mode)
        if (!TEST_MODE && lastSentDate == today) {
            Log.d(TAG, "SMS already sent today ($today), skipping")
            // Still reschedule for next day
            AlarmUtils.cancelAlarm(context)
            AlarmUtils.scheduleDailyAlarm(context)
            return
        }

        val db = BirthdayDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val list = db.birthdayDao().getBirthdaysByDate(today)
            Log.d(TAG, "Found ${list.size} birthdays for $today")
            list.forEach {
                Log.d(TAG, "Sending SMS to ${it.phoneNumber} for ${it.name}")
                sendSMS(context, it.phoneNumber, it.message)
            }

            // Update last sent date (only in production mode)
            if (!TEST_MODE) {
                prefs.edit().putString(PREF_SENT_DATE, today).apply()
            }

            // Reschedule alarm for next trigger
            AlarmUtils.cancelAlarm(context)
            AlarmUtils.scheduleDailyAlarm(context)
            Log.d(TAG, "Alarm rescheduled for ${if (TEST_MODE) "1 minute" else "next day"}")
        }
    }
}