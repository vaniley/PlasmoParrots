#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CLEAN=0
COMPAT=0
PLUGINS_DIR="${PLUGINS_DIR:-}"

usage() {
    cat <<'USAGE'
Usage:
  scripts/build-plugin.sh [--clean] [--compat-1.21.11] [--plugins-dir PATH]
  PLUGINS_DIR=/path/to/server/plugins scripts/build-plugin.sh

By default, builds against Paper 26.2 (JDK 25+ is required to read its API).
--compat-1.21.11 verifies the same Java 21 bytecode against Paper 1.21.11.
If a plugins directory is provided, the built jar is copied there.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean) CLEAN=1; shift ;;
        --compat-1.21.11) COMPAT=1; shift ;;
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

JAVAC="$(command -v javac || true)"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then JAVAC="$JAVA_HOME/bin/javac"; fi
if [[ -z "$JAVAC" ]]; then
    echo "A JDK is required, but javac was not found." >&2
    exit 127
fi

JDK_MAJOR="$("$JAVAC" -version 2>&1 | awk '{print $2}' | cut -d. -f1)"
if [[ "$COMPAT" -eq 0 && "$JDK_MAJOR" -lt 25 ]]; then
    echo "The Paper 26.2 API requires JDK 25+ to build (current: $JDK_MAJOR)." >&2
    echo "Use a newer build JDK, or pass --compat-1.21.11 with JDK 21+." >&2
    exit 2
fi
if [[ "$JDK_MAJOR" -lt 21 ]]; then
    echo "PlasmoParrots requires JDK 21 or newer to build." >&2
    exit 2
fi

GOALS=(package)
if [[ "$CLEAN" -eq 1 ]]; then GOALS=(clean package); fi
MAVEN_ARGS=(-DskipTests)
if [[ "$COMPAT" -eq 1 ]]; then MAVEN_ARGS+=(-Pcompat-1.21.11); fi

"$MVN" "${MAVEN_ARGS[@]}" "${GOALS[@]}"

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
