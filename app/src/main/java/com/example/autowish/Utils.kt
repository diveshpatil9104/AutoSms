package com.example.autowish

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BottomNavigationBar(
    navController: NavController,
    isHomeSelected: Boolean
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = isHomeSelected,
            onClick = { navController.navigate("home") }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(id = R.drawable.database), contentDescription = "Database") },
            label = { Text("Database") },
            selected = !isHomeSelected,
            onClick = { navController.navigate("Database") }
        )
    }
}
@Composable
fun BirthdayCard(
    entry: BirthdayEntry,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular image container with dynamic drawable icon
            Box(
                modifier = Modifier
                    .size(55.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        id = when {
                            entry.personType == "Staff" && entry.isHod -> R.drawable.hod
                            entry.personType == "Staff" -> R.drawable.staff
                            else -> R.drawable.student
                        }
                    ),
                    contentDescription = when {
                        entry.personType == "Staff" && entry.isHod -> "HOD"
                        entry.personType == "Staff" -> "Staff"
                        else -> "Student"
                    },
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Phone No.: ${entry.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "Birthday: ${entry.birthDate}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = buildString {
                        append("${entry.department}")
                        if (entry.personType == "Student" && !entry.year.isNullOrBlank()) {
                            append("    ${entry.year}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun parseCsv(context: Context, uri: Uri): List<BirthdayEntry> {
    val entries = mutableListOf<BirthdayEntry>()
    val validPersonTypes = listOf("Student", "Staff")
    val validYears = listOf("2nd", "3rd", "4th")
    val validDepartments = listOf(
        "Computer",
        "ENTC",
        "Civil",
        "Mechanical",
        "Electronics",
        "Electronics & Comp"
    )

    context.contentResolver?.openInputStream(uri)?.use { inputStream ->
        inputStream.bufferedReader().useLines { lines ->
            lines.drop(1).forEachIndexed { index, line ->
                try {
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size < 4) {
                        throw IllegalArgumentException("Row ${index + 2} has insufficient fields")
                    }

                    // Validate name
                    val name = parts[0]
                    if (!name.matches(Regex("^[A-Za-z\\s-']+$")) || name.isEmpty()) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid name '$name'")
                    }

                    // Validate phone number (exactly 10 digits)
                    val phoneNumber = parts[1]
                    if (!phoneNumber.matches(Regex("^\\d{10}$"))) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid phone number '$phoneNumber' (must be 10 digits)")
                    }

                    // Validate birth date (MM-dd)
                    val birthDate = parts[2]
                    if (!birthDate.matches(Regex("^\\d{2}-\\d{2}$"))) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid birth date format '$birthDate' (use MM-dd)")
                    }
                    val (month, day) = birthDate.split("-").map { it.toIntOrNull() }
                    if (month == null || day == null || month !in 1..12 || day !in 1..31 ||
                        (month in listOf(4, 6, 9, 11) && day > 30) ||
                        (month == 2 && day > 29)
                    ) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid birth date '$birthDate'")
                    }

                    // Validate person type
                    val personType = parts[3]
                    if (personType !in validPersonTypes) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid person type '$personType' (use Student or Staff)")
                    }

                    // Validate department
                    val department = parts.getOrElse(4) { "Computer" }
                    if (department !in validDepartments) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid department '$department' (use ${validDepartments.joinToString()})")
                    }

                    // Validate year
                    val year = parts.getOrElse(5) { "" }
                    if (personType == "Student" && year !in validYears) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid year '$year' for Student (use 2nd, 3rd, 4th)")
                    }
                    if (personType == "Staff" && year.isNotEmpty()) {
                        throw IllegalArgumentException("Row ${index + 2}: Year must be empty for Staff")
                    }

                    // Validate groupId (required, alphanumeric)
                    val groupId = parts.getOrElse(6) { "" }
                    if (groupId.isEmpty() || !groupId.matches(Regex("^[A-Za-z0-9]+$"))) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid group ID '$groupId' (use alphanumeric, cannot be empty)")
                    }

                    // Validate isHod
                    val isHod = parts.getOrElse(7) { "false" }
                    if (isHod !in listOf("true", "false")) {
                        throw IllegalArgumentException("Row ${index + 2}: Invalid isHod value '$isHod' (use true or false)")
                    }

                    val entry = BirthdayEntry(
                        id = 0,
                        name = name,
                        phoneNumber = phoneNumber,
                        birthDate = birthDate,
                        personType = personType,
                        department = department,
                        year = year.takeIf { it.isNotEmpty() && personType == "Student" },
                        groupId = groupId,
                        isHod = isHod.toBoolean()
                    )
                    entries.add(entry)
                } catch (e: Exception) {
                    throw IllegalArgumentException("CSV validation failed: ${e.message}")
                }
            }
        }
    } ?: throw IllegalArgumentException("Failed to open CSV file")
    return entries
}
