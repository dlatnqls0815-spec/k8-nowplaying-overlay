#!/system/bin/sh
# NEMESIS root has no CAP_CHOWN, so the system-uid service keeps its dalvik cache in a 0700
# "private" subdirectory of this world-writable directory.
data=/data/nemesis/logs/k8np.keys
mkdir -p "$data"
chmod 777 "$data"
exit 0
