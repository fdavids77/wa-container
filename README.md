# wa-container

Runs multiple native (unmodified, Play-updatable) WhatsApp instances on one rooted Pixel.
Each instance lives in its own Android **CLONE profile** with a separate UID and data
directory. Clone-profile apps are invisible to the Pixel launcher, so each gets a trampoline
launcher icon. Those icons are installed into **Private Space** (user 10) — Android's own
biometric/PIN gate covers them, no custom auth code required.

**Verified on:** Pixel 9 Pro XL (komodo), Android 16, Magisk 30.6, LSPosed JingMatrix/Vector fork.

---

## Repo layout

| Module | Purpose |
|--------|---------|
| `dpc/` | `LaunchWA` trampoline activity, `<activity-alias>` launcher icons, adaptive icon assets. `AdminReceiver` is vestigial from an earlier managed-profile approach and unused. |
| `hide/` | LSPosed module that filtered a package from the launcher drawer. Unused in the current design — clone-profile apps are already invisible to the launcher by default. |

---

## 1. Prerequisites

- Rooted Pixel with **Magisk** (tested 30.6)
- **LSPosed JingMatrix/Vector fork** — the original LSPosed SIGILLs on Android 16; the Vector fork does not
- **adb** + Android SDK build-tools (for `apksigner` and `aapt2`)
- **Private Space** already set up on the device (Settings → Safety & privacy → Private Space)
- LSPosed module: **[icepony/AlwaysCreateUser](https://github.com/icepony/AlwaysCreateUser)**
  (ID `io.github.icepony.alwayscreateuser`), scope set to **System Framework**

---

## 2. Why AlwaysCreateUser is required

Stock per-type caps from `dumpsys user`:

```
CLONE  mMaxAllowed 3  mMaxAllowedPerParent 1
```

User 0 is the only parent, so without intervention only one CLONE profile can exist.
AlwaysCreateUser lifts the per-parent cap. On Android 14+ its hook target is
`isCreationOverrideEnabled` in `UserManagerService` (not `canAddMoreProfilesToUser`,
which targets Android 11–13).

The total-user cap is separate. Check it and raise if needed:

```sh
adb shell su -c "getprop fw.max_users"
adb shell su -c "resetprop fw.max_users 5"   # not persistent across reboot
```

To persist across reboots, add to a Magisk module's `system.prop`:

```
fw.max_users=5
```

> **Safety note:** AlwaysCreateUser hooks `system_server`. A bad hook can bootloop.
> Know how to reach recovery and remove the hook before installing:
> the LSPosed module entry lives in `/data/adb/lspd/`.

---

## 3. Provisioning a WhatsApp instance

Run once per instance. Replace `<id>` with the user id printed by `pm create-user`.

```sh
adb shell su -c "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE WA3"
# → Success: created user id 13   (note this number)

adb shell su -c "pm install-existing --user 13 com.whatsapp"
adb shell su -c "am start-user 13"
adb shell su -c "am start --user 13 -n com.whatsapp/com.whatsapp.Main"
# register the phone number in the instance that opens
```

> Use `pm install-existing`, not `pm install <apk>`. Installing an APK into a secondary
> user fails with `INSTALL_FAILED_VERSION_DOWNGRADE` when a newer WhatsApp is already
> on the device.

**Verify the instance is running:**

```sh
adb shell su -c "ls -la /data/user/13/ | grep whatsapp"   # own UID, e.g. u13_a382
adb shell su -c "ps -A | grep whatsapp"                    # one live process per instance
adb shell su -c "pm list users"                            # profile shows 'running'
```

Clone profiles need no profile owner and auto-start after reboot.

---

## 4. Adding a launcher alias for an instance

Each instance gets one `<activity-alias>` in `dpc/src/main/AndroidManifest.xml`.
`LaunchWA` reads `target_user_id` from the alias's own component meta-data — when the
launcher starts an alias, `Activity.componentName` resolves to the alias, so each alias
independently targets its own user.

**Existing aliases:**

| Alias | Label | User | Background colour |
|-------|-------|------|-------------------|
| `.LaunchWA3` | WA3 | 13 | `#128C7E` (teal) |
| `.LaunchWA4` | WA4 | 11 | `#075E54` (dark green) |

**To add a new instance**, copy this block into the manifest and fill in `<id>`,
`<Label>`, and `<colorName>`:

```xml
<activity-alias
    android:name=".LaunchWA<Label>"
    android:targetActivity=".LaunchWA"
    android:label="<Label>"
    android:icon="@mipmap/ic_wa<label_lower>"
    android:theme="@android:style/Theme.NoDisplay"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    <meta-data
        android:name="target_user_id"
        android:value="<id>" />
</activity-alias>
```

Then add alongside it:

**`res/values/colors.xml`**
```xml
<color name="<colorName>">#RRGGBB</color>
```

**`res/drawable/ic_wa<label_lower>_background.xml`**
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/<colorName>" />
</shape>
```

**`res/mipmap-anydpi-v26/ic_wa<label_lower>.xml`**
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_wa<label_lower>_background" />
    <foreground android:drawable="@drawable/ic_wa_foreground" />
</adaptive-icon>
```

`ic_wa_foreground` is the shared white speech-bubble-with-phone-cutout vector. The
background colour is the only visual distinction between icons.

---

## 5. Build and install

**Build:**

```sh
./gradlew :dpc:assembleRelease
```

**Verify the signature before installing:**

```sh
~/Android/Sdk/build-tools/36.1.0/apksigner verify --print-certs \
  dpc/build/outputs/apk/release/dpc-release.apk | grep -i sha-256
```

Expected digest: `93fb957b9a0815642481760948587ff980c263fd7875d4699609ae9fe38704c4`

> **Install only local builds.** CI signs with an ephemeral debug key that exists
> nowhere else. Installing a CI artifact produces `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
> on the next local install, which can only be cleared by fully uninstalling the app.
>
> `keytool` cannot read v2/v3-only APK signatures — use `apksigner` as shown above.

**Push and install into Private Space (user 10):**

```sh
adb push dpc/build/outputs/apk/release/dpc-release.apk /data/local/tmp/
adb shell su -c "pm install -r --user 10 /data/local/tmp/dpc-release.apk"
adb shell su -c "pm grant --user 10 com.fdavids77.wacontainer android.permission.INTERACT_ACROSS_USERS_FULL"
```

**Remove the stray user-0 install** (the app has no UI there; Private Space is the only intended home):

```sh
adb shell su -c "pm uninstall --user 0 com.fdavids77.wacontainer"
```

Unlock Private Space and tap the WA3 / WA4 icons to verify.

---

## 6. How the launch works

`LaunchWA.tryReflection()` calls `Context.startActivityAsUser(Intent, UserHandle)` and
`UserHandle.of(int)` via reflection. Both are hidden API, reachable because the app holds
`INTERACT_ACROSS_USERS_FULL`. If reflection fails, `tryRootShell()` falls back to:

```sh
su -c "am start --user <id> -n com.whatsapp/com.whatsapp.Main"
```

The fallback has not been needed; strategy 1 succeeds including cross-sibling
(user 10 → user 13).

**Why `LauncherApps` is not used:**
`LauncherApps.getProfiles()` and `LauncherApps.startMainActivity()` are both gated on
`ROLE_HOME` — the caller must be the active default launcher. Our app is not the launcher.
`startMainActivity()` throws a `SecurityException` with the misleading message:

```
Attempt to launch activity without category Intent.CATEGORY_LAUNCHER
ComponentInfo{com.whatsapp/com.whatsapp.Main}
```

`com.whatsapp.Main` is a valid `LAUNCHER` component — the real cause is the missing
launcher role, not the component. `CrossProfileApps.startMainActivity()` is also a dead
end: it requires `crossProfile=true` in the target app's manifest, which WhatsApp does
not declare.

---

## 7. Troubleshooting

**Filter launch logs:**
```sh
adb logcat -d -s LaunchWA:*
```
Plain `grep LaunchWA` also matches framework component-name noise; use `-s LaunchWA:*`
for clean output.

| Symptom | Likely cause |
|---------|-------------|
| Both icons launch the same instance | Alias meta-data not resolving — check `target_user_id` value in the manifest |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Wrong build signed with CI key — verify with `apksigner`, do a full uninstall/reinstall |
| Icon shows generic Android robot | Launcher icon cache stale — reboot, or `pm clear com.google.android.apps.nexuslauncher` (**warning:** resets home screen layout) |
| `aapt2 dump badging` shows no `launchable-activity` | Expected — `aapt2` does not report activity-aliases as launchable. Inspect the merged manifest with `aapt2 dump xmltree --file AndroidManifest.xml <apk>` instead |
| Clone profile not created, "too many users" error | `fw.max_users` cap hit — `resetprop fw.max_users 5`; see §2 for persistence |
| Can only create one CLONE profile | AlwaysCreateUser not active or not scoped to System Framework |

---

## 8. Why not X

**APK cloning / repackaging** (e.g. App-Cloner): repackaging breaks the Play Integrity
signature check on every WhatsApp update. Needs redoing on every update.

**VirtualApp / BlackBox app-level virtualisation:** guest apps fail Play Integrity checks
from inside a virtual environment.

**Managed profile (DPC):** works technically, but creates a "Work" tab in the Pixel
launcher that exposes every app in the profile. Worse: the profile-owner package is
protected — `pm uninstall` returns `DELETE_FAILED_DEVICE_POLICY_MANAGER`, components
inside the profile cannot be disabled, and `dpm remove-active-admin` only works on
`testOnly` builds. A signed release build as profile owner is permanently pinned.
Clone profiles have none of these constraints.

**Hooking NexusLauncher to render clone-profile apps:** the launcher has internal support
(`UserType.CLONED`, `BadgeProvider.BadgeType.CLONE`, `ic_clone_app_badge`) but something
upstream excludes clone profiles from enumeration. Not pursued — invisibility is actually
desirable here.

**`LauncherApps` / `CrossProfileApps`:** see §6.
