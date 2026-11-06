package io.github.jheyzo.panedeck.icons

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.jheyzo.panedeck.R

internal enum class LauncherIconStyle(
    val key: String,
    @StringRes val label: Int,
    @DrawableRes val artwork: Int,
    val aliasName: String,
) {
    DEFAULT("default", R.string.launcher_icon_default,
        R.drawable.ic_launcher_square_artwork, "DefaultLauncher"),
    NIGHT("night", R.string.launcher_icon_night,
        R.drawable.ic_launcher_night_artwork, "NightLauncher");

    companion object {
        val selectable = entries.filter { it != NIGHT }

        fun fromKey(key: String?): LauncherIconStyle? = selectable.firstOrNull { it.key == key }
    }
}
