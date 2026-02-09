# Copilot Instructions for mysmarter

## Important Rules
**DO NOT create markdown documentation files** when generating code or implementing features. Only create markdown files when explicitly requested by the user. Focus on writing code, not documentation.

## Project Overview
Android voice assistant with real-time speech-to-text using iFlytek SparkChain SDK. Single-activity app with custom waveform visualizations and backend integration via SSE.

## Architecture & Structure

### Build System
- **Gradle Kotlin DSL**: All build files use `.gradle.kts` (not Groovy)
- **Version Catalog**: Dependency versions centralized in `gradle/libs.versions.toml`
  - Reference dependencies as `libs.androidx.core.ktx`, `libs.material`, etc.
  - Plugin references use `alias(libs.plugins.android.application)`
- **Single Module**: Currently a single `:app` module (defined in `settings.gradle.kts`)
- **Local AAR**: iFlytek `SparkChain.aar` in `app/libs/` loaded via `fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar")))`

### Key Configurations
- **Package**: `com.example.mysmarter`
- **SDK Targets**: minSdk 24, targetSdk/compileSdk 36
- **Kotlin**: Version 2.0.21 with JVM target 11
- **AGP**: Android Gradle Plugin 8.13.1
- **ViewBinding**: Enabled for type-safe view access
- **Lint**: `abortOnError = false` to allow builds despite lint warnings

### Code Organization
- Main source: `app/src/main/java/com/example/mysmarter/`
  - `MyApplication.kt`: **Critical** - Initializes iFlytek SparkChain SDK with AppID/ApiKey/ApiSecret constants
  - `MainActivity.kt`: Orchestrates recording → recognition → backend flow using coroutines
  - `SpeechRecognitionService.kt`: Wraps iFlytek SparkChain ASR with AudioRecord (16kHz, 1280 byte buffer)
  - `ApiService.kt`: Retrofit service for GET `/mcp/sse/task?task={text}` endpoint  
  - `WaveformView.kt`: Custom view with 3 animated sine waves, glow effects, particle simulation
- Resources: `app/src/main/res/`
  - `layout/activity_main.xml`: ConstraintLayout with gradient, waveform, button, transcription card
  - `drawable/`: Custom shapes for button glow, pulse effects
  - `values/colors.xml`: Status colors (recording=#E94560, processing=#FFA500, success=#4CAF50)

## Development Workflows

### Building & Running
```bash
# Build the project
./gradlew build

# Install debug APK
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean build
```

### Adding Dependencies
1. Add version to `[versions]` section in `gradle/libs.versions.toml`
2. Define library in `[libraries]` section using `version.ref`
3. Reference in `app/build.gradle.kts` as `implementation(libs.library.name)`

## Project Features

### Voice Recording Flow
1. User presses/holds button → `MainActivity.startRecording()`
2. Permission check via `ActivityResultContracts.RequestPermission()`
3. `SpeechRecognitionService.startListening()` → creates iFlytek ASR instance with params: `ASR("zh_cn", "slm", "mandarin")`
4. AudioRecord reads 16kHz PCM → sends to ASR via `asr.write(buffer, length)` in coroutine
5. Real-time callbacks: `AsrCallbacks.onResult()` → updates `binding.transcriptionText` with partial results
6. `WaveformView` animates using ValueAnimator with infinite duration, 3 sine waves
7. User releases → `stopListening()` → waits for final text via callback
8. Sends to backend: `apiService.sendTranscription(task)` → GET request with query param
9. UI state machine: IDLE → RECORDING → PROCESSING → SUCCESS/ERROR

### Critical iFlytek Integration Points
- **Initialization**: Must call `SparkChain.getInst().init(context, config)` in `MyApplication.onCreate()` before any ASR usage
- **Credentials**: Update `IFLYTEK_APP_ID`, `IFLYTEK_API_KEY`, `IFLYTEK_API_SECRET` in `MyApplication.kt`
- **ASR Parameters**: Set via builder methods - `vadEos(1500)` for silence detection, `ptt(true)` for push-to-talk
- **Error Handling**: Error code 11201 = authorization failure, check console service activation

## Project Conventions

### UI/UX Patterns
- **Dark theme**: Gradient background from `#1A1A2E` to `#0F3460`
- **Material Components**: CardView, ConstraintLayout
- **Animations**: ObjectAnimator for button press, ValueAnimator for waveforms
- **States**: IDLE, RECORDING, PROCESSING, SUCCESS, ERROR (reflected in text color)

### Permission Handling
- Uses ActivityResultContracts for RECORD_AUDIO permission
- Permission checked before starting recording
- Graceful fallback with Toast messages

### Resource Naming
- Themes: PascalCase (`Theme.Mysmarter`)
- String resources: snake_case (`app_name`)
- Colors: descriptive names (`gradient_start`, `recording`, `wave_color`)
- Drawables: ic_ prefix for icons, descriptive names for shapes

### Testing
- Unit tests: JUnit 4 in `app/src/test/`
- Instrumented tests: AndroidJUnit4 in `app/src/androidTest/`

## Network Configuration

### Backend Integration
- **Base URL**: `http://10.220.185.155:8181/` (hardcoded in `ApiService.kt` companion object)
- **Endpoint**: `GET /mcp/sse/task?task={text}` (SSE-based, not POST)
- **Request**: Query parameter `task` with transcribed text
- **Response**: `{ "success": Boolean, "message": String, "data": Any? }`
- **Timeout**: 30 seconds for connect/read/write
- **Logging**: HttpLoggingInterceptor at BODY level shows full request/response

## Critical Dependencies
- **iFlytek SparkChain SDK**: Must manually download `SparkChain.aar` from https://www.xfyun.cn/doc/spark/大模型识别.html
  - Place in `app/libs/SparkChain.aar`
  - Required for ASR functionality - app won't compile without it
  - Register account at https://console.xfyun.cn/app/myapp to get credentials
- **Retrofit**: 2.9.0 with Gson converter for backend communication
- **Coroutines**: 1.7.3 for async audio recording and network calls
- **Lottie**: 6.1.0 (listed but check if actually used)

## Important Files
- `app/src/main/java/com/example/mysmarter/MyApplication.kt`: **MUST configure iFlytek credentials here**
- `app/src/main/java/com/example/mysmarter/ApiService.kt`: Update BASE_URL before deployment
- `app/libs/SparkChain.aar`: Required binary dependency (not in version control)
- `gradle/libs.versions.toml`: Central dependency versions
- `app/src/main/AndroidManifest.xml`: 8 permissions including RECORD_AUDIO, INTERNET, phone state
- `BACKEND_EXAMPLE.md`: Sample Node.js/Python backend for testing

## Development Gotchas
- **Missing AAR**: Project won't build without `SparkChain.aar` in libs - download manually
- **Credentials**: Default credentials in `MyApplication.kt` are placeholders - get real ones from iFlytek console
- **Audio Format**: iFlytek requires 16kHz PCM, not 44.1kHz - don't change SAMPLE_RATE constant
- **Thread Safety**: ASR callbacks run on background threads - use `runOnUiThread` or coroutines for UI updates
- **Permissions**: App requests 8 permissions at runtime - some for iFlytek SDK requirements (phone state, storage)
- **Network**: Uses `usesCleartextTraffic="true"` in manifest - required for local backend testing
