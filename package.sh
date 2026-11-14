#!/usr/bin/env bash
# Builds the installers into dist/ (or the folder given as the first argument):
#   subnet-calculator-<version>-linux-x64.tar.gz  portable package with a per-user install.sh
#   subnet-calculator-<version>-1.<dist>.x86_64.rpm  system-wide package (needs rpmbuild)
#   install.sh, INSTALL.txt, LICENSE, SHA256SUMS
# Both packages bundle a trimmed Java runtime made with jlink, so the target needs no Java.
# Needs: a JDK 25 with its jmods (Fedora: java-25-openjdk-devel java-25-openjdk-jmods) and rpm-build.
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(realpath -m "${1:-dist}")
APP=subnet-calculator
VERSION=$(tr -d '[:space:]' < VERSION)
JMODS="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}/jmods"
[[ -d "$JMODS" ]] || { echo "No jmods in $JMODS (Fedora: sudo dnf install java-25-openjdk-jmods)" >&2; exit 1; }
command -v rpmbuild >/dev/null || { echo "rpmbuild is missing (Fedora: sudo dnf install rpm-build)" >&2; exit 1; }

./build.sh
STAGE=build/pkg/$APP
rm -rf build/pkg build/rpm
mkdir -p "$STAGE/bin" "$STAGE/lib" "$STAGE/share" "$OUT"

# The smallest runtime that runs the app: only the modules it uses, without debug symbols or docs.
jlink --module-path "$JMODS" --add-modules "$(jdeps --print-module-deps --ignore-missing-deps build/$APP.jar)" \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$STAGE/runtime"

install -m 755 packaging/launcher.sh "$STAGE/bin/$APP"
install -m 755 packaging/user-install.sh "$STAGE/install.sh"
install -m 644 "build/$APP.jar" "build/$APP.classlist" "$STAGE/lib/"
install -m 644 "packaging/$APP.svg" "$STAGE/share/"
install -m 644 LICENSE "$STAGE/"
"$STAGE/bin/$APP" --version # smoke test: the bundled runtime starts the app

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
rm -f "$OUT"/$APP-*.rpm
cp build/rpm/RPMS/x86_64/$APP-"$VERSION"-*.x86_64.rpm "$OUT/"

# Installer front end and checksums
sed "s/@VERSION@/$VERSION/g" packaging/installer.sh > "$OUT/install.sh"
chmod 755 "$OUT/install.sh"
sed "s/@VERSION@/$VERSION/g" packaging/INSTALL.txt > "$OUT/INSTALL.txt"
cp LICENSE "$OUT/"
(cd "$OUT" && sha256sum "$TARBALL" $APP-"$VERSION"-*.x86_64.rpm > SHA256SUMS)

echo "Installers for $VERSION are in $OUT:"
ls -lh "$OUT" | awk 'NR > 1 {print "  " $5 "\t" $9}'
