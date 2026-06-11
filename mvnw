#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAVEN_VERSION="3.9.9"
MAVEN_DIR="$ROOT_DIR/.mvn/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_DIR/bin/mvn"
ARCHIVE="$ROOT_DIR/.mvn/apache-maven-$MAVEN_VERSION-bin.tar.gz"
URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"

if [[ ! -x "$MAVEN_BIN" ]]; then
    mkdir -p "$ROOT_DIR/.mvn"
    if [[ ! -f "$ARCHIVE" ]]; then
        if command -v curl >/dev/null 2>&1; then
            curl -fL "$URL" -o "$ARCHIVE"
        elif command -v wget >/dev/null 2>&1; then
            wget -O "$ARCHIVE" "$URL"
        else
            echo "Neither curl nor wget was found; cannot download Maven." >&2
            exit 127
        fi
    fi
    tar -xzf "$ARCHIVE" -C "$ROOT_DIR/.mvn"
fi

exec "$MAVEN_BIN" "$@"
