package com.techfix.app.core.util

import android.util.Log
import com.techfix.app.BuildConfig

/** Debug-only diagnostics for the repair-image pipeline. Never logs API keys. */
object UploadDiagnostics {
    const val TAG = "FIXORA_UPLOAD"

    fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun error(message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.e(
                TAG,
                "$message exceptionClass=${throwable::class.java.name} " +
                    "exceptionMessage=${throwable.message}",
                throwable,
            )
        }
    }
}
