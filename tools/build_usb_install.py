#!/usr/bin/env python3
"""Build a NEMESIS USB package that installs the K8 Now Playing APK (k8np.install).

Writes private/nemesis-apk/k8np.install-<ver>.zip, a USB layout in private/nemesis-apk/usb/ and a
zip of that layout for sharing. Offline only; installs nothing.
"""
import argparse
import hashlib
import io
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "nemesis-apk"
OUT = ROOT / "private" / "nemesis-apk"
PACKAGE_ID = "k8np.install"
SHORT_DESC = "K8 Now Playing overlay app installer"
DETAILED_DESC = ("Installs the bundled K8 Now Playing overlay APK with pm install -r (sys.boot_completed is set "
                 "to 0 only during the install and always restored). Launch to install again and see the result.")
ZIP_TIME = (2026, 11, 18, 0, 0, 0)


def descriptor(version):
    if len(SHORT_DESC) > 64 or len(DETAILED_DESC) > 256:
        raise ValueError("NEMESIS description limit exceeded")
    return "\n".join([
        "[nemesis]", "version=1.1", "id=" + PACKAGE_ID, "name=K8 Now Playing Installer",
        "type=application", "package-version=" + version,
        'short-desc="' + SHORT_DESC + '"', 'detailed-desc="' + DETAILED_DESC + '"',
        "os=any", "models=any", "postinstall=scripts/postinstall.sh", "",
        "[app.0]", "kind=sh", "path=bin/install.sh", "uid=root", "",
    ])


def entry(name, mode):
    info = zipfile.ZipInfo(name, ZIP_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = mode << 16
    return info


def script(path):
    text = path.read_bytes().decode("utf-8")
    if not text.startswith("#!/system/bin/sh\n") or "\r" in text:
        raise ValueError(str(path) + ": must be an LF-only /system/bin/sh script")
    return text


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("apk")
    p.add_argument("--version", required=True)
    a = p.parse_args(argv)
    apk = Path(a.apk).read_bytes()
    with zipfile.ZipFile(io.BytesIO(apk)) as z:
        names = z.namelist()
        if "AndroidManifest.xml" not in names or not any(n.startswith("META-INF/") and n.endswith(".RSA") for n in names):
            raise SystemExit("APK has no v1 signature")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", allowZip64=False) as z:
        z.writestr(entry("NEMESIS.data", 0o644), descriptor(a.version))
        z.writestr(entry("bin/install.sh", 0o755), script(SRC / "bin" / "install.sh"))
        z.writestr(entry("scripts/postinstall.sh", 0o755), script(SRC / "scripts" / "postinstall.sh"))
        info = entry("apk/k8-nowplaying.apk", 0o644)
        info.compress_type = zipfile.ZIP_STORED
        z.writestr(info, apk)
    data = buf.getvalue()
    name = PACKAGE_ID + "-" + a.version + ".zip"
    usb_data = "[nemesis]\nversion=1.0\ntype=install\npath=packages/" + name + "\n"
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_bytes(data)
    pk = OUT / "usb" / "packages"
    pk.mkdir(parents=True, exist_ok=True)
    for stale in pk.glob(PACKAGE_ID + "-*.zip"):
        stale.unlink()
    (pk / name).write_bytes(data)
    (OUT / "usb" / "NEMESIS.data").write_text(usb_data, encoding="utf-8", newline="\n")
    share = OUT / ("K8-nowplaying-" + a.version + "-USB.zip")
    with zipfile.ZipFile(share, "w", allowZip64=False) as z:
        z.writestr(entry("NEMESIS.data", 0o644), usb_data)
        st = entry("packages/" + name, 0o644)
        st.compress_type = zipfile.ZIP_STORED
        z.writestr(st, data)
    print("package=%s\nshare=%s\nshare_sha256=%s\napk_sha256=%s" % (
        OUT / name, share, hashlib.sha256(share.read_bytes()).hexdigest(), hashlib.sha256(apk).hexdigest()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
