package com.example.autowish

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.example.autowish.ui.theme.AutoWishTheme

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.forEach { (permission, granted) ->
            Log.d(TAG, "Permission $permission: ${if (granted) "granted" else "denied"}")
        }
        val allCriticalGranted = permissions[Manifest.permission.SEND_SMS] == true &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || permissions[Manifest.permission.SCHEDULE_EXACT_ALARM] == true)
        if (allCriticalGranted) {
            Log.d(TAG, "All critical permissions granted, scheduling alarm")
            AlarmUtils.scheduleDailyAlarm(this)
        } else {
            Log.e(TAG, "Critical permissions not granted: $permissions")
            // Optionally show UI prompt to request permissions again
        }
    }

    private val requestExactAlarm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Log.d(TAG, "Returned from exact alarm permission request")
        AlarmUtils.scheduleDailyAlarm(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make status bar transparent and content fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Adjust icon color based on theme
        val isDarkTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme

        // Request permissions
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM)
        }
        requestPermissions.launch(permissions.toTypedArray())

        // Request battery optimization exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(TAG, "Requesting to ignore battery optimizations")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        // Request exact alarm permission explicitly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "Requesting exact alarm permission")
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                requestExactAlarm.launch(intent)
            } else {
                AlarmUtils.scheduleDailyAlarm(this)
            }
        } else {
            AlarmUtils.scheduleDailyAlarm(this)
        }

        // Initialize WorkManager
        WorkManager.getInstance(this)

        // Compose content
        setContent {
            AutoWishTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("Database") { DatabaseScreen(navController) }
    }
}