package com.example.autowish

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.telephony.SmsManager

class SmsSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val TAG = "SmsSendWorker"
    private val PREFS_NAME = "BirthdayPrefs"

    override suspend fun doWork(): Result {
        val phoneNumber = inputData.getString("phoneNumber") ?: return Result.failure()
        val name = inputData.getString("name") ?: return Result.failure()
        val personType = inputData.getString("personType") ?: ""
        val type = inputData.getString("type") ?: ""
        val retryCount = inputData.getInt("retryCount", 0)

        if (!SmsUtils.validatePermissions(applicationContext) || !SmsUtils.isSimAvailable(applicationContext)) {
            return Result.failure()
        }

        val message = when (type) {
            "direct" -> when (personType) {
                "Student" -> "Happy Birthday, $name! Wishing you a day filled with joy and a year full of success. -SSVPS College of Engineering, Dhule"
                "Staff" -> "Happy Birthday, $name! Wishing you good health, happiness, and continued success. -SSVPS College of Engineering, Dhule"
                else -> return Result.failure()
            }
            "peer" -> when (personType) {
                "Student" -> "Today is $name’s birthday! 🎉 Wish them well!"
                "Staff" -> "Today is $name’s birthday! 🎉 Send your wishes!"
                else -> return Result.failure()
            }
            "hod" -> "Today is $name’s birthday! 🎉 Send your wishes!"
            else -> return Result.failure()
        }

        try {
            val smsManager = SmsUtils.getSmsManager(applicationContext)
            val sentIntent = SmsUtils.createSentIntent(applicationContext, phoneNumber, name, type)
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            Log.d(TAG, "$type SMS sent to $phoneNumber")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed $type SMS to $phoneNumber: ${e.message}")
            if (retryCount < 5) {
                val db = BirthdayDatabase.getInstance(applicationContext)
                db.smsQueueDao().insert(SmsQueueEntry(
                    phoneNumber = phoneNumber,
                    name = name,
                    personType = personType,
                    type = type,
                    retryCount = retryCount + 1,
                    timestamp = System.currentTimeMillis()
                ))
                return Result.retry()
            }
            SmsUtils.showNotification(applicationContext, "$type SMS Failed", "Failed to send $type SMS to $name after $retryCount retries")
            return Result.failure()
        }
    }
}