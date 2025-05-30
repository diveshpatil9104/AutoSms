package com.example.autowish

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

@Composable
fun BirthdayEntryScreen() {
    val context = LocalContext.current
    val db = BirthdayDatabase.getInstance(context)

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") } // Format: MM-dd
    var message by remember { mutableStateOf("Happy Birthday!") }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
        OutlinedTextField(value = birthDate, onValueChange = { birthDate = it }, label = { Text("Birth Date (MM-dd)") })
        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") })

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            if (name.isBlank() || phone.isBlank() || birthDate.isBlank() || message.isBlank()) {
                showNotification(context, "Input Error", "Please fill in all fields")
                return@Button
            }
            CoroutineScope(Dispatchers.IO).launch {
                val entry = BirthdayEntry(0, name, phone, birthDate, message)
                db.birthdayDao().insert(entry)

                // Schedule alarm for automatic SMS (1-minute test trigger)
                AlarmUtils.scheduleDailyAlarm(context)

                // Show notification for saved birthday
                showNotification(context, "Birthday Saved", "Birthday entry for $name saved! Automatic SMS scheduled.")
            }
        }) {
            Text("Save Birthday")
        }

        // Manual Send SMS Button (commented out to focus on automatic sending)
        /*
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (phone.isNotBlank() && message.isNotBlank()) {
                sendSMS(context, phone, message)
            } else {
                showNotification(context, "Input Error", "Please enter a valid phone and message")
            }
        }) {
            Text("Send SMS Now")
        }
        */
    }
}

private fun showNotification(context: Context, title: String, content: String) {
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

    if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}