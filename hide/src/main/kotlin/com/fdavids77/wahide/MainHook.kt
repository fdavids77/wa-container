package com.fdavids77.wahide

import android.content.pm.LauncherActivityInfo
import android.os.Process
import android.os.UserHandle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TARGET_PACKAGE = "com.whatsapp"
        private val LAUNCHER_PACKAGES = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.teslacoilsw.launcher",
            "app.lawnchair"
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in LAUNCHER_PACKAGES) return

        XposedBridge.log("WA Hide: installing hook in ${lpparam.packageName}")

        XposedHelpers.findAndHookMethod(
            android.content.pm.LauncherApps::class.java,
            "getActivityList",
            String::class.java,
            UserHandle::class.java,
            object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? List<LauncherActivityInfo> ?: return
                    val me = Process.myUserHandle()
                    val filtered = result.filter { info ->
                        if (info.componentName.packageName != TARGET_PACKAGE) return@filter true
                        val keep = info.user == me
                        if (!keep) {
                            XposedBridge.log("WA Hide: filtered ${info.componentName} (user ${info.user})")
                        }
                        keep
                    }
                    if (filtered.size != result.size) param.setResult(filtered)
                }
            }
        )
    }
}
