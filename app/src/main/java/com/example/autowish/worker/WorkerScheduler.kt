package com.example.autowish.worker

import android.content.Context
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

fun scheduleDailyBirthdayWorker(context: Context) {
    val delay = calculateInitialDelay()

    val workRequest = PeriodicWorkRequestBuilder<BirthdayWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily_birthday_worker",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

private fun calculateInitialDelay(): Long {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (now.after(target)) {
        target.add(Calendar.DAY_OF_YEAR, 1)
    }

    return target.timeInMillis - now.timeInMillis
}