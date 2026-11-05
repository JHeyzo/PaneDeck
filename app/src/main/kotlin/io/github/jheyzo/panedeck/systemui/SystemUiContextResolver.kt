package io.github.jheyzo.panedeck.systemui

import android.content.Context

object SystemUiContextResolver {
    fun fromHookTarget(target: Any?): Context? = when (target) {
        is Context -> target.applicationContext ?: target
        else -> null
    }
}
