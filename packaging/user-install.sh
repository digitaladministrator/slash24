#!/usr/bin/env bash
# Installs Slash24 for the current user only; no administrator rights needed.
#   ./install.sh              install or update
#   ./install.sh --uninstall  remove it again
set -euo pipefail
APP=slash24
SRC=$(cd "$(dirname "$0")" && pwd)
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
DEST="$DATA/$APP"
BIN="$HOME/.local/bin"
MENU="$DATA/applications/$APP.desktop"

# Before 1.4.0 the app was called subnet-calculator; remove that install too.
legacy_remove() {
    rm -rf "$DATA/subnet-calculator" "${XDG_CACHE_HOME:-$HOME/.cache}/subnet-calculator" \
        "$HOME/.local/bin/subnet-calculator" "$DATA/applications/subnet-calculator.desktop"
}

remove() {
    rm -rf "$DEST" "$BIN/$APP" "$MENU"
    update-desktop-database "$DATA/applications" 2>/dev/null || true
}

if [[ "${1:-}" == "--uninstall" ]]; then
    remove
    echo "Slash24 has been removed."
    exit 0
fi

remove # also replaces an older install
legacy_remove
mkdir -p "$DEST" "$BIN" "$DATA/applications"
cp -a "$SRC/bin" "$SRC/lib" "$SRC/runtime" "$SRC/share" "$SRC/LICENSE" "$DEST/"
"$DEST/bin/$APP" --build-cache >/dev/null 2>&1 \
    || echo "Note: the startup cache could not be built; the app still works, it just starts a little slower."
ln -s "$DEST/bin/$APP" "$BIN/$APP"
cat > "$MENU" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Slash24
GenericName=IPv4 Subnet Calculator
Comment=IPv4 subnets, VLSM plans, route summaries and Cisco IOS configs
Exec=$DEST/bin/$APP
Icon=$DEST/share/$APP.svg
Terminal=false
Categories=Network;
Keywords=subnet;cidr;ip;ipv4;netmask;vlsm;cisco;network;
StartupWMClass=slash24-App
DESKTOP
update-desktop-database "$DATA/applications" 2>/dev/null || true

echo "Slash24 $("$DEST/bin/$APP" --version | awk '{print $NF}') is installed for $USER."
echo "Open it from the application menu, or run: $APP"
case ":$PATH:" in
    *":$BIN:"*) ;;
    *) echo "($BIN is not on your PATH yet; log out and back in, or use the menu entry.)" ;;
esac
