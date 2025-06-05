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
    private val PREF_FAILED_SMS = "failedSms"
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 10000L
    private val SMS_DELAY_MS = 10000L
    private val MAX_STUDENT_PEERS = 3
    private val MAX_STAFF_PEERS = 2

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

                processMissedBirthdays(context, db, prefs, today, yesterday)
                processFailedMessages(context, prefs)
            }
        } else {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
        }
    }

    private suspend fun processMissedBirthdays(context: Context, db: BirthdayDatabase, prefs: SharedPreferences, today: String, yesterday: String) {
        val lastSentDate = prefs.getString(PREF_SENT_DATE, "")
        val failedMessages = mutableListOf<BirthdayAlarmReceiver.FailedMessage>()

        if (lastSentDate != today) {
            try {
                val todayList = db.birthdayDao().getBirthdaysByDate(today)
                Log.d(TAG, "Found ${todayList.size} birthdays for $today after boot")
                if (todayList.isEmpty()) {
                    Log.d(TAG, "No birthdays found for $today")
                }
                todayList.forEach { birthday ->
                    val directSuccess = sendWithRetries(context, birthday.phoneNumber, birthday.name, birthday.personType, "direct", failedMessages)
                    if (!directSuccess) {
                        Log.w(TAG, "Failed to send direct SMS to ${birthday.name}")
                    }
                    delay(SMS_DELAY_MS)

                    val peers = when (birthday.personType) {
                        "Student" -> {
                            db.birthdayDao().getPeers(birthday.department, birthday.year, birthday.groupId, birthday.id)
                                .filter { it.personType == "Student" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber }
                                .let { if (it.size <= 3) it else it.shuffled().take(MAX_STUDENT_PEERS) }
                        }
                        "Staff" -> {
                            val allPeers = db.birthdayDao().getPeers(birthday.department, null, birthday.groupId, birthday.id)
                                .filter { it.personType == "Staff" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber }
                            val hod = allPeers.filter { it.isHod == true }.shuffled().take(1)
                            val nonHod = allPeers.filter { it.isHod == false }.shuffled().take(MAX_STAFF_PEERS)
                            (hod + nonHod).distinctBy { it.phoneNumber }
                        }
                        else -> emptyList()
                    }

                    peers.forEach { peer ->
                        val type = if (peer.isHod == true) "hod" else "peer"
                        val peerSuccess = sendWithRetries(context, peer.phoneNumber, birthday.name, birthday.personType, type, failedMessages)
                        if (!peerSuccess) {
                            Log.w(TAG, "Failed to send $type SMS to ${peer.phoneNumber}")
                        }
                        delay(SMS_DELAY_MS)
                    }
                }
                if (todayList.isNotEmpty()) {
                    prefs.edit().putString(PREF_SENT_DATE, today).apply()
                    Log.d(TAG, "Updated lastSentDate = $today")
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
                yesterdayList.forEach { birthday ->
                    val directSuccess = sendWithRetries(context, birthday.phoneNumber, birthday.name, birthday.personType, "direct", failedMessages)
                    if (!directSuccess) {
                        Log.w(TAG, "Failed to send direct SMS to ${birthday.name}")
                    }
                    delay(SMS_DELAY_MS)
                }
                if (yesterdayList.isNotEmpty()) {
                    prefs.edit().putString(PREF_SENT_DATE, yesterday).apply()
                    Log.d(TAG, "Updated lastSentDate = $yesterday")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing yesterday’s birthdays: ${e.message}")
            }
        } else {
            Log.d(TAG, "SMS already sent for $yesterday, skipping")
        }

        saveFailedMessages(prefs, failedMessages)
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
        processMissedBirthdays(context, db, prefs, today, yesterday)
        processFailedMessages(context, prefs)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun processFailedMessages(context: Context, prefs: SharedPreferences) {
        val failedSet = prefs.getStringSet(PREF_FAILED_SMS, emptySet())?.toList() ?: emptyList()
        if (failedSet.isEmpty()) {
            Log.d(TAG, "No failed messages to process")
            return
        }

        Log.d(TAG, "Processing ${failedSet.size} failed messages")
        val remainingFailed = mutableListOf<BirthdayAlarmReceiver.FailedMessage>()
        failedSet.forEach { entry ->
            val failedMessage = BirthdayAlarmReceiver.FailedMessage.fromString(entry)
            if (failedMessage != null && failedMessage.retryCount < 5) {
                val success = sendWithRetries(context, failedMessage.phoneNumber, failedMessage.name, failedMessage.personType, failedMessage.type, remainingFailed, retries = 1)
                if (success) {
                    Log.d(TAG, "Resent ${failedMessage.type} SMS to ${failedMessage.phoneNumber}")
                } else {
                    Log.w(TAG, "Failed to resend ${failedMessage.type} SMS to ${failedMessage.phoneNumber}")
                    remainingFailed.add(failedMessage.copy(retryCount = failedMessage.retryCount + 1))
                }
                delay(SMS_DELAY_MS)
            } else if (failedMessage != null) {
                Log.w(TAG, "Max retries reached for ${failedMessage.type} SMS to ${failedMessage.phoneNumber}")
                remainingFailed.add(failedMessage)
            }
        }

        saveFailedMessages(prefs, remainingFailed)
    }

    private fun saveFailedMessages(prefs: SharedPreferences, failedMessages: List<BirthdayAlarmReceiver.FailedMessage>) {
        val failedSet = failedMessages.map { it.toString() }.toSet()
        prefs.edit().putStringSet(PREF_FAILED_SMS, failedSet).apply()
        Log.d(TAG, "Saved ${failedSet.size} failed messages to SharedPreferences")
    }

    private suspend fun sendWithRetries(
        context: Context,
        phoneNumber: String,
        name: String,
        personType: String,
        type: String,
        failedMessages: MutableList<BirthdayAlarmReceiver.FailedMessage>,
        retries: Int = MAX_RETRIES
    ): Boolean {
        var attempt = 0
        var delay = RETRY_DELAY_MS
        while (attempt < retries) {
            try {
                when (type) {
                    "direct" -> SmsUtils.sendDirectSms(context, phoneNumber, name, personType)
                    "peer" -> SmsUtils.sendPeerSms(context, phoneNumber, name, personType)
                    "hod" -> SmsUtils.sendHodSms(context, phoneNumber, name)
                }
                Log.d(TAG, "$type SMS sent successfully to $phoneNumber on attempt ${attempt + 1}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed $type SMS to $phoneNumber on attempt ${attempt + 1}: ${e.message}")
                attempt++
                if (attempt < retries) {
                    delay(delay)
                    delay *= 2
                } else {
                    failedMessages.add(BirthdayAlarmReceiver.FailedMessage(phoneNumber, name, personType, type, 0))
                }
            }
        }
        return false
    }
}