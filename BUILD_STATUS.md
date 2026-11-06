# Build status

This repository targets Minecraft Java 1.20.1, Minecraft Forge 47.4.10, Java 17, and Gradle 8.8.

A GitHub Actions workflow is included at `.github/workflows/build.yml`. On push, pull request, or manual dispatch it provisions Eclipse Temurin Java 17, bootstraps Gradle 8.8, resolves Forge dependencies, runs `./gradlew build`, and uploads the resulting JAR as a workflow artifact. A push of a `v*` tag also attaches the JAR to a GitHub Release.

## Local build
- Windows: `build.bat`
- Linux/macOS: `./build.sh`

The first build requires internet access to download Gradle and Forge/Minecraft dependencies.

## Verification status
The source archive and Gradle configuration were inspected, but no successful compile, Forge launch, or in-game test is claimed. The first real build should run in GitHub Actions.
