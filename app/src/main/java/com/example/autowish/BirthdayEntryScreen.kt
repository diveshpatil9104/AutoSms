package com.example.autowish

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun BirthdayEntryScreen() {
    val context = LocalContext.current
    val db = BirthdayDatabase.getInstance(context)
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") } // Format: MM-dd
    var message by remember { mutableStateOf("Happy Birthday!") }
    var birthdayList by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Fetch birthdays
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            birthdayList = db.birthdayDao().getAll()
        }
    }

    // CSV file picker
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entries = parseCsv(context, uri)
                    entries.forEach { db.birthdayDao().insert(it) }
                    birthdayList = db.birthdayDao().getAll()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        showNotification(context, "CSV Imported", "${entries.size} birthdays imported successfully!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to import CSV: ${e.message}"
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Birthday Wishes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number (e.g., +91xxxxxxxxxx)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("Birth Date (MM-dd)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || phone.isBlank() || birthDate.isBlank() || message.isBlank()) {
                                errorMessage = "Please fill in all fields"
                                return@Button
                            }
                            if (!birthDate.matches(Regex("\\d{2}-\\d{2}"))) {
                                errorMessage = "Birth date must be in MM-dd format"
                                return@Button
                            }
                            if (!phone.matches(Regex("\\+\\d{10,15}"))) {
                                errorMessage = "Phone number must start with + and have 10-15 digits"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            CoroutineScope(Dispatchers.IO).launch {
                                val entry = BirthdayEntry(0, name, phone, birthDate, message)
                                db.birthdayDao().insert(entry)
                                birthdayList = db.birthdayDao().getAll()
                                AlarmUtils.scheduleDailyAlarm(context)
                                withContext(Dispatchers.Main) {
                                    showNotification(context, "Birthday Saved", "Birthday for $name saved!")
                                    name = ""
                                    phone = ""
                                    birthDate = ""
                                    message = "Happy Birthday!"
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save Birthday")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { csvPicker.launch("text/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Upload CSV")
                    }
                }
            }

            // Error Message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Birthday List
            Text(
                text = "Saved Birthdays",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (birthdayList.isEmpty()) {
                Text(
                    text = "No birthdays saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(birthdayList) { entry ->
                        BirthdayCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
fun BirthdayCard(entry: BirthdayEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Phone: ${entry.phoneNumber}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Birth Date: ${entry.birthDate}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Message: ${entry.message}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun parseCsv(context: Context, uri: Uri): List<BirthdayEntry> {
    val entries = mutableListOf<BirthdayEntry>()
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val header = reader.readLine() // Skip header
            if (header != "name,phoneNumber,birthDate,message") {
                throw IllegalArgumentException("Invalid CSV format. Expected headers: name,phoneNumber,birthDate,message")
            }
            reader.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size == 4) {
                    val name = parts[0].trim()
                    val phoneNumber = parts[1].trim()
                    val birthDate = parts[2].trim()
                    val message = parts[3].trim()
                    if (name.isNotBlank() &&
                        phoneNumber.matches(Regex("\\+\\d{10,15}")) &&
                        birthDate.matches(Regex("\\d{2}-\\d{2}")) &&
                        message.isNotBlank()
                    ) {
                        entries.add(BirthdayEntry(0, name, phoneNumber, birthDate, message))
                    } else {
                        throw IllegalArgumentException("Invalid data in CSV: $line")
                    }
                }
            }
        }
    }
    return entries
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