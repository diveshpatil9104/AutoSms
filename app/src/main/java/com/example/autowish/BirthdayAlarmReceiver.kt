package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class BirthdayAlarmReceiver : BroadcastReceiver() {
    private val TAG = "BirthdayAlarmReceiver"
    private val TEST_MODE = false
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_DATE = "lastSentDate"
    private val PREF_FAILED_SMS = "failedSms"
    private val PREF_SENT_PEERS = "sentPeers"
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 10000L
    private val SMS_DELAY_MS = 10000L
    private val MAX_RETRY_ATTEMPTS = 5
    private val PEER_WORK_TAG = "peer_sms_work"

    companion object {
        const val ACTION_PEER_SMS = "com.example.autowish.PEER_SMS"
        const val ACTION_RETRY_FAILED_SMS = "com.example.autowish.RETRY_FAILED_SMS"
        const val EXTRA_BIRTHDAY_ID = "birthday_id"
        const val EXTRA_BIRTHDAY_NAME = "birthday_name"
        const val EXTRA_PERSON_TYPE = "person_type"
        const val EXTRA_DEPARTMENT = "department"
        const val EXTRA_YEAR = "year"
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received at ${Date()}, action: ${intent.action}")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())
        val lastSentDate = prefs.getString(PREF_SENT_DATE, "")

        when (intent.action) {
            ACTION_RETRY_FAILED_SMS -> {
                CoroutineScope(Dispatchers.IO).launch {
                    processFailedMessages(context, prefs)
                }
            }
            else -> {
                if (!TEST_MODE && lastSentDate == today) {
                    Log.d(TAG, "SMS already sent today ($today), processing failed messages")
                    CoroutineScope(Dispatchers.IO).launch {
                        processFailedMessages(context, prefs)
                        AlarmUtils.cancelAlarm(context)
                        AlarmUtils.scheduleDailyAlarm(context)
                    }
                    return
                }

                val db = try {
                    BirthdayDatabase.getInstance(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize database: ${e.message}")
                    return
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val list = db.birthdayDao().getBirthdaysByDate(today)
                        Log.d(TAG, "Found ${list.size} birthdays for $today: ${list.joinToString { it.name }}")
                        if (list.isEmpty()) {
                            Log.d(TAG, "No birthdays found for $today")
                        }

                        val failedMessages = mutableListOf<FailedMessage>()
                        // Only clear sent peers if it's a new day
                        if (lastSentDate != today) {
                            prefs.edit().remove(PREF_SENT_PEERS).apply()
                            Log.d(TAG, "Cleared sent peers for new day $today")
                        }

                        // Send direct messages
                        list.forEachIndexed { index, birthday ->
                            Log.d(TAG, "Processing birthday ${index + 1}/${list.size}: ${birthday.name} (ID: ${birthday.id}, Type: ${birthday.personType})")
                            val directSuccess = sendWithRetries(context, birthday.phoneNumber, birthday.name, birthday.personType, "direct", failedMessages)
                            if (!directSuccess) {
                                Log.w(TAG, "Failed to send direct SMS to ${birthday.name}")
                            }
                            delay(SMS_DELAY_MS)
                        }

                        // Schedule peer messages
                        list.forEachIndexed { index, birthday ->
                            schedulePeerSmsWork(context, birthday, index * SMS_DELAY_MS)
                        }

                        // Save failed messages
                        saveFailedMessages(prefs, failedMessages)
                        if (!TEST_MODE && list.isNotEmpty()) {
                            prefs.edit().putString(PREF_SENT_DATE, today).apply()
                            Log.d(TAG, "Updated lastSentDate = $today")
                        }

                        processFailedMessages(context, prefs)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing birthdays: ${e.message}")
                    } finally {
                        AlarmUtils.cancelAlarm(context)
                        AlarmUtils.scheduleDailyAlarm(context)
                        Log.d(TAG, "Alarm rescheduled")
                    }
                }
            }
        }
    }

    private fun schedulePeerSmsWork(context: Context, birthday: BirthdayEntry, delayMs: Long) {
        val data = Data.Builder()
            .putInt(EXTRA_BIRTHDAY_ID, birthday.id)
            .putString(EXTRA_BIRTHDAY_NAME, birthday.name)
            .putString(EXTRA_PERSON_TYPE, birthday.personType)
            .putString(EXTRA_DEPARTMENT, birthday.department)
            .putString(EXTRA_YEAR, birthday.year)
            .putString(EXTRA_GROUP_ID, birthday.groupId)
            .putString(EXTRA_PHONE_NUMBER, birthday.phoneNumber)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<PeerSmsWorker>()
            .setInputData(data)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(PEER_WORK_TAG)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "peer_sms_${birthday.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Scheduled peer SMS work for ${birthday.name} with delay ${delayMs}ms")
    }

    private suspend fun sendWithRetries(
        context: Context,
        phoneNumber: String,
        name: String,
        personType: String,
        type: String,
        failedMessages: MutableList<FailedMessage>,
        retries: Int = MAX_RETRIES
    ): Boolean {
        if (!phoneNumber.matches(Regex("\\d{10,25}"))) {
            Log.w(TAG, "Invalid phone number: $phoneNumber for $type SMS")
            return false
        }
        var attempt = 0
        var delay = RETRY_DELAY_MS
        while (attempt < retries) {
            try {
                when (type) {
                    "direct" -> SmsUtils.sendDirectSms(context, phoneNumber, name, personType)
                    "peer" -> SmsUtils.sendPeerSms(context, phoneNumber, name, personType)
                    "hod" -> SmsUtils.sendHodSms(context, phoneNumber, name)
                }
                Log.d(TAG, "$type SMS sent to $phoneNumber on attempt ${attempt + 1}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed $type SMS to $phoneNumber on attempt ${attempt + 1}: ${e.message}")
                attempt++
                if (attempt < retries) {
                    delay(delay)
                    delay *= 2
                } else {
                    failedMessages.add(FailedMessage(phoneNumber, name, personType, type, 0))
                }
            }
        }
        return false
    }

    private suspend fun processFailedMessages(context: Context, prefs: SharedPreferences) {
        val failedSet = prefs.getStringSet(PREF_FAILED_SMS, emptySet())?.toList() ?: emptyList()
        if (failedSet.isEmpty()) {
            Log.d(TAG, "No failed messages to process")
            return
        }

        Log.d(TAG, "Processing ${failedSet.size} failed messages")
        val remainingFailed = mutableListOf<FailedMessage>()
        failedSet.forEach { entry ->
            val failedMessage = FailedMessage.fromString(entry)
            if (failedMessage != null && failedMessage.retryCount < MAX_RETRY_ATTEMPTS) {
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
        if (remainingFailed.isNotEmpty() && remainingFailed.any { it.retryCount < MAX_RETRY_ATTEMPTS }) {
            scheduleRetryFailedSms(context, 10 * 60 * 1000L)
        } else if (remainingFailed.isNotEmpty()) {
            Log.d(TAG, "All failed messages reached max retries")
        } else {
            prefs.edit().remove(PREF_FAILED_SMS).apply()
            Log.d(TAG, "Cleared failed messages")
        }
    }

    private fun scheduleRetryFailedSms(context: Context, delayMs: Long) {
        val workRequest = OneTimeWorkRequestBuilder<RetryFailedSmsWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "retry_failed_sms",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Scheduled retry failed SMS with delay ${delayMs}ms")
    }

    private fun saveFailedMessages(prefs: SharedPreferences, failedMessages: List<FailedMessage>) {
        val failedSet = failedMessages.map { it.toString() }.toSet()
        prefs.edit().putStringSet(PREF_FAILED_SMS, failedSet).apply()
        Log.d(TAG, "Saved ${failedSet.size} failed messages")
    }

    data class FailedMessage(
        val phoneNumber: String,
        val name: String,
        val personType: String,
        val type: String,
        val retryCount: Int = 0
    ) {
        override fun toString(): String = "$phoneNumber|$name|$personType|$type|$retryCount"
        companion object {
            fun fromString(entry: String): FailedMessage? {
                val parts = entry.split("|")
                return if (parts.size == 5) {
                    FailedMessage(parts[0], parts[1], parts[2], parts[3], parts[4].toIntOrNull() ?: 0)
                } else null
            }
        }
    }
}