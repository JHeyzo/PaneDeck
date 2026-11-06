package io.github.jheyzo.panedeck.trigger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.PendingIntent

object TriggerBroadcastContract {
    const val ACTION_TOGGLE_WORKSPACE = "io.github.jheyzo.panedeck.action.TOGGLE_WORKSPACE"
    const val ACTION_SET_WORKSPACE = "io.github.jheyzo.panedeck.action.SET_WORKSPACE"
    const val ACTION_QUERY_WORKSPACE = "io.github.jheyzo.panedeck.action.QUERY_WORKSPACE"
    const val ACTION_HOST_TOGGLE_REQUEST = "io.github.jheyzo.panedeck.action.HOST_TOGGLE_REQUEST"
    const val ACTION_HOST_SET_REQUEST = "io.github.jheyzo.panedeck.action.HOST_SET_REQUEST"
    const val ACTION_SYSTEMUI_SET_WORKSPACE = "io.github.jheyzo.panedeck.action.SYSTEMUI_SET_WORKSPACE"
    const val ACTION_LAUNCH_SLOT_TEST = "io.github.jheyzo.panedeck.action.LAUNCH_SLOT_TEST"
    const val ACTION_COMPARE = "io.github.jheyzo.panedeck.action.COMPARE"
    const val EXTRA_COMPARE_QUERY = "io.github.jheyzo.panedeck.extra.COMPARE_QUERY"
    const val EXTRA_COMPARE_MODE = "io.github.jheyzo.panedeck.extra.COMPARE_MODE"
    const val ACTION_LAUNCHER_APP_REQUEST = "io.github.jheyzo.panedeck.action.LAUNCHER_APP_REQUEST"
    const val CONTROL_PERMISSION = "io.github.jheyzo.panedeck.permission.CONTROL_WORKSPACE"
    const val EXTRA_SOURCE = "io.github.jheyzo.panedeck.extra.TRIGGER_SOURCE"
    const val EXTRA_ACTIVE = "io.github.jheyzo.panedeck.extra.WORKSPACE_ACTIVE"
    const val EXTRA_REQUESTED_ACTIVE = "io.github.jheyzo.panedeck.extra.REQUESTED_ACTIVE"
    const val EXTRA_GESTURE_EVENT_TIME = "io.github.jheyzo.panedeck.extra.GESTURE_EVENT_TIME"
    const val EXTRA_ONLY_IF_INACTIVE = "io.github.jheyzo.panedeck.extra.ONLY_IF_INACTIVE"
    const val EXTRA_HOME_PACKAGE = "io.github.jheyzo.panedeck.extra.HOME_PACKAGE"
    const val EXTRA_RUNTIME_PROTOCOL_VERSION = "io.github.jheyzo.panedeck.extra.RUNTIME_PROTOCOL_VERSION"
    const val EXTRA_SLOT_STATES = "io.github.jheyzo.panedeck.extra.SLOT_STATES"
    const val EXTRA_LAUNCH_INTENT = "io.github.jheyzo.panedeck.extra.LAUNCH_INTENT"
    const val EXTRA_LAUNCHER_IDENTITY = "io.github.jheyzo.panedeck.extra.LAUNCHER_IDENTITY"
    const val EXTRA_SYSTEMUI_IDENTITY = "io.github.jheyzo.panedeck.extra.SYSTEMUI_IDENTITY"
    const val EXTRA_TARGET_USER = "io.github.jheyzo.panedeck.extra.TARGET_USER"
    const val EXTRA_TARGET_USER_ID = "io.github.jheyzo.panedeck.extra.TARGET_USER_ID"
    const val EXTRA_BYPASS_MULTI_APP_CHOOSER =
        "io.github.jheyzo.panedeck.extra.BYPASS_MULTI_APP_CHOOSER"
    const val RUNTIME_PROTOCOL_VERSION = 39
    /**
     * Desktops the launcher hook supports (mirrors ColorOsLauncherHook).
     * A third-party default desktop (e.g. Niagara) has no hook, so the tile
     * and toggle flows must not wait for it to answer.
     */
    val SUPPORTED_LAUNCHER_PACKAGES = setOf("com.android.launcher", "com.oplus.launcher")

    fun isSupportedLauncher(packageName: String): Boolean =
        packageName in SUPPORTED_LAUNCHER_PACKAGES
    const val RESULT_HANDLED = 0x4E58
    const val RESULT_NOT_READY = 0

    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val MODULE_PACKAGE = "io.github.jheyzo.panedeck"
    const val ASSISTANT_SCREEN_PACKAGE = "com.coloros.assistantscreen"

    fun queryIntent(targetPackage: String): Intent =
        targetIntent(ACTION_QUERY_WORKSPACE, targetPackage)

    fun setIntent(targetPackage: String, active: Boolean, source: TriggerSource): Intent =
        targetIntent(ACTION_SET_WORKSPACE, targetPackage)
            .putExtra(EXTRA_REQUESTED_ACTIVE, active)
            .putExtra(EXTRA_SOURCE, source.name)

    fun hostSetIntent(active: Boolean): Intent = Intent(ACTION_HOST_SET_REQUEST)
        .setComponent(
            ComponentName(
                MODULE_PACKAGE,
                "io.github.jheyzo.panedeck.trigger.WorkspaceControlBrokerReceiver",
            ),
        )
        .putExtra(EXTRA_REQUESTED_ACTIVE, active)

    fun systemUiSetIntent(context: Context, active: Boolean): Intent? =
        resolveHomePackage(context)?.let { homePackage ->
            val identity = PendingIntent.getBroadcast(
                context,
                1,
                Intent(ACTION_SYSTEMUI_SET_WORKSPACE).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            targetIntent(ACTION_SYSTEMUI_SET_WORKSPACE, homePackage)
                .putExtra(EXTRA_REQUESTED_ACTIVE, active)
                .putExtra(EXTRA_SYSTEMUI_IDENTITY, identity)
        }

    fun assistantScreenSetIntent(context: Context, active: Boolean): Intent {
        val identity = PendingIntent.getBroadcast(
            context,
            2,
            Intent(ACTION_SYSTEMUI_SET_WORKSPACE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return targetIntent(ACTION_SYSTEMUI_SET_WORKSPACE, ASSISTANT_SCREEN_PACKAGE)
            .putExtra(EXTRA_REQUESTED_ACTIVE, active)
            .putExtra(EXTRA_SYSTEMUI_IDENTITY, identity)
    }

    fun launcherAppRequestIntent(context: Context, launchIntent: Intent): Intent {
        val identity = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_LAUNCHER_APP_REQUEST).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return targetIntent(ACTION_LAUNCHER_APP_REQUEST, SYSTEM_UI_PACKAGE)
            .putExtra(EXTRA_LAUNCH_INTENT, Intent(launchIntent))
            .putExtra(EXTRA_LAUNCHER_IDENTITY, identity)
    }

    fun resolveHomePackage(context: Context): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeIf { it.isNotBlank() && it != "android" }
    }

    private fun targetIntent(action: String, targetPackage: String): Intent =
        Intent(action).setPackage(targetPackage)
}
