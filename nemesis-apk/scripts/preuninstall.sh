#!/system/bin/sh
# Removes the installed app when NEMESIS uninstalls this package, so nothing is left behind.
case "$0" in
    */scripts/*) pkg_dir=${0%/scripts/*} ;;
    *) pkg_dir=/data/nemesis/apps/k8np.install ;;
esac
sh "$pkg_dir/bin/uninstall.sh"
exit 0
