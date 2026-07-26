package com.fdavids77.wacontainer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.UserHandle
import android.util.Log

class ProfileWatcherService : Service() {

    companion object {
        private const val TAG = "ProfileWatcher"
        private const val CHANNEL_ID = "wa_container_watcher"
        private const val NOTIFICATION_ID = 1
        private const val PRIVATE_SPACE_USER_ID = 10
        private const val META_TARGET_USER = "target_user_id"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ProfileWatcherService::class.java))
        }

        // Called from LaunchWA (user 10) to ensure the user-0 service instance is running.
        // Without this, the user-0 app stays in the "stopped" state after a fresh install
        // (no launcher icons in user 0 means it is never manually opened, so BOOT_COMPLETED
        // is never delivered to it). startForegroundServiceAsUser reflection is tried first;
        // root shell is the fallback. Either path pulls user 0 out of stopped state.
        fun ensureRunningInUser0(context: Context) {
            try {
                val userHandleOf = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
                val user0 = userHandleOf.invoke(null, 0) as UserHandle
                val intent = Intent(context, ProfileWatcherService::class.java)
                val startFgsAsUser = Context::class.java.getMethod(
                    "startForegroundServiceAsUser",
                    Intent::class.java,
                    UserHandle::class.java
                )
                startFgsAsUser.invoke(context, intent, user0)
                Log.i(TAG, "ensureRunningInUser0: kicked service in user 0 via reflection")
            } catch (t: Throwable) {
                Log.w(TAG, "ensureRunningInUser0: reflection failed, falling back to root", t)
                try {
                    Runtime.getRuntime().exec(arrayOf(
                        "su", "-c",
                        "am start-foreground-service --user 0 -n ${context.packageName}/.ProfileWatcherService"
                    ))
                    Log.i(TAG, "ensureRunningInUser0: kicked service in user 0 via root shell")
                } catch (t2: Throwable) {
                    Log.w(TAG, "ensureRunningInUser0: root shell also failed", t2)
                }
            }
        }
    }

    private var screenLockReceiver: ScreenLockReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        disableAliasesIfNotPrivateSpace()
        val userIds = resolveManagedUserIds()
        Log.i(TAG, "managed user ids: $userIds (${userIds.size})")
        val receiver = ScreenLockReceiver(userIds)
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        screenLockReceiver = receiver
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        screenLockReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Profile Watcher", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Managing WhatsApp profiles"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("WA Container")
            .setContentText("Managing WhatsApp profiles")
            .setSmallIcon(R.drawable.ic_wa_foreground)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // Enumerate activity-aliases (targetActivity != null) that carry a target_user_id meta-data int.
    // MATCH_DISABLED_COMPONENTS is required: in user 0 the aliases are disabled by
    // disableAliasesIfNotPrivateSpace() before this runs, so without that flag the scan
    // returns empty and SCREEN_OFF teardown watches zero users.
    @Suppress("DEPRECATION")
    private fun resolveManagedUserIds(): Set<Int> {
        return try {
            val intFlags = PackageManager.GET_ACTIVITIES or
                           PackageManager.GET_META_DATA or
                           PackageManager.MATCH_DISABLED_COMPONENTS
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(intFlags.toLong())
                )
            } else {
                packageManager.getPackageInfo(packageName, intFlags)
            }
            pi.activities
                ?.filter { it.targetActivity != null }
                ?.mapNotNull { info ->
                    info.metaData?.getInt(META_TARGET_USER, 0)?.takeIf { id -> id > 0 }
                }
                ?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "could not resolve managed user ids", e)
            emptySet()
        }
    }

    // Disable the launcher aliases when running as any user other than Private Space (user 10),
    // so icons only appear inside Private Space and not in the main user's app drawer.
    @Suppress("DEPRECATION")
    private fun disableAliasesIfNotPrivateSpace() {
        val userId = myUserId()
        if (userId == PRIVATE_SPACE_USER_ID) return
        Log.i(TAG, "running as user $userId (not Private Space $PRIVATE_SPACE_USER_ID) — disabling launcher aliases")
        try {
            val pi = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
            )
            pi.activities?.filter { it.targetActivity != null }?.forEach { info ->
                val cn = ComponentName(packageName, info.name)
                packageManager.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(TAG, "disabled alias ${cn.shortClassName}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not disable aliases", e)
        }
    }

    private fun myUserId(): Int = try {
        UserHandle::class.java.getDeclaredMethod("myUserId").invoke(null) as Int
    } catch (e: Exception) {
        Log.w(TAG, "UserHandle.myUserId() reflection failed", e)
        -1
    }
}
