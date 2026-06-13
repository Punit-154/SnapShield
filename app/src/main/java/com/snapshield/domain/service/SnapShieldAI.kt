package com.snapshield.domain.service

import android.content.Context
import com.snapshield.data.model.ClassificationResult
import com.snapshield.data.model.DeepCheckUpdate

interface SnapShieldAI {
    fun initialize(context: Context, callback: (Boolean) -> Unit)
    fun classifySMS(smsText: String, callback: (ClassificationResult) -> Unit)
    fun startDeepCheck(smsText: String, listener: DeepCheckListener): DeepCheckSession
    fun enableDemoMode()
}

interface DeepCheckListener {
    fun onUpdate(update: DeepCheckUpdate)
}

interface DeepCheckSession {
    val isActive: Boolean
    fun cancel()
}
