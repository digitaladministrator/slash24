#!/bin/sh
# Starts Subnet Calculator with its bundled Java runtime.
#   subnet-calculator                 start the app
#   subnet-calculator --version       print the version
#   subnet-calculator --build-cache   (re)build the startup cache; installers run this once
APP_DIR=$(dirname "$(dirname "$(readlink -f "$0")")")
JAVA="$APP_DIR/runtime/bin/java"
LIB="$APP_DIR/lib"

if [ "${1:-}" = "--build-cache" ]; then
    # A class-data-sharing archive of the classes a launch loads, built in a separate short run
    # (it never starts the app). Java ignores the archive if it is missing, so this is optional.
    exec "$JAVA" -Xshare:dump -XX:SharedClassListFile="$LIB/subnet-calculator.classlist" \
        -XX:SharedArchiveFile="$LIB/subnet-calculator.jsa" -cp "$LIB/subnet-calculator.jar"
fi

# Small heap, serial GC and a quick-start JIT keep memory use low and startup short.
exec "$JAVA" -Xms8m -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
    -Xshare:auto -XX:SharedArchiveFile="$LIB/subnet-calculator.jsa" -Xlog:cds=off -Xlog:cds+dynamic=off \
    -Dawt.useSystemAAFontSettings=on -jar "$LIB/subnet-calculator.jar" "$@"
