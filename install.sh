#!/usr/bin/env bash
# Installs Subnet Calculator for the current user (menu entry, icon, launcher).
#   ./install.sh              build if needed, then install
#   ./install.sh --uninstall  remove everything this script installed
set -euo pipefail
cd "$(dirname "$0")"
APP=subnet-calculator
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
BIN="$HOME/.local/bin"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/$APP"

if [[ "${1:-}" == "--uninstall" ]]; then
    rm -rf "$DATA/$APP" "$CACHE" "$BIN/$APP" "$DATA/applications/$APP.desktop"
    update-desktop-database "$DATA/applications" 2>/dev/null || true
    echo "Subnet Calculator removed."
    exit 0
fi

[[ -f build/$APP.jar ]] || ./build.sh
install -Dm644 "build/$APP.jar" "$DATA/$APP/$APP.jar"
rm -f "$CACHE/app.jsa"   # the startup cache belongs to the previous jar
install -Dm644 "packaging/$APP.svg" "$DATA/$APP/$APP.svg"
mkdir -p "$BIN" "$DATA/applications"

# Small heap, serial GC and C1-only JIT keep the footprint low; the class-data-sharing archive
# is created on first run and makes every later start faster.
cat > "$BIN/$APP" <<LAUNCHER
#!/bin/sh
mkdir -p "$CACHE"
exec java -Xms8m -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \\
    -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile="$CACHE/app.jsa" \\
    -Dawt.useSystemAAFontSettings=on -jar "$DATA/$APP/$APP.jar" "\$@"
LAUNCHER
chmod 755 "$BIN/$APP"

sed -e "s|@EXEC@|$BIN/$APP|" -e "s|@ICON@|$DATA/$APP/$APP.svg|" "packaging/$APP.desktop" > "$DATA/applications/$APP.desktop"
update-desktop-database "$DATA/applications" 2>/dev/null || true
echo "Installed. Launch \"Subnet Calculator\" from the application menu or run $APP."
