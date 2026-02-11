# OpenGUI Android

<div align="center">

![Android](https://img.shields.io/badge/Android-11%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue)
![API](https://img.shields.io/badge/API-30%2B-brightgreen)
![License](https://img.shields.io/badge/License-Apache%202.0-yellow)

### AI-Powered GUI Automation Agent for Android

[中文文档](README.md)

[Features](#features) • [Quick Use](#quick-use) • [Quick Start](#quick-start) • [Tech Stack](#tech-stack) • [Development Guide](#development-guide)

</div>

---

## Introduction

**OpenGUI Android** is an open-source AI agent that combines voice recognition with GUI automation, allowing you to control your Android device through natural voice commands. It uses the iFlytek SparkChain SDK for real-time speech recognition and communicates with a backend AI server via WebSocket to perform intelligent GUI operations.

## Features

- **Voice Control** - Real-time speech recognition with press-to-record interface
- **AI Automation** - Intelligent GUI automation based on accessibility services
- **Screen Understanding** - AI-powered UI element detection and analysis
- **Floating Assistant** - System-wide voice assistant accessible from any app
- **Multi-Agent Support** - Multiple AI agents with different capabilities
- **Scheduled Tasks** - Task scheduling and automation
- **Skill System** - Customizable automation workflow skills
- **Real-time Feedback** - Visual status indicators and notification updates
- **Offline Mode** - Local operation without backend (limited features)

## Quick Use
### Register an Account
1. Open https://www.yiling.top/ in your browser and register a new account
2. Apply for an access code ![Access Code Application](images/accessCode01.png)
3. Wait for approval (click "Apply for Access Code" again to check the latest status and your access code)

### Download APK
...


### Install & Login & Authorization
1. After installation, enter your username, password, and the access code you applied for to log in
2. Next, proceed with app authorization
- After logging in, the authorization guide will pop up automatically. Follow the steps to grant the required permissions

<img src="images/auth/01.jpg" width="280" />

Some settings that are easy to misconfigure or hard to find:

<img src="images/auth/03.png" width="280" /> <img src="images/auth/04.png" width="280" />

For Xiaomi devices, additional configuration is needed after completing the permission guide:

<img src="images/auth/05.png" width="280" /> <img src="images/auth/06.png" width="280" /> <img src="images/auth/07.png" width="280" />

### Configure Models
Tap the settings button on the top-left corner of the OpenGUI home page >> Advanced Settings
+ Configure the LLM (the dropdown is pre-sorted; doubao-seed-1.8 is recommended)
+ Configure the speech recognition model: currently iFlytek Voice is supported (if not configured, the home page will fall back to text input)
<img src="images/model/01.jpg" width="280" />

### Start Using
Go back to the home page and start your GUI exploration.
Here are some example commands for reference:
- Buy a high-speed train ticket from XX to XX
- Buy a movie ticket for XX on Meituan
- Open Douyin (TikTok) and like the first video

### More Examples
https://www.bilibili.com/video/BV1LHFozvE8p/?spm_id_from=333.1365.list.card_archive.click&vd_source=188ebf1bb206b1bd7cdeca76fcae790d

--------

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
# Required: Android SDK path (verify your actual path)
sdk.dir=~/Library/Android/sdk

# Required: Backend server URLs
WEBSOCKET_URL=ws://xiaoling.yiling.top/mcp/ws
API_BASE_URL=http://xiaoling.yiling.top

# Optional: iFlytek SDK credentials (required for voice features)
# Get credentials from: https://console.xfyun.cn/app/myapp
IFLYTEK_APP_ID=your_app_id
IFLYTEK_API_KEY=your_api_key
IFLYTEK_API_SECRET=your_api_secret
```

### 3. Build and Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

### 4. Grant Permissions

Open the app and grant the required permissions.

The app includes a guided permission setup flow.

### 5. Start Using

1. Open the app and log in or register
2. Follow the permission guide to complete setup
3. Long press the floating ball to start recording
4. Speak your command (e.g., "Open WeChat")
5. Release to send the command
6. Watch the agent execute operations automatically

### Shizuku Integration (Optional)

For enhanced capabilities:
1. Install [Shizuku app](https://github.com/RikkaApps/Shizuku)
2. Start Shizuku service (requires ADB or root)
3. Authorize OpenGUI in Shizuku app
4. Advanced permissions will be granted automatically

## Tech Stack

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
