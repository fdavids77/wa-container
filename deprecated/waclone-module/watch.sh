#!/system/bin/sh
##############################################################################
# waclone — WhatsApp clone-profile lock/unlock watcher
#
# USERS: space-separated list of Android user IDs to manage.
#   Edit this line (and re-flash, or edit in place at
#   /data/adb/modules/waclone/watch.sh) when adding or removing instances.
USERS="11 12 13 14 15"
#
# LOCK SIGNAL: mDreamingLockscreen from `dumpsys window`.
#   REQUIREMENT: the screen saver / Daydream must be DISABLED (Settings →
#   Display → Screen saver → Off). When a dream is active, mShowingDream=true
#   is set at the same time as mDreamingLockscreen=true, making the reading
#   ambiguous and causing premature stop-user calls.
#
#   FALLBACK if screen saver cannot be disabled:
#     STATE=$(dumpsys power | grep -m1 mWakefulness | grep -o 'Awake\|Asleep')
#     treat "Asleep" as locked, "Awake" as unlocked.
#   Replace the STATE= line below with the fallback and adjust the comparisons
#   ("true"/"false" → "Asleep"/"Awake").
##############################################################################

# Wait for boot to fully settle before starting the loop.
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 20

LOCKED=0
while true; do
  STATE="$(dumpsys window 2>/dev/null | grep -m1 'mDreamingLockscreen' | grep -o 'true\|false')"
  if [ "$STATE" = "true" ] && [ "$LOCKED" = "0" ]; then
    for u in $USERS; do am stop-user -f "$u"; done
    LOCKED=1
    log -t waclone "locked - stopped $USERS"
  elif [ "$STATE" = "false" ] && [ "$LOCKED" = "1" ]; then
    for u in $USERS; do am start-user "$u"; done
    LOCKED=0
    log -t waclone "unlocked - started $USERS"
  fi
  sleep 2
done
