package io.github.jheyzo.panedeck.xposed

import io.github.jheyzo.panedeck.logging.NeXtepLog

object HookGuard {
    inline fun run(feature: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (error: Throwable) {
        NeXtepLog.error(feature, "Hook initialization failed open", error)
        false
    }
}
