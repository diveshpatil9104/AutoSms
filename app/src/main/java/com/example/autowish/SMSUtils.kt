package com.example.autowish

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

fun sendSMS(context: Context, phoneNumber: String, name: String, personType: String) {
    val TAG = "SmsUtils"
    val message = when (personType) {
        "Student" -> "Happy Birthday, $name!  Wishing you a day filled with joy and a year full of success. -SSVPS College of Engineering, Dhule"
        "Staff" -> "Happy Birthday, $name!  Wishing you good health, happiness, and continued success. -SSVPS College of Engineering, Dhule"
        else -> return
    }

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "SMS sent to $phoneNumber: $message")
            showNotification(context, "SMS Sent", "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phoneNumber: ${e.message}")
            showNotification(context, "SMS Failed", "Failed to send SMS: ${e.message}")
        }
    } else {
        Log.e(TAG, "SEND_SMS permission not granted")
        showNotification(context, "Permission Error", "SEND_SMS permission not granted")
    }
}

fun sendPeerSMS(context: Context, phoneNumber: String, birthdayName: String, personType: String) {
    val TAG = "SmsUtils"
    val message = when (personType) {
        "Student" -> "Today is $birthdayName’s birthday! 🎉 Wish them well!"
        "Staff" -> "Today is $birthdayName’s birthday! 🎉 Send your wishes!"
        else -> return
    }

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "Peer SMS sent to $phoneNumber: $message")
            showNotification(context, "Peer SMS Sent", "Peer SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send peer SMS to $phoneNumber: ${e.message}")
            showNotification(context, "Peer SMS Failed", "Failed to send peer SMS: ${e.message}")
        }
    } else {
        Log.e(TAG, "SEND_SMS permission not granted")
        showNotification(context, "Permission Error", "SEND_SMS permission not granted")
    }
}

fun sendHodSMS(context: Context, phoneNumber: String, birthdayName: String) {
    val TAG = "SmsUtils"
    val message = "Today is $birthdayName’s birthday from your department team."

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "HOD SMS sent to $phoneNumber: $message")
            showNotification(context, "HOD SMS Sent", "HOD SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send HOD SMS to $phoneNumber: ${e.message}")
            showNotification(context, "HOD SMS Failed", "Failed to send HOD SMS: ${e.message}")
        }
    } else {
        Log.e(TAG, "SEND_SMS permission not granted")
        showNotification(context, "Permission Error", "SEND_SMS permission not granted")
    }
}

fun showNotification(context: Context, title: String, content: String) {
    val channelId = "birthday_notifications"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Birthday Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}