#!/system/bin/sh
# Shows the last helper log line (NEMESIS Manager shows script output as a toast).
log=/data/nemesis/logs/k8np.keys/keys.log
last=
if [ -f "$log" ]; then
    while read -r line; do
        last=$line
    done < "$log"
fi
echo "${last:-no log yet}"
