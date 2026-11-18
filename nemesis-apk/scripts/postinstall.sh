#!/system/bin/sh
# Runs the APK install once right after NEMESIS installs this package.
case "$0" in
    */scripts/*) pkg_dir=${0%/scripts/*} ;;
    *) pkg_dir=/data/nemesis/apps/k8np.install ;;
esac
sh "$pkg_dir/bin/install.sh"
exit 0
