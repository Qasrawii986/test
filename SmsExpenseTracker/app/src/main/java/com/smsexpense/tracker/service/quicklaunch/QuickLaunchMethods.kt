package com.smsexpense.tracker.service.quicklaunch

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.smsexpense.tracker.R
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchId
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchOption
import com.smsexpense.tracker.domain.quicklaunch.QuickLaunchState
import com.smsexpense.tracker.util.AppLog

/**
 * One way of launching the app quickly. Adding a new shortcut type means adding
 * an implementation here and listing it in [QuickLaunchRegistry] — the settings
 * screen renders whatever the registry reports as supported, with no UI changes.
 */
interface QuickLaunchMethod {
    val id: QuickLaunchId
    fun describe(context: Context, backTapEnabled: Boolean): QuickLaunchOption
    /** Performs or guides the setup. [activity] is needed by APIs that require one. */
    fun activate(context: Context, activity: Activity?)
}

/** Quick Settings tile: works everywhere, costs nothing. */
object QsTileMethod : QuickLaunchMethod {
    override val id = QuickLaunchId.QS_TILE

    override fun describe(context: Context, backTapEnabled: Boolean) = QuickLaunchOption(
        id = id,
        title = context.getString(R.string.ql_tile_title),
        description = context.getString(R.string.ql_tile_desc),
        supported = true,
        // The system does not tell us whether the user kept the tile.
        state = QuickLaunchState.UNKNOWN,
        actionLabel = context.getString(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) R.string.ql_tile_add
            else R.string.ql_tile_how
        ),
    )

    override fun activate(context: Context, activity: Activity?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBar = context.getSystemService<android.app.StatusBarManager>()
            statusBar?.requestAddTileService(
                ComponentName(context, QuickLaunchTileService::class.java),
                "SMS Expense",
                android.graphics.drawable.Icon.createWithResource(
                    context, R.drawable.ic_launcher_foreground,
                ),
                {}, {},
            )
        }
        // Below API 33 there is no request API; the settings screen shows manual steps.
    }
}

/** Pinned home-screen shortcut. */
object HomeShortcutMethod : QuickLaunchMethod {
    override val id = QuickLaunchId.HOME_SHORTCUT

    override fun describe(context: Context, backTapEnabled: Boolean): QuickLaunchOption {
        val supported = ShortcutManagerCompat.isRequestPinShortcutSupported(context)
        return QuickLaunchOption(
            id = id,
            title = context.getString(R.string.ql_shortcut_title),
            description = context.getString(R.string.ql_shortcut_desc),
            supported = supported,
            state = QuickLaunchState.UNKNOWN,
            actionLabel = context.getString(R.string.ql_shortcut_add),
            unsupportedReason = if (supported) null else context.getString(R.string.ql_shortcut_unsupported),
        )
    }

    override fun activate(context: Context, activity: Activity?) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return
        val shortcut = ShortcutInfoCompat.Builder(context, "quick-actions")
            .setShortLabel(context.getString(R.string.ql_shortcut_label))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(
                Intent(context, QuickLaunchActivity::class.java).setAction(Intent.ACTION_VIEW)
            )
            .build()
        runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }
            .onFailure { AppLog.e("Pin shortcut failed", it) }
    }
}

/**
 * The OEM's own back-tap gesture. There is no public API for Quick Tap or
 * Samsung's equivalent, so all we can honestly do is detect the likely path and
 * send the user to the right settings screen.
 */
object SystemBackTapMethod : QuickLaunchMethod {
    override val id = QuickLaunchId.SYSTEM_BACK_TAP

    private fun isPixel(): Boolean =
        Build.MANUFACTURER.equals("Google", ignoreCase = true)

    private fun isSamsung(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private fun hasRegistar(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.samsung.android.app.galaxyregistry", 0)
        true
    }.getOrDefault(false)

    override fun describe(context: Context, backTapEnabled: Boolean): QuickLaunchOption {
        val quickTapLikely = isPixel() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val samsungPath = isSamsung() && hasRegistar(context)
        val supported = quickTapLikely || samsungPath
        return QuickLaunchOption(
            id = id,
            title = context.getString(R.string.ql_system_title),
            description = context.getString(
                when {
                    quickTapLikely -> R.string.ql_system_quicktap
                    samsungPath -> R.string.ql_system_samsung
                    else -> R.string.ql_system_none
                }
            ),
            supported = supported,
            state = QuickLaunchState.UNKNOWN,
            actionLabel = context.getString(R.string.ql_open_settings),
            unsupportedReason = if (supported) null
                else context.getString(R.string.ql_system_unsupported),
        )
    }

    override fun activate(context: Context, activity: Activity?) {
        // No public deep link exists for these screens; fall back to system settings.
        val candidates = listOf(
            Intent("android.settings.GESTURE_SETTINGS"),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(intent) }.onSuccess { return }
            }
        }
    }
}

/** Our own accelerometer-based triple back tap. Opt-in; costs battery. */
object SensorBackTapMethod : QuickLaunchMethod {
    override val id = QuickLaunchId.SENSOR_BACK_TAP

    private fun hasAccelerometer(context: Context): Boolean =
        context.getSystemService<SensorManager>()
            ?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    override fun describe(context: Context, backTapEnabled: Boolean): QuickLaunchOption {
        val supported = hasAccelerometer(context)
        return QuickLaunchOption(
            id = id,
            title = context.getString(R.string.ql_sensor_title),
            description = context.getString(R.string.ql_sensor_desc),
            supported = supported,
            state = if (backTapEnabled) QuickLaunchState.ACTIVE else QuickLaunchState.INACTIVE,
            actionLabel = context.getString(if (backTapEnabled) R.string.ql_turn_off else R.string.ql_turn_on),
            unsupportedReason = if (supported) null else context.getString(R.string.ql_sensor_no_accel),
            warning = context.getString(R.string.ql_sensor_warning),
        )
    }

    override fun activate(context: Context, activity: Activity?) = Unit // handled by the ViewModel
}

/** Assistant role: the only sanctioned way to bind the power button. */
object AssistantMethod : QuickLaunchMethod {
    override val id = QuickLaunchId.ASSISTANT

    override fun describe(context: Context, backTapEnabled: Boolean) = QuickLaunchOption(
        id = id,
        title = context.getString(R.string.ql_assistant_title),
        description = context.getString(R.string.ql_assistant_desc),
        supported = true,
        state = QuickLaunchState.UNKNOWN,
        actionLabel = context.getString(R.string.ql_open_settings),
        warning = context.getString(R.string.ql_assistant_warning),
    )

    override fun activate(context: Context, activity: Activity?) {
        val candidates = listOf(
            Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(intent) }.onSuccess { return }
            }
        }
    }
}

/** The single list the settings screen renders from. */
object QuickLaunchRegistry {
    val methods: List<QuickLaunchMethod> = listOf(
        QsTileMethod,
        HomeShortcutMethod,
        SystemBackTapMethod,
        SensorBackTapMethod,
        AssistantMethod,
    )

    /** Descriptions are localized, so the context is wrapped with the app language. */
    fun describeAll(context: Context, backTapEnabled: Boolean): List<QuickLaunchOption> {
        val localized = com.smsexpense.tracker.util.AppLocale.wrap(context)
        return methods.map { it.describe(localized, backTapEnabled) }
    }

    fun byId(id: QuickLaunchId): QuickLaunchMethod? = methods.firstOrNull { it.id == id }
}
