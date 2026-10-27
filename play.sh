#!/usr/bin/env bash
# Builds JavaPrac when sources changed, then launches it. Arguments pass straight through:
#   ./play.sh --tag streams --max-difficulty 2
#   ./play.sh stats
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="$ROOT/build/install/javaprac/bin/javaprac"

fail() {
    gum style --foreground 1 --bold "✗ $1"
    exit 1
}

require_gum() {
    command -v gum >/dev/null 2>&1 || {
        echo "✗ This script needs gum (brew install gum)." >&2
        exit 1
    }
}

ensure_jdk() {
    # macOS ships a /usr/bin/javac stub that exists but fails, so run it rather than just looking it up
    if javac -version >/dev/null 2>&1; then
        return
    fi
    local brew_jdk="/opt/homebrew/opt/openjdk@21"
    if [[ -x "$brew_jdk/bin/javac" ]]; then
        export JAVA_HOME="$brew_jdk"
        export PATH="$JAVA_HOME/bin:$PATH"
        return
    fi
    fail "No JDK found. Install one with: brew install openjdk@21"
}

needs_build() {
    [[ ! -x "$BIN" ]] && return 0
    [[ -n "$(find "$ROOT/src" "$ROOT/build.gradle.kts" -newer "$BIN" -print -quit)" ]]
}

build() {
    gum spin --spinner dot --title "Building JavaPrac..." --show-error -- \
        "$ROOT/gradlew" --project-dir "$ROOT" -q installDist \
        || fail "Build failed. Run ./gradlew installDist to see why."
}

main() {
    require_gum
    ensure_jdk
    if needs_build; then
        build
    fi
    exec "$BIN" "$@"
}

main "$@"
