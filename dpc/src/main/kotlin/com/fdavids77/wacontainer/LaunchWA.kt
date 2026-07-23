package com.fdavids77.wacontainer

import android.app.Activity
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.widget.Toast

class LaunchWA : Activity() {

    companion object {
        private const val WA_PACKAGE = "com.whatsapp"
        private const val WA_ACTIVITY = "com.whatsapp.Main"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val la = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val um = getSystemService(USER_SERVICE) as UserManager
        val me = Process.myUserHandle()
        val component = ComponentName(WA_PACKAGE, WA_ACTIVITY)

        var launched = false
        var checked = 0

        try {
            val profiles = um.userProfiles
            for (profile in profiles) {
                if (profile == me) continue
                checked++
                try {
                    la.startMainActivity(component, profile, null, null)
                    launched = true
                    break
                } catch (e: SecurityException) {
                    Log.w("LaunchWA", "startMainActivity denied for user $profile", e)
                }
            }
        } catch (e: Exception) {
            Log.e("LaunchWA", "Profile enumeration failed", e)
        }

        if (!launched) {
            Toast.makeText(
                this,
                "Launch failed ($checked profiles checked)",
                Toast.LENGTH_SHORT
            ).show()
        }

        finish()
    }
}
