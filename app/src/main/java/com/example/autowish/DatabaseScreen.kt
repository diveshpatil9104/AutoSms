package com.example.autowish

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(navController: NavController) {
    val context = LocalContext.current
    val db = BirthdayDatabase.getInstance(context)
    var birthdayList by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var selectedItems by remember { mutableStateOf(setOf<Int>()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf("Old to New") }
    var showForm by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<BirthdayEntry?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var hasData by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(searchQuery, filterType, sortOrder) {
        coroutineScope.launch(Dispatchers.IO) {
            var list = when (filterType) {
                null -> db.birthdayDao().getAll()
                else -> db.birthdayDao().getByPersonType(filterType!!)
            }
            if (searchQuery.isNotEmpty()) {
                list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            list = when (sortOrder) {
                "Old to New" -> db.birthdayDao().getAllSortedAsc()
                "New to Old" -> db.birthdayDao().getAllSortedDesc()
                else -> list
            }
            withContext(Dispatchers.Main) {
                birthdayList = list
                hasData = list.isNotEmpty()
            }
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
                    val newList = db.birthdayDao().getAll()
                    withContext(Dispatchers.Main) {
                        birthdayList = newList
                        hasData = newList.isNotEmpty()
                        showNotification(context, "CSV Imported", "${entries.size} birthdays imported!")
                    }
                    AlarmUtils.scheduleDailyAlarm(context)
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
                    val newList = db.birthdayDao().getAll()
                    withContext(Dispatchers.Main) {
                        birthdayList = newList
                        hasData = newList.isNotEmpty()
                        showNotification(context, "CSV Merged", "$insertedCount birthdays merged!")
                    }
                    AlarmUtils.scheduleDailyAlarm(context)
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
            if (selectedItems.isEmpty()) {
                TopAppBar(
                    title = { Text("Database") },
                    actions = {
                        IconButton(onClick = { showForm = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import New CSV") },
                                onClick = {
                                    showMenu = false
                                    importCsvPicker.launch("text/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Merge CSV") },
                                onClick = {
                                    showMenu = false
                                    mergeCsvPicker.launch("text/*")
                                },
                                enabled = true
                            )
                            DropdownMenuItem(
                                text = { Text("Export CSV") },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val entries = db.birthdayDao().getAll()
                                        if (entries.isEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                showNotification(context, "Export Failed", "No data to export")
                                            }
                                            return@launch
                                        }
                                        val csvContent = buildString {
                                            appendLine("name,phoneNumber,birthDate,personType")
                                            entries.forEach {
                                                appendLine("${it.name},${it.phoneNumber},${it.birthDate},${it.personType}")
                                            }
                                        }
                                        val file = File(context.getExternalFilesDir(null), "birthdays_export_${System.currentTimeMillis()}.csv")
                                        file.writeText(csvContent)
                                        withContext(Dispatchers.Main) {
                                            showNotification(context, "CSV Exported", "Saved to ${file.absolutePath}")
                                        }
                                    }
                                }
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("${selectedItems.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedItems = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                selectedItems.forEach { id ->
                                    db.birthdayDao().deleteById(id)
                                }
                                val newList = db.birthdayDao().getAll()
                                withContext(Dispatchers.Main) {
                                    birthdayList = newList
                                    hasData = newList.isNotEmpty()
                                    showNotification(context, "Entries Deleted", "${selectedItems.size} entries deleted")
                                    selectedItems = emptySet()
                                }
                                AlarmUtils.scheduleDailyAlarm(context)
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                        if (selectedItems.size == 1) {
                            IconButton(onClick = {
                                val selectedId = selectedItems.first()
                                Log.d("DatabaseScreen", "Edit clicked for ID: $selectedId")
                                coroutineScope.launch(Dispatchers.IO) {
                                    val entry = db.birthdayDao().getAll().find { it.id == selectedId }
                                    withContext(Dispatchers.Main) {
                                        editingEntry = entry
                                        if (entry != null) {
                                            showForm = true
                                            Log.d("DatabaseScreen", "Form opened for entry: ${entry.name}")
                                        } else {
                                            Log.e("DatabaseScreen", "No entry found for ID: $selectedId")
                                            errorMessage = "Entry not found"
                                        }
                                        selectedItems = emptySet()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = false,
                    onClick = { navController.navigate("home") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Database") },
                    label = { Text("Database") },
                    selected = true,
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
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search by Name") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilterChip(
                        selected = filterType == "Student",
                        onClick = { filterType = if (filterType == "Student") null else "Student" },
                        label = { Text("Students") }
                    )
                    FilterChip(
                        selected = filterType == "Staff",
                        onClick = { filterType = if (filterType == "Staff") null else "Staff" },
                        label = { Text("Staff") }
                    )

                    // Sort By Chip with Dropdown Menu
                    var sortMenuExpanded by remember { mutableStateOf(false) }

                    FilterChip(
                        selected = false,
                        onClick = { sortMenuExpanded = true },
                        label = { Text("Sort by: $sortOrder") },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Sort Options")
                        }
                    )

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Old to New") },
                            onClick = {
                                sortOrder = "Old to New"
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New to Old") },
                            onClick = {
                                sortOrder = "New to Old"
                                sortMenuExpanded = false
                            }
                        )
                    }
                }

                if (birthdayList.isEmpty()) {
                    Text(
                        text = if (searchQuery.isEmpty() && filterType == null) "No birthdays saved." else "No matching birthdays.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(birthdayList) { entry ->
                            BirthdayCard(
                                entry = entry,
                                isSelected = entry.id in selectedItems,
                                onLongPress = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedItems = selectedItems + entry.id
                                    Log.d("DatabaseScreen", "Long press on entry ID: ${entry.id}")
                                },
                                onClick = {
                                    if (selectedItems.isNotEmpty()) {
                                        selectedItems = if (entry.id in selectedItems) {
                                            selectedItems - entry.id
                                        } else {
                                            selectedItems + entry.id
                                        }
                                        Log.d("DatabaseScreen", "Click toggled selection for ID: ${entry.id}, selectedItems: $selectedItems")
                                    }
                                }
                            )
                        }
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

    if (showForm) {
        BirthdayEntryForm(
            onSave = { entry ->
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                    db.birthdayDao().insert(entry)
                    val newList = db.birthdayDao().getAll()
                    withContext(Dispatchers.Main) {
                        birthdayList = newList
                        hasData = newList.isNotEmpty()
                        showNotification(context, "Birthday Saved", "Birthday for ${entry.name} ${if (editingEntry != null) "updated" else "saved"}!")
                        showForm = false
                        editingEntry = null
                        isLoading = false
                    }
                    AlarmUtils.scheduleDailyAlarm(context)
                }
            },
            onCancel = {
                showForm = false
                editingEntry = null
            },
            initialEntry = editingEntry,
            database = db,
            coroutineScope = coroutineScope,
            setErrorMessage = { errorMessage = it }
        )
    }
}