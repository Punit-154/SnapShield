package com.smssentry.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MmsReceiver"
        const val ACTION_MMS_RECEIVED = "com.smssentry.MMS_RECEIVED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "MMS received")

        val broadcastIntent = Intent(ACTION_MMS_RECEIVED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(broadcastIntent)
    }
}
