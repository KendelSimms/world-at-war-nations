#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
./gradlew build
printf '\nBuild complete. Look in build/libs/ for the mod JAR.\n'
