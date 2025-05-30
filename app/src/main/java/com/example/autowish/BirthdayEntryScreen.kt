package com.example.autowish

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var birthdayList by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }

    // Fetch all birthdays when the screen is first composed or when a new entry is saved
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            birthdayList = db.birthdayDao().getAll()
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        // Input form
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

                // Refresh the list after saving
                birthdayList = db.birthdayDao().getAll()

                // Schedule alarm for automatic SMS (1-minute test trigger)
                AlarmUtils.scheduleDailyAlarm(context)

                // Show notification for saved birthday
                showNotification(context, "Birthday Saved", "Birthday entry for $name saved! Automatic SMS scheduled.")

                // Clear input fields after saving
                name = ""
                phone = ""
                birthDate = ""
                message = "Happy Birthday!"
            }
        }) {
            Text("Save Birthday")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List of all birthday entries
        Text(
            text = "Saved Birthdays",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (birthdayList.isEmpty()) {
            Text("No birthdays saved yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp) // Limit height to avoid taking too much space
            ) {
                items(birthdayList) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Name: ${entry.name}", style = MaterialTheme.typography.bodyLarge)
                            Text("Phone: ${entry.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
                            Text("Birth Date: ${entry.birthDate}", style = MaterialTheme.typography.bodyMedium)
                            Text("Message: ${entry.message}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
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