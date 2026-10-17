#!/usr/bin/env bash
# Одна команда вместо export JAVA_HOME, export PATH и ./gradlew runClient.
set -euo pipefail

cd "$(dirname "$0")"

find_jdk17() {
  shopt -s nullglob
  local candidates=( "/c/Program Files/Eclipse Adoptium"/jdk-17*/bin/java )
  if (( ${#candidates[@]} == 0 )); then
    echo "JDK 17 не найден в /c/Program Files/Eclipse Adoptium." >&2
    echo "Поставьте Temurin 17 или задайте JAVA_HOME вручную." >&2
    exit 1
  fi
  local java_bin
  java_bin=$(printf '%s\n' "${candidates[@]}" | sort -V | tail -n 1)
  dirname "$(dirname "$java_bin")"
}

version=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  version=$("$JAVA_HOME/bin/java" -version 2>&1 || true)
fi

if [[ "$version" != *'"17.'* ]]; then
  JAVA_HOME=$(find_jdk17)
  export JAVA_HOME
fi

export PATH="$JAVA_HOME/bin:$PATH"
echo "JDK: $JAVA_HOME"
exec ./gradlew runClient "$@"
