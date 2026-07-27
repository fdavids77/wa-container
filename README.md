# wa-container

Five native, unmodified, Play-updatable WhatsApp instances on one rooted Pixel.
Each lives in its own Android **CLONE profile** with a separate UID and data directory.
Launcher icons live in **Private Space** (user 10) — Android's biometric/PIN gate controls
access. Recents cards are hidden by an LSPosed hook so WhatsApp doesn't clutter the task
switcher.

**Verified on:** Pixel 8 (blazer), Android 16, Magisk 30.7.

---

## Components

| # | Component | Role |
|---|-----------|------|
| 1 | CLONE profiles (users 11–15) | Each holds one registered WhatsApp |
| 2 | **AlwaysCreateUser** LSPosed module | Lifts the 1-clone-per-parent cap so >1 clone profile can exist |
| 3 | **dpc** APK | Trampoline launcher installed in Private Space; `am start-user` then `startActivityAsUser` |
| 4 | **hideRecent** LSPosed module | Hides all WhatsApp recents cards via `isVisibleRecentTask` hook |

---

## 1. Prerequisites

- Rooted Pixel with **Magisk** (tested 30.7)
- **LSPosed JingMatrix/Vector fork** — the original SIGILLs on Android 16; the Vector fork does not
- **adb** + Android SDK build-tools (for `apksigner`)
- **Private Space** already set up (Settings → Safety & privacy → Private Space)
- LSPosed module **[AlwaysCreateUser](https://github.com/icepony/AlwaysCreateUser)**
  (`io.github.icepony.alwayscreateuser`), scope → **System Framework**
- LSPosed module **[hideRecent](https://github.com/Young-Lord/hideRecent)**
  (`com.young_lord.hiderecent` or per release), scope → **System Framework**

> **AlwaysCreateUser hook (Android 14+):** patches `isCreationOverrideEnabled` in
> `UserManagerService`. A bad hook can bootloop — know how to reach recovery and remove
> the module from `/data/adb/lspd/` before enabling.

---

## 2. One-time device settings

### 2a. Magisk Multiuser Mode (CRITICAL)

In the Magisk app: **Settings → Superuser → Multiuser mode**, set to
**"Device owner and profile users"** (or equivalent non-default option).

The default value ("Device owner only") silently **denies su to apps running in secondary
or profile users**, including Private Space. With the default, tapping a trampoline icon
runs `am start-user` as the app user rather than root — the command fails with exit 13,
the clone profile stays stopped, and the icon does nothing.

### 2b. Disable per-clone lockscreen

Each clone profile has its own lock by default, which prompts for the device PIN on every
cross-profile launch. Private Space is already the security gate, so disable the lock for
each clone user:

```sh
# Run for each user id (11–15):
adb shell su -c "locksettings set-disabled --user 11 true"
adb shell su -c "locksettings set-disabled --user 12 true"
adb shell su -c "locksettings set-disabled --user 13 true"
adb shell su -c "locksettings set-disabled --user 14 true"
adb shell su -c "locksettings set-disabled --user 15 true"
```

### 2c. Screensaver

Disable the screen saver (Settings → Display → Screen saver → Off). This is only relevant
if you run a lock-based profile watcher; the shipped design has no watcher, so this is
optional but still good practice.

---

## 3. Provisioning an instance

> Requires AlwaysCreateUser active. Without it, Android allows only one CLONE profile per
> parent (user 0).

Run once per instance. Note the user id printed by `pm create-user`.

```sh
# Example: a sixth instance (substitute the printed id for <id>)
adb shell su -c "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE WA6"
# → Success: created user id 16

adb shell su -c "pm install-existing --user <id> com.whatsapp"
adb shell su -c "am start-user <id>"
adb shell su -c "am start --user <id> -n com.whatsapp/com.whatsapp.Main"
# Register the phone number in the WhatsApp screen that opens.

adb shell su -c "locksettings set-disabled --user <id> true"
```

> **Use `pm install-existing`, NOT `pm install <apk>`.** Installing an APK into a
> secondary user fails with `INSTALL_FAILED_VERSION_DOWNGRADE` when a newer version is
> already on the device.

**Verify:**

```sh
adb shell su -c "ls /data/user/<id>/ | grep whatsapp"   # own UID e.g. u16_a382
adb shell su -c "ps -A | grep whatsapp"                  # one process per running instance
adb shell su -c "pm list users"                          # user shows as 'running'
```

**fw.max_users:** Stock Android may cap total users. Check and raise if needed:

```sh
adb shell su -c "getprop fw.max_users"
adb shell su -c "resetprop fw.max_users 9"   # not persistent; add to a Magisk module's system.prop
```

---

## 4. Adding a launcher icon for an instance

Each instance gets one `<activity-alias>` in `dpc/src/main/AndroidManifest.xml`.

**Existing aliases (users 11–15):**

| Alias | Label | User | Icon background |
|-------|-------|------|----------------|
| `.LaunchWA1` | WA1 | 11 | `#E53935` red |
| `.LaunchWA2` | WA2 | 12 | `#F57F17` amber |
| `.LaunchWA3` | WA3 | 13 | `#00897B` teal |
| `.LaunchWA4` | WA4 | 14 | `#1565C0` blue |
| `.LaunchWA5` | WA5 | 15 | `#6A1B9A` purple |

**To add a new instance**, add this block to the manifest:

```xml
<activity-alias
    android:name=".LaunchWA6"
    android:targetActivity=".LaunchWA"
    android:label="WA6"
    android:icon="@mipmap/ic_wa6"
    android:theme="@android:style/Theme.NoDisplay"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    <meta-data
        android:name="target_user_id"
        android:value="16" />
</activity-alias>
```

Then add alongside it:

**`res/values/colors.xml`**
```xml
<color name="wa6_color">#RRGGBB</color>
```

**`res/drawable/ic_wa6_background.xml`**
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/wa6_color" />
</shape>
```

**`res/mipmap-anydpi-v26/ic_wa6.xml`**
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_wa6_background" />
    <foreground android:drawable="@drawable/ic_wa_foreground" />
</adaptive-icon>
```

`ic_wa_foreground` is a shared white speech-bubble vector with a phone-shape cutout
(`fillType="evenOdd"`); the background colour shows through the cutout, making icons
distinguishable by colour alone. Build and reinstall into user 10 after.

---

## 5. Hiding WhatsApp from recents

Install the **hideRecent** LSPosed module, scope → **System Framework**, then:

1. Open the hideRecent app → add **com.whatsapp** → set mode to **Hide task**.
2. Reboot (the `isVisibleRecentTask` hook applies at boot; a soft-restart is not enough).

The hook cannot distinguish between users, so it also hides user-0 WhatsApp from recents
if installed there. Acceptable — the clone instances are the only ones in active use.

> **Why not force-stop or `am task remove`?** Neither reliably clears the recents card.
> The `isVisibleRecentTask` hook is the only reliable approach without a custom launcher.

---

## 6. Build and install dpc

### Signing

The release keystore (`wacontainer.jks`, alias `wacontainer`) is used for all builds.

**Local** — add to `~/.gradle/gradle.properties`:
```properties
WA_KEYSTORE_PATH=/home/kali/projects/wa-container/wacontainer.jks
WA_KEYSTORE_PASSWORD=<password>
WA_KEY_ALIAS=wacontainer
WA_KEY_PASSWORD=<password>
```

**CI** — GitHub Actions secrets: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Without either, AGP falls back to a debug key (visible `⚠` in Gradle output). A debug-signed
APK cannot be installed over a release-signed build — uninstall first:

```sh
adb shell su -c "pm uninstall --user 10 com.fdavids77.wacontainer"
```

### Build

```sh
./gradlew :dpc:assembleRelease
```

### Verify signature

```sh
~/Android/Sdk/build-tools/36.1.0/apksigner verify --print-certs \
  dpc/build/outputs/apk/release/dpc-release.apk | grep -i sha-256
# Expected: 5665fbc234080a635c4b6042e60aabe2ad88a67abf554c4680ae1b2b5d7fa9ce
```

> `keytool` cannot read APK v2/v3 signatures — use `apksigner`.

### Install

Install into **user 10** (Private Space — where the launcher icons live).
User 0 install is not required; the Magisk module and locksettings handle everything else.

```sh
adb push dpc/build/outputs/apk/release/dpc-release.apk /data/local/tmp/
adb shell su -c "pm install -r --user 10 /data/local/tmp/dpc-release.apk"
adb shell su -c "pm grant --user 10 com.fdavids77.wacontainer android.permission.INTERACT_ACROSS_USERS_FULL"
```

Unlock Private Space and tap WA1–WA5 to verify.

---

## 7. How the launch works

When a trampoline icon is tapped in Private Space:

1. **`LaunchWA.onCreate()`** reads `target_user_id` from the alias's own `ActivityInfo`
   meta-data (`getActivityInfo(componentName, GET_META_DATA)`). Each alias carries its own
   meta-data, so the correct user id is resolved regardless of which icon was tapped.

2. **`disableAliasesInUser0()`** hides the WA Container icons from the main app drawer by
   calling `PackageManager.setComponentEnabledSettingAsUser` (hidden API, accessible via
   reflection with `INTERACT_ACROSS_USERS_FULL`; falls back to `pm disable --user 0`).

3. **`ensureUserRunning()`** starts the target clone profile:
   - Runs `am start-user <id>` via `su` (root shell). This is synchronous-ish and waits
     until the user reaches at least `RUNNING_LOCKED`. **Requires Magisk Multiuser Mode
     set to allow su in profile users** (see §2a) — without this, exit code is 13 and
     the profile stays stopped.
   - Polls `am get-started-user-state <id>` every 500 ms for up to 8 s until the output
     contains `RUNNING_UNLOCKED`. Proceeds anyway on timeout.

4. **Launch:** calls `Context.startActivityAsUser(Intent, UserHandle)` via reflection
   (hidden API; `INTERACT_ACROSS_USERS_FULL` is the gate). Falls back to
   `su -c "am start --user <id> -n com.whatsapp/com.whatsapp.Main"`.

**Why not `LauncherApps`?**
`LauncherApps.getProfiles()` and `startMainActivity()` require `ROLE_HOME` — the caller
must be the active default launcher. `CrossProfileApps.startMainActivity()` requires
`crossProfile=true` in the target manifest, which WhatsApp does not declare. Neither is
usable here.

---

## 8. Troubleshooting

```sh
# Live launch log:
adb logcat -s LaunchWA:*
```

| Symptom | Cause | Fix |
|---------|-------|-----|
| Icons tap but nothing happens; `start-user exit=13` in logcat | Magisk Multiuser Mode is "Device owner only" — su denied in Private Space | Magisk → Settings → Superuser → Multiuser mode → change to include profile users |
| Device PIN prompted on every launch | Clone-profile lockscreen still enabled | `locksettings set-disabled --user <id> true` for each id |
| WhatsApp still appears in recents | hideRecent not configured, or not rebooted after setting | Set com.whatsapp to "Hide task" in hideRecent, then reboot |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when installing dpc | Signature mismatch (debug vs release key) | Uninstall existing: `pm uninstall --user 10 com.fdavids77.wacontainer` |
| `aapt2 dump badging` shows no launchable-activity | Expected — `aapt2` does not enumerate `<activity-alias>`. Use `aapt2 dump xmltree --file AndroidManifest.xml <apk>` |
| "Too many users" on `pm create-user` | `fw.max_users` cap | `resetprop fw.max_users 9` (see §3) |
| Only one CLONE profile allowed | AlwaysCreateUser not active or not scoped to System Framework | |

---

## 9. Why not X

**APK cloning / repackaging:** breaks Play Integrity on every WhatsApp update; must redo each time.

**VirtualApp / BlackBox:** guest apps fail Play Integrity from inside a virtual environment.

**Managed profile (DPC / Work profile):** creates a permanent "Work" tab in the Pixel launcher
exposing every app in the profile. The profile-owner APK is protected — `pm uninstall`
returns `DELETE_FAILED_DEVICE_POLICY_MANAGER`; `dpm remove-active-admin` only works on
`testOnly` builds. A release-signed build as profile owner is permanently pinned. Clone
profiles have none of these constraints.

**Custom recents watcher (force-stop / am task remove / stop-user on screen lock):**
force-stopping WhatsApp or running `am task remove` does not reliably clear the recents
card. The `isVisibleRecentTask` hook is the only approach that works without a custom
launcher or Shizuku. The Magisk shell-loop watcher was prototyped (`deprecated/waclone-module/`)
but abandoned once hideRecent was confirmed to solve the recents problem cleanly.

**`LauncherApps` / `CrossProfileApps`:** see §7.

---

## Repo layout

| Path | Purpose |
|------|---------|
| `dpc/` | LaunchWA trampoline + five launcher aliases + adaptive icon assets. `AdminReceiver` is vestigial from an earlier managed-profile experiment. |
| `hide/` | LSPosed drawer-filter module. Unused — clone-profile apps are invisible to the launcher by default, so no filtering is needed. |
| `deprecated/` | Abandoned experiments: `waclone-module/` (Magisk shell-loop watcher, superseded by hideRecent) and `waclone.zip`. |
