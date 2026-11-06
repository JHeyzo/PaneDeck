package io.github.jheyzo.panedeck.xposed

import android.util.Log
import io.github.jheyzo.panedeck.logging.NeXtepLog
import io.github.jheyzo.panedeck.launcher.ColorOsLauncherHook
import io.github.jheyzo.panedeck.multiapp.OplusMultiAppHook
import io.github.jheyzo.panedeck.framework.TaskSurfaceCompat
import io.github.jheyzo.panedeck.safety.EmergencyDisable
import io.github.jheyzo.panedeck.safety.FeatureGate
import io.github.jheyzo.panedeck.systemui.SystemUiHook
import io.github.jheyzo.panedeck.systemserver.SystemServerHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class PaneDeckHookEntry : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        NeXtepLog.info("module", "PaneDeckHookEntry loaded; systemServer=${param.isSystemServer}")
        if (param.isSystemServer) {
            log(Log.INFO, "PaneDeck", "system_server module generation loaded")
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log(Log.INFO, "PaneDeck", "system_server starting callback received")
        if (EmergencyDisable.isRequested() ||
            !FeatureGate.SYSTEM_SERVER_PATCHES.defaultEnabled
        ) {
            log(Log.WARN, "PaneDeck", "system_server hooks disabled by safety gate")
            return
        }
        SystemServerHook.install(this, param.classLoader)
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        val packageName = param.packageName
        NeXtepLog.info(
            "dispatcher",
            "Observed package=$packageName",
        )

        if (packageName == "com.android.systemui") {
            if (!EmergencyDisable.isRequested() && FeatureGate.SYSTEM_UI.defaultEnabled) {
                TaskSurfaceCompat.initialize(param.defaultClassLoader)
                SystemUiHook.install(this, param.defaultClassLoader)
            }
            return
        }

        if (packageName == "io.github.jheyzo.panedeck") {
            return
        }

        if (packageName == "com.oplus.multiapp") {
            if (!EmergencyDisable.isRequested() &&
                FeatureGate.LAUNCHER_TRANSFORM.defaultEnabled
            ) {
                OplusMultiAppHook.install(this, param.defaultClassLoader)
            }
            return
        }

        if (EmergencyDisable.isRequested() || !FeatureGate.LAUNCHER_TRANSFORM.defaultEnabled) return

        ColorOsLauncherHook.install(this, packageName, param.defaultClassLoader)
    }
}
