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
            try {
                val list = db.birthdayDao().getBirthdaysByDate(today)
                Log.d(TAG, "Found ${list.size} birthdays for $today")
                if (list.isEmpty()) {
                    Log.d(TAG, "No birthdays found for $today")
                }

                // Process each birthday sequentially
                list.forEach { birthday ->
                    // Direct message
                    Log.d(TAG, "Sending direct SMS to ${birthday.phoneNumber} for ${birthday.name}")
                    sendSMS(context, birthday.phoneNumber, birthday.name, birthday.personType)

                    // Peer group messages
                    val peers = db.birthdayDao().getPeers(
                        department = birthday.department,
                        year = birthday.year,
                        groupId = birthday.groupId,
                        excludeId = birthday.id
                    ).shuffled().take(5)
                    peers.forEach { peer ->
                        Log.d(TAG, "Sending peer SMS to ${peer.phoneNumber} for ${birthday.name}")
                        sendPeerSMS(context, peer.phoneNumber, birthday.name, birthday.personType)
                        delay(500) // 500ms delay between peer messages
                    }

                    // HOD notification (for staff birthdays only)
                    if (birthday.personType == "Staff") {
                        val hods = db.birthdayDao().getHodByDepartment(birthday.department)
                        hods.forEach { hod ->
                            Log.d(TAG, "Sending HOD SMS to ${hod.phoneNumber} for ${birthday.name}")
                            sendHodSMS(context, hod.phoneNumber, birthday.name)
                        }
                    }
                }

                if (!TEST_MODE && list.isNotEmpty()) {
                    prefs.edit().putString(PREF_SENT_DATE, today).apply()
                    Log.d(TAG, "Updated SharedPreferences: lastSentDate = $today")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing birthdays: ${e.message}")
            } finally {
                AlarmUtils.cancelAlarm(context)
                AlarmUtils.scheduleDailyAlarm(context)
                Log.d(TAG, "Alarm rescheduled for next midnight")
            }
        }
    }
}