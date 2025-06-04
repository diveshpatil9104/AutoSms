package com.example.autowish

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayEntryForm(
    onSave: (BirthdayEntry) -> Unit,
    onCancel: () -> Unit,
    initialEntry: BirthdayEntry? = null,
    database: BirthdayDatabase,
    coroutineScope: CoroutineScope,
    setErrorMessage: (String?) -> Unit
) {
    var name by remember { mutableStateOf(initialEntry?.name ?: "") }
    var phone by remember { mutableStateOf(initialEntry?.phoneNumber ?: "") }
    var birthDate by remember { mutableStateOf<LocalDate?>(parseInitialBirthDate(initialEntry?.birthDate)) }
    var personType by remember { mutableStateOf(initialEntry?.personType ?: "Student") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var birthDateError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val formatter = DateTimeFormatter.ofPattern("MM-dd")

    LaunchedEffect(name, phone, birthDate, personType) {
        setErrorMessage(null)
        errorMessage = null
    }

    LaunchedEffect(birthDate) {
        birthDateError = validateBirthDate(birthDate)
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                setErrorMessage(null)
                errorMessage = null
                onCancel()
            }
        },
        title = { Text(if (initialEntry == null) "Add Birthday" else "Edit Birthday") },
        text = {
            Column {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = if (it.isBlank()) "Name cannot be empty" else null
                    },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = {
                        nameError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        phoneError = if (!it.matches(Regex("\\d{10}"))) "Phone number must be 10 digits" else null
                    },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = phoneError != null,
                    supportingText = {
                        phoneError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = birthDate?.format(formatter) ?: "",
                    onValueChange = {},
                    label = { Text("Birth Date (MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                        }
                    },
                    isError = birthDateError != null,
                    supportingText = {
                        birthDateError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = personType,
                        onValueChange = {},
                        label = { Text("Person Type") },
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor() // 🔥 THIS IS THE FIX
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Student") },
                            onClick = {
                                personType = "Student"
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Staff") },
                            onClick = {
                                personType = "Staff"
                                expanded = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    nameError = if (name.isBlank()) "Name cannot be empty" else null
                    phoneError = if (!phone.matches(Regex("\\d{10}"))) "Phone number must be 10 digits" else null
                    birthDateError = validateBirthDate(birthDate)

                    if (personType !in listOf("Student", "Staff")) {
                        setErrorMessage("Select a valid person type")
                        errorMessage = "Select a valid person type"
                        return@TextButton
                    }

                    if (nameError != null || phoneError != null || birthDateError != null) return@TextButton

                    isLoading = true
                    coroutineScope.launch(Dispatchers.IO) {
                        if (initialEntry == null) {
                            val existing = database.birthdayDao().getByNameAndPhone(name, phone)
                            if (existing.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    setErrorMessage("Entry with this name and phone number already exists")
                                    errorMessage = "Entry with this name and phone number already exists"
                                    isLoading = false
                                }
                                return@launch
                            }
                        }

                        val entry = BirthdayEntry(
                            id = initialEntry?.id ?: 0,
                            name = name,
                            phoneNumber = phone,
                            birthDate = birthDate!!.format(formatter),
                            personType = personType
                        )
                        onSave(entry)
                        withContext(Dispatchers.Main) {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = birthDate?.atStartOfDay()?.toEpochSecond(java.time.ZoneOffset.UTC)?.times(1000)
                ?: LocalDate.of(2000, 1, 1).atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = LocalDate.ofEpochDay(millis / (1000 * 60 * 60 * 24))
                            birthDate = LocalDate.of(2000, selected.monthValue, selected.dayOfMonth)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// Parse MM-dd to LocalDate with dummy year
private fun parseInitialBirthDate(birthDate: String?): LocalDate? {
    return try {
        birthDate?.let {
            LocalDate.parse("2000-$it", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
    } catch (e: Exception) {
        null
    }
}

// Ensure valid month and day only
private fun validateBirthDate(birthDate: LocalDate?): String? {
    if (birthDate == null) return "Birth date is required"
    val month = birthDate.monthValue
    val day = birthDate.dayOfMonth
    if (month !in 1..12) return "Month must be 01–12"
    val maxDay = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        else -> 31
    }
    if (day !in 1..maxDay) return "Invalid day for month"
    return null
}