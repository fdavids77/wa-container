# wa-container

Runs multiple native (unmodified, Play-updatable) WhatsApp instances on one rooted Pixel.
Each lives in its own Android **CLONE profile** with a separate UID and data directory.
Clone-profile apps are invisible to the Pixel launcher, so each gets a trampoline launcher
icon. Those icons are installed into **Private Space** (user 10) — Android's own
biometric/PIN lock gates access, no custom auth code required.

**Verified on:** Pixel 9 Pro XL (komodo), Android 16, Magisk 30.6, LSPosed JingMatrix/Vector fork.

---

## Repo layout

| Module | Purpose |
|--------|---------|
| `dpc/` | `LaunchWA` trampoline, `<activity-alias>` launcher icons, adaptive icon assets. `AdminReceiver` is vestigial from an earlier managed-profile approach and unused. |
| `hide/` | LSPosed drawer-filter module. Unused in the current design — clone-profile apps are invisible to the launcher by default. |

---

## 1. Prerequisites

- Rooted Pixel with **Magisk** (tested 30.6)
- **LSPosed JingMatrix/Vector fork** — the original SIGILLs on Android 16; the Vector fork does not
- **adb** + Android SDK build-tools (for `apksigner` and `aapt2`)
- **Private Space** already set up (Settings → Safety & privacy → Private Space)
- LSPosed module **[icepony/AlwaysCreateUser](https://github.com/icepony/AlwaysCreateUser)**
  (`io.github.icepony.alwayscreateuser`), scope set to **System Framework**

> **Safety note — AlwaysCreateUser:** It hooks `system_server`. A bad hook can bootloop.
> Know how to reach recovery and remove the LSPosed module entry from `/data/adb/lspd/`
> before enabling.

---

## 2. Why AlwaysCreateUser is needed

Stock per-type caps from `dumpsys user`:

```
PRIVATE  mMaxAllowedPerParent 1
MANAGED  mMaxAllowedPerParent 1
CLONE    mMaxAllowed 3   mMaxAllowedPerParent 1
```

User 0 is the only parent, so stock Android allows only one CLONE profile.
AlwaysCreateUser lifts the per-parent cap. On Android 14+ its hook target is
`isCreationOverrideEnabled` in `UserManagerService` (not `canAddMoreProfilesToUser`,
which targets Android 11–13). Confirmed working on Android 16.

**Total-user cap** is separate. Check and raise if needed:

```sh
adb shell su -c "getprop fw.max_users"
adb shell su -c "resetprop fw.max_users 5"   # not persistent across reboot
```

To persist across reboots, add to a Magisk module's `system.prop`:

```
fw.max_users=5
```

---

## 3. Provisioning a WhatsApp instance

Run once per instance. Note the user id printed by `pm create-user`.

```sh
adb shell su -c "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE WA3"
# → Success: created user id 13

adb shell su -c "pm install-existing --user 13 com.whatsapp"
adb shell su -c "am start-user 13"
adb shell su -c "am start --user 13 -n com.whatsapp/com.whatsapp.Main"
# register the phone number in the WhatsApp instance that opens
```

> Use `pm install-existing`, NOT `pm install <apk>`. Installing an APK into a secondary
> user fails with `INSTALL_FAILED_VERSION_DOWNGRADE` when a newer version is already
> installed on the device.

**Verify the instance:**

```sh
adb shell su -c "ls -la /data/user/13/ | grep whatsapp"   # own UID e.g. u13_a382
adb shell su -c "ps -A | grep whatsapp"                    # one live process per instance
adb shell su -c "pm list users"                            # profile shows 'running'
```

Clone profiles need no profile owner and auto-start after reboot.

---

## 4. Adding a launcher icon for an instance

Each instance gets one `<activity-alias>` in `dpc/src/main/AndroidManifest.xml`.
`LaunchWA` reads `target_user_id` from the alias's own component meta-data. When the
launcher starts an alias, `Activity.componentName` resolves to the alias (not the target
activity), so each alias independently targets its own user via `getActivityInfo()`.

**Existing aliases:**

| Alias | Label | User | Background colour |
|-------|-------|------|-------------------|
| `.LaunchWA3` | WA3 | 13 | `#128C7E` teal |
| `.LaunchWA4` | WA4 | 11 | `#075E54` dark green |

**To add a new instance,** copy this block into the manifest:

```xml
<activity-alias
    android:name=".LaunchWA<N>"
    android:targetActivity=".LaunchWA"
    android:label="WA<N>"
    android:icon="@mipmap/ic_wa<n>"
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
<color name="wa<n>_color">#RRGGBB</color>
```

**`res/drawable/ic_wa<n>_background.xml`**
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/wa<n>_color" />
</shape>
```

**`res/mipmap-anydpi-v26/ic_wa<n>.xml`**
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_wa<n>_background" />
    <foreground android:drawable="@drawable/ic_wa_foreground" />
</adaptive-icon>
```

`ic_wa_foreground` is the shared white speech-bubble vector with a phone-shape cutout
(`fillType="evenOdd"` — the background colour shows through the cutout, making icons
distinguishable by colour alone). The background is the only thing that changes per alias.

---

## 5. Build and install

### Signing setup

The same keystore (`wacontainer.jks`, alias `wacontainer`) is used by CI and local
builds so APKs are always interchangeable.

**Local** — add to `~/.gradle/gradle.properties`:

```properties
WA_KEYSTORE_PATH=/home/kali/projects/wa-container/wacontainer.jks
WA_KEYSTORE_PASSWORD=<password>
WA_KEY_ALIAS=wacontainer
WA_KEY_PASSWORD=<password>
```

**CI** — GitHub Actions secrets on `fdavids77/wa-container`:
`KEYSTORE_B64` (base64 of the jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

If neither is configured, the build falls back to AGP's debug key with a visible
`⚠` warning in Gradle output. That APK cannot be installed over a release-signed build.

> **First-time switch from debug-signed build:** uninstall the old copy before installing
> the release-signed one — a signature mismatch cannot be resolved by `-r`:
> ```sh
> adb shell su -c "pm uninstall --user 10 com.fdavids77.wacontainer"
> ```

### Build

```sh
./gradlew :dpc:assembleRelease
```

### Verify the signature before installing

```sh
~/Android/Sdk/build-tools/36.1.0/apksigner verify --print-certs \
  dpc/build/outputs/apk/release/dpc-release.apk | grep -i sha-256
```

> `keytool` cannot read APK v2/v3 signatures — use `apksigner` as shown above.

### Install into Private Space (user 10)

```sh
adb push dpc/build/outputs/apk/release/dpc-release.apk /data/local/tmp/
adb shell su -c "pm install -r --user 10 /data/local/tmp/dpc-release.apk"
adb shell su -c "pm grant --user 10 com.fdavids77.wacontainer android.permission.INTERACT_ACROSS_USERS_FULL"
```

Remove the stray user-0 install (the app has no function there):

```sh
adb shell su -c "pm uninstall --user 0 com.fdavids77.wacontainer"
```

Unlock Private Space and tap the WA3 / WA4 icons to verify.

---

## 6. How the launch works

`LaunchWA` uses two strategies, tried in order:

**Strategy 1 — reflection:** calls `Context.startActivityAsUser(Intent, UserHandle)` and
`UserHandle.of(int)` via reflection. Both are hidden API, reachable because the app holds
`INTERACT_ACROSS_USERS_FULL`. Cross-sibling launch (user 10 → user 13) confirmed working.

**Strategy 2 — root shell fallback** (not yet needed in practice):

```sh
su -c "am start --user <id> -n com.whatsapp/com.whatsapp.Main"
```

**Why `LauncherApps` is not used:**
`LauncherApps.getProfiles()` and `LauncherApps.startMainActivity()` are both gated on
`ROLE_HOME` — the caller must be the active default launcher. This app is not the
launcher. `startMainActivity()` throws a `SecurityException` with the misleading message:

```
Attempt to launch activity without category Intent.CATEGORY_LAUNCHER
ComponentInfo{com.whatsapp/com.whatsapp.Main}
```

`com.whatsapp.Main` is a valid `CATEGORY_LAUNCHER` component — the real cause is the
missing launcher role, not the component. `CrossProfileApps.startMainActivity()` is
also a dead end: it requires `crossProfile=true` in the target's manifest, which
WhatsApp does not declare.

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
| Both icons launch same instance | Alias meta-data not resolving — verify `target_user_id` int in the manifest with `aapt2 dump xmltree` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signature mismatch (debug vs release, or two different debug keys) — verify with `apksigner`, then uninstall before reinstalling |
| Icon shows generic Android robot | Launcher icon cache stale — reboot, or `pm clear com.google.android.apps.nexuslauncher` (**warning:** resets home screen layout) |
| `aapt2 dump badging` shows no `launchable-activity` | Expected — `aapt2` does not report `<activity-alias>` entries. Use `aapt2 dump xmltree --file AndroidManifest.xml <apk>` to inspect the merged manifest |
| Clone profile not created, "too many users" | `fw.max_users` cap hit — `resetprop fw.max_users 5`; see §2 for persistence |
| Only one CLONE profile allowed | AlwaysCreateUser not active or not scoped to System Framework |

---

## 8. Why not X

**APK cloning / repackaging:** repackaging breaks the Play Integrity signature on every
WhatsApp update; must redo on every update.

**VirtualApp / BlackBox app-level virtualisation:** guest apps fail Play Integrity checks
from inside a virtual environment.

**Managed profile (DPC):** creates a "Work" tab in the Pixel launcher exposing every app
in the profile. Worse: the profile-owner package is permanently protected —
`pm uninstall` returns `DELETE_FAILED_DEVICE_POLICY_MANAGER`, components inside the
profile cannot be disabled, and `dpm remove-active-admin` only works on `testOnly`
builds. A signed release build as profile owner is permanently pinned. Clone profiles
have none of these constraints.

**Hooking NexusLauncher to render clone-profile apps:** the launcher has internal
support (`UserType.CLONED`, `BadgeProvider.BadgeType.CLONE`, `ic_clone_app_badge`) but
something upstream excludes clone profiles from enumeration. Not pursued —
invisibility is actually desirable here.

**`LauncherApps` / `CrossProfileApps`:** see §6.
