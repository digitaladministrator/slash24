#!/usr/bin/env bash
# Installs Subnet Calculator for the current user only; no administrator rights needed.
#   ./install.sh              install or update
#   ./install.sh --uninstall  remove it again
set -euo pipefail
APP=subnet-calculator
SRC=$(cd "$(dirname "$0")" && pwd)
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
DEST="$DATA/$APP"
BIN="$HOME/.local/bin"
MENU="$DATA/applications/$APP.desktop"

remove() {
    rm -rf "$DEST" "$BIN/$APP" "$MENU"
    update-desktop-database "$DATA/applications" 2>/dev/null || true
}

if [[ "${1:-}" == "--uninstall" ]]; then
    remove
    echo "Subnet Calculator has been removed."
    exit 0
fi

remove # also replaces an older install
mkdir -p "$DEST" "$BIN" "$DATA/applications"
cp -a "$SRC/bin" "$SRC/lib" "$SRC/runtime" "$SRC/share" "$SRC/LICENSE" "$DEST/"
"$DEST/bin/$APP" --build-cache >/dev/null 2>&1 \
    || echo "Note: the startup cache could not be built; the app still works, it just starts a little slower."
ln -s "$DEST/bin/$APP" "$BIN/$APP"
cat > "$MENU" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Subnet Calculator
GenericName=IPv4 Subnet Calculator
Comment=IPv4 subnets, VLSM plans, route summaries and Cisco IOS configs
Exec=$DEST/bin/$APP
Icon=$DEST/share/$APP.svg
Terminal=false
Categories=Network;
Keywords=subnet;cidr;ip;ipv4;netmask;vlsm;cisco;network;
StartupWMClass=subnetcalc-App
DESKTOP
update-desktop-database "$DATA/applications" 2>/dev/null || true

echo "Subnet Calculator $("$DEST/bin/$APP" --version | awk '{print $NF}') is installed for $USER."
echo "Open it from the application menu, or run: $APP"
case ":$PATH:" in
    *":$BIN:"*) ;;
    *) echo "($BIN is not on your PATH yet; log out and back in, or use the menu entry.)" ;;
esac
