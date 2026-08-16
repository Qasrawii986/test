package com.smsexpense.tracker.domain.quicklaunch

/** Identifies a way of opening the app quickly. Adding one is a new enum entry + method. */
enum class QuickLaunchId {
    QS_TILE,
    HOME_SHORTCUT,
    SYSTEM_BACK_TAP,
    SENSOR_BACK_TAP,
    ASSISTANT,
}

enum class QuickLaunchState {
    /** Available but not set up yet. */
    INACTIVE,
    /** Confirmed on. */
    ACTIVE,
    /** Set up outside the app, so we cannot read it back (e.g. a system gesture). */
    UNKNOWN,
}

/**
 * A quick-launch option as the settings screen should render it. Methods that the
 * device cannot support are reported with [supported] = false and a plain reason,
 * so the UI never offers something that silently does nothing.
 */
data class QuickLaunchOption(
    val id: QuickLaunchId,
    val title: String,
    val description: String,
    val supported: Boolean,
    val state: QuickLaunchState,
    val actionLabel: String,
    val unsupportedReason: String? = null,
    val warning: String? = null,
)
