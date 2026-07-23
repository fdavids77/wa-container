package com.fdavids77.wacontainer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenLockReceiver(private val managedUserIds: Set<Int>) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        Log.i("ProfileWatcher", "SCREEN_OFF — stopping users $managedUserIds")
        for (userId in managedUserIds) {
            runStopUser(userId)
        }
    }

    private fun runStopUser(userId: Int) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "am stop-user -f $userId"))
            val exit = proc.waitFor()
            Log.i("ProfileWatcher", "am stop-user -f $userId exit=$exit")
        } catch (t: Throwable) {
            Log.w("ProfileWatcher", "am stop-user -f $userId failed", t)
        }
    }
}
