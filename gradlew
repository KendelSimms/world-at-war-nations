#!/usr/bin/env sh
# Lightweight Gradle bootstrap for this project. Downloads Gradle only on first build.
set -eu
GRADLE_VERSION="8.8"
BASE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/worldatwar-bootstrap"
DIST_DIR="$BASE_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$BASE_DIR/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  mkdir -p "$BASE_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    echo "Downloading Gradle $GRADLE_VERSION (one-time setup)..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ZIP_FILE"
    else
      echo "Install curl or wget to download Gradle." >&2
      exit 1
    fi
  fi
  unzip -q -o "$ZIP_FILE" -d "$BASE_DIR"
fi
exec "$DIST_DIR/bin/gradle" "$@"
