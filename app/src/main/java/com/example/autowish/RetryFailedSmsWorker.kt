package com.example.autowish

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class RetryFailedSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val TAG = "RetryFailedSmsWorker"
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_FAILED_SMS = "failedSms"
    private val SMS_DELAY_MS = 10000L
    private val MAX_RETRY_ATTEMPTS = 5

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val failedSet = prefs.getStringSet(PREF_FAILED_SMS, emptySet())?.toList() ?: emptyList()
        if (failedSet.isEmpty()) {
            Log.d(TAG, "No failed messages to process")
            return Result.success()
        }

        Log.d(TAG, "Processing ${failedSet.size} failed messages")
        val remainingFailed = mutableListOf<BirthdayAlarmReceiver.FailedMessage>()
        failedSet.forEach { entry ->
            val failedMessage = BirthdayAlarmReceiver.FailedMessage.fromString(entry)
            if (failedMessage != null && failedMessage.retryCount < MAX_RETRY_ATTEMPTS) {
                val (phoneNumber, name, personType, type, retryCount) = failedMessage
                val success = sendWithRetries(phoneNumber, name, personType, type, remainingFailed)
                if (success) {
                    Log.d(TAG, "Successfully resent $type SMS to $phoneNumber for $name")
                } else {
                    Log.w(TAG, "Failed to resend $type SMS to $phoneNumber for $name")
                    remainingFailed.add(failedMessage.copy(retryCount = retryCount + 1))
                }
                delay(SMS_DELAY_MS)
            } else if (failedMessage != null) {
                Log.w(TAG, "Max retries reached for ${failedMessage.type} SMS to ${failedMessage.phoneNumber}")
                remainingFailed.add(failedMessage)
            }
        }

        saveFailedMessages(prefs, remainingFailed)
        return if (remainingFailed.isEmpty()) {
            prefs.edit().remove(PREF_FAILED_SMS).apply()
            Log.d(TAG, "Cleared failed messages from SharedPreferences")
            Result.success()
        } else {
            Result.retry()
        }
    }

    private suspend fun sendWithRetries(
        phoneNumber: String,
        name: String,
        personType: String,
        type: String,
        failedMessages: MutableList<BirthdayAlarmReceiver.FailedMessage>,
        retries: Int = 1
    ): Boolean {
        var attempt = 0
        var delay = 10000L
        while (attempt < retries) {
            try {
                when (type) {
                    "direct" -> SmsUtils.sendDirectSms(applicationContext, phoneNumber, name, personType)
                    "peer" -> SmsUtils.sendPeerSms(applicationContext, phoneNumber, name, personType)
                    "hod" -> SmsUtils.sendHodSms(applicationContext, phoneNumber, name)
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

    private fun saveFailedMessages(prefs: SharedPreferences, failedMessages: List<BirthdayAlarmReceiver.FailedMessage>) {
        val failedSet = failedMessages.map { it.toString() }.toSet()
        prefs.edit().putStringSet(PREF_FAILED_SMS, failedSet).apply()
        Log.d(TAG, "Saved ${failedSet.size} failed messages to SharedPreferences")
    }
}