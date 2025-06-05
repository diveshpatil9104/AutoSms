package com.example.autowish

import android.app.IntentService
import android.content.Intent
import android.util.Log

class SmsSentService : IntentService("SmsSentService") {
    private val TAG = "SmsSentService"

    override fun onHandleIntent(intent: Intent?) {
        val phoneNumber = intent?.getStringExtra("phoneNumber") ?: "Unknown"
        val name = intent?.getStringExtra("name") ?: "Unknown"
        val messageType = intent?.getStringExtra("messageType") ?: "Unknown"
        val resultCode = intent?.getIntExtra("android.telephony.SmsManager.RESULT", -1) ?: -1

        Log.d(TAG, "$messageType SMS result for $phoneNumber ($name), resultCode: $resultCode")
        when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                SmsUtils.showNotification(this, "$messageType SMS Sent", "$messageType SMS sent to $name ($phoneNumber)")
            }
            else -> {
                SmsUtils.showNotification(this, "$messageType SMS Failed", "$messageType SMS failed for $name: Error code $resultCode")
            }
        }
    }
}