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

        // Ensure the watcher service is running in the current user (no-op if already started).
        ProfileWatcherService.start(this)
        // Also ensure user-0 service is up. A fresh install leaves the user-0 package in the
        // "stopped" state (no launcher icon there = never manually launched), which blocks
        // BOOT_COMPLETED delivery. Poking startForegroundServiceAsUser from user 10 clears that.
        ProfileWatcherService.ensureRunningInUser0(this)

        // Bring the target user up before launching; launching into a stopped user fails.
        startUserBeforeLaunch(targetUserId)

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

    private fun startUserBeforeLaunch(userId: Int) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "am start-user $userId"))
            val finished = proc.waitFor(3, TimeUnit.SECONDS)
            val exit = if (finished) proc.exitValue() else { proc.destroyForcibly(); -1 }
            Log.i(TAG, "am start-user $userId exit=$exit" + if (!finished) " (timed out)" else "")
        } catch (t: Throwable) {
            Log.w(TAG, "am start-user $userId threw", t)
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
