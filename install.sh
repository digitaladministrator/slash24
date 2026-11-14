#!/usr/bin/env bash
# Installs Slash24 for the current user (menu entry, icon, launcher).
#   ./install.sh              build if needed, then install
#   ./install.sh --uninstall  remove everything this script installed
set -euo pipefail
cd "$(dirname "$0")"
APP=slash24
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
BIN="$HOME/.local/bin"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/$APP"

# Before 1.4.0 the app was called subnet-calculator; remove that install too.
legacy_remove() {
    rm -rf "$DATA/subnet-calculator" "${XDG_CACHE_HOME:-$HOME/.cache}/subnet-calculator" \
        "$HOME/.local/bin/subnet-calculator" "$DATA/applications/subnet-calculator.desktop"
}

if [[ "${1:-}" == "--uninstall" ]]; then
    rm -rf "$DATA/$APP" "$CACHE" "$BIN/$APP" "$DATA/applications/$APP.desktop"
    update-desktop-database "$DATA/applications" 2>/dev/null || true
    echo "Slash24 removed."
    exit 0
fi

legacy_remove
[[ -f build/$APP.jar ]] || ./build.sh
install -Dm644 "build/$APP.jar" "$DATA/$APP/$APP.jar"
install -Dm644 "build/$APP.classlist" "$DATA/$APP/$APP.classlist"
rm -rf "$CACHE" # 1.0–1.3.3 kept an automatically created startup cache here

# Build the startup cache (class-data-sharing archive) once, now, in a separate short Java run.
# If the system Java is updated later the archive no longer matches and Java simply ignores it;
# running this script again rebuilds it.
java -Xshare:dump -XX:SharedClassListFile="$DATA/$APP/$APP.classlist" \
    -XX:SharedArchiveFile="$DATA/$APP/$APP.jsa" -cp "$DATA/$APP/$APP.jar" >/dev/null 2>&1 \
    || echo "Note: the startup cache could not be built; the app still works, it just starts a little slower."
install -Dm644 "packaging/$APP.svg" "$DATA/$APP/$APP.svg"
mkdir -p "$BIN" "$DATA/applications"

# Small heap, serial GC and a quick-start JIT keep the footprint low; the archive built above
# speeds up class loading (Java falls back silently if it is missing or out of date).
cat > "$BIN/$APP" <<LAUNCHER
#!/bin/sh
exec java -Xms8m -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \\
    -Xshare:auto -XX:SharedArchiveFile="$DATA/$APP/$APP.jsa" -Xlog:cds=off -Xlog:cds+dynamic=off \\
    -Dawt.useSystemAAFontSettings=on -jar "$DATA/$APP/$APP.jar" "\$@"
LAUNCHER
chmod 755 "$BIN/$APP"

sed -e "s|@EXEC@|$BIN/$APP|" -e "s|@ICON@|$DATA/$APP/$APP.svg|" "packaging/$APP.desktop" > "$DATA/applications/$APP.desktop"
update-desktop-database "$DATA/applications" 2>/dev/null || true
echo "Installed. Launch \"Slash24\" from the application menu or run $APP."
