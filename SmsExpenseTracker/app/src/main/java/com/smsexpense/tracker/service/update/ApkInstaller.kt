package com.smsexpense.tracker.service.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.smsexpense.tracker.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs an APK with the modern PackageInstaller session API
 * (Intent.ACTION_INSTALL_PACKAGE is deprecated since API 29, and the session
 * API avoids needing a FileProvider at all).
 *
 * The system still shows its own confirmation screen — that is mandatory for a
 * non-system app and cannot be bypassed. The user must also have granted
 * "install unknown apps" for this app; see [canInstall].
 */
object ApkInstaller {

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants "install unknown apps" for this app. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${context.packageName}"),
        )

    /**
     * Streams [apk] into a new install session and commits it. Returns null on
     * success (the system dialog takes over), or an error message.
     */
    suspend fun install(context: Context, apk: File): String? = withContext(Dispatchers.IO) {
        if (!apk.exists() || apk.length() == 0L) return@withContext "Downloaded file is missing"
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Ask for an unattended update where the platform allows it; the
                    // system still falls back to a confirmation dialog when it doesn't.
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(WRITE_NAME, 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
            null
        } catch (e: Exception) {
            AppLog.e("APK install failed", e)
            if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
            "Install failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private const val WRITE_NAME = "app-update.apk"
}
