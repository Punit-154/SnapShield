package com.smssentry.domain.service

import android.content.Context
import com.smssentry.data.model.ClassificationResult
import com.smssentry.data.model.DeepCheckUpdate

interface SMSSentryAI {
    fun initialize(context: Context, callback: (Boolean) -> Unit)
    fun classifySMS(smsText: String, callback: (ClassificationResult) -> Unit)
    fun startDeepCheck(smsText: String, listener: DeepCheckListener): DeepCheckSession
}

interface DeepCheckListener {
    fun onUpdate(update: DeepCheckUpdate)
}

interface DeepCheckSession {
    val isActive: Boolean
    fun cancel()
}
