# OpenGUI Android

<div align="center">

![Android](https://img.shields.io/badge/Android-11%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue)
![API](https://img.shields.io/badge/API-30%2B-brightgreen)
![License](https://img.shields.io/badge/License-Apache%202.0-yellow)

### AI-Powered GUI Automation Agent for Android

[中文文档](README_ZH.md)

[Features](#features) • [Quick Start](#quick-start) • [Configuration](#configuration) • [Architecture](#architecture) • [Contributing](#contributing)

</div>

---

## Overview

**OpenGUI Android** is an open-source AI agent that combines voice recognition with GUI automation to control your Android device naturally through speech commands. It leverages the iFlytek SparkChain SDK for real-time speech recognition and communicates with a backend AI server via WebSocket to execute intelligent GUI operations.

## Features

- **Voice Control** - Real-time speech recognition with press-to-record interface
- **AI Automation** - Intelligent GUI automation using accessibility services
- **Screen Understanding** - AI-powered UI element detection and analysis
- **Floating Assistant** - System-wide voice assistant accessible from any app
- **Multi-Agent Support** - Multiple AI agents with different capabilities
- **Scheduled Tasks** - Task scheduling and automation
- **Skill System** - Customizable skill definitions for automation workflows
- **Real-time Feedback** - Visual status indicators and notification updates
- **Offline Mode** - Local operation without backend (limited features)

## Quick Start

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or higher
- JDK 11+
- Android 11+ device (API 30+)
- Backend AI server (or use mock mode for testing)

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/opengui-android.git
cd opengui-android
```

### 2. Configure Build Settings

Copy the configuration template and fill in your values:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
# Required: Backend server URLs
WEBSOCKET_URL=ws://your-server:8181/mcp/ws
API_BASE_URL=http://your-server:8181

# Optional: iFlytek SDK credentials (required for voice features)
# Get credentials from: https://console.xfyun.cn/app/myapp
IFLYTEK_APP_ID=your_app_id
IFLYTEK_API_KEY=your_api_key
IFLYTEK_API_SECRET=your_api_secret
```

### 3. Download iFlytek SDK

The iFlytek SDK must be manually downloaded and placed in the `app/libs/` directory:

1. Download **SparkChain.aar** from [iFlytek SparkChain Documentation](https://www.xfyun.cn/doc/spark/%E5%A4%A7%E6%A8%A1%E5%9E%8B%E8%AF%86%E5%88%AB.html)
2. (Optional) Download **AIKit.aar** from [iFlytek AIKit Documentation](https://www.xfyun.cn/doc/asr/awaken/Android-SDK.html) for voice wake-up features
3. Place the `.aar` files in `app/libs/`

### 4. Build and Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Or build and install in one step
./gradlew :app:installDebug
```

### 5. Grant Permissions

Open the app and grant the following permissions:

1. **Accessibility Service** - Required for GUI automation
2. **Overlay Permission** - Required for floating assistant
3. **Microphone** - Required for voice input
4. **Storage** - Required for screenshots
5. **Notifications** - Required for status updates
6. **(Optional) Shizuku** - Enhanced system operations

The app includes a guided permission setup flow.

### 6. Start Using

1. Open the app and log in or register
2. Follow the permission guide
3. Long press the floating ball to start recording
4. Speak your command (e.g., "打开微信" - Open WeChat)
5. Release to send the command
6. Watch the agent execute operations automatically

## Configuration

### Backend Server

The app requires a backend AI server that:
1. Receives speech transcripts via WebSocket
2. Processes commands using LLM
3. Returns GUI automation instructions

See [docs/BACKEND_EXAMPLE.md](docs/BACKEND_EXAMPLE.md) for backend implementation examples.

### iFlytek SDK Configuration

You can configure iFlytek SDK in two ways:

1. **Build-time configuration** - Set values in `local.properties` before building
2. **Runtime configuration** - Enter credentials in app Settings

Both methods are supported. Runtime configuration takes precedence.

### Shizuku Integration (Optional)

For enhanced capabilities:
1. Install [Shizuku app](https://github.com/RikkaApps/Shizuku)
2. Start Shizuku service (requires ADB or root)
3. Authorize OpenGUI in Shizuku app
4. Grant advanced permissions automatically

## Architecture

```
┌─────────────────────────────────────────────┐
│           Voice Interface Layer             │
│  SpeechRecognition + VoiceWakeUp            │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│       WebSocket Communication               │
│  WebSocketService ↔ Backend AI Server       │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         Command Execution Layer             │
│  CommandExecutor → Action Handlers          │
│  ├── Click / LongPress / Drag               │
│  ├── Type / Clipboard                       │
│  ├── Screenshot / Screen Analysis           │
│  └── Scroll / System Actions                │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│      Accessibility Service Layer            │
│  MyAccessibilityService                     │
│  ├── GestureExecutor                        │
│  ├── TextInputExecutor                      │
│  ├── ScreenshotCapture                      │
│  └── WindowAnalyzer                         │
└─────────────────────────────────────────────┘
```

For detailed architecture documentation, see [docs/](docs/).

## Project Structure

```
opengui-android/
├── app/                              # Main application module
│   ├── src/main/
│   │   ├── java/top/yling/ozx/guiagent/
│   │   │   ├── websocket/            # WebSocket communication
│   │   │   ├── a11y/                 # Accessibility service
│   │   │   ├── shizuku/              # Shizuku integration
│   │   │   ├── task/                 # Task management
│   │   │   ├── scheduler/            # Scheduled tasks
│   │   │   ├── ui/                   # UI components
│   │   │   ├── util/                 # Utilities
│   │   │   └── provider/             # Content providers
│   │   └── res/                      # Resources
│   └── libs/                         # Native libraries (SDK .aar files)
├── hidden_api/                       # Hidden API definitions
├── docs/                             # Documentation
└── gradle/                           # Gradle configuration
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 2.1.0 |
| **Min SDK** | Android 11 (API 30) |
| **Target SDK** | Android 36 |
| **Build System** | Gradle 8.13.2 + Kotlin DSL |
| **Architecture** | MVVM + Coroutines + Flow |
| **Speech Recognition** | iFlytek SparkChain SDK |
| **Voice Wake-up** | iFlytek AIKit SDK |
| **Networking** | OkHttp + WebSocket |
| **Database** | Room (scheduled tasks) |
| **Async** | Kotlin Coroutines |
| **Node Selection** | GKD Selector |

## Permissions

| Permission | Purpose | Required |
|------------|---------|----------|
| `RECORD_AUDIO` | Voice recording | Yes |
| `INTERNET` | Backend communication | Yes |
| `SYSTEM_ALERT_WINDOW` | Floating assistant | Yes |
| `BIND_ACCESSIBILITY_SERVICE` | GUI automation | Yes |
| `WRITE_EXTERNAL_STORAGE` | Screenshot storage | Yes (API ≤32) |
| `POST_NOTIFICATIONS` | Status notifications | Yes (API ≥33) |
| `Shizuku` | Enhanced operations | Optional |

## Development Guide

### Building

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

### Code Style

- **Kotlin**: Follow [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- **Coroutines**: Use structured concurrency
- **Naming**: Use descriptive names for variables and functions

## Documentation

- [Architecture Overview](docs/DIRECTORY_RESTRUCTURE.md)
- [Permission Guide](docs/PERMISSION_GUIDE.md)
- [Node Click & Shizuku Usage](docs/NODE_CLICK_AND_SHIZUKU_USAGE.md)
- [Backend Integration Example](docs/BACKEND_EXAMPLE.md)
- [Voice Wake-up Integration](docs/WAKEUP_INTEGRATION.md)

## Troubleshooting

<details>
<summary><b>Accessibility service cannot be enabled?</b></summary>

Some devices require disabling "Accessibility service restrictions" in Developer Options. Also check if another accessibility service is conflicting.
</details>

<details>
<summary><b>Speech recognition failed?</b></summary>

- Check network connection
- Verify iFlytek SDK configuration in Settings
- Ensure microphone permission is granted
- Check logcat for error codes
</details>

<details>
<summary><b>Build fails with missing SDK?</b></summary>

Ensure you have downloaded and placed the `.aar` files in `app/libs/`:
- SparkChain.aar (required for speech recognition)
- AIKit.aar (optional for voice wake-up)
</details>

<details>
<summary><b>Click operations not working?</b></summary>

- Verify accessibility service is enabled
- Try installing Shizuku for enhanced capabilities
- Check if the target app restricts automation
</details>

## Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

Please ensure:
- Code follows the project's style guidelines
- All tests pass
- Documentation is updated if needed

## License

```
Copyright 2025 OpenGUI Android Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- [iFlytek SparkChain](https://www.xfyun.cn/) - Speech recognition SDK
- [Shizuku](https://github.com/RikkaApps/Shizuku) - Enhanced system operations
- [GKD](https://github.com/gkd-kit/gkd) - Node selector library
- [LSPosed HiddenApiBypass](https://github.com/LSPosed/HiddenApiBypass) - Hidden API access
- [AndroidX](https://developer.android.com/jetpack/androidx) - Modern Android libraries

---

<div align="center">

**Made with ❤️ by the OpenGUI Community**

</div>
