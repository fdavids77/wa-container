package com.fdavids77.wacontainer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.Toast

class LaunchWA : Activity() {

    companion object {
        private const val TARGET_USER_ID = 13
        private const val WA_PKG = "com.whatsapp"
        private const val WA_ACTIVITY = "com.whatsapp.Main"
        private const val TAG = "LaunchWA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launched = tryReflection() || tryRootShell()

        if (!launched) {
            Toast.makeText(this, "Launch failed — check logcat (tag LaunchWA)", Toast.LENGTH_LONG).show()
        }

        finish()
    }

    private fun buildIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(WA_PKG, WA_ACTIVITY))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun tryReflection(): Boolean {
        return try {
            val userHandleOf = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
            val targetUser = userHandleOf.invoke(null, TARGET_USER_ID) as UserHandle

            val startActivityAsUser = android.content.Context::class.java.getMethod(
                "startActivityAsUser",
                Intent::class.java,
                UserHandle::class.java
            )
            startActivityAsUser.invoke(this, buildIntent(), targetUser)

            Log.i(TAG, "strategy 1 (startActivityAsUser reflection) succeeded for user $TARGET_USER_ID")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "strategy 1 (startActivityAsUser reflection) failed", t)
            false
        }
    }

    private fun tryRootShell(): Boolean {
        return try {
            val cmd = "am start --user $TARGET_USER_ID -n $WA_PKG/$WA_ACTIVITY"
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
            Log.w(TAG, "strategy 2 (root shell) failed", t)
            false
        }
    }
}
