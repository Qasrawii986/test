package com.smsexpense.tracker.domain.model

/** Contents of update.json published alongside each release. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

sealed class UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck()
    data class UpToDate(val currentVersionName: String) : UpdateCheck()
    data class Failed(val message: String) : UpdateCheck()
}
