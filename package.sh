#!/bin/sh
# Packages the desktop apps: dist/twx-code-analyzer-<version>-linux-x64.tar.gz and -windows-x64.zip with a trimmed Java runtime (jlink).
# Needs: a Linux JDK 17+ in JAVA_HOME (jlink) and, for the Windows package, an extracted Windows JDK of the same major version in WIN_JDK (its jmods are used).
set -e
cd "$(dirname "$0")"; VERSION=${VERSION:-1.1}; JLINK=${JAVA_HOME:+$JAVA_HOME/bin/}jlink
./build.sh
MODS=java.base,java.desktop,jdk.httpserver,java.logging,java.xml,jdk.crypto.ec,jdk.zipfs
rm -rf dist; mkdir -p dist
stage() { # $1 = dir
  mkdir -p "$1"; cp build/twx-code-analyzer.jar "$1/"; cp -r static "$1/"; cp run.sh run.bat README.md "$1/"; mkdir -p "$1/docs"; cp docs/*.md "$1/docs/"; mkdir -p "$1/data"
}
# Linux
L=dist/twx-code-analyzer-$VERSION-linux-x64; stage "$L"
$JLINK --add-modules $MODS --strip-debug --no-header-files --no-man-pages --compress=2 --output "$L/jre"
(cd dist && tar czf "twx-code-analyzer-$VERSION-linux-x64.tar.gz" "$(basename $L)")
# Windows (cross jlink with the Windows jmods)
if [ -n "$WIN_JDK" ] && [ -d "$WIN_JDK/jmods" ]; then
  W=dist/twx-code-analyzer-$VERSION-windows-x64; stage "$W"; rm -f "$W/run.sh"
  $JLINK --module-path "$WIN_JDK/jmods" --add-modules $MODS --strip-debug --no-header-files --no-man-pages --compress=2 --output "$W/jre"
  (cd dist && zip -qr "twx-code-analyzer-$VERSION-windows-x64.zip" "$(basename $W)")
fi
ls -la dist/*.tar.gz dist/*.zip 2>/dev/null
