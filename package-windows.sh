#!/usr/bin/env bash
# Builds the Windows packages into dist/ (or the folder given as the first argument):
#   Slash24-<version>-windows-x64.msi  installer: Start menu entry, optional desktop shortcut, upgrades
#   Slash24-<version>-windows-x64.zip  portable: unzip anywhere and run Slash24.exe
# Both include a trimmed Java runtime. Runs on Windows in Git Bash with a JDK 25; the .msi also needs
# the WiX Toolset (GitHub's windows-2022 runners have it).
set -euo pipefail
cd "$(dirname "$0")"
OUT=${1:-dist}
VERSION=$(tr -d '[:space:]' < VERSION)
# Never change this: it is how Windows recognises a newer Slash24 .msi as an upgrade of an older one.
UPGRADE_UUID=dc304c02-b662-4c89-a91a-e4c3b97fd16d

./build.sh
rm -rf build/win
mkdir -p build/win/input "$OUT"
cp build/slash24.jar build/win/input/

# The smallest runtime that runs the app. --generate-cds-archive pre-builds Java's class cache for it.
MODULE_PATH=()
[[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME/jmods" ]] && MODULE_PATH=(--module-path "$JAVA_HOME/jmods")
jlink "${MODULE_PATH[@]}" --add-modules "$(jdeps --print-module-deps --ignore-missing-deps build/slash24.jar)" \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --generate-cds-archive --output build/win/runtime

COMMON=(--name Slash24 --app-version "$VERSION" --vendor digitaladministrator
        --description "IPv4 subnet calculator, VLSM planner and Cisco IOS config generator"
        --copyright "Copyright (c) 2026 digitaladministrator. MIT License." --icon packaging/slash24.ico)

jpackage --type app-image "${COMMON[@]}" --input build/win/input --main-jar slash24.jar \
    --runtime-image build/win/runtime --dest build/win/image \
    --java-options "-Xms8m -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
cp LICENSE build/win/image/Slash24/

rm -f "$OUT"/Slash24-*-windows-x64.*
powershell -NoProfile -Command \
    "Compress-Archive -Path 'build/win/image/Slash24' -DestinationPath '$OUT/Slash24-$VERSION-windows-x64.zip'"

jpackage --type msi "${COMMON[@]}" --app-image build/win/image/Slash24 --dest build/win/msi \
    --win-menu --win-menu-group Slash24 --win-shortcut --win-shortcut-prompt --win-dir-chooser \
    --win-upgrade-uuid "$UPGRADE_UUID" --license-file LICENSE \
    --about-url https://github.com/digitaladministrator/slash24
mv "build/win/msi/Slash24-$VERSION.msi" "$OUT/Slash24-$VERSION-windows-x64.msi"

echo "Windows packages for $VERSION are in $OUT:"
ls -l "$OUT"/Slash24-*-windows-x64.*
