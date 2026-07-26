package com.fdavids77.wacontainer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import java.util.concurrent.TimeUnit

class LaunchWA : Activity() {

    companion object {
        private const val DEFAULT_USER_ID = 13
        private const val WA_PKG = "com.whatsapp"
        private const val WA_ACTIVITY = "com.whatsapp.Main"
        private const val TAG = "LaunchWA"
        private const val META_TARGET_USER = "target_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUserId = resolveTargetUserId()

        // Hide WA Container icons from user 0's main app drawer. Idempotent.
        disableAliasesInUser0()

        // Ensure the target clone profile is running before launching into it.
        // am start-user is a no-op if already running, so this is safe every time.
        ensureUserRunning(targetUserId)

        val launched = tryReflection(targetUserId) || tryRootShell(targetUserId)

        if (!launched) {
            Toast.makeText(this, "Launch failed — check logcat (tag LaunchWA)", Toast.LENGTH_LONG).show()
        }

        finish()
    }

    private fun resolveTargetUserId(): Int {
        // When launched through an <activity-alias>, componentName is the alias component.
        // getActivityInfo() returns the alias's own ActivityInfo, including its meta-data.
        val src = componentName
        return try {
            val ai = packageManager.getActivityInfo(src, PackageManager.GET_META_DATA)
            val id = ai.metaData?.getInt(META_TARGET_USER, DEFAULT_USER_ID) ?: DEFAULT_USER_ID
            Log.i(TAG, "target user id: $id (from ${src.shortClassName})")
            id
        } catch (e: Exception) {
            Log.w(TAG, "could not read $META_TARGET_USER from $src, using default $DEFAULT_USER_ID", e)
            DEFAULT_USER_ID
        }
    }

    // Disable the launcher aliases in user 0 so the WA Container icons don't appear in
    // the main app drawer. LaunchWA always runs in user 10 (Private Space), so the aliases
    // stay enabled there. Uses setComponentEnabledSettingAsUser reflection
    // (INTERACT_ACROSS_USERS_FULL grants this); falls back to root shell pm-disable.
    @Suppress("DEPRECATION")
    private fun disableAliasesInUser0() {
        try {
            val pi = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
            )
            val aliases = pi.activities?.filter { it.targetActivity != null } ?: return
            val userHandleOf = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
            val user0 = userHandleOf.invoke(null, 0) as UserHandle
            val setEnabledAsUser = PackageManager::class.java.getMethod(
                "setComponentEnabledSettingAsUser",
                ComponentName::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                UserHandle::class.java
            )
            for (info in aliases) {
                val cn = ComponentName(packageName, info.name)
                setEnabledAsUser.invoke(
                    packageManager, cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                    user0
                )
                Log.i(TAG, "disabled alias ${cn.shortClassName} in user 0")
            }
        } catch (e: Exception) {
            Log.w(TAG, "disableAliasesInUser0: reflection failed, trying root shell", e)
            try {
                @Suppress("DEPRECATION")
                val pi = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
                )
                val cmds = pi.activities
                    ?.filter { it.targetActivity != null }
                    ?.joinToString("; ") { info -> "pm disable --user 0 ${packageName}/${info.name}" }
                    ?: return
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmds))
                proc.waitFor(3, TimeUnit.SECONDS)
                Log.i(TAG, "disableAliasesInUser0: done via root shell")
            } catch (t: Throwable) {
                Log.w(TAG, "disableAliasesInUser0: root shell also failed", t)
            }
        }
    }

    private fun ensureUserRunning(userId: Int) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "am start-user $userId"))
            val finished = proc.waitFor(4, TimeUnit.SECONDS)
            val exit = if (finished) proc.exitValue() else { proc.destroyForcibly(); -1 }
            Log.i(TAG, "ensuring user $userId running (start-user exit=$exit)" +
                    if (!finished) " (timed out)" else "")
        } catch (t: Throwable) {
            Log.w(TAG, "ensuring user $userId running threw", t)
        }
    }

    private fun buildIntent(userId: Int): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(WA_PKG, WA_ACTIVITY))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun tryReflection(userId: Int): Boolean {
        return try {
            val userHandleOf = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
            val targetUser = userHandleOf.invoke(null, userId) as UserHandle

            val startActivityAsUser = android.content.Context::class.java.getMethod(
                "startActivityAsUser",
                Intent::class.java,
                UserHandle::class.java
            )
            startActivityAsUser.invoke(this, buildIntent(userId), targetUser)

            Log.i(TAG, "strategy 1 (startActivityAsUser reflection) succeeded for user $userId")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "strategy 1 (startActivityAsUser reflection) failed for user $userId", t)
            false
        }
    }

    private fun tryRootShell(userId: Int): Boolean {
        return try {
            val cmd = "am start --user $userId -n $WA_PKG/$WA_ACTIVITY"
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exit = proc.waitFor()
            if (exit == 0) {
                Log.i(TAG, "strategy 2 (root shell) succeeded: $cmd")
                true
            } else {
                val err = proc.errorStream.bufferedReader().readText().trim()
                Log.w(TAG, "strategy 2 (root shell) exit=$exit err=$err")
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "strategy 2 (root shell) failed for user $userId", t)
            false
        }
    }
}
