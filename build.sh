#!/usr/bin/env bash
# Compiles, runs the tests and packages build/slash24.jar plus its startup class list.
# Needs only a JDK (25+).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build
javac --release 25 -Xlint:all,-serial -Werror -d build/classes $(find src -name '*.java')
javac --release 25 -cp build/classes -d build/test $(find test -name '*.java')
java -cp build/classes:build/test slash24.SubnetTest
VERSION=$(tr -d '[:space:]' < VERSION)
printf 'Implementation-Title: Slash24\nImplementation-Version: %s\n' "$VERSION" > build/manifest.txt
jar --create --file build/slash24.jar --main-class slash24.App --manifest build/manifest.txt -C build/classes .

# Record which classes a launch loads (headless, so no display is needed). Installing turns this list
# into a class-data-sharing archive, which makes the app start faster.
javac --release 25 -Xlint:all,-serial -Werror -cp build/classes -d build/tools $(find tools -name '*.java')
java -Djava.awt.headless=true -XX:DumpLoadedClassList=build/classes.raw \
    -cp build/slash24.jar:build/tools slash24.Training
grep -v 'slash24/Training' build/classes.raw > build/slash24.classlist
rm build/classes.raw
echo "Built build/slash24.jar ($VERSION)"
