package io.github.jheyzo.panedeck.framework

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager

/**
 * Ported from Panes Workbench 3.0.5 (yishisanren, Apache License 2.0),
 * via the zealler9560 mirror, with attribution retained. Only the package
 * name changed; the resolution logic is unchanged.
 *
 * Resolves an external (deep-link / VIEW) intent to the target app's own
 * router Activity, without adding CATEGORY_LAUNCHER and without touching
 * the intent's data URI. A system chooser is not a valid target: the
 * resolved activity must belong to the intent's own package and be
 * exported and enabled, otherwise the intent is unresolvable and the
 * caller must fall back or skip the target.
 */
/** A system chooser is not the target app's router Activity. */
internal object WorkspaceIntentResolver {
    fun resolve(manager: PackageManager, source: Intent): Intent? = runCatching {
        val target = source.component?.packageName ?: source.`package` ?: return@runCatching null
        fun valid(info: ActivityInfo) = info.packageName == target && info.exported &&
            info.enabled && info.applicationInfo?.enabled != false
        val info = source.component?.let { manager.getActivityInfo(it, 0).takeIf(::valid) }
            ?: manager.resolveActivity(source, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.takeIf(::valid)
            ?: manager.queryIntentActivities(source, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo }.firstOrNull(::valid)
            ?: return@runCatching null
        Intent(source).setComponent(ComponentName(info.packageName, info.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()
}
