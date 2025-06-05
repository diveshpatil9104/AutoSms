package com.example.autowish

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PeerSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val TAG = "PeerSmsWorker"
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_SENT_PEERS = "sentPeers"
    private val PREF_SMS_COUNT = "smsCount"
    private val PREF_SMS_TIMESTAMP = "smsTimestamp"
    private val SMS_DELAY_MS = 36000L
    private val MAX_STUDENT_PEERS = 3
    private val MAX_STAFF_PEERS = 2
    private val SMS_LIMIT_PER_HOUR = 90
    private val HOUR_MS = 3600000L

    private val sentPeers = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun doWork(): Result {
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 16) {
            Log.w(TAG, "Outside sending window (midnight to 4 PM), queuing for tomorrow")
            return Result.retry()
        }

        val birthdayId = inputData.getInt(BirthdayAlarmReceiver.EXTRA_BIRTHDAY_ID, -1)
        val name = inputData.getString(BirthdayAlarmReceiver.EXTRA_BIRTHDAY_NAME) ?: return Result.failure()
        val personType = inputData.getString(BirthdayAlarmReceiver.EXTRA_PERSON_TYPE) ?: ""
        val department = inputData.getString(BirthdayAlarmReceiver.EXTRA_DEPARTMENT) ?: ""
        val year = inputData.getString(BirthdayAlarmReceiver.EXTRA_YEAR) ?: ""
        val groupId = inputData.getString(BirthdayAlarmReceiver.EXTRA_GROUP_ID) ?: ""
        val phoneNumber = inputData.getString(BirthdayAlarmReceiver.EXTRA_PHONE_NUMBER) ?: ""

        Log.d(TAG, "Processing peer SMS for $name (ID: $birthdayId, Type: $personType, Dept: $department, Year: $year, Group: $groupId, Phone: $phoneNumber)")

        val db = try {
            BirthdayDatabase.getInstance(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database: ${e.message}")
            return Result.failure()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSentPeers(prefs)
        val smsCount = AtomicInteger(prefs.getInt(PREF_SMS_COUNT, 0))
        val lastSmsTimestamp = prefs.getLong(PREF_SMS_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSmsTimestamp > HOUR_MS) {
            smsCount.set(0)
            prefs.edit().putInt(PREF_SMS_COUNT, 0).putLong(PREF_SMS_TIMESTAMP, currentTime).apply()
        }

        try {
            val peers = when (personType) {
                "Student" -> fetchStudentPeers(db, birthdayId, department, year, groupId, phoneNumber)
                "Staff" -> fetchStaffPeers(db, birthdayId, department, groupId, phoneNumber)
                else -> emptyList()
            }
            Log.d(TAG, "Selected ${peers.size} peers for $name: ${peers.joinToString { "${it.name} (${it.phoneNumber}, ID: ${it.id}, HOD: ${it.isHod})" }}")

            if (peers.isEmpty()) {
                Log.w(TAG, "No valid peers found for $name")
            } else {
                peers.forEachIndexed { index, peer ->
                    val peerKey = "${peer.phoneNumber}|$birthdayId"
                    val type = if (peer.isHod == true) "hod" else "peer"
                    if (!hasSentPeer(peerKey)) {
                        if (smsCount.get() >= SMS_LIMIT_PER_HOUR) {
                            db.smsQueueDao().insert(SmsQueueEntry(
                                phoneNumber = peer.phoneNumber,
                                name = name,
                                personType = personType,
                                type = type,
                                retryCount = 0,
                                timestamp = currentTime
                            ))
                        } else {
                            val success = SmsUtils.sendWithRetries(applicationContext, peer.phoneNumber, name, personType, type, smsCount, prefs)
                            if (success) {
                                markPeerAsSent(peerKey, prefs)
                                Log.d(TAG, "$type SMS sent successfully to ${peer.phoneNumber}")
                            } else {
                                db.smsQueueDao().insert(SmsQueueEntry(
                                    phoneNumber = peer.phoneNumber,
                                    name = name,
                                    personType = personType,
                                    type = type,
                                    retryCount = 1,
                                    timestamp = currentTime
                                ))
                            }
                        }
                        delay(SMS_DELAY_MS)
                    } else {
                        Log.d(TAG, "Skipping duplicate $type SMS to ${peer.phoneNumber} for birthday ID $birthdayId")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing peer SMS for $name: ${e.message}")
            return Result.retry()
        }

        return Result.success()
    }

    private suspend fun fetchStudentPeers(db: BirthdayDatabase, birthdayId: Int, department: String, year: String, groupId: String, phoneNumber: String): List<BirthdayEntry> {
        if (department.isEmpty() || year.isEmpty() || groupId.isEmpty()) return emptyList()
        return db.birthdayDao().getPeers(department, year, groupId, birthdayId)
            .filter { it.personType == "Student" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != phoneNumber && it.isHod == false }
            .let { if (it.size <= MAX_STUDENT_PEERS) it else it.shuffled().take(MAX_STUDENT_PEERS) }
    }

    private suspend fun fetchStaffPeers(db: BirthdayDatabase, birthdayId: Int, department: String, groupId: String, phoneNumber: String): List<BirthdayEntry> {
        if (department.isEmpty() || groupId.isEmpty()) return emptyList()
        val nonHodPeers = db.birthdayDao().getPeers(department, null, groupId, birthdayId)
            .filter { it.personType == "Staff" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != phoneNumber && it.isHod == false }
            .shuffled().take(MAX_STAFF_PEERS)
        val hodPeers = db.birthdayDao().getHodByDepartment(department, birthdayId)
            .filter { it.personType == "Staff" && it.phoneNumber.matches(Regex("\\d{10,25}")) && it.phoneNumber != phoneNumber && it.isHod == true }
            .shuffled().take(1)
        return (nonHodPeers + hodPeers).distinctBy { it.phoneNumber }
    }

    private fun loadSentPeers(prefs: SharedPreferences) {
        val sentSet = prefs.getStringSet(PREF_SENT_PEERS, emptySet()) ?: emptySet()
        sentSet.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) {
                val (phoneNumber, birthdayId) = parts
                sentPeers.getOrPut(phoneNumber) { mutableSetOf() }.add(birthdayId)
            }
        }
    }

    private fun hasSentPeer(peerKey: String): Boolean {
        val parts = peerKey.split("|")
        if (parts.size != 2) return false
        val (phoneNumber, birthdayId) = parts
        return sentPeers[phoneNumber]?.contains(birthdayId) == true
    }

    private fun markPeerAsSent(peerKey: String, prefs: SharedPreferences) {
        val parts = peerKey.split("|")
        if (parts.size != 2) return
        val (phoneNumber, birthdayId) = parts
        sentPeers.getOrPut(phoneNumber) { mutableSetOf() }.add(birthdayId)
        val updatedSet = sentPeers.entries.flatMap { (phone, ids) -> ids.map { "$phone|$it" } }.toSet()
        prefs.edit().putStringSet(PREF_SENT_PEERS, updatedSet).apply()
    }
}