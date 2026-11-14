#!/usr/bin/env bash
# Compiles, runs the tests and packages build/subnet-calculator.jar. Needs only a JDK (25+).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build
javac --release 25 -Xlint:all,-serial -Werror -d build/classes $(find src -name '*.java')
javac --release 25 -cp build/classes -d build/test $(find test -name '*.java')
java -cp build/classes:build/test subnetcalc.SubnetTest
jar --create --file build/subnet-calculator.jar --main-class subnetcalc.App -C build/classes .
echo "Built build/subnet-calculator.jar"
