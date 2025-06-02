package com.example.autowish

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    val snackbarHostState = remember { SnackbarHostState() } // Added for Snackbar

    // Function to update upcoming birthdays
    suspend fun updateUpcomingBirthdays() {
        try {
            val allBirthdays = db.birthdayDao().getAll()
            hasData = allBirthdays.isNotEmpty()
            val today = Calendar.getInstance()
            val newUpcomingBirthdays = allBirthdays
                .mapNotNull { entry ->
                    val parts = entry.birthDate.split("-")
                    if (parts.size == 2) {
                        val month = parts[0].toIntOrNull()
                        val day = parts[1].toIntOrNull()
                        if (month != null && day != null) {
                            val nextBirthday = Calendar.getInstance().apply {
                                set(Calendar.MONTH, month - 1)
                                set(Calendar.DAY_OF_MONTH, day)
                                set(Calendar.YEAR, today.get(Calendar.YEAR))
                                if (before(today)) {
                                    add(Calendar.YEAR, 1)
                                }
                            }
                            entry to nextBirthday.timeInMillis
                        } else null
                    } else null
                }
                .sortedBy { it.second }
                .take(7)
                .map { it.first }
            withContext(Dispatchers.Main) {
                upcomingBirthdays = newUpcomingBirthdays
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                errorMessage = "Failed to load birthdays: ${e.message}"
            }
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
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${entries.size} birthdays imported!")
                        }
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
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("$insertedCount birthdays merged!")
                        }
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AutoWish",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 28.sp,
                            letterSpacing = 4.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }, // Added SnackbarHost
        containerColor = Color.Transparent
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Text(
                        text = "Upload CSV file or add data manually",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(bottom = 18.dp)
                            .fillMaxWidth(),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier
                                .size(110.dp)
                                .padding(end = 8.dp)
                        ) {
                            IconButton(
                                onClick = { showCsvDialog = true },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.upload),
                                    contentDescription = "Upload CSV",
                                    modifier = Modifier.size(50.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier
                                .size(110.dp)
                                .padding(start = 8.dp)
                        ) {
                            IconButton(
                                onClick = { showForm = true },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Entry",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }
                item {
                    Text(
                        text = "Upcoming Birthdays",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                    )
                }
                if (upcomingBirthdays.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No upcoming birthdays.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    items(upcomingBirthdays) { entry ->
                        BirthdayCard(entry)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                errorMessage?.let {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("Upload CSV") },
            text = { Text("Importing a new file will delete all existing entries. To keep existing entries and add new ones, select 'Merge CSV' instead.\"\n") },
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
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Birthday for ${entry.name} saved!")
                        }
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