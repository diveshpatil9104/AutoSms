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
    private val TEST_MODE = false
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_DATE = "lastSentDate"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received at ${Date()}")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())
        val lastSentDate = prefs.getString(PREF_SENT_DATE, "")

        if (!TEST_MODE && lastSentDate == today) {
            Log.d(TAG, "SMS already sent today ($today), skipping")
            AlarmUtils.cancelAlarm(context)
            AlarmUtils.scheduleDailyAlarm(context)
            return
        }

        val db = BirthdayDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val list = db.birthdayDao().getBirthdaysByDate(today)
            Log.d(TAG, "Found ${list.size} birthdays for $today")
            if (list.isEmpty()) {
                Log.d(TAG, "No birthdays found for $today")
            }
            list.forEach {
                Log.d(TAG, "Sending SMS to ${it.phoneNumber} for ${it.name}")
                sendSMS(context, it.phoneNumber, it.name, it.personType)
            }

            if (!TEST_MODE && list.isNotEmpty()) {
                prefs.edit().putString(PREF_SENT_DATE, today).apply()
                Log.d(TAG, "Updated SharedPreferences: lastSentDate = $today")
            }

            AlarmUtils.cancelAlarm(context)
            AlarmUtils.scheduleDailyAlarm(context)
            Log.d(TAG, "Alarm rescheduled for next midnight")
        }
    }
}