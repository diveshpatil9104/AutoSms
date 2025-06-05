package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BirthdayAlarmReceiver : BroadcastReceiver() {
    private val TAG = "BirthdayAlarmReceiver"
    private val TEST_MODE = false
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_DATE = "lastSentDate"
    private val PREF_SMS_COUNT = "smsCount"
    private val PREF_SMS_TIMESTAMP = "smsTimestamp"
    private val SMS_DELAY_MS = 36000L
    private val SMS_LIMIT_PER_HOUR = 90
    private val HOUR_MS = 3600000L
    private val PEER_WORK_TAG = "peer_sms_work"

    companion object {
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
        Log.d(TAG, "Received alarm at ${Date()} with action: ${intent.action}")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dateFormat = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        val today = dateFormat.format(Date())

        when (intent.action) {
            ACTION_RETRY_FAILED_SMS -> handleRetryFailedSms(context, prefs)
            else -> processBirthdays(context, prefs, today)
        }
    }

    private fun handleRetryFailedSms(context: Context, prefs: SharedPreferences) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Processing retry for failed SMS")
            processQueuedSms(context, prefs)
        }
    }

    private fun processBirthdays(context: Context, prefs: SharedPreferences, today: String) {
        if (isOutsideSendingWindow()) {
            Log.w(TAG, "Outside SMS sending window (midnight to 4 PM), deferring to tomorrow")
            return
        }

        val db = try {
            BirthdayDatabase.getInstance(context)
        } catch (e: Exception) {
            Log.e(TAG, "Database initialization failed: ${e.message}")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!TEST_MODE && isAlreadyProcessed(prefs, today)) {
                    Log.d(TAG, "SMS already processed for $today, handling queued SMS only")
                    processQueuedSms(context, prefs)
                    return@launch
                }

                val birthdays = fetchBirthdays(db, today)
                if (birthdays.isEmpty()) {
                    Log.d(TAG, "No birthdays found for ${today.substring(0, 5)}")
                } else {
                    Log.d(TAG, "Processing ${birthdays.size} birthdays for ${today.substring(0, 5)}: ${birthdays.joinToString { it.name }}")
                    sendDirectSms(context, prefs, db, birthdays)
                    schedulePeerSms(context, birthdays)
                    if (!TEST_MODE) {
                        updateLastSentDate(prefs, today)
                    }
                }

                processQueuedSms(context, prefs)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing birthdays: ${e.message}")
            } finally {
                rescheduleAlarm(context)
            }
        }
    }

    private fun isOutsideSendingWindow(): Boolean {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 17
    }

    private fun isAlreadyProcessed(prefs: SharedPreferences, today: String): Boolean {
        val lastSentDate = prefs.getString(PREF_SENT_DATE, "")
        Log.d(TAG, "Comparing PREF_SENT_DATE: $lastSentDate with today: $today")
        return lastSentDate == today
    }

    private suspend fun fetchBirthdays(db: BirthdayDatabase, today: String): List<BirthdayEntry> {
        return db.birthdayDao().getBirthdaysByDate(today.substring(0, 5))
    }

    private suspend fun sendDirectSms(context: Context, prefs: SharedPreferences, db: BirthdayDatabase, birthdays: List<BirthdayEntry>) {
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSmsTimestamp > HOUR_MS) {
            smsCount.set(0)
            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
            Log.d(TAG, "Reset SMS count and timestamp")
        }

        birthdays.forEachIndexed { index, birthday ->
            if (smsCount.get() >= SMS_LIMIT_PER_HOUR) {
                queueSms(db, birthday, "direct", currentTime)
            } else {
                val success = SmsUtils.sendWithRetries(
                    context, birthday.phoneNumber, birthday.name, birthday.personType, "direct", smsCount, prefs
                )
                if (!success) {
                    queueSms(db, birthday, "direct", currentTime, retryCount = 1)
                }
            }
            delay(SMS_DELAY_MS)
        }
    }

    private suspend fun schedulePeerSms(context: Context, birthdays: List<BirthdayEntry>) {
        birthdays.forEachIndexed { index, birthday ->
            val delayMs = index * SMS_DELAY_MS
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
            Log.d(TAG, "Scheduled peer SMS work for ${birthday.name} (ID: ${birthday.id}) with delay ${delayMs}ms")
        }
    }

    private fun updateLastSentDate(prefs: SharedPreferences, today: String) {
        prefs.edit().putString(PREF_SENT_DATE, today).apply()
        Log.d(TAG, "Updated PREF_SENT_DATE to $today")
    }

    private suspend fun queueSms(
        db: BirthdayDatabase, birthday: BirthdayEntry, type: String, timestamp: Long, retryCount: Int = 0
    ) {
        db.smsQueueDao().insert(
            SmsQueueEntry(
                phoneNumber = birthday.phoneNumber,
                name = birthday.name,
                personType = birthday.personType,
                type = type,
                retryCount = retryCount,
                timestamp = timestamp
            )
        )
        Log.d(TAG, "Queued $type SMS for ${birthday.name} (${birthday.phoneNumber})")
    }

    private suspend fun processQueuedSms(context: Context, prefs: SharedPreferences) {
        val db = BirthdayDatabase.getInstance(context)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSmsTimestamp > HOUR_MS) {
            smsCount.set(0)
            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
            Log.d(TAG, "Reset SMS count and timestamp for queued SMS")
        }

        if (isOutsideSendingWindow()) {
            Log.w(TAG, "Outside sending window for queued SMS, deferring")
            return
        }

        while (smsCount.get() < SMS_LIMIT_PER_HOUR) {
            val queuedSms = db.smsQueueDao().getPendingSms(10)
            if (queuedSms.isEmpty()) {
                Log.d(TAG, "No queued SMS to process")
                break
            }

            queuedSms.forEach { sms ->
                if (smsCount.get() >= SMS_LIMIT_PER_HOUR) return@forEach
                val success = SmsUtils.sendWithRetries(
                    context, sms.phoneNumber, sms.name, sms.personType, sms.type, smsCount, prefs, sms.retryCount + 1
                )
                if (success) {
                    db.smsQueueDao().deleteById(sms.id)
                    Log.d(TAG, "Sent queued ${sms.type} SMS to ${sms.phoneNumber}")
                } else {
                    if (sms.retryCount < 5) {
                        db.smsQueueDao().insert(sms.copy(retryCount = sms.retryCount + 1))
                        db.smsQueueDao().deleteById(sms.id)
                        Log.d(TAG, "Re-queued ${sms.type} SMS for ${sms.phoneNumber} with retry count ${sms.retryCount + 1}")
                    } else {
                        Log.w(TAG, "Failed to send queued ${sms.type} SMS to ${sms.phoneNumber} after max retries")
                    }
                }
                delay(SMS_DELAY_MS)
            }
        }

        if (db.smsQueueDao().getQueueSize() > 0) {
            scheduleRetryFailedSms(context)
        }
    }

    private fun scheduleRetryFailedSms(context: Context) {
        val delayMs = 10 * 60 * 1000L // 10 minutes
        val workRequest = OneTimeWorkRequestBuilder<RetryFailedSmsWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "retry_failed_sms",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Scheduled retry for failed SMS with delay ${delayMs}ms")
    }

    private fun rescheduleAlarm(context: Context) {
        AlarmUtils.cancelAlarm(context)
        AlarmUtils.scheduleDailyAlarm(context)
        Log.d(TAG, "Alarm rescheduled for next day")
    }
}