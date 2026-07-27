#!/system/bin/sh
# Launched by Magisk at late_start service stage after each boot.
# Starts watch.sh detached so it persists after this script returns.
MODDIR=${0%/*}
( "$MODDIR/watch.sh" ) &
