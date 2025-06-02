package com.example.autowish

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var birthDate by remember { mutableStateOf(initialEntry?.birthDate ?: "") }
    var personType by remember { mutableStateOf(initialEntry?.personType ?: "Student") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var birthDateError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) } // Local state for error message

    // Clear error message when fields change or dialog is dismissed
    LaunchedEffect(name, phone, birthDate, personType) {
        setErrorMessage(null)
        errorMessage = null
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
                // Display error message if it exists
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
                    supportingText = { if (nameError != null) Text(nameError!!, color = MaterialTheme.colorScheme.error) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        phoneError = if (!it.matches(Regex("\\d{10,25}"))) "Phone number must be 10-25 digits" else null
                    },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = phoneError != null,
                    supportingText = { if (phoneError != null) Text(phoneError!!, color = MaterialTheme.colorScheme.error) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = {
                        birthDate = it
                        birthDateError = if (!it.matches(Regex("\\d{2}-\\d{2}"))) "Birth date must be in MM-dd format" else null
                    },
                    label = { Text("Birth Date (MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = birthDateError != null,
                    supportingText = { if (birthDateError != null) Text(birthDateError!!, color = MaterialTheme.colorScheme.error) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = personType,
                        onValueChange = { },
                        label = { Text("Person Type") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true
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
                    phoneError = if (!phone.matches(Regex("\\d{10,25}"))) "Phone number must be 10-25 digits" else null
                    birthDateError = if (!birthDate.matches(Regex("\\d{2}-\\d{2}"))) "Birth date must be in MM-dd format" else null
                    if (personType !in listOf("Student", "Staff")) {
                        setErrorMessage("Select a valid person type")
                        errorMessage = "Select a valid person type"
                        return@TextButton
                    }
                    if (nameError != null || phoneError != null || birthDateError != null) {
                        return@TextButton
                    }
                    isLoading = true
                    setErrorMessage(null)
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        // Skip duplicate check for edits
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
                            birthDate = birthDate,
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
            TextButton(
                onClick = {
                    setErrorMessage(null)
                    errorMessage = null
                    onCancel()
                },
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}