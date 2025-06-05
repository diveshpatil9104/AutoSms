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
//import java.util.concurrent.AtomicInteger
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
        Log.d(TAG, "Alarm received at ${Date()}, action: ${intent.action}")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())

        when (intent.action) {
            ACTION_RETRY_FAILED_SMS -> {
                CoroutineScope(Dispatchers.IO).launch {
                    processQueuedSms(context, prefs)
                }
            }
            else -> {
                if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 16) {
                    Log.w(TAG, "Outside sending window (midnight to 4 PM), queuing for tomorrow")
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
                        if (!TEST_MODE && prefs.getString(PREF_SENT_DATE, "") == today) {
                            Log.d(TAG, "SMS already sent today ($today), processing queued SMS")
                            processQueuedSms(context, prefs)
                            return@launch
                        }

                        val list = db.birthdayDao().getBirthdaysByDate(today)
                        Log.d(TAG, "Found ${list.size} birthdays for $today: ${list.joinToString { it.name }}")
                        if (list.isEmpty()) {
                            Log.d(TAG, "No birthdays found for $today")
                        }

                        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
                        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - lastSmsTimestamp > HOUR_MS) {
                            smsCount.set(0)
                            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
                        }

                        list.forEachIndexed { index, birthday ->
                            if (smsCount.get() >= SMS_LIMIT_PER_HOUR) {
                                db.smsQueueDao().insert(SmsQueueEntry(
                                    phoneNumber = birthday.phoneNumber,
                                    name = birthday.name,
                                    personType = birthday.personType,
                                    type = "direct",
                                    retryCount = 0,
                                    timestamp = currentTime
                                ))
                            } else {
                                val directSuccess = SmsUtils.sendWithRetries(context, birthday.phoneNumber, birthday.name, birthday.personType, "direct", smsCount, prefs)
                                if (!directSuccess) {
                                    db.smsQueueDao().insert(SmsQueueEntry(
                                        phoneNumber = birthday.phoneNumber,
                                        name = birthday.name,
                                        personType = birthday.personType,
                                        type = "direct",
                                        retryCount = 1,
                                        timestamp = currentTime
                                    ))
                                }
                            }
                            delay(SMS_DELAY_MS)
                        }

                        list.forEachIndexed { index, birthday ->
                            schedulePeerSmsWork(context, birthday, index * SMS_DELAY_MS)
                        }

                        if (!TEST_MODE && list.isNotEmpty()) {
                            prefs.edit().putString(PREF_SENT_DATE, today).apply()
                            Log.d(TAG, "Updated lastSentDate = $today")
                        }

                        processQueuedSms(context, prefs)
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

    private suspend fun processQueuedSms(context: Context, prefs: SharedPreferences) {
        val db = BirthdayDatabase.getInstance(context)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSmsTimestamp > HOUR_MS) {
            smsCount.set(0)
            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
        }

        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 16) {
            Log.w(TAG, "Outside sending window, queuing for tomorrow")
            return
        }

        while (smsCount.get() < SMS_LIMIT_PER_HOUR) {
            val queuedSms = db.smsQueueDao().getPendingSms(10)
            if (queuedSms.isEmpty()) break

            queuedSms.forEach { sms ->
                if (smsCount.get() >= SMS_LIMIT_PER_HOUR) return@forEach
                val success = SmsUtils.sendWithRetries(context, sms.phoneNumber, sms.name, sms.personType, sms.type, smsCount, prefs, sms.retryCount + 1)
                if (success) {
                    db.smsQueueDao().deleteById(sms.id)
                    Log.d(TAG, "Sent queued ${sms.type} SMS to ${sms.phoneNumber}")
                } else {
                    if (sms.retryCount < 5) {
                        db.smsQueueDao().insert(sms.copy(retryCount = sms.retryCount + 1))
                        db.smsQueueDao().deleteById(sms.id)
                    }
                    Log.w(TAG, "Failed to send queued ${sms.type} SMS to ${sms.phoneNumber}")
                }
                delay(SMS_DELAY_MS)
            }
        }

        if (db.smsQueueDao().getQueueSize() > 0) {
            scheduleRetryFailedSms(context, 10 * 60 * 1000L)
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
}