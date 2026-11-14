#!/usr/bin/env bash
# Subnet Calculator installer. Run it from a terminal in this folder:
#   ./install.sh              Fedora / RHEL family: system-wide RPM (asks for your password).
#                             Any other Linux: installs just for you.
#   ./install.sh --user       just for you, no password needed (any Linux)
#   ./install.sh --system     system-wide RPM (Fedora / RHEL family)
#   ./install.sh --uninstall  remove whichever version is installed
set -euo pipefail
cd "$(dirname "$0")"
APP=subnet-calculator
VERSION=@VERSION@
TARBALL="$APP-$VERSION-linux-x64.tar.gz"
RPM=$(ls "$APP-$VERSION"-*.x86_64.rpm 2>/dev/null | head -1 || true)

fail() { echo "Error: $*" >&2; exit 1; }

# Root commands: sudo in a terminal, or the desktop's password prompt when there is none.
as_root() {
    if [[ $EUID -eq 0 ]]; then "$@"
    elif [[ -t 0 ]] && command -v sudo >/dev/null; then sudo "$@"
    elif command -v pkexec >/dev/null; then pkexec "$@"
    else fail "administrator rights are needed; run this from a terminal or use --user"
    fi
}

check_system() {
    [[ $(uname -m) == x86_64 ]] || fail "this package is for 64-bit Intel/AMD (x86_64) systems"
    local glibc
    glibc=$(getconf GNU_LIBC_VERSION 2>/dev/null | awk '{print $2}')
    if [[ -n "$glibc" ]] && [[ $(printf '%s\n2.38\n' "$glibc" | sort -V | head -1) != 2.38 ]]; then
        fail "glibc $glibc is too old; 2.38 or newer is needed (Fedora 39+, Ubuntu 24.04+, Debian 13+)"
    fi
    if command -v sha256sum >/dev/null && [[ -f SHA256SUMS ]]; then
        sha256sum --quiet -c SHA256SUMS || fail "the package files are damaged; download them again"
    fi
}

user_install() {
    local tmp
    tmp=$(mktemp -d)
    trap "rm -rf '$tmp'" EXIT # expanded now: the variable is gone by the time the script exits
    tar -xzf "$TARBALL" -C "$tmp"
    "$tmp/$APP/install.sh"
}

system_install() {
    [[ -n "$RPM" ]] || fail "the RPM package is missing from this folder"
    command -v dnf >/dev/null || fail "dnf was not found; use ./install.sh --user instead"
    as_root dnf install -y "./$RPM"
    echo "Subnet Calculator $VERSION is installed for all users. Open it from the application menu, or run: $APP"
}

uninstall() {
    local removed=no
    if rpm -q "$APP" >/dev/null 2>&1; then
        as_root dnf remove -y "$APP"
        removed=yes
    fi
    local data="${XDG_DATA_HOME:-$HOME/.local/share}"
    if [[ -e "$data/$APP" || -e "$HOME/.local/bin/$APP" || -e "$data/applications/$APP.desktop" ]]; then
        rm -rf "$data/$APP" "$HOME/.local/bin/$APP" "$data/applications/$APP.desktop"
        update-desktop-database "$data/applications" 2>/dev/null || true
        removed=yes
    fi
    [[ $removed == yes ]] && echo "Subnet Calculator has been removed." || echo "Subnet Calculator was not installed."
}

case "${1:-auto}" in
    --uninstall) uninstall ;;
    --user) check_system; user_install ;;
    --system) check_system; system_install ;;
    auto)
        check_system
        if command -v dnf >/dev/null && command -v rpm >/dev/null && [[ -n "$RPM" ]]; then system_install; else user_install; fi
        ;;
    -h|--help) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//' ;;
    *) fail "unknown option $1 (try --help)" ;;
esac
