package com.example.autowish

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

object SmsUtils {
    private const val TAG = "SmsUtils"
    private const val NOTIFICATION_CHANNEL_ID = "birthday_notifications"
    private const val NOTIFICATION_CHANNEL_NAME = "Birthday Notifications"
    private const val SMS_QUEUE_DELAY_MS = 36000L // 36 seconds to avoid throttling (~100 SMS/hour)
    private const val PREFS_NAME = "BirthdayPrefs"
    private const val PREF_SMS_COUNT = "smsCount"
    private const val PREF_SMS_TIMESTAMP = "smsTimestamp"
    private const val HOUR_MS = 3600000L

    private fun validatePermissions(context: Context): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted")
            showNotification(context, "Permission Error", "SEND_SMS permission not granted")
            return false
        }
        return true
    }

    private fun isSimAvailable(context: Context): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_PHONE_STATE permission not granted")
            showNotification(context, "Permission Error", "READ_PHONE_STATE permission not granted")
            return false
        }
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        return subscriptionManager.activeSubscriptionInfoList?.isNotEmpty() == true
    }

    private fun getSmsManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    }

    private fun createSentIntent(context: Context, phoneNumber: String, name: String, messageType: String): PendingIntent {
        val intent = Intent(context, SmsSentService::class.java).apply {
            putExtra("phoneNumber", phoneNumber)
            putExtra("name", name)
            putExtra("messageType", messageType)
        }
        return PendingIntent.getService(
            context,
            (phoneNumber + messageType + System.currentTimeMillis()).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    suspend fun sendWithRetries(
        context: Context,
        phoneNumber: String,
        name: String,
        personType: String,
        type: String,
        smsCount: AtomicInteger,
        prefs: SharedPreferences,
        retryCount: Int = 0
    ): Boolean {
        if (!validatePermissions(context)) return false
        if (!isSimAvailable(context)) {
            Log.e(TAG, "No active SIM card")
            showNotification(context, "SIM Error", "No active SIM card detected")
            return false
        }
        if (!phoneNumber.matches(Regex("\\d{10,25}"))) {
            Log.w(TAG, "Invalid phone number: $phoneNumber for $type SMS")
            return false
        }
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 16) {
            Log.w(TAG, "Outside sending window (midnight to 4 PM) for $type SMS to $phoneNumber")
            return false
        }

        var attempt = 0
        var delayMs = 10000L
        while (attempt < 3) {
            try {
                val message = when (type) {
                    "direct" -> when (personType) {
                        "Student" -> "Happy Birthday, $name! Wishing you a day filled with joy and a year full of success. -SSVPS College of Engineering, Dhule"
                        "Staff" -> "Happy Birthday, $name! Wishing you good health, happiness, and continued success. -SSVPS College of Engineering, Dhule"
                        else -> throw IllegalArgumentException("Invalid personType: $personType")
                    }
                    "peer" -> when (personType) {
                        "Student" -> "Today is $name’s birthday! 🎉 Wish them well!"
                        "Staff" -> "Today is $name’s birthday! 🎉 Send your wishes!"
                        else -> throw IllegalArgumentException("Invalid personType: $personType")
                    }
                    "hod" -> "Today is $name’s birthday! 🎉 Send your wishes!"
                    else -> throw IllegalArgumentException("Invalid type: $type")
                }

                val smsManager = getSmsManager(context)
                val sentIntent = createSentIntent(context, phoneNumber, name, type)
                smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
                smsCount.incrementAndGet()
                prefs.edit()
                    .putInt(PREF_SMS_COUNT, smsCount.get())
                    .putLong(PREF_SMS_TIMESTAMP, System.currentTimeMillis())
                    .apply()
                Log.d(TAG, "$type SMS sent to $phoneNumber on attempt ${attempt + 1} (SMS count: ${smsCount.get()})")
                delay(SMS_QUEUE_DELAY_MS)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed $type SMS to $phoneNumber on attempt ${attempt + 1}: ${e.message}")
                attempt++
                if (attempt < 3) {
                    delay(delayMs)
                    delayMs *= 2
                } else {
                    showNotification(context, "$type SMS Failed", "Failed to send $type SMS to $name after $retryCount retries")
                }
            }
        }
        return false
    }

    suspend fun sendDirectSms(context: Context, phoneNumber: String, name: String, personType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val success = sendWithRetries(context, phoneNumber, name, personType, "direct", smsCount, prefs)
        if (!success) {
            Log.w(TAG, "Failed to send direct SMS to $phoneNumber after retries")
        }
    }

    suspend fun sendPeerSms(context: Context, phoneNumber: String, name: String, personType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val success = sendWithRetries(context, phoneNumber, name, personType, "peer", smsCount, prefs)
        if (!success) {
            Log.w(TAG, "Failed to send peer SMS to $phoneNumber after retries")
        }
    }

    suspend fun sendHodSms(context: Context, phoneNumber: String, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val success = sendWithRetries(context, phoneNumber, name, "Staff", "hod", smsCount, prefs)
        if (!success) {
            Log.w(TAG, "Failed to send HOD SMS to $phoneNumber after retries")
        }
    }

    fun showNotification(context: Context, title: String, content: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}