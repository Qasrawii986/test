package com.smsexpense.tracker.util

import android.util.Log
import com.smsexpense.tracker.BuildConfig

/**
 * Debug-only logging. Message bodies and other sensitive text never reach Logcat
 * in release builds.
 */
object AppLog {
    private const val TAG = "SmsExpense"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, error)
    }

    /** Errors are logged in release too, but callers must not include SMS bodies. */
    fun e(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
    }
}
