package com.smssentry.sms

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

class SmsContentObserver(
    private val onMessagesChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onMessagesChanged()
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        onMessagesChanged()
    }
}

