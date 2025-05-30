//package com.example.autowish
//
//import android.app.Application
//import android.content.Intent
//import android.net.Uri
//import android.os.Build
//import android.os.PowerManager
//import android.provider.Settings
//import android.util.Log
//import androidx.work.OneTimeWorkRequestBuilder
//import androidx.work.WorkManager
//import com.example.autowish.worker.BirthdayWorker
//import com.example.autowish.worker.scheduleDailyBirthdayWorker
//import java.util.concurrent.TimeUnit
//
//class AutoWishApplication : Application() {
//    companion object {
//        private const val TAG = "AutoWish"
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.d(TAG, "AutoWishApplication: onCreate called")
//
//        // Clear old WorkManager tasks to avoid conflicts
//        WorkManager.getInstance(this).cancelAllWork()
//        Log.d(TAG, "Cleared all WorkManager tasks")
//
//        // Schedule daily birthday worker
//        scheduleDailyBirthdayWorker(this)
//        Log.d(TAG, "Scheduled daily BirthdayWorker")
//
//        // Schedule one-time workers for testing
//        val testRequest = OneTimeWorkRequestBuilder<BirthdayWorker>()
//            .setInitialDelay(5, TimeUnit.MINUTES)
//            .addTag("BirthdayTestWorker")
//            .build()
//        WorkManager.getInstance(this).enqueue(testRequest)
//        Log.d(TAG, "Enqueued one-time test BirthdayWorker (5-minute delay)")
//
//        val debugRequest = OneTimeWorkRequestBuilder<BirthdayWorker>()
//            .setInitialDelay(1, TimeUnit.MINUTES)
//            .addTag("BirthdayDebugWorker")
//            .build()
//        WorkManager.getInstance(this).enqueue(debugRequest)
//        Log.d(TAG, "Enqueued one-time debug BirthdayWorker (1-minute delay)")
//
//        // Prompt for battery optimization exemption
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
//            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
//                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
//                    data = Uri.parse("package:$packageName")
//                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                }
//                try {
//                    startActivity(intent)
//                    Log.i(TAG, "Battery optimization prompt shown")
//                } catch (e: Exception) {
//                    Log.e(TAG, "Failed to show battery optimization prompt: ${e.message}")
//                }
//            } else {
//                Log.d(TAG, "Battery optimization already disabled")
//            }
//        }
//
//        // Monitor WorkManager state
//        WorkManager.getInstance(this)
//            .getWorkInfosByTagLiveData("BirthdayAutoSmsWorker")
//            .observeForever { workInfos ->
//                workInfos.forEach { Log.d(TAG, "Work state: ${it.state}, id: ${it.id}, tags: ${it.tags}") }
//            }
//        WorkManager.getInstance(this)
//            .getWorkInfosByTagLiveData("BirthdayTestWorker")
//            .observeForever { workInfos ->
//                workInfos.forEach { Log.d(TAG, "Test work state: ${it.state}, id: ${it.id}, tags: ${it.tags}") }
//            }
//        WorkManager.getInstance(this)
//            .getWorkInfosByTagLiveData("BirthdayDebugWorker")
//            .observeForever { workInfos ->
//                workInfos.forEach { Log.d(TAG, "Debug work state: ${it.state}, id: ${it.id}, tags: ${it.tags}") }
//            }
//    }
//}