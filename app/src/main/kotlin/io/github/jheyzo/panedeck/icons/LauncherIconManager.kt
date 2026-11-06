package io.github.jheyzo.panedeck.icons

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.jheyzo.panedeck.logging.NeXtepLog

/** Local launcher preferences are deliberately separate from workspace settings. */
internal object LauncherIconManager {
    const val PREFERENCES = "launcher_icon"
    const val ACTION_REFRESH = "io.github.jheyzo.panedeck.action.REFRESH_LAUNCHER_ICON"
    private const val KEY_CHOICE = "choice"
    private const val KEY_STARTED = "first_open_completed"
    private const val KEY_APPLIED = "applied_icon"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun selection(context: Context): String {
        // No "automatic" choice anymore: the seasonal variants were removed
        // 2026-11-05, only default + night remain. A saved "auto" from an
        // older build falls back to the default icon.
        val saved = preferences(context).getString(KEY_CHOICE, LauncherIconStyle.DEFAULT.key)
        return LauncherIconStyle.fromKey(saved)?.key ?: LauncherIconStyle.DEFAULT.key
    }

    fun currentIcon(context: Context): LauncherIconStyle =
        LauncherIconStyle.entries.firstOrNull { isEnabled(context, it) } ?: LauncherIconStyle.DEFAULT

    @Synchronized
    fun onAppOpened(context: Context) {
        runCatching {
            val preferences = preferences(context)
            // Receivers never set this flag: installation alone keeps the default icon.
            if (!preferences.getBoolean(KEY_STARTED, false)) {
                check(preferences.edit().putBoolean(KEY_STARTED, true).commit())
            }
            update(context)
        }.onFailure { handleFailure(context, it) }
    }

    @Synchronized
    fun refresh(context: Context) {
        runCatching { update(context) }.onFailure { handleFailure(context, it) }
    }

    @Synchronized
    fun select(context: Context, choice: String): Boolean {
        require(LauncherIconStyle.fromKey(choice) != null)
        return runCatching {
            check(preferences(context).edit()
                .putString(KEY_CHOICE, choice)
                .putBoolean(KEY_STARTED, true)
                .commit())
            update(context)
        }.onFailure { handleFailure(context, it) }.isSuccess
    }

    private fun update(context: Context) {
        if (!preferences(context).getBoolean(KEY_STARTED, false)) {
            // Upgrade safety: a removed alias may have been the enabled one
            // (e.g. a seasonal alias deleted in v5), leaving no launcher entry
            // at all. Restore the default icon instead of staying invisible.
            // A fresh install already has DefaultLauncher enabled by default,
            // so this is a no-op there.
            if (LauncherIconStyle.entries.none { isEnabled(context, it) }) {
                applyIcon(context, LauncherIconStyle.DEFAULT)
            }
            return
        }
        val style = LauncherIconStyle.fromKey(selection(context)) ?: LauncherIconStyle.DEFAULT
        // Clear any alarm an older build scheduled for seasonal rotation.
        cancelAlarm(context)
        applyIcon(context, style)
    }

    private fun applyIcon(context: Context, selected: LauncherIconStyle) {
        val changes = LauncherIconStyle.entries.mapNotNull { style ->
            val enabled = style == selected
            if (isEnabled(context, style) == enabled) null else PackageManager.ComponentEnabledSetting(
                component(context, style),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        if (changes.isNotEmpty()) {
            // API 33+ updates all aliases atomically. The real activity stays enabled.
            context.packageManager.setComponentEnabledSettings(changes)
        }
        val preferences = preferences(context)
        if (preferences.getString(KEY_APPLIED, null) != selected.key) {
            preferences.edit().putString(KEY_APPLIED, selected.key).apply()
        }
    }

    private fun isEnabled(context: Context, style: LauncherIconStyle): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context, style))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> style == LauncherIconStyle.DEFAULT
            else -> false
        }

    private fun component(context: Context, style: LauncherIconStyle) =
        ComponentName(context.packageName, "${context.packageName}.icons.${style.aliasName}")

    private fun cancelAlarm(context: Context) {
        // Seasonal rotation used to schedule this; kept only to clear alarms
        // left behind by older builds. No new alarm is ever scheduled.
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, LauncherIconReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    private fun handleFailure(context: Context, error: Throwable) {
        NeXtepLog.warn("launcher_icon", "Unable to update launcher icon", error)
    }
}
