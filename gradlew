#!/bin/sh
# Minimal Gradle wrapper — downloads Gradle to ~/.gradle/wrapper/dists
# and delegates to it. Enough for our CI use case.

set -e

GRADLE_VERSION=8.11.1
WRAPPER_DIR="${HOME}/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_HOME="${WRAPPER_DIR}/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "$WRAPPER_DIR"
    ZIP="${WRAPPER_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
    if [ ! -f "$ZIP" ]; then
        curl -sSL -o "$ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    fi
    (cd "$WRAPPER_DIR" && unzip -q -o "$ZIP")
    rm -f "$ZIP"
fi

exec "$GRADLE_BIN" "$@"
