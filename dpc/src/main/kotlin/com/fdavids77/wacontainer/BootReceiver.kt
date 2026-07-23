package com.fdavids77.wacontainer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("ProfileWatcher", "BOOT_COMPLETED — starting ProfileWatcherService")
        ProfileWatcherService.start(context)
    }
}
