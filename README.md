# wa-container

Two-module system for running a second native WhatsApp instance in an Android managed profile on a
rooted Pixel 9 Pro XL (komodo, Android 16).

## Modules

| Module | Package | Purpose |
|--------|---------|---------|
| `dpc` | `com.fdavids77.wacontainer` | Profile owner DPC + WA2 launcher trampoline |
| `hide` | `com.fdavids77.wahide` | LSPosed hook — removes badged WA from app drawer without suspending it |

## Prerequisites

- Managed profile already created (user 11, "WA2")
- WhatsApp installed in user 11: `pm install-existing --user 11 com.whatsapp`
- Storage isolation and cross-profile notifications verified working
- LSPosed (or similar XposedBridge provider) installed in system mode

## Device-side install sequence

```sh
# Push APKs from your machine first:
#   adb push dpc/build/outputs/apk/release/dpc-release.apk /data/local/tmp/
#   adb push hide/build/outputs/apk/release/hide-release.apk /data/local/tmp/
# Then in an adb shell (root):

pm install /data/local/tmp/dpc-release.apk
pm install --user 11 /data/local/tmp/dpc-release.apk
dpm set-profile-owner --user 11 com.fdavids77.wacontainer/.AdminReceiver
pm grant com.fdavids77.wacontainer android.permission.INTERACT_ACROSS_USERS_FULL
pm install /data/local/tmp/hide-release.apk
```

## Smoke-test order (important)

1. **Test the trampoline first** — tap "WA2" from the launcher. It should open WhatsApp inside
   user 11. If it doesn't, diagnose before proceeding; failures are harder to attribute once the
   hide module is active.

2. **Enable `hide` in LSPosed** — scope it to the launcher package
   (`com.google.android.apps.nexuslauncher` on Pixel). Do **not** scope it to WhatsApp itself;
   the hook runs inside the launcher process.

3. Force-stop the launcher (`am force-stop com.google.android.apps.nexuslauncher`) and re-open
   the app drawer. The badged WA profile icon should be gone; WA user 11 continues running and
   receiving FCM notifications normally.

## Notes

### Why not `setApplicationHidden`?

`setApplicationHidden` suspends the app, killing its FCM connection and stopping background
processes. The LSPosed approach only filters the `LauncherApps.getActivityList` result inside
the launcher process — the app in user 11 stays fully alive.

### Persisting `fw.max_users` across reboots

If you create additional profiles and Android resets `fw.max_users`, add a Magisk `system.prop`
overlay:

```
# /data/adb/modules/<your-module>/system.prop
fw.max_users=4
```

Replace `4` with however many users you need. Apply and reboot.

## Build

CI builds on push to `main` via GitHub Actions (`.github/workflows/build.yml`). APKs are uploaded
as artifacts named `apks`.

For local builds you need a JDK 17+ and the Gradle wrapper jar
(`gradle/wrapper/gradle-wrapper.jar`). If the jar is missing, bootstrap it:

```sh
sdk install gradle 8.11.1   # if sdkman is available
gradle wrapper --gradle-version 8.11.1
```
