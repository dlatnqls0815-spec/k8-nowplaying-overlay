#!/usr/bin/env python3
"""Build the k8np.keys NEMESIS package (system-uid media key helper) and a USB install layout.

Compiles helper/src against the API 19 android.jar, converts it with D8 --min-api 19, and writes
private/nemesis/k8np.keys-<ver>.zip plus private/nemesis/usb/. Offline only; installs nothing.
"""
import argparse
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NEM = ROOT / "nemesis"
SRC = ROOT / "helper" / "src"
OUT = ROOT / "private" / "nemesis"
DEFAULT_JAR = ROOT.parent / "k8-ambient-sync" / ".tools" / "android19" / "android-4.4.2" / "android.jar"
DEFAULT_D8 = Path.home() / ".local/share/android-sdk/build-tools/35.0.1/lib/d8.jar"
MAIN = b"Lio/github/k8nowplaying/helper/KeyHelper;"

PACKAGE_ID = "k8np.keys"
PACKAGE_VERSION = "0.1.1"
SHORT_DESC = "K8 Now Playing: prev/play/next buttons helper"
DETAILED_DESC = ("Lets the K8 Now Playing overlay buttons send the steering-wheel previous, pause/play and "
                 "next keys. Accepts only that app and only those three keys. No vehicle settings are changed.")
ZIP_TIME = (2026, 11, 16, 0, 0, 0)


def descriptor():
    if len(SHORT_DESC) > 64 or len(DETAILED_DESC) > 256:
        raise ValueError("NEMESIS description limit exceeded")
    return "\n".join([
        "[nemesis]",
        "version=1.1",
        "id=" + PACKAGE_ID,
        "name=K8 Now Playing Keys",
        "type=application,service",
        "package-version=" + PACKAGE_VERSION,
        'short-desc="' + SHORT_DESC + '"',
        'detailed-desc="' + DETAILED_DESC + '"',
        "os=any",
        "models=any",
        "postinstall=scripts/postinstall.sh",
        "",
        "[app.0]",
        "kind=sh",
        "path=bin/status.sh",
        "uid=root",
        "",
        "[service.0]",
        "name=keys",
        "kind=sh",
        "path=svc/keys.sh",
        "uid=system",
        "mode=restart",
        "",
    ])


def compile_dex(jdk, d8_jar, android_jar):
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(Path(jdk) / "bin" / ("javac" + suffix))
    java = str(Path(jdk) / "bin" / ("java" + suffix))
    sources = sorted(str(p) for p in SRC.rglob("*.java"))
    with tempfile.TemporaryDirectory(prefix="k8np-dex-") as stage:
        stage = Path(stage)
        classes, dex_dir = stage / "classes", stage / "dex"
        classes.mkdir()
        dex_dir.mkdir()
        jar = stage / "android.jar"
        shutil.copyfile(android_jar, jar)
        subprocess.run([javac, "-encoding", "UTF-8", "-source", "7", "-target", "7", "-Xlint:-options",
                        "-bootclasspath", str(jar), "-d", str(classes)] + sources, check=True)
        class_files = sorted(str(p) for p in classes.rglob("*.class"))
        subprocess.run([java, "-cp", str(d8_jar), "com.android.tools.r8.D8", "--min-api", "19",
                        "--lib", str(jar), "--output", str(dex_dir)] + class_files, check=True)
        data = (dex_dir / "classes.dex").read_bytes()
    if not data.startswith(b"dex\n035\x00") or MAIN not in data:
        raise SystemExit("unexpected DEX output")
    return data


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


def package_bytes(dex):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", allowZip64=False) as z:
        z.writestr(entry("NEMESIS.data", 0o644), descriptor())
        z.writestr(entry("svc/keys.sh", 0o755), script(NEM / "svc" / "keys.sh"))
        z.writestr(entry("bin/status.sh", 0o755), script(NEM / "bin" / "status.sh"))
        z.writestr(entry("scripts/postinstall.sh", 0o755), script(NEM / "scripts" / "postinstall.sh"))
        z.writestr(entry("bin/k8np-keys.dex", 0o644), dex)
    return buf.getvalue()


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--jdk", default=os.environ.get("JAVA_HOME"))
    p.add_argument("--d8", default=str(DEFAULT_D8))
    p.add_argument("--android-jar", default=str(DEFAULT_JAR))
    a = p.parse_args(argv)
    if not a.jdk:
        raise SystemExit("Set JAVA_HOME or pass --jdk")
    dex = compile_dex(a.jdk, a.d8, a.android_jar)
    data = package_bytes(dex)
    name = PACKAGE_ID + "-" + PACKAGE_VERSION + ".zip"
    packages = OUT / "usb" / "packages"
    packages.mkdir(parents=True, exist_ok=True)
    for folder in (OUT, packages):
        for stale in folder.glob(PACKAGE_ID + "-*.zip"):
            stale.unlink()
    (OUT / name).write_bytes(data)
    (packages / name).write_bytes(data)
    (OUT / "usb" / "NEMESIS.data").write_text(
        "[nemesis]\nversion=1.0\ntype=install\npath=packages/" + name + "\n", encoding="utf-8", newline="\n")
    print("zip=%s\nzip_sha256=%s\ndex_sha256=%s" % (OUT / name, hashlib.sha256(data).hexdigest(),
                                                   hashlib.sha256(dex).hexdigest()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
