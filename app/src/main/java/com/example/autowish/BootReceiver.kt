package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_DATE = "lastSentDate"
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 5000L

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed at ${Date()}, rescheduling alarm")
            AlarmUtils.scheduleDailyAlarm(context)

            val db = try {
                BirthdayDatabase.getInstance(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize database: ${e.message}")
                return
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())
            val yesterday = SimpleDateFormat("MM-dd", Locale.getDefault()).format(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
            )

            CoroutineScope(Dispatchers.IO).launch {
                if (!isNetworkAvailable(context)) {
                    Log.w(TAG, "No network available, attempting retry")
                    retrySendSMS(context, db, prefs, today, yesterday)
                    return@launch
                }

                val lastSentDate = prefs.getString(PREF_SENT_DATE, "")
                if (lastSentDate != today) {
                    try {
                        val todayList = db.birthdayDao().getBirthdaysByDate(today)
                        Log.d(TAG, "Found ${todayList.size} birthdays for $today after boot")
                        if (todayList.isEmpty()) {
                            Log.d(TAG, "No birthdays found for $today")
                        }
                        todayList.forEach {
                            Log.d(TAG, "Sending missed SMS to ${it.phoneNumber} for ${it.name} on $today")
                            sendSMS(context, it.phoneNumber, it.name, it.personType)
                        }
                        if (todayList.isNotEmpty()) {
                            prefs.edit().putString(PREF_SENT_DATE, today).apply()
                            Log.d(TAG, "Updated SharedPreferences: lastSentDate = $today")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing today’s birthdays: ${e.message}")
                    }
                } else {
                    Log.d(TAG, "SMS already sent for $today, skipping")
                }

                if (lastSentDate != yesterday) {
                    try {
                        val yesterdayList = db.birthdayDao().getBirthdaysByDate(yesterday)
                        Log.d(TAG, "Found ${yesterdayList.size} birthdays for $yesterday after boot")
                        if (yesterdayList.isEmpty()) {
                            Log.d(TAG, "No birthdays found for $yesterday")
                        }
                        yesterdayList.forEach {
                            Log.d(TAG, "Sending missed SMS to ${it.phoneNumber} for ${it.name} on $yesterday")
                            sendSMS(context, it.phoneNumber, it.name, it.personType)
                        }
                        if (yesterdayList.isNotEmpty()) {
                            prefs.edit().putString(PREF_SENT_DATE, yesterday).apply()
                            Log.d(TAG, "Updated SharedPreferences: lastSentDate = $yesterday")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing yesterday’s birthdays: ${e.message}")
                    }
                } else {
                    Log.d(TAG, "SMS already sent for $yesterday, skipping")
                }
            }
        } else {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun retrySendSMS(context: Context, db: BirthdayDatabase, prefs: SharedPreferences, today: String, yesterday: String) {
        var attempts = 0
        while (attempts < MAX_RETRIES && !isNetworkAvailable(context)) {
            Log.d(TAG, "Retry attempt ${attempts + 1} of $MAX_RETRIES: Waiting for network")
            delay(RETRY_DELAY_MS)
            attempts++
        }
        if (!isNetworkAvailable(context)) {
            Log.e(TAG, "Failed to send SMS: No network after $MAX_RETRIES retries")
            return
        }
        Log.d(TAG, "Network available after retry, processing birthdays")
        val lastSentDate = prefs.getString(PREF_SENT_DATE, "")
        if (lastSentDate != today) {
            val todayList = db.birthdayDao().getBirthdaysByDate(today)
            todayList.forEach {
                sendSMS(context, it.phoneNumber, it.name, it.personType)
            }
            if (todayList.isNotEmpty()) {
                prefs.edit().putString(PREF_SENT_DATE, today).apply()
            }
        }
        if (lastSentDate != yesterday) {
            val yesterdayList = db.birthdayDao().getBirthdaysByDate(yesterday)
            yesterdayList.forEach {
                sendSMS(context, it.phoneNumber, it.name, it.personType)
            }
            if (yesterdayList.isNotEmpty()) {
                prefs.edit().putString(PREF_SENT_DATE, yesterday).apply()
            }
        }
    }
}