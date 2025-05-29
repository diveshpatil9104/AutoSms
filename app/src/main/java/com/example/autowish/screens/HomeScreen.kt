package com.example.autowish.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.example.autowish.data.BirthdayEntry
import com.example.autowish.viewmodel.BirthdayViewModel
import com.example.autowish.worker.BirthdayWorker
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: BirthdayViewModel = viewModel(),
    onAddClicked: () -> Unit
) {
    val context = LocalContext.current
    val birthdayList by viewModel.birthdays.collectAsState(initial = emptyList())

    // 📤 File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromExcel(context, uri)
            Toast.makeText(context, "Import started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No file selected", Toast.LENGTH_SHORT).show()
        }
    }

    // 📱 SMS permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Toast.makeText(
            context,
            if (isGranted) "SMS Permission Granted" else "SMS Permission Denied",
            Toast.LENGTH_SHORT
        ).show()
    }

    // 🔁 Request SMS permission and schedule WorkManager once on first composition
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        // ⏰ Schedule WorkManager to run daily
        val workManager = WorkManager.getInstance(context)
        val workRequest = PeriodicWorkRequestBuilder<BirthdayWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS) // Optional: delay to avoid double-run on install day
            .addTag("BirthdayAutoSmsWorker")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "BirthdaySmsSender",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // 🖼 UI
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎉 AutoWish") })
        },
        floatingActionButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📤 Import CSV")
                }

                Button(
                    onClick = {
                        onAddClicked()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("➕ Add Birthday")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(birthdayList) { entry ->
                BirthdayItem(entry)
            }
        }
    }
}

@Composable
fun BirthdayItem(entry: BirthdayEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🎂 ${entry.name}", style = MaterialTheme.typography.titleMedium)
            Text("📅 ${entry.date}")
            Text("📱 ${entry.phone}")
        }
    }
}