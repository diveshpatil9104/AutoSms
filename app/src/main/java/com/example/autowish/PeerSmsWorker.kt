package com.example.autowish

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

class PeerSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val TAG = "PeerSmsWorker"
    private val PREFS_NAME = "BirthdayPrefs"
    private val PREF_FAILED_SMS = "failedSms"
    private val PREF_SENT_PEERS = "sentPeers"
    private val SMS_DELAY_MS = 10000L // 10s to avoid throttling
    private val MAX_RETRIES = 3
    private val MAX_STUDENT_PEERS = 3
    private val MAX_STAFF_PEERS = 2

    private val sentPeers = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun doWork(): Result {
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
        val failedMessages = mutableListOf<BirthdayAlarmReceiver.FailedMessage>()
        loadSentPeers(prefs)

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
                    val type = if (peer.isHod == true) "hod" else "peer" // Define type here
                    if (!hasSentPeer(peerKey)) {
                        Log.d(TAG, "Sending $type SMS ${index + 1}/${peers.size} to ${peer.name} (${peer.phoneNumber}) for $name")
                        val success = sendWithRetries(peer.phoneNumber, name, personType, type, failedMessages)
                        if (success) {
                            markPeerAsSent(peerKey, prefs)
                            Log.d(TAG, "${type.replaceFirstChar { it.uppercase() }} SMS sent successfully to ${peer.phoneNumber}")
                        } else {
                            Log.e(TAG, "Failed to send $type SMS to ${peer.phoneNumber} after $MAX_RETRIES attempts")
                        }
                        delay(SMS_DELAY_MS)
                    } else {
                        Log.d(TAG, "Skipping duplicate $type SMS to ${peer.phoneNumber} for birthday ID $birthdayId") // Use type here
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing peer SMS for $name: ${e.message}", e)
            return Result.retry()
        }

        saveFailedMessages(prefs, failedMessages)
        return if (failedMessages.isEmpty()) {
            Log.d(TAG, "All peer/HOD SMS processed successfully for $name")
            Result.success()
        } else {
            Log.w(TAG, "Some peer/HOD SMS failed for $name, scheduling retry")
            Result.retry()
        }
    }

    private suspend fun fetchStudentPeers(
        db: BirthdayDatabase,
        birthdayId: Int,
        department: String,
        year: String,
        groupId: String,
        phoneNumber: String
    ): List<BirthdayEntry> {
        if (department.isEmpty() || year.isEmpty() || groupId.isEmpty()) {
            Log.w(TAG, "Invalid student parameters: dept=$department, year=$year, group=$groupId")
            return emptyList()
        }

        val allPeers = db.birthdayDao().getPeers(department, year, groupId, birthdayId)
            .filter {
                it.personType == "Student" &&
                        it.phoneNumber.matches(Regex("\\d{10,25}")) &&
                        it.phoneNumber != phoneNumber
            }

        Log.d(TAG, "Found ${allPeers.size} student peers in group $groupId, dept $department, year $year")

        return when {
            allPeers.size <= 3 -> allPeers // Small group, take all
            else -> allPeers.shuffled().take(MAX_STUDENT_PEERS) // Randomly select 3
        }
    }

    private suspend fun fetchStaffPeers(
        db: BirthdayDatabase,
        birthdayId: Int,
        department: String,
        groupId: String,
        phoneNumber: String
    ): List<BirthdayEntry> {
        if (department.isEmpty() || groupId.isEmpty()) {
            Log.w(TAG, "Invalid staff parameters: dept=$department, group=$groupId")
            return emptyList()
        }

        val allPeers = db.birthdayDao().getPeers(department, null, groupId, birthdayId)
            .filter {
                it.personType == "Staff" &&
                        it.phoneNumber.matches(Regex("\\d{10,25}")) &&
                        it.phoneNumber != phoneNumber
            }

        Log.d(TAG, "Found ${allPeers.size} staff peers in group $groupId, dept $department")

        val hod = allPeers.filter { it.isHod == true }.shuffled().take(1)
        val nonHodPeers = allPeers.filter { it.isHod == false }.shuffled().take(MAX_STAFF_PEERS)

        val selectedPeers = (hod + nonHodPeers).distinctBy { it.phoneNumber }
        Log.d(TAG, "Selected ${hod.size} HOD(s) and ${nonHodPeers.size} non-HOD peer(s) for staff")
        return selectedPeers
    }

    private suspend fun sendWithRetries(
        phoneNumber: String,
        name: String,
        personType: String,
        type: String,
        failedMessages: MutableList<BirthdayAlarmReceiver.FailedMessage>,
        retries: Int = MAX_RETRIES
    ): Boolean {
        if (!phoneNumber.matches(Regex("\\d{10,25}"))) {
            Log.w(TAG, "Invalid phone number: $phoneNumber for $type SMS")
            return false
        }

        var attempt = 0
        var delayMs = 10000L
        while (attempt < retries) {
            try {
                Log.d(TAG, "Attempting $type SMS to $phoneNumber for $name on attempt ${attempt + 1}")
                when (type) {
                    "peer" -> SmsUtils.sendPeerSms(applicationContext, phoneNumber, name, personType)
                    "hod" -> SmsUtils.sendHodSms(applicationContext, phoneNumber, name)
                }
                Log.d(TAG, "$type SMS sent successfully to $phoneNumber on attempt ${attempt + 1}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed $type SMS to $phoneNumber on attempt ${attempt + 1}: ${e.message}", e)
                attempt++
                if (attempt < retries) {
                    Log.d(TAG, "Retrying $type SMS to $phoneNumber after ${delayMs}ms")
                    delay(delayMs)
                    delayMs *= 2
                } else {
                    failedMessages.add(BirthdayAlarmReceiver.FailedMessage(phoneNumber, name, personType, type, 0))
                }
            }
        }
        Log.e(TAG, "Exhausted retries for $type SMS to $phoneNumber for $name")
        return false
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
        Log.d(TAG, "Loaded ${sentSet.size} sent peer entries")
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
        Log.d(TAG, "Marked peer SMS as sent for $peerKey")
    }

    private fun saveFailedMessages(prefs: SharedPreferences, failedMessages: List<BirthdayAlarmReceiver.FailedMessage>) {
        val failedSet = failedMessages.map { it.toString() }.toSet()
        prefs.edit().putStringSet(PREF_FAILED_SMS, failedSet).apply()
        Log.d(TAG, "Saved ${failedSet.size} failed messages to SharedPreferences")
    }
}