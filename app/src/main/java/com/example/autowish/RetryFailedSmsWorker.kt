package com.example.autowish

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class RetryFailedSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val TAG = "RetryFailedSmsWorker"
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SMS_COUNT = "smsCount"
    private val PREF_SMS_TIMESTAMP = "smsTimestamp"
    private val SMS_LIMIT_PER_HOUR = 90
    private val HOUR_MS = 3600000L

    override suspend fun doWork(): Result {
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 16) {
            Log.w(TAG, "Outside sending window, retrying tomorrow")
            return Result.retry()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val db = BirthdayDatabase.getInstance(applicationContext)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSmsTimestamp > HOUR_MS) {
            smsCount.set(0)
            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
        }

        val queuedSms = db.smsQueueDao().getPendingSms(10)
        if (queuedSms.isEmpty()) {
            Log.d(TAG, "No queued messages to process")
            return Result.success()
        }

        Log.d(TAG, "Processing ${queuedSms.size} queued messages")
        queuedSms.forEach { sms ->
            if (smsCount.get() >= SMS_LIMIT_PER_HOUR) return@forEach
            val success = SmsUtils.sendWithRetries(
                applicationContext,
                sms.phoneNumber,
                sms.name,
                sms.personType,
                sms.type,
                smsCount,
                prefs,
                sms.retryCount + 1
            )
            if (success) {
                db.smsQueueDao().deleteById(sms.id)
            } else if (sms.retryCount >= 5) {
                db.smsQueueDao().deleteById(sms.id)
            }
        }

        return if (db.smsQueueDao().getQueueSize() > 0) Result.retry() else Result.success()
    }
}