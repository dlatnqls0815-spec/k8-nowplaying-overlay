#!/usr/bin/env python3
"""Build the HMG-Seogaemo module seogaemo/k8np-call-split into private/seogaemo/<id>-<ver>.hsm.

Compiles hook/src against the hmgapi compile stubs (hookstub/src, left out of the DEX: the real
classes come from the Seogaemo hook host), packs the DEX as files/k8np-call-hook.jar, and checks the
module with the same rules the k8-ecs-damping builder mirrors from the Seogaemo installer. Offline only.
"""
import argparse
import hashlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "seogaemo" / "k8np-call-split"
OUT = ROOT / "private" / "seogaemo"
ECS_TOOLS = ROOT.parent / "k8-ecs-damping" / "tools"
SDK = Path.home() / ".local/share/android-sdk"
ZIP_TIME = (2026, 11, 17, 0, 0, 0)
MEMBERS = ["module.properties", "apply.sh", "check.sh", "status.sh", "ui.txt", "strings/en.txt", "strings/ko.txt"]
JAR_NAME = "files/k8np-call-hook.jar"
ENTRY = b"Lio/github/k8nowplaying/hook/CallSplitEntry;"


def newest(pattern):
    found = sorted(SDK.glob(pattern))
    if not found:
        raise SystemExit("not found: " + pattern)
    return found[-1]


def build_dex(jdk):
    suffix = ".exe" if os.name == "nt" else ""
    android_jar = newest("platforms/android-*/android.jar")
    d8 = newest("build-tools/*/lib/d8.jar")
    sources = sorted(str(p) for r in (ROOT / "hook" / "src", ROOT / "hookstub" / "src") for p in r.rglob("*.java"))
    with tempfile.TemporaryDirectory(prefix="k8np-hook-") as stage:
        stage = Path(stage)
        classes, dex_dir = stage / "classes", stage / "dex"
        classes.mkdir()
        dex_dir.mkdir()
        subprocess.run([str(Path(jdk) / "bin" / ("javac" + suffix)), "-encoding", "UTF-8", "-source", "7",
                        "-target", "7", "-Xlint:-options", "-bootclasspath", str(android_jar),
                        "-d", str(classes)] + sources, check=True)
        class_files = sorted(str(p) for p in classes.rglob("*.class") if "hmgapi" not in p.parts)
        subprocess.run([str(Path(jdk) / "bin" / ("java" + suffix)), "-cp", str(d8), "com.android.tools.r8.D8",
                        "--min-api", "19", "--lib", str(android_jar), "--output", str(dex_dir)] + class_files,
                       check=True)
        data = (dex_dir / "classes.dex").read_bytes()
    if not data.startswith(b"dex\n035\x00") or ENTRY not in data or b"Lhmgapi/hook/ModEntry;" not in data:
        raise SystemExit("unexpected DEX output")
    if b"Lhmgapi/hook/Hooks;" not in data or data.count(b"Lhmgapi/hook/MethodHook;") == 0:
        raise SystemExit("hook DEX does not reference the hook API")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        info = zipfile.ZipInfo("classes.dex", ZIP_TIME)
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, data)
    return buf.getvalue()


def build(jdk, out_dir):
    sys.path.insert(0, str(ECS_TOOLS))
    from build_seogaemo import check, text  # same installer limits as the k8-ecs-damping modules
    files = {m: text(SRC / m) for m in MEMBERS}
    jar = build_dex(jdk)
    props = check(dict(files, **{JAR_NAME: ""}))
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", allowZip64=False) as z:
        for name, data in [(m, files[m].encode("utf-8")) for m in MEMBERS] + [(JAR_NAME, jar)]:
            info = zipfile.ZipInfo(name, ZIP_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, data)
    data = buf.getvalue()
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    path = out / (props["id"] + "-" + props["version"] + ".hsm")
    path.write_bytes(data)
    return path, hashlib.sha256(data).hexdigest()


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--jdk", default=os.environ.get("JAVA_HOME"))
    p.add_argument("--out", default=str(OUT))
    a = p.parse_args(argv)
    if not a.jdk:
        raise SystemExit("Set JAVA_HOME or pass --jdk")
    path, sha = build(a.jdk, a.out)
    print("hsm=%s sha256=%s" % (path, sha))
    return 0


if __name__ == "__main__":
    sys.exit(main())
