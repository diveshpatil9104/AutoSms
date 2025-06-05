package com.example.autowish

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_DATE = "lastSentDate"
    private val PREF_SMS_COUNT = "smsCount"
    private val PREF_SMS_TIMESTAMP = "smsTimestamp"
    private val SMS_DELAY_MS = 36000L // 36 seconds
    private val MAX_STUDENT_PEERS = 3
    private val MAX_STAFF_PEERS = 2
    private val SMS_LIMIT_PER_HOUR = 90
    private val HOUR_MS = 3600000L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Device boot completed at ${Date()}")

        AlarmUtils.scheduleDailyAlarm(context)
        val db = try {
            BirthdayDatabase.getInstance(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database: ${e.message}")
            return
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        CoroutineScope(Dispatchers.IO).launch {
            processMissedBirthdays(context, db, prefs)
            processQueuedSms(context, db, prefs)
        }
    }

    private suspend fun processMissedBirthdays(context: Context, db: BirthdayDatabase, prefs: SharedPreferences) {
        val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val yesterday = sdf.format(calendar.time)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSmsTimestamp > HOUR_MS) {
            smsCount.set(0)
            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour > 16) {
            Log.w(TAG, "Outside sending window (midnight to 4 PM), queuing for tomorrow")
            return
        }

        val datesToProcess = listOf(today, yesterday).distinct()
        Log.d(TAG, "Processing birthdays for dates: ${datesToProcess.joinToString()}")
        for (date in datesToProcess) {
            if (prefs.getString(PREF_SENT_DATE, "") == date) {
                Log.d(TAG, "SMS already sent for $date, skipping")
                continue
            }

            val birthdayList = db.birthdayDao().getBirthdaysByDate(date)
            Log.d(TAG, "Found ${birthdayList.size} birthdays for $date after boot")
            if (birthdayList.isEmpty()) continue

            birthdayList.forEach { birthday ->
                if (smsCount.get() >= SMS_LIMIT_PER_HOUR) {
                    db.smsQueueDao().insert(SmsQueueEntry(
                        phoneNumber = birthday.phoneNumber,
                        name = birthday.name,
                        personType = birthday.personType,
                        type = "direct",
                        retryCount = 0,
                        timestamp = currentTime
                    ))
                } else {
                    val directSuccess = SmsUtils.sendWithRetries(context, birthday.phoneNumber, birthday.name, birthday.personType, "direct", smsCount, prefs)
                    if (directSuccess) {
                        Log.d(TAG, "Direct SMS sent for ${birthday.name}")
                    } else {
                        db.smsQueueDao().insert(SmsQueueEntry(
                            phoneNumber = birthday.phoneNumber,
                            name = birthday.name,
                            personType = birthday.personType,
                            type = "direct",
                            retryCount = 1,
                            timestamp = currentTime
                        ))
                    }
                }
                delay(SMS_DELAY_MS)

                val peers = when (birthday.personType) {
                    "Student" -> db.birthdayDao().getPeers(birthday.department, birthday.year, birthday.groupId, birthday.id)
                        .filter { it.personType == "Student" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber && it.isHod == false }
                        .let { if (it.size <= MAX_STUDENT_PEERS) it else it.shuffled().take(MAX_STUDENT_PEERS) }
                    "Staff" -> {
                        val nonHodPeers = db.birthdayDao().getPeers(birthday.department, null, birthday.groupId, birthday.id)
                            .filter { it.personType == "Staff" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber && it.isHod == false }
                            .shuffled().take(MAX_STAFF_PEERS)
                        val hodPeers = db.birthdayDao().getHodByDepartment(birthday.department, birthday.id)
                            .filter { it.personType == "Staff" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber && it.isHod == true }
                            .shuffled().take(1)
                        (nonHodPeers + hodPeers).distinctBy { it.phoneNumber }
                    }
                    else -> emptyList()
                }

                peers.forEach { peer ->
                    val type = if (peer.isHod == true) "hod" else "peer"
                    if (smsCount.get() >= SMS_LIMIT_PER_HOUR) {
                        db.smsQueueDao().insert(SmsQueueEntry(
                            phoneNumber = peer.phoneNumber,
                            name = birthday.name,
                            personType = birthday.personType,
                            type = type,
                            retryCount = 0,
                            timestamp = currentTime
                        ))
                    } else {
                        val peerSuccess = SmsUtils.sendWithRetries(context, peer.phoneNumber, birthday.name, birthday.personType, type, smsCount, prefs)
                        if (peerSuccess) {
                            Log.d(TAG, "$type SMS sent for ${birthday.name} to ${peer.phoneNumber}")
                        } else {
                            db.smsQueueDao().insert(SmsQueueEntry(
                                phoneNumber = peer.phoneNumber,
                                name = birthday.name,
                                personType = birthday.personType,
                                type = type,
                                retryCount = 1,
                                timestamp = currentTime
                            ))
                        }
                    }
                    delay(SMS_DELAY_MS)
                }
            }

            if (birthdayList.isNotEmpty()) {
                prefs.edit().putString(PREF_SENT_DATE, date).apply()
                Log.d(TAG, "Updated lastSentDate = $date")
            }
        }
    }

    private suspend fun processQueuedSms(context: Context, db: BirthdayDatabase, prefs: SharedPreferences) {
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSmsTimestamp > HOUR_MS) {
            smsCount.set(0)
            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
        }

        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 16) {
            Log.w(TAG, "Outside sending window, queuing for tomorrow")
            return
        }

        while (smsCount.get() < SMS_LIMIT_PER_HOUR) {
            val queuedSms = db.smsQueueDao().getPendingSms(10)
            if (queuedSms.isEmpty()) break

            queuedSms.forEach { sms ->
                if (smsCount.get() >= SMS_LIMIT_PER_HOUR) return@forEach
                val success = SmsUtils.sendWithRetries(context, sms.phoneNumber, sms.name, sms.personType, sms.type, smsCount, prefs, sms.retryCount + 1)
                if (success) {
                    db.smsQueueDao().deleteById(sms.id)
                    Log.d(TAG, "Sent queued ${sms.type} SMS to ${sms.phoneNumber}")
                } else {
                    if (sms.retryCount < 5) {
                        db.smsQueueDao().insert(sms.copy(retryCount = sms.retryCount + 1))
                        db.smsQueueDao().deleteById(sms.id)
                    }
                    Log.w(TAG, "Failed to send queued ${sms.type} SMS to ${sms.phoneNumber}")
                }
                delay(SMS_DELAY_MS)
            }
        }
    }
}