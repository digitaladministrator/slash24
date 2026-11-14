#!/bin/sh
# Starts Slash24 with its bundled Java runtime.
#   slash24                 start the app
#   slash24 --version       print the version
#   slash24 --build-cache   (re)build the startup cache; installers run this once
APP_DIR=$(dirname "$(dirname "$(readlink -f "$0")")")
JAVA="$APP_DIR/runtime/bin/java"
LIB="$APP_DIR/lib"

if [ "${1:-}" = "--build-cache" ]; then
    # A class-data-sharing archive of the classes a launch loads, built in a separate short run
    # (it never starts the app). Java ignores the archive if it is missing, so this is optional.
    exec "$JAVA" -Xshare:dump -XX:SharedClassListFile="$LIB/slash24.classlist" \
        -XX:SharedArchiveFile="$LIB/slash24.jsa" -cp "$LIB/slash24.jar"
fi

# Small heap, serial GC and a quick-start JIT keep memory use low and startup short.
exec "$JAVA" -Xms8m -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
    -Xshare:auto -XX:SharedArchiveFile="$LIB/slash24.jsa" -Xlog:cds=off -Xlog:cds+dynamic=off \
    -Dawt.useSystemAAFontSettings=on -jar "$LIB/slash24.jar" "$@"
