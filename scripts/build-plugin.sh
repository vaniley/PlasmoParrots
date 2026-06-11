#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CLEAN=0
PLUGINS_DIR="${PLUGINS_DIR:-}"

usage() {
    cat <<'USAGE'
Usage:
  scripts/build-plugin.sh [--clean] [--plugins-dir PATH]
  PLUGINS_DIR=/path/to/server/plugins scripts/build-plugin.sh

Builds the Paper plugin jar with Maven. If a plugins directory is provided,
the built jar is copied there for quick local server testing.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean) CLEAN=1; shift ;;
        --plugins-dir)
            if [[ $# -lt 2 ]]; then echo "Missing value for --plugins-dir" >&2; exit 2; fi
            PLUGINS_DIR="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [[ -x "$ROOT_DIR/mvnw" ]]; then
    MVN="$ROOT_DIR/mvnw"
elif command -v mvn >/dev/null 2>&1; then
    MVN="mvn"
else
    echo "Maven was not found. Install Maven or add a Maven wrapper (./mvnw)." >&2
    exit 127
fi

if ! command -v javac >/dev/null 2>&1; then
    cat >&2 <<'ERROR'
Java is installed, but javac was not found.

Install a JDK 21 package, not only a Java runtime, then run this script again.
On Arch/Manjaro, for example:
  sudo pacman -S jdk21-openjdk
ERROR
    exit 127
fi

GOALS=(package)
if [[ "$CLEAN" -eq 1 ]]; then GOALS=(clean package); fi

"$MVN" -DskipTests "${GOALS[@]}"

JAR_PATH="$(find "$ROOT_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | sort | tail -n 1)"

if [[ -z "$JAR_PATH" ]]; then
    echo "Build completed, but no plugin jar was found in target/." >&2
    exit 1
fi

echo "Built: $JAR_PATH"

if [[ -n "$PLUGINS_DIR" ]]; then
    mkdir -p "$PLUGINS_DIR"
    cp "$JAR_PATH" "$PLUGINS_DIR/"
    echo "Copied to: $PLUGINS_DIR/$(basename "$JAR_PATH")"
fi
