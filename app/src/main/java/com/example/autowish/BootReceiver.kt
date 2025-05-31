package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_DATE = "lastSentDate"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed at ${Date()}, rescheduling alarm")
            // Reschedule the alarm for next midnight
            AlarmUtils.scheduleDailyAlarm(context)

            // Check for missed birthdays
            val db = BirthdayDatabase.getInstance(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())
            val yesterday = SimpleDateFormat("MM-dd", Locale.getDefault()).format(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
            )

            CoroutineScope(Dispatchers.IO).launch {
                // Check today’s birthdays (e.g., 05-31)
                val lastSentDate = prefs.getString(PREF_SENT_DATE, "")
                if (lastSentDate != today) {
                    val todayList = db.birthdayDao().getBirthdaysByDate(today)
                    Log.d(TAG, "Found ${todayList.size} birthdays for $today after boot")
                    if (todayList.isEmpty()) {
                        Log.d(TAG, "No birthdays found for $today")
                    }
                    todayList.forEach {
                        Log.d(TAG, "Sending missed SMS to ${it.phoneNumber} for ${it.name} on $today")
                        sendSMS(context, it.phoneNumber, it.message)
                    }
                    // Update SharedPreferences
                    if (todayList.isNotEmpty()) {
                        prefs.edit().putString(PREF_SENT_DATE, today).apply()
                        Log.d(TAG, "Updated SharedPreferences: lastSentDate = $today")
                    }
                } else {
                    Log.d(TAG, "SMS already sent for $today, skipping")
                }

                // Check yesterday’s birthdays (e.g., 05-30)
                if (lastSentDate != yesterday) {
                    val yesterdayList = db.birthdayDao().getBirthdaysByDate(yesterday)
                    Log.d(TAG, "Found ${yesterdayList.size} birthdays for $yesterday after boot")
                    if (yesterdayList.isEmpty()) {
                        Log.d(TAG, "No birthdays found for $yesterday")
                    }
                    yesterdayList.forEach {
                        Log.d(TAG, "Sending missed SMS to ${it.phoneNumber} for ${it.name} on $yesterday")
                        sendSMS(context, it.phoneNumber, it.message)
                    }
                    // Update SharedPreferences for yesterday
                    if (yesterdayList.isNotEmpty()) {
                        prefs.edit().putString(PREF_SENT_DATE, yesterday).apply()
                        Log.d(TAG, "Updated SharedPreferences: lastSentDate = $yesterday")
                    }
                } else {
                    Log.d(TAG, "SMS already sent for $yesterday, skipping")
                }
            }
        } else {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
        }
    }
}