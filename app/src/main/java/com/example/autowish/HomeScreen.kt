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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val db = BirthdayDatabase.getInstance(context)
    var todayBirthdays by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var upcomingBirthdays by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var hasData by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showCsvDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Date formatter for MM-dd
    val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Function to update birthdays
    suspend fun updateBirthdays() {
        try {
            val today = Calendar.getInstance()
            val currentYear = yearFormat.format(today.time).toInt()
            val todayMMdd = dateFormat.format(today.time)
            val sevenDaysLater = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 7) }
            val sevenDaysTime = sevenDaysLater.timeInMillis

            val allBirthdays = db.birthdayDao().getAll()
            hasData = allBirthdays.isNotEmpty()

            // Upcoming birthdays (next 7 days, excluding today)
            val upcomingList = allBirthdays
                .mapNotNull { entry ->
                    try {
                        // Parse birth date as MM-dd
                        val parts = entry.birthDate.split("-")
                        if (parts.size == 2) {
                            val month = parts[0].toIntOrNull()
                            val day = parts[1].toIntOrNull()
                            if (month != null && day != null) {
                                val nextBirthday = Calendar.getInstance().apply {
                                    set(Calendar.MONTH, month - 1)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.YEAR, currentYear)
                                    // If the birthday is today or has passed, use next year
                                    if (timeInMillis <= today.timeInMillis) {
                                        add(Calendar.YEAR, 1)
                                    }
                                }
                                Pair(entry, nextBirthday.timeInMillis)
                            } else null
                        } else null
                    } catch (e: Exception) {
                        null // Skip invalid dates
                    }
                }
                .filter { (_, birthTime) ->
                    // Include birthdays from tomorrow to 7 days later
                    birthTime > today.timeInMillis && birthTime <= sevenDaysTime
                }
                .sortedBy { it.second }
                .map { it.first }
                .take(7)

            withContext(Dispatchers.Main) {
                upcomingBirthdays = upcomingList
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                errorMessage = "Failed to load birthdays: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            updateBirthdays()
        }
    }

    // CSV import
    val importCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entries = parseCsv(context, uri)
                    db.birthdayDao().deleteAll()
                    entries.forEach { db.birthdayDao().insert(it) }
                    updateBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${entries.size} birthdays imported successfully!")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "CSV import failed: ${e.message}"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("CSV import failed: ${e.message}")
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }

    // CSV merge
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
                    updateBirthdays()
                    AlarmUtils.scheduleDailyAlarm(context)
                    withContext(Dispatchers.Main) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("$insertedCount birthdays merged successfully!")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "CSV merge failed: ${e.message}"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("CSV merge failed: ${e.message}")
                        }
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
            BottomNavigationBar(
                navController = navController,
                isHomeSelected = true
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            .fillMaxWidth()
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
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Today's Birthdays",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (todayBirthdays.isEmpty()) {
                                    "No birthdays today"
                                } else {
                                    "${todayBirthdays.size} birthday${if (todayBirthdays.size > 1) "s" else ""} today: ${todayBirthdays.joinToString { it.name }}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
                    items(upcomingBirthdays, key = { it.id }) { entry ->
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
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("Upload CSV") },
            text = { Text("Importing a new file will delete all existing entries. To keep existing entries and add new ones, select 'Merge CSV' instead.") },
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
                    updateBirthdays()
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