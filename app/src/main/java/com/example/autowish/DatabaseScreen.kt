package com.example.autowish

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.autowish.SmsUtils.showNotification
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
    var isSelectionMode by remember { mutableStateOf(false) }
    var birthdayList by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var selectedItems by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("Student") }
    var department by remember { mutableStateOf("Computer") }
    var year by remember { mutableStateOf("2nd") }
    var sortOrder by remember { mutableStateOf("Old First") }
    var showForm by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<BirthdayEntry?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var hasData by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteFilteredDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val departments = listOf(
        "Computer", "ENTC", "Civil", "Mechanical", "Electronics", "Electronics & Comp"
    )
    val years = listOf("2nd", "3rd", "4th", "All")

    // Load birthdays
    LaunchedEffect(searchQuery, filterType, department, year, sortOrder) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val effectiveYear = if (filterType == "Student" && year != "All") year else null
                val list = db.birthdayDao().getByDepartmentAndYear(department, effectiveYear, if (filterType == "All") null else filterType)
                Log.d("DatabaseScreen", "Filter: $filterType, Dept: $department, Year: $effectiveYear, Initial size: ${list.size}")

                val filteredList = if (searchQuery.isNotEmpty()) {
                    list.filter { it.name.contains(searchQuery, ignoreCase = true) }
                } else {
                    list
                }
                Log.d("DatabaseScreen", "Search '$searchQuery', Size: ${filteredList.size}")

                val sortedList = when (sortOrder) {
                    "Old First" -> filteredList.sortedBy { it.birthDate }
                    "New First" -> filteredList.sortedByDescending { it.birthDate }
                    else -> filteredList
                }
                Log.d("DatabaseScreen", "Sort '$sortOrder', Size: ${sortedList.size}")

                withContext(Dispatchers.Main) {
                    birthdayList = sortedList
                    hasData = sortedList.isNotEmpty()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load data: ${e.message}"
                    Log.e("DatabaseScreen", "Load error", e)
                }
            }
        }
    }

    // CSV import
    val importCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isLoading = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entries = parseCsv(context, uri)
                    db.birthdayDao().deleteAll()
                    entries.forEach { entry -> db.birthdayDao().insert(entry) }
                    val newList = db.birthdayDao().getByDepartmentAndYear(department, if (filterType == "Student" && year != "All") year else null, if (filterType == "All") null else filterType)
                    withContext(Dispatchers.Main) {
                        birthdayList = newList
                        hasData = newList.isNotEmpty()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${entries.size} birthdays imported successfully")
                        }
                    }
                    AlarmUtils.scheduleDailyAlarm(context)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "CSV import failed: ${e.message}"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("CSV import failed: ${e.message}")
                        }
                        Log.e("DatabaseScreen", "Import error", e)
                    }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    // CSV merge
    val mergeCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
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
                    val newList = db.birthdayDao().getByDepartmentAndYear(department, if (filterType == "Student" && year != "All") year else null, if (filterType == "All") null else filterType)
                    withContext(Dispatchers.Main) {
                        birthdayList = newList
                        hasData = newList.isNotEmpty()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("$insertedCount birthdays merged successfully")
                        }
                    }
                    AlarmUtils.scheduleDailyAlarm(context)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "CSV merge failed: ${e.message}"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("CSV merge failed: ${e.message}")
                        }
                        Log.e("DatabaseScreen", "Merge error", e)
                    }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectedItems.isEmpty()) {
                TopAppBar(
                    title = {
                        Text(
                            "Birthdays",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 28.sp,
                                letterSpacing = 4.sp
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = { showForm = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Entry")
                        }
                        IconButton(
                            onClick = { showDeleteFilteredDialog = true },
                            enabled = hasData && (filterType != "All" || department != departments.first() || year != "All")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Filtered")
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
                                    showImportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Merge CSV") },
                                onClick = {
                                    showMenu = false
                                    mergeCsvPicker.launch("text/*")
                                },
                                enabled = hasData
                            )
                            DropdownMenuItem(
                                text = { Text("Export CSV") },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val entries = db.birthdayDao().getAll()
                                            if (entries.isEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    showNotification(context, "Export Failed", "No data to export")
                                                }
                                                return@launch
                                            }
                                            val csvContent = buildString {
                                                appendLine("name,phoneNumber,birthDate,personType,department,year,groupId,isHod")
                                                entries.forEach { entry ->
                                                    appendLine("${entry.name},${entry.phoneNumber},${entry.birthDate},${entry.personType},${entry.department},${entry.year ?: ""},${entry.groupId},${entry.isHod}")
                                                }
                                            }
                                            val file = File(context.getExternalFilesDir(null), "birthdays_export_${System.currentTimeMillis()}.csv")
                                            file.writeText(csvContent)
                                            withContext(Dispatchers.Main) {
                                                showNotification(context, "CSV Exported", "Saved to ${file.absolutePath}")
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                errorMessage = "Failed to export CSV: ${e.message}"
                                                Log.e("DatabaseScreen", "Export error", e)
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete All") },
                                onClick = {
                                    showMenu = false
                                    showDeleteAllDialog = true
                                },
                                enabled = hasData
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.background(Color.Transparent)
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "${selectedItems.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 24.sp,
                                letterSpacing = 3.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedItems = emptySet()
                            isSelectionMode = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val count = selectedItems.size
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    selectedItems.forEach { id -> db.birthdayDao().deleteById(id) }
                                    val newList = db.birthdayDao().getByDepartmentAndYear(department, if (filterType == "Student" && year != "All") year else null, if (filterType == "All") null else filterType)
                                    withContext(Dispatchers.Main) {
                                        birthdayList = newList
                                        hasData = newList.isNotEmpty()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("$count entries deleted")
                                        }
                                        selectedItems = emptySet()
                                        isSelectionMode = false
                                    }
                                    AlarmUtils.scheduleDailyAlarm(context)
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = "Failed to delete entries: ${e.message}"
                                        Log.e("DatabaseScreen", "Delete error", e)
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                        if (selectedItems.size == 1) {
                            IconButton(onClick = {
                                val selectedId = selectedItems.first()
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val entry = db.birthdayDao().getAll().find { it.id == selectedId }
                                        withContext(Dispatchers.Main) {
                                            editingEntry = entry
                                            if (entry != null) {
                                                showForm = true
                                                Log.d("DatabaseScreen", "Editing entry: ${entry.name}")
                                            } else {
                                                errorMessage = "Entry not found"
                                                Log.e("DatabaseScreen", "No entry for ID: $selectedId")
                                            }
                                            selectedItems = emptySet()
                                            isSelectionMode = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            errorMessage = "Failed to load entry: ${e.message}"
                                            Log.e("DatabaseScreen", "Edit load error", e)
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Selected")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.background(Color.Transparent)
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                isHomeSelected = false
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        EnhancedSearchBarSection(
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                            filterType = filterType,
                            onFilterTypeChange = { filterType = it },
                            department = department,
                            onDepartmentChange = { department = it },
                            year = year,
                            onYearChange = { year = it },
                            sortOrder = sortOrder,
                            onSortOrderChange = { sortOrder = it }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (birthdayList.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = when {
                                        searchQuery.isNotEmpty() -> "No matching birthdays."
                                        filterType == "Student" -> "No students in $department${if (year != "All") " ($year)" else ""}."
                                        filterType == "Staff" -> "No staff in $department."
                                        else -> "No birthdays in $department${if (year != "All") " ($year)" else ""}."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(birthdayList, key = { it.id }) { entry ->
                            BirthdayListItem(
                                entry = entry,
                                isSelected = entry.id in selectedItems,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedItems = if (entry.id in selectedItems) {
                                            selectedItems - entry.id
                                        } else {
                                            selectedItems + entry.id
                                        }
                                        if (selectedItems.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                        Log.d("DatabaseScreen", "Click on ${entry.name}, Selected: $selectedItems")
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItems = selectedItems + entry.id
                                        Log.d("DatabaseScreen", "Long press on ${entry.name}, Selection mode: $isSelectionMode")
                                    }
                                }
                            )
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
    }

    // Confirmation dialog for Delete All
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Entries") },
            text = { Text("Are you sure you want to delete all entries? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        isLoading = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                db.birthdayDao().deleteAll()
                                val newList = db.birthdayDao().getByDepartmentAndYear(department, if (filterType == "Student" && year != "All") year else null, if (filterType == "All") null else filterType)
                                withContext(Dispatchers.Main) {
                                    birthdayList = newList
                                    hasData = newList.isNotEmpty()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("All birthdays have been deleted")
                                    }
                                    isSelectionMode = false
                                    selectedItems = emptySet()
                                }
                                AlarmUtils.scheduleDailyAlarm(context)
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Failed to delete all entries: ${e.message}"
                                    Log.e("DatabaseScreen", "Delete all error", e)
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                }
                            }
                        }
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for Delete Filtered
    if (showDeleteFilteredDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFilteredDialog = false },
            title = { Text("Delete Filtered Entries") },
            text = {
                Text(
                    "Are you sure you want to delete all ${filterType.lowercase()} in $department${if (filterType == "Student" && year != "All") " ($year)" else ""}? This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteFilteredDialog = false
                        isLoading = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                db.birthdayDao().deleteByDepartmentAndYear(
                                    department,
                                    if (filterType == "Student" && year != "All") year else null,
                                    if (filterType == "All") null else filterType
                                )
                                val newList = db.birthdayDao().getByDepartmentAndYear(department, if (filterType == "Student" && year != "All") year else null, if (filterType == "All") null else filterType)
                                withContext(Dispatchers.Main) {
                                    birthdayList = newList
                                    hasData = newList.isNotEmpty()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Filtered entries deleted")
                                    }
                                    isSelectionMode = false
                                    selectedItems = emptySet()
                                }
                                AlarmUtils.scheduleDailyAlarm(context)
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Failed to delete filtered entries: ${e.message}"
                                    Log.e("DatabaseScreen", "Delete filtered error", e)
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                }
                            }
                        }
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFilteredDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import New CSV") },
            text = { Text("Importing a new file will delete all existing entries. To keep existing entries and add new ones, select 'Merge CSV' instead.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importCsvPicker.launch("text/*")
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showForm) {
        BirthdayEntryForm(
            onSave = { entry ->
                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        db.birthdayDao().insert(entry)
                        val newList = db.birthdayDao().getByDepartmentAndYear(department, if (filterType == "Student" && year != "All") year else null, if (filterType == "All") null else filterType)
                        withContext(Dispatchers.Main) {
                            birthdayList = newList
                            hasData = newList.isNotEmpty()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Birthday for ${entry.name} ${if (editingEntry != null) "updated" else "saved"}"
                                )
                            }
                            showForm = false
                            editingEntry = null
                            selectedItems = emptySet()
                            isSelectionMode = false
                        }
                        AlarmUtils.scheduleDailyAlarm(context)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Failed to save birthday: ${e.message}"
                            Log.e("DatabaseScreen", "Save error", e)
                        }
                    } finally {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            },
            onCancel = {
                showForm = false
                editingEntry = null
                selectedItems = emptySet()
                isSelectionMode = false
            },
            initialEntry = editingEntry,
            database = db,
            coroutineScope = coroutineScope,
            setErrorMessage = { errorMessage = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BirthdayListItem(
    entry: BirthdayEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = "Phone No.: ${entry.phoneNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Birthday: ${entry.birthDate}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Type: ${entry.personType}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Department: ${entry.department}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (entry.personType == "Student") {
                        Text(
                            text = "Year: ${entry.year}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "Group ID: ${entry.groupId}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (entry.personType == "Staff" && entry.isHod) {
                        Text(
                            text = "HOD: Yes",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            leadingContent = if (isSelected) {
                {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else null,
            colors = ListItemDefaults.colors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                headlineColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primary,
                supportingColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        if (isSelectionMode) onClick()
                    },
                    onLongClick = {
                        if (!isSelectionMode) onLongPress()
                    }
                )
        )
        Divider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EnhancedSearchBarSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    filterType: String,
    onFilterTypeChange: (String) -> Unit,
    department: String,
    onDepartmentChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    sortOrder: String,
    onSortOrderChange: (String) -> Unit
) {
    val departments = listOf(
        "Computer", "ENTC", "Civil", "Mechanical", "Electronics", "Electronics & Comp"
    )

    val years = listOf("All", "2nd", "3rd", "4th")

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var departmentExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Search by Name") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            FilterChip(
                selected = filterType == "All",
                onClick = { onFilterTypeChange("All") },
                label = { Text("All") }
            )
            FilterChip(
                selected = filterType == "Student",
                onClick = { onFilterTypeChange("Student") },
                label = { Text("Students") }
            )
            FilterChip(
                selected = filterType == "Staff",
                onClick = { onFilterTypeChange("Staff") },
                label = { Text("Staff") }
            )
            Box {
                FilterChip(
                    selected = sortMenuExpanded,
                    onClick = { sortMenuExpanded = true },
                    label = { Text("Sort: $sortOrder") },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Sort Options")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Old First") },
                        onClick = {
                            onSortOrderChange("Old First")
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New First") },
                        onClick = {
                            onSortOrderChange("New First")
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = departmentExpanded,
                onExpandedChange = { departmentExpanded = !departmentExpanded },
                modifier = Modifier
                    .width(200.dp) // Fixed width for smaller box
            ) {
                OutlinedTextField(
                    value = department,
                    onValueChange = {},
                    label = { Text("Department", style = MaterialTheme.typography.bodySmall) }, // Smaller font
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .width(200.dp) // Match box width
                        .padding(horizontal = 4.dp), // Compact padding
                    textStyle = MaterialTheme.typography.bodySmall, // Smaller text
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = departmentExpanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = departmentExpanded,
                    onDismissRequest = { departmentExpanded = false },
                    modifier = Modifier
                        .width(200.dp) // Match text field width
                        .heightIn(max = 200.dp) // Limit dropdown height
                ) {
                    departments.forEach { dept ->
                        DropdownMenuItem(
                            text = { Text(dept, style = MaterialTheme.typography.bodySmall) }, // Smaller text
                            onClick = {
                                onDepartmentChange(dept)
                                departmentExpanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp) // Compact padding
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = yearExpanded,
                onExpandedChange = { yearExpanded = !yearExpanded },
                modifier = Modifier
                    .width(200.dp) // Fixed width for smaller box
            ) {
                OutlinedTextField(
                    value = year,
                    onValueChange = {},
                    label = { Text("Year", style = MaterialTheme.typography.bodySmall) }, // Smaller font
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .width(100.dp) // Match box width
                        .padding(horizontal = 4.dp), // Compact padding
                    textStyle = MaterialTheme.typography.bodySmall, // Smaller text
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded)
                    },
                    enabled = filterType == "Student"
                )
                ExposedDropdownMenu(
                    expanded = yearExpanded,
                    onDismissRequest = { yearExpanded = false },
                    modifier = Modifier
                        .width(200.dp) // Match text field width
                        .heightIn(max = 200.dp) // Limit dropdown height
                ) {
                    years.forEach { yr ->
                        DropdownMenuItem(
                            text = { Text(yr, style = MaterialTheme.typography.bodySmall) }, // Smaller text
                            onClick = {
                                onYearChange(yr)
                                yearExpanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp) // Compact padding
                        )
                    }
                }
            }
        }
    }
}