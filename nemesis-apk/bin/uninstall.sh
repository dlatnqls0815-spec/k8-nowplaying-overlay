#!/system/bin/sh
# Removes the K8 Now Playing app (io.github.k8nowplaying). Run by preuninstall when NEMESIS
# uninstalls k8np.install; up to 0.4.5 the installer had no such hook and left the app behind.
# Plain "pm uninstall" first; only if that fails, retry with sys.boot_completed=0 around it
# (same guard as the installer) and always put it back.
app=io.github.k8nowplaying
log_dir=/data/nemesis/logs/k8np.install
mkdir -p "$log_dir"
log="$log_dir/uninstall.log"
say() { echo "$1"; echo "$(date) $1" >> "$log"; }

if [ -z "$(pm path "$app" 2>/dev/null)" ]; then
    say "already removed ($app not installed)"
    exit 0
fi

am force-stop "$app" >/dev/null 2>&1
out=$(pm uninstall "$app" 2>&1)
case "$out" in
    *Success*) ;;
    *)
        old=$(getprop sys.boot_completed)
        if [ "$old" = "1" ]; then
            trap 'setprop sys.boot_completed 1' EXIT INT TERM HUP
            setprop sys.boot_completed 0
            out2=$(pm uninstall "$app" 2>&1)
            setprop sys.boot_completed 1
            [ "$(getprop sys.boot_completed)" = "1" ] || setprop sys.boot_completed 1
            out="$out / retry: $out2"
        fi
        ;;
esac

if [ -z "$(pm path "$app" 2>/dev/null)" ]; then
    say "uninstalled K8 Now Playing"
else
    say "uninstall failed: $out"
    exit 1
fi
