//package com.example.autowish
//
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.content.SharedPreferences
//import android.os.Build
//import android.os.IBinder
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import kotlinx.coroutines.*
//import java.text.SimpleDateFormat
//import java.util.*
//import java.util.concurrent.atomic.AtomicInteger
//
//class BirthdaySmsService : Service() {
//    private val TAG = "BirthdaySmsService"
//    private val PREFS_NAME = "BirthdayPrefs"
//    private val PREF_SENT_DATE = "lastSentDate"
//    private val PREF_SMS_COUNT = "smsCount"
//    private val PREF_SMS_TIMESTAMP = "smsTimestamp"
//    private val SMS_DELAY_MS = 5000L // 36 seconds
//    private val MAX_STUDENT_PEERS = 3
//    private val MAX_STAFF_PEERS = 2
//    private val SMS_LIMIT_PER_HOUR = 90
//    private val HOUR_MS = 5000L
//    private val NOTIFICATION_CHANNEL_ID = "birthday_sms_service"
//    private val NOTIFICATION_ID = 1001
//
//    private lateinit var db: BirthdayDatabase
//    private lateinit var prefs: SharedPreferences
//    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.d(TAG, "Service created")
//        db = BirthdayDatabase.getInstance(this)
//        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//        startForegroundService()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        Log.d(TAG, "Service started with intent: $intent")
//        scope.launch {
//            processBirthdays()
//            processQueuedSms()
//            Log.d(TAG, "Processing complete, stopping service")
//            stopSelf()
//        }
//        return START_STICKY
//    }
//
//    private fun startForegroundService() {
//        Log.d(TAG, "Starting foreground service")
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                NOTIFICATION_CHANNEL_ID,
//                "Birthday SMS Service",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
//        }
//
//        val notificationIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(
//            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//        )
//
//        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
//            .setContentTitle("AutoWish")
//            .setContentText("Sending birthday SMS in the background")
//            .setSmallIcon(android.R.drawable.ic_dialog_info)
//            .setContentIntent(pendingIntent)
//            .setPriority(NotificationCompat.PRIORITY_LOW)
//            .build()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
//        } else {
//            startForeground(NOTIFICATION_ID, notification)
//        }
//        Log.d(TAG, "Foreground notification started")
//    }
//
//    private suspend fun processBirthdays() {
//        Log.d(TAG, "Starting birthday processing")
//        val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
//        val today = sdf.format(Date())
//        val calendar = Calendar.getInstance()
//        calendar.add(Calendar.DAY_OF_MONTH, -1)
//        val yesterday = sdf.format(calendar.time)
//        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
//        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
//        val currentTime = System.currentTimeMillis()
//
//        if (currentTime - lastSmsTimestamp > HOUR_MS) {
//            smsCount.set(0)
//            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
//            Log.d(TAG, "Reset SMS count and timestamp")
//        }
//
//        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
//        if (currentHour > 20) {
//            Log.w(TAG, "Outside sending window (midnight to 8 PM), queuing for tomorrow")
//            return
//        }
//
//        val datesToProcess = listOf(today, yesterday).distinct()
//        Log.d(TAG, "Processing birthdays for dates: ${datesToProcess.joinToString()}")
//        for (date in datesToProcess) {
//            if (prefs.getString(PREF_SENT_DATE, "") == date) {
//                Log.d(TAG, "SMS already sent for $date, skipping")
//                continue
//            }
//
//            val birthdayList = db.birthdayDao().getBirthdaysByDate(date)
//            Log.d(TAG, "Found ${birthdayList.size} birthdays for $date")
//            if (birthdayList.isEmpty()) continue
//
//            birthdayList.forEach { birthday ->
//                if (smsCount.get() >= SMS_LIMIT_PER_HOUR) {
//                    db.smsQueueDao().insert(SmsQueueEntry(
//                        phoneNumber = birthday.phoneNumber,
//                        name = birthday.name,
//                        personType = birthday.personType,
//                        type = "direct",
//                        retryCount = 0,
//                        timestamp = currentTime
//                    ))
//                    Log.d(TAG, "Queued direct SMS for ${birthday.name} due to SMS limit")
//                } else {
//                    val directSuccess = SmsUtils.sendWithRetries(this, birthday.phoneNumber, birthday.name, birthday.personType, "direct", smsCount, prefs)
//                    if (directSuccess) {
//                        Log.d(TAG, "Direct SMS sent for ${birthday.name}")
//                    } else {
//                        db.smsQueueDao().insert(SmsQueueEntry(
//                            phoneNumber = birthday.phoneNumber,
//                            name = birthday.name,
//                            personType = birthday.personType,
//                            type = "direct",
//                            retryCount = 1,
//                            timestamp = currentTime
//                        ))
//                        Log.w(TAG, "Failed to send direct SMS for ${birthday.name}, queued for retry")
//                    }
//                }
//                delay(SMS_DELAY_MS)
//
//                val peers = when (birthday.personType) {
//                    "Student" -> db.birthdayDao().getPeers(birthday.department, birthday.year, birthday.groupId, birthday.id)
//                        .filter { it.personType == "Student" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber && it.isHod == false }
//                        .let { if (it.size <= MAX_STUDENT_PEERS) it else it.shuffled().take(MAX_STUDENT_PEERS) }
//                    "Staff" -> {
//                        val nonHodPeers = db.birthdayDao().getPeers(birthday.department, null, birthday.groupId, birthday.id)
//                            .filter { it.personType == "Staff" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber && it.isHod == false }
//                            .shuffled().take(MAX_STAFF_PEERS)
//                        val hodPeers = db.birthdayDao().getHodByDepartment(birthday.department, birthday.id)
//                            .filter { it.personType == "Staff" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != birthday.phoneNumber && it.isHod == true }
//                            .shuffled().take(1)
//                        (nonHodPeers + hodPeers).distinctBy { it.phoneNumber }
//                    }
//                    else -> emptyList()
//                }
//
//                peers.forEach { peer ->
//                    val type = if (peer.isHod == true) "hod" else "peer"
//                    if (smsCount.get() >= SMS_LIMIT_PER_HOUR) {
//                        db.smsQueueDao().insert(SmsQueueEntry(
//                            phoneNumber = peer.phoneNumber,
//                            name = birthday.name,
//                            personType = birthday.personType,
//                            type = type,
//                            retryCount = 0,
//                            timestamp = currentTime
//                        ))
//                        Log.d(TAG, "Queued $type SMS for ${birthday.name} to ${peer.phoneNumber} due to SMS limit")
//                    } else {
//                        val peerSuccess = SmsUtils.sendWithRetries(this, peer.phoneNumber, birthday.name, birthday.personType, type, smsCount, prefs)
//                        if (peerSuccess) {
//                            Log.d(TAG, "$type SMS sent for ${birthday.name} to ${peer.phoneNumber}")
//                        } else {
//                            db.smsQueueDao().insert(SmsQueueEntry(
//                                phoneNumber = peer.phoneNumber,
//                                name = birthday.name,
//                                personType = birthday.personType,
//                                type = type,
//                                retryCount = 1,
//                                timestamp = currentTime
//                            ))
//                            Log.w(TAG, "Failed to send $type SMS for ${birthday.name} to ${peer.phoneNumber}, queued for retry")
//                        }
//                    }
//                    delay(SMS_DELAY_MS)
//                }
//            }
//
//            if (birthdayList.isNotEmpty()) {
//                prefs.edit().putString(PREF_SENT_DATE, date).apply()
//                Log.d(TAG, "Updated lastSentDate = $date")
//            }
//        }
//    }
//
//    private suspend fun processQueuedSms() {
//        Log.d(TAG, "Starting queued SMS processing")
//        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
//        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
//        val currentTime = System.currentTimeMillis()
//
//        if (currentTime - lastSmsTimestamp > HOUR_MS) {
//            smsCount.set(0)
//            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
//            Log.d(TAG, "Reset SMS count and timestamp for queued SMS")
//        }
//
//        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 20) {
//            Log.w(TAG, "Outside sending window, queuing for tomorrow")
//            return
//        }
//
//        while (smsCount.get() < SMS_LIMIT_PER_HOUR) {
//            val queuedSms = db.smsQueueDao().getPendingSms(10)
//            if (queuedSms.isEmpty()) {
//                Log.d(TAG, "No queued SMS to process")
//                break
//            }
//
//            Log.d(TAG, "Processing ${queuedSms.size} queued SMS")
//            queuedSms.forEach { sms ->
//                if (smsCount.get() >= SMS_LIMIT_PER_HOUR) return@forEach
//                val success = SmsUtils.sendWithRetries(this, sms.phoneNumber, sms.name, sms.personType, sms.type, smsCount, prefs, sms.retryCount + 1)
//                if (success) {
//                    db.smsQueueDao().deleteById(sms.id)
//                    Log.d(TAG, "Sent queued ${sms.type} SMS to ${sms.phoneNumber}")
//                } else {
//                    if (sms.retryCount < 5) {
//                        db.smsQueueDao().insert(sms.copy(retryCount = sms.retryCount + 1))
//                        db.smsQueueDao().deleteById(sms.id)
//                        Log.d(TAG, "Re-queued ${sms.type} SMS to ${sms.phoneNumber} with retry count ${sms.retryCount + 1}")
//                    } else {
//                        Log.w(TAG, "Failed to send queued ${sms.type} SMS to ${sms.phoneNumber} after max retries")
//                    }
//                }
//                delay(SMS_DELAY_MS)
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        scope.cancel()
//        Log.d(TAG, "Service destroyed")
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//}