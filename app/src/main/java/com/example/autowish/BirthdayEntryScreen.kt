//package com.example.autowish
//
//import android.content.Context
//import android.net.Uri
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.animation.fadeIn
//import androidx.compose.animation.fadeOut
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun BirthdayEntryScreen() {
//    val context = LocalContext.current
//    val db = BirthdayDatabase.getInstance(context)
//    var name by remember { mutableStateOf("") }
//    var phone by remember { mutableStateOf("") }
//    var birthDate by remember { mutableStateOf("") }
//    var personType by remember { mutableStateOf("Student") }
//    var birthdayList by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
//    var errorMessage by remember { mutableStateOf<String?>(null) }
//    var isLoading by remember { mutableStateOf(false) }
//
//    LaunchedEffect(Unit) {
//        CoroutineScope(Dispatchers.IO).launch {
//            birthdayList = db.birthdayDao().getAll()
//        }
//    }
//
//    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
//        if (uri != null) {
//            isLoading = true
//            errorMessage = null
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    val entries = parseCsv(context, uri)
//                    entries.forEach { db.birthdayDao().insert(it) }
//                    birthdayList = db.birthdayDao().getAll()
//                    AlarmUtils.scheduleDailyAlarm(context)
//                    withContext(Dispatchers.Main) {
//                        showNotification(context, "CSV Imported", "${entries.size} birthdays imported!")
//                    }
//                } catch (e: Exception) {
//                    withContext(Dispatchers.Main) {
//                        errorMessage = "Failed to import CSV: ${e.message}"
//                    }
//                } finally {
//                    isLoading = false
//                }
//            }
//        }
//    }
//
//    Surface(
//        modifier = Modifier.fillMaxSize(),
//        color = MaterialTheme.colorScheme.background
//    ) {
//        Column(
//            modifier = Modifier
//                .padding(16.dp)
//                .fillMaxWidth(),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text(
//                text = "Birthday Wishes",
//                style = MaterialTheme.typography.headlineMedium.copy(
//                    fontWeight = FontWeight.Bold,
//                    fontSize = 28.sp
//                ),
//                color = MaterialTheme.colorScheme.primary,
//                modifier = Modifier.padding(bottom = 16.dp)
//            )
//
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(bottom = 16.dp),
//                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
//                shape = RoundedCornerShape(12.dp)
//            ) {
//                Column(
//                    modifier = Modifier
//                        .padding(16.dp)
//                        .fillMaxWidth()
//                ) {
//                    OutlinedTextField(
//                        value = name,
//                        onValueChange = { name = it },
//                        label = { Text("Name") },
//                        modifier = Modifier.fillMaxWidth(),
//                        singleLine = true
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))
//                    OutlinedTextField(
//                        value = phone,
//                        onValueChange = { phone = it },
//                        label = { Text("Phone Number") },
//                        modifier = Modifier.fillMaxWidth(),
//                        singleLine = true
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))
//                    OutlinedTextField(
//                        value = birthDate,
//                        onValueChange = { birthDate = it },
//                        label = { Text("Birth Date (MM-dd)") },
//                        modifier = Modifier.fillMaxWidth(),
//                        singleLine = true
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))
//                    var expanded by remember { mutableStateOf(false) }
//                    ExposedDropdownMenuBox(
//                        expanded = expanded,
//                        onExpandedChange = { expanded = it }
//                    ) {
//                        OutlinedTextField(
//                            value = personType,
//                            onValueChange = { },
//                            label = { Text("Person Type") },
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .menuAnchor(),
//                            readOnly = true
//                        )
//                        ExposedDropdownMenu(
//                            expanded = expanded,
//                            onDismissRequest = { expanded = false }
//                        ) {
//                            DropdownMenuItem(
//                                text = { Text("Student") },
//                                onClick = {
//                                    personType = "Student"
//                                    expanded = false
//                                }
//                            )
//                            DropdownMenuItem(
//                                text = { Text("Staff") },
//                                onClick = {
//                                    personType = "Staff"
//                                    expanded = false
//                                }
//                            )
//                        }
//                    }
//                    Spacer(modifier = Modifier.height(12.dp))
//                    Button(
//                        onClick = {
//                            if (name.isBlank() || phone.isBlank() || birthDate.isBlank()) {
//                                errorMessage = "Please fill in all fields"
//                                return@Button
//                            }
//                            if (!birthDate.matches(Regex("\\d{2}-\\d{2}"))) {
//                                errorMessage = "Birth date must be in MM-dd format"
//                                return@Button
//                            }
//                            if (!phone.matches(Regex("\\d{10,15}"))) {
//                                errorMessage = "Phone number must have 10-15 digits"
//                                return@Button
//                            }
//                            if (personType !in listOf("Student", "Staff")) {
//                                errorMessage = "Select a valid person type"
//                                return@Button
//                            }
//                            isLoading = true
//                            errorMessage = null
//                            CoroutineScope(Dispatchers.IO).launch {
//                                val entry = BirthdayEntry(0, name, phone, birthDate, personType)
//                                db.birthdayDao().insert(entry)
//                                birthdayList = db.birthdayDao().getAll()
//                                AlarmUtils.scheduleDailyAlarm(context)
//                                withContext(Dispatchers.Main) {
//                                    showNotification(context, "Birthday Saved", "Birthday for $name saved!")
//                                    name = ""
//                                    phone = ""
//                                    birthDate = ""
//                                    personType = "Student"
//                                    isLoading = false
//                                }
//                            }
//                        },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .height(48.dp),
//                        shape = RoundedCornerShape(8.dp)
//                    ) {
//                        if (isLoading) {
//                            CircularProgressIndicator(
//                                modifier = Modifier.size(24.dp),
//                                color = MaterialTheme.colorScheme.onPrimary
//                            )
//                        } else {
//                            Text("Save Birthday")
//                        }
//                    }
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Button(
//                        onClick = { csvPicker.launch("text/*") },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .height(48.dp),
//                        shape = RoundedCornerShape(8.dp),
//                        colors = ButtonDefaults.buttonColors(
//                            containerColor = MaterialTheme.colorScheme.secondary
//                        )
//                    ) {
//                        Text("Upload CSV")
//                    }
//                }
//            }
//
//            AnimatedVisibility(
//                visible = errorMessage != null,
//                enter = fadeIn(),
//                exit = fadeOut()
//            ) {
//                errorMessage?.let {
//                    Text(
//                        text = it,
//                        color = MaterialTheme.colorScheme.error,
//                        style = MaterialTheme.typography.bodyMedium,
//                        modifier = Modifier.padding(bottom = 8.dp)
//                    )
//                }
//            }
//
//            Text(
//                text = "Saved Birthdays",
//                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
//                modifier = Modifier.padding(vertical = 8.dp)
//            )
//            if (birthdayList.isEmpty()) {
//                Text(
//                    text = "No birthdays saved yet.",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
//                )
//            } else {
//                LazyColumn(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .weight(1f)
//                ) {
//                    items(birthdayList) { entry ->
//                        BirthdayCard(entry)
//                    }
//                }
//            }
//        }
//    }
//}