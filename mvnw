#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAVEN_VERSION="3.9.9"
MAVEN_DIR="$ROOT_DIR/.mvn/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_DIR/bin/mvn"
ARCHIVE="$ROOT_DIR/.mvn/apache-maven-$MAVEN_VERSION-bin.tar.gz"
URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"
SHA512="a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b"

verify_archive() {
    local actual
    if command -v sha512sum >/dev/null 2>&1; then
        actual="$(sha512sum "$ARCHIVE" | awk '{print $1}')"
    elif command -v shasum >/dev/null 2>&1; then
        actual="$(shasum -a 512 "$ARCHIVE" | awk '{print $1}')"
    else
        echo "sha512sum or shasum is required to verify Maven." >&2
        exit 127
    fi
    if [[ "$actual" != "$SHA512" ]]; then
        echo "Maven archive checksum verification failed: $ARCHIVE" >&2
        rm -f "$ARCHIVE"
        exit 1
    fi
}

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
    verify_archive
    tar -xzf "$ARCHIVE" -C "$ROOT_DIR/.mvn"
fi

exec "$MAVEN_BIN" "$@"
