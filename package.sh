#!/usr/bin/env bash
# Builds the installers into dist/ (or the folder given as the first argument):
#   slash24-<version>-linux-x64.tar.gz  portable package with a per-user install.sh
#   slash24-<version>-1.<dist>.x86_64.rpm  system-wide package (needs rpmbuild)
#   install.sh, INSTALL.txt, LICENSE, SHA256SUMS
# Both packages bundle a trimmed Java runtime made with jlink, so the target needs no Java.
# Needs: a JDK 25 (Fedora: java-25-openjdk-devel and java-25-openjdk-jmods; or Eclipse Temurin) and
# rpmbuild (Fedora: rpm-build; Ubuntu: rpm). Windows packages are built by package-windows.sh.
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(realpath -m "${1:-dist}")
APP=slash24
VERSION=$(tr -d '[:space:]' < VERSION)
# jlink uses the JDK's jmods when it has them (Fedora: java-25-openjdk-jmods), otherwise it links from
# the JDK itself, which works for unmodified JDK builds such as Eclipse Temurin.
JMODS="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}/jmods"
MODULE_PATH=()
[[ -d "$JMODS" ]] && MODULE_PATH=(--module-path "$JMODS")
command -v rpmbuild >/dev/null || { echo "rpmbuild is missing (Fedora: sudo dnf install rpm-build)" >&2; exit 1; }

./build.sh
STAGE=build/pkg/$APP
rm -rf build/pkg build/rpm
mkdir -p "$STAGE/bin" "$STAGE/lib" "$STAGE/share" "$OUT"

# The smallest runtime that runs the app: only the modules it uses, without debug symbols or docs.
jlink "${MODULE_PATH[@]}" --add-modules "$(jdeps --print-module-deps --ignore-missing-deps build/$APP.jar)" \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$STAGE/runtime"

install -m 755 packaging/launcher.sh "$STAGE/bin/$APP"
install -m 755 packaging/user-install.sh "$STAGE/install.sh"
install -m 644 "build/$APP.jar" "build/$APP.classlist" "$STAGE/lib/"
install -m 644 "packaging/$APP.svg" "packaging/$APP.png" "$STAGE/share/"
install -m 644 LICENSE "$STAGE/"
"$STAGE/bin/$APP" --version # smoke test: the bundled runtime starts the app
# The newest glibc the runtime was built against; the installer refuses older systems.
GLIBC=$(find "$STAGE/runtime" -type f \( -name '*.so' -o -path '*/bin/*' \) -exec objdump -T {} + 2>/dev/null \
    | grep -o 'GLIBC_[0-9.]*' | sed 's/GLIBC_//' | sort -uV | tail -1)

# Start from a clean output folder (older versions and names included).
rm -f "$OUT"/*.rpm "$OUT"/*-linux-x64.tar.gz "$OUT"/SHA256SUMS

# Portable package
TARBALL="$APP-$VERSION-linux-x64.tar.gz"
tar -C build/pkg --owner=0 --group=0 -czf "$OUT/$TARBALL" "$APP"

# RPM: the runtime's own libraries must not become package dependencies.
BUNDLED=$(find "$STAGE/runtime" -name '*.so' -printf '%f\n' | sed 's/\./\\\\./g' | sort -u | paste -sd'|')
mkdir -p build/rpm/SOURCES build/rpm/SPECS
cp "$OUT/$TARBALL" "build/rpm/SOURCES/$APP-$VERSION.tar.gz"
sed -e "s/@VERSION@/$VERSION/g" -e "s/@BUNDLED_LIBS@/$BUNDLED/" -e "s/@DATE@/$(LC_ALL=C date '+%a %b %d %Y')/" \
    "packaging/$APP.spec" > "build/rpm/SPECS/$APP.spec"
rpmbuild --quiet --define "_topdir $PWD/build/rpm" -bb "build/rpm/SPECS/$APP.spec"
cp build/rpm/RPMS/x86_64/$APP-"$VERSION"-*.x86_64.rpm "$OUT/"

# Installer front end and checksums
sed -e "s/@VERSION@/$VERSION/g" -e "s/@GLIBC@/$GLIBC/g" packaging/installer.sh > "$OUT/install.sh"
chmod 755 "$OUT/install.sh"
sed -e "s/@VERSION@/$VERSION/g" -e "s/@GLIBC@/$GLIBC/g" packaging/INSTALL.txt > "$OUT/INSTALL.txt"
cp LICENSE "$OUT/"
(cd "$OUT" && sha256sum "$TARBALL" $APP-"$VERSION"-*.x86_64.rpm > SHA256SUMS)

echo "Installers for $VERSION (needs glibc $GLIBC or newer) are in $OUT:"
ls -lh "$OUT" | awk 'NR > 1 {print "  " $5 "\t" $9}'
