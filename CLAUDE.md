# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android voice assistant app (Kotlin) with press-to-record interface, real-time speech recognition via iFlytek SparkChain SDK, and backend transcription submission.

## Build Commands

```bash
# Build project
./gradlew build

# Install debug APK to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean build
```

## Architecture

### Build System
- **Gradle Kotlin DSL** (`.gradle.kts` files, not Groovy)
- **Version Catalog**: All dependency versions in `gradle/libs.versions.toml`
  - Reference as `libs.androidx.core.ktx`, `libs.retrofit`, etc.
  - Plugins via `alias(libs.plugins.android.application)`
- **SDK**: minSdk 24, targetSdk 36, Kotlin 2.0.21, JVM 11
- **ViewBinding**: Enabled for type-safe view access

### Core Components (`app/src/main/java/com/example/mysmarter/`)

| File | Purpose |
|------|---------|
| `MainActivity.kt` | UI lifecycle, permission handling, orchestrates recording/recognition/API flow |
| `SpeechRecognitionService.kt` | iFlytek SparkChain integration for real-time Chinese speech recognition |
| `ApiService.kt` | Retrofit client for `POST /api/transcription` endpoint (configurable BASE_URL) |
| `WaveformView.kt` | Custom View with 3 animated sine waves + particle effects |
| `MyApplication.kt` | Application class, iFlytek SDK initialization (AppID config here) |

### Voice Recording Flow
1. User holds record button → SpeechRecognitionService starts recognition
2. WaveformView shows animated waveforms during recording
3. On release → final transcription captured
4. ApiService POSTs `{ "text": String, "timestamp": Long }` to backend
5. UI updates with success/error state

### External SDK Dependency
- **iFlytek SparkChain**: Manual download required, place `SparkChain.aar` in `app/libs/`
- SDK AppID configured in `MyApplication.kt`

## Adding Dependencies

1. Add version to `[versions]` in `gradle/libs.versions.toml`
2. Define library in `[libraries]` using `version.ref`
3. Add to `app/build.gradle.kts` as `implementation(libs.library.name)`

## Important Notes

- **DO NOT create markdown documentation files** unless explicitly requested
- Lint errors disabled (`abortOnError = false`) to allow builds
- Backend URL in `ApiService.kt` must be updated before deployment
- Requires RECORD_AUDIO and INTERNET permissions (already declared in manifest)
