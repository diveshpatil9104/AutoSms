package com.example.autowish

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val db = BirthdayDatabase.getInstance(context)
    var upcomingBirthdays by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var hasData by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showCsvDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Function to update upcoming birthdays
    suspend fun updateUpcomingBirthdays() {
        val allBirthdays = db.birthdayDao().getAll()
        hasData = allBirthdays.isNotEmpty()
        val today = Calendar.getInstance()
        val newUpcomingBirthdays = allBirthdays
            .map { entry ->
                val parts = entry.birthDate.split("-")
                entry to Calendar.getInstance().apply {
                    set(Calendar.MONTH, parts[0].toInt() - 1)
                    set(Calendar.DAY_OF_MONTH, parts[1].toInt())
                    set(Calendar.YEAR, today.get(Calendar.YEAR))
                    if (timeInMillis < today.timeInMillis) {
                        add(Calendar.YEAR, 1)
                    }
                }
            }
            .filter { it.second.timeInMillis >= today.timeInMillis }
            .sortedBy { it.second.timeInMillis }
            .take(7)
            .map { it.first }
        withContext(Dispatchers.Main) {
            upcomingBirthdays = newUpcomingBirthdays
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            updateUpcomingBirthdays()
        }
    }

    val importCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    db.birthdayDao().deleteAll()
                    val entries = parseCsv(context, uri)
                    entries.forEach { db.birthdayDao().insert(it) }
                    updateUpcomingBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        showNotification(context, "CSV Imported", "${entries.size} birthdays imported!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to import CSV: ${e.message}"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    val mergeCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entries = parseCsv(context, uri)
                    var insertedCount = 0
                    entries.forEach { entry ->
                        val existing = db.birthdayDao().getByNameAndPhone(entry.name, entry.phoneNumber)
                        if (existing.isEmpty()) {
                            db.birthdayDao().insert(entry)
                            insertedCount++
                        }
                    }
                    updateUpcomingBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        showNotification(context, "CSV Merged", "$insertedCount birthdays merged!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to merge CSV: ${e.message}"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = { navController.navigate("home") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Database") },
                    label = { Text("Database") },
                    selected = false,
                    onClick = { navController.navigate("database") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Upcoming Birthdays",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (upcomingBirthdays.isEmpty()) {
                    Text(
                        text = "No upcoming birthdays.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(upcomingBirthdays) { entry ->
                            BirthdayCard(entry)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { showCsvDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Upload CSV", fontSize = 16.sp)
                    }
                    Button(
                        onClick = { showForm = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .padding(8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Add Entry", fontSize = 16.sp)
                    }
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("Upload CSV") },
            text = { Text("Choose an option for uploading the CSV file.") },
            confirmButton = {
                TextButton(onClick = {
                    showCsvDialog = false
                    importCsvPicker.launch("text/*")
                }) {
                    Text("New CSV File")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCsvDialog = false
                        mergeCsvPicker.launch("text/*")
                    },
                    enabled = hasData
                ) {
                    Text("Merge CSV File")
                }
            }
        )
    }

    if (showForm) {
        BirthdayEntryForm(
            onSave = { entry ->
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                    db.birthdayDao().insert(entry)
                    updateUpcomingBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        showNotification(context, "Birthday Saved", "Birthday for ${entry.name} saved!")
                        showForm = false
                        isLoading = false
                    }
                }
            },
            onCancel = { showForm = false },
            initialEntry = null,
            database = db,
            coroutineScope = coroutineScope,
            setErrorMessage = { errorMessage = it }
        )
    }
}