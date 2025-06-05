package com.example.autowish

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay

object SmsUtils {
    private const val TAG = "SmsUtils"
    private const val NOTIFICATION_CHANNEL_ID = "birthday_notifications"
    private const val NOTIFICATION_CHANNEL_NAME = "Birthday Notifications"
    private const val SMS_QUEUE_DELAY_MS = 10000L

    private fun validatePermissions(context: Context): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted")
            showNotification(context, "Permission Error", "SEND_SMS permission not granted")
            return false
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
        }
        return true
    }

    private fun isSimAvailable(context: Context): Boolean {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_PHONE_STATE permission not granted")
            return false
        }
        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
        return activeSubscriptions?.isNotEmpty() == true
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

    suspend fun sendDirectSms(context: Context, phoneNumber: String, name: String, personType: String) {
        if (!validatePermissions(context)) throw SecurityException("SEND_SMS permission not granted")
        if (!isSimAvailable(context)) throw IllegalStateException("No active SIM card")
        if (!phoneNumber.matches(Regex("\\d{10,25}"))) throw IllegalArgumentException("Invalid phone number: $phoneNumber")

        val message = when (personType) {
            "Student" -> "Happy Birthday, $name! Wishing you a day filled with joy and a year full of success. -SSVPS College of Engineering, Dhule"
            "Staff" -> "Happy Birthday, $name! Wishing you good health, happiness, and continued success. -SSVPS College of Engineering, Dhule"
            else -> throw IllegalArgumentException("Invalid personType: $personType")
        }

        try {
            val smsManager = getSmsManager(context)
            val sentIntent = createSentIntent(context, phoneNumber, name, "direct")
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            Log.d(TAG, "Direct SMS sent to $phoneNumber: $message")
            showNotification(context, "Direct SMS Sent", "Birthday SMS sent to $name ($phoneNumber)")
            delay(SMS_QUEUE_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending direct SMS to $phoneNumber: ${e.message}")
            showNotification(context, "Direct SMS Failed", "Failed to send SMS to $name: ${e.message}")
            throw e
        }
    }

    suspend fun sendPeerSms(context: Context, phoneNumber: String, birthdayName: String, personType: String) {
        if (!validatePermissions(context)) throw SecurityException("SEND_SMS permission not granted")
        if (!isSimAvailable(context)) throw IllegalStateException("No active SIM card")
        if (!phoneNumber.matches(Regex("\\d{10,25}"))) throw IllegalArgumentException("Invalid phone number: $phoneNumber")

        val message = when (personType) {
            "Student" -> "Today is $birthdayName’s birthday! 🎉 Wish them well!"
            "Staff" -> "Today is $birthdayName’s birthday! 🎉 Send your wishes!"
            else -> throw IllegalArgumentException("Invalid personType: $personType")
        }

        try {
            val smsManager = getSmsManager(context)
            val sentIntent = createSentIntent(context, phoneNumber, birthdayName, "peer")
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            Log.d(TAG, "Peer SMS sent to $phoneNumber: $message")
            showNotification(context, "Peer SMS Sent", "Peer SMS sent for $birthdayName to $phoneNumber")
            delay(SMS_QUEUE_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending peer SMS to $phoneNumber: ${e.message}")
            showNotification(context, "Peer SMS Failed", "Failed to send peer SMS for $birthdayName: ${e.message}")
            throw e
        }
    }

    suspend fun sendHodSms(context: Context, phoneNumber: String, birthdayName: String) {
        if (!validatePermissions(context)) throw SecurityException("SEND_SMS permission not granted")
        if (!isSimAvailable(context)) throw IllegalStateException("No active SIM card")
        if (!phoneNumber.matches(Regex("\\d{10,25}"))) throw IllegalArgumentException("Invalid phone number: $phoneNumber")

        val message = "Today is $birthdayName’s birthday! 🎉 Send your wishes!"

        try {
            val smsManager = getSmsManager(context)
            val sentIntent = createSentIntent(context, phoneNumber, birthdayName, "hod")
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            Log.d(TAG, "HOD SMS sent to $phoneNumber: $message")
            showNotification(context, "HOD SMS Sent", "HOD SMS sent for $birthdayName to $phoneNumber")
            delay(SMS_QUEUE_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending HOD SMS to $phoneNumber: ${e.message}")
            showNotification(context, "HOD SMS Failed", "Failed to send HOD SMS for $birthdayName: ${e.message}")
            throw e
        }
    }

    fun showNotification(context: Context, title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
        }
    }
}