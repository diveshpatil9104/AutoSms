package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed at ${Date()}, rescheduling alarm")
            // Reschedule the alarm
            AlarmUtils.scheduleDailyAlarm(context)

            // Check for missed birthdays
            val db = BirthdayDatabase.getInstance(context)
            val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())
            Log.d(TAG, "Checking for missed birthdays on $today")

            CoroutineScope(Dispatchers.IO).launch {
                val list = db.birthdayDao().getBirthdaysByDate(today)
                Log.d(TAG, "Found ${list.size} birthdays for $today after boot")
                if (list.isEmpty()) {
                    Log.d(TAG, "No birthdays found for $today")
                }
                list.forEach {
                    Log.d(TAG, "Sending missed SMS to ${it.phoneNumber} for ${it.name}")
                    sendSMS(context, it.phoneNumber, it.message)
                }
            }
        } else {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
        }
    }
}