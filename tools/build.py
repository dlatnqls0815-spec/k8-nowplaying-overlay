#!/usr/bin/env python3
"""Build the K8 now-playing overlay APK (Android 4.4 / API 19) without Gradle.

javac (Java 8 bytecode against the SDK android.jar) -> D8 -> aapt2 link (manifest only) ->
zipalign -> apksigner with a local debug key under private/. Offline only; installs nothing.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [ROOT / "src"]
MANIFEST = ROOT / "AndroidManifest.xml"
PRIVATE = ROOT / "private"
OUTPUT = PRIVATE / "k8-nowplaying-overlay.apk"
KEYSTORE = PRIVATE / "overlay-debug.p12"
MIN_SDK, TARGET_SDK = "19", "19"


def run(command, **kwargs):
    subprocess.run([str(c) for c in command], check=True, **kwargs)


def build(sdk, platform, build_tools, jdk):
    exe = ".exe" if os.name == "nt" else ""
    tools = Path(sdk) / "build-tools" / build_tools
    android_jar = Path(sdk) / "platforms" / platform / "android.jar"
    javac, java, keytool = (Path(jdk) / "bin" / (n + exe) for n in ("javac", "java", "keytool"))
    aapt2, zipalign = tools / ("aapt2" + exe), tools / ("zipalign" + exe)
    d8, apksigner = tools / "lib" / "d8.jar", tools / "lib" / "apksigner.jar"
    for required in (android_jar, aapt2, zipalign, d8, apksigner, javac):
        if not required.is_file():
            raise FileNotFoundError(required)
    PRIVATE.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="k8-np-") as stage:
        stage = Path(stage)
        classes, dex = stage / "classes", stage / "dex"
        classes.mkdir()
        dex.mkdir()
        jar = stage / "android.jar"
        shutil.copyfile(android_jar, jar)
        manifest = stage / "AndroidManifest.xml"
        shutil.copyfile(MANIFEST, manifest)
        sources = sorted(str(p) for root in SOURCES for p in root.rglob("*.java"))
        run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-Xlint:-options",
             "-bootclasspath", jar, "-d", classes] + sources)
        run([java, "-cp", d8, "com.android.tools.r8.D8", "--min-api", MIN_SDK, "--lib", jar,
             "--output", dex] + sorted(str(p) for p in classes.rglob("*.class")))
        unsigned = stage / "unsigned.apk"
        run([aapt2, "link", "-o", unsigned, "--manifest", manifest, "-I", jar,
             "--min-sdk-version", MIN_SDK, "--target-sdk-version", TARGET_SDK])
        with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as apk:
            apk.write(dex / "classes.dex", "classes.dex")
        aligned = stage / "aligned.apk"
        run([zipalign, "-f", "-p", "4", unsigned, aligned])
        if not KEYSTORE.is_file():
            new_key = stage / "overlay-debug.p12"
            run([keytool, "-genkeypair", "-noprompt", "-storetype", "PKCS12", "-keystore", new_key,
                 "-storepass", "android", "-keypass", "android", "-alias", "k8np",
                 "-dname", "CN=K8 Now Playing Debug", "-keyalg", "RSA", "-keysize", "2048",
                 "-validity", "10000"])
            shutil.copyfile(new_key, KEYSTORE)
        key = stage / "key.p12"
        shutil.copyfile(KEYSTORE, key)
        signed = stage / "signed.apk"
        run([java, "-jar", apksigner, "sign", "--ks", key, "--ks-key-alias", "k8np",
             "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", signed, aligned])
        run([java, "-jar", apksigner, "verify", "--min-sdk-version", MIN_SDK, signed])
        shutil.copyfile(signed, OUTPUT)
    return hashlib.sha256(OUTPUT.read_bytes()).hexdigest()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--sdk", required=True, help="Android SDK root")
    parser.add_argument("--platform", default="android-36")
    parser.add_argument("--build-tools", default="35.0.1")
    parser.add_argument("--jdk", default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args(argv)
    if not args.jdk:
        raise SystemExit("Set JAVA_HOME or pass --jdk")
    digest = build(args.sdk, args.platform, args.build_tools, args.jdk)
    print("apk=%s\napk_sha256=%s" % (OUTPUT, digest))
    return 0


if __name__ == "__main__":
    sys.exit(main())
