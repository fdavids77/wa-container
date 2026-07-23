package com.fdavids77.wacontainer

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast

class LaunchWA : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val la = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val me = Process.myUserHandle()
        var launched = false

        try {
            for (profile in la.profiles) {
                if (profile == me) continue
                val acts = la.getActivityList("com.whatsapp", profile)
                if (acts.isNotEmpty()) {
                    la.startMainActivity(acts[0].componentName, profile, null, null)
                    launched = true
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("LaunchWA", "Failed to launch WhatsApp in managed profile", e)
        }

        if (!launched) {
            Toast.makeText(this, "WhatsApp not found in managed profile", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
