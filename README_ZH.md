# OpenGUI Android

<div align="center">

![Android](https://img.shields.io/badge/Android-11%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue)
![API](https://img.shields.io/badge/API-30%2B-brightgreen)
![License](https://img.shields.io/badge/License-Apache%202.0-yellow)

### Android AI 驱动 GUI 自动化智能体

[English](README.md)

[功能特性](#功能特性) • [快速开始](#快速开始) • [配置说明](#配置说明) • [架构设计](#架构设计) • [贡献指南](#贡献指南)

</div>

---

## 简介

**OpenGUI Android** 是一个开源的 AI 智能体，结合语音识别和 GUI 自动化功能，让您能够通过自然语音命令控制 Android 设备。它使用讯飞星火链 SDK 进行实时语音识别，并通过 WebSocket 与后端 AI 服务器通信，执行智能化的 GUI 操作。

## 功能特性

- **语音控制** - 按住录音的实时语音识别
- **AI 自动化** - 基于辅助功能的智能 GUI 自动化
- **屏幕理解** - AI 驱动的 UI 元素检测与分析
- **悬浮助手** - 系统级语音助手，可在任何应用中使用
- **多 Agent 支持** - 支持多个不同能力的 AI Agent
- **定时任务** - 任务调度与自动化
- **技能系统** - 可自定义的自动化工作流技能
- **实时反馈** - 可视化状态指示和通知更新
- **离线模式** - 无需后端的本地操作（功能受限）

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 11+
- Android 11+ 设备 (API 30+)
- 后端 AI 服务器（或使用模拟模式进行测试）

### 1. 克隆仓库

```bash
git clone https://github.com/yourusername/opengui-android.git
cd opengui-android
```

### 2. 配置构建设置

复制配置模板并填写您的配置：

```bash
cp local.properties.example local.properties
```

编辑 `local.properties`：

```properties
# 必填：后端服务器 URL
WEBSOCKET_URL=ws://your-server:8181/mcp/ws
API_BASE_URL=http://your-server:8181

# 可选：讯飞 SDK 凭证（语音功能需要）
# 获取地址：https://console.xfyun.cn/app/myapp
IFLYTEK_APP_ID=your_app_id
IFLYTEK_API_KEY=your_api_key
IFLYTEK_API_SECRET=your_api_secret
```

### 3. 下载讯飞 SDK

讯飞 SDK 需要手动下载并放置在 `app/libs/` 目录中：

1. 从[讯飞星火链文档](https://www.xfyun.cn/doc/spark/%E5%A4%A7%E6%A8%A1%E5%9E%8B%E8%AF%86%E5%88%AB.html)下载 **SparkChain.aar**
2. （可选）从[讯飞 AIKit 文档](https://www.xfyun.cn/doc/asr/awaken/Android-SDK.html)下载 **AIKit.aar** 用于语音唤醒功能
3. 将 `.aar` 文件放入 `app/libs/` 目录

### 4. 构建并安装

```bash
# 构建调试 APK
./gradlew assembleDebug

# 安装到已连接的设备
./gradlew installDebug

# 或一步完成构建和安装
./gradlew :app:installDebug
```

### 5. 授予权限

打开应用并授予以下权限：

1. **辅助功能服务** - GUI 自动化所需
2. **悬浮窗权限** - 悬浮助手所需
3. **麦克风权限** - 语音输入所需
4. **存储权限** - 截图所需
5. **通知权限** - 状态更新所需
6. **（可选）Shizuku** - 增强系统操作

应用包含引导式权限设置流程。

### 6. 开始使用

1. 打开应用并登录或注册
2. 按照权限引导完成设置
3. 长按悬浮球开始录音
4. 说出您的指令（如："打开微信"）
5. 松开发送指令
6. 观察 Agent 自动执行操作

## 配置说明

### 后端服务器

应用需要后端 AI 服务器来：
1. 通过 WebSocket 接收语音转录文本
2. 使用 LLM 处理命令
3. 返回 GUI 自动化指令

参考 [docs/BACKEND_EXAMPLE.md](docs/BACKEND_EXAMPLE.md) 查看后端实现示例。

### 讯飞 SDK 配置

您可以通过两种方式配置讯飞 SDK：

1. **构建时配置** - 在构建前于 `local.properties` 中设置值
2. **运行时配置** - 在应用设置中输入凭证

两种方式均支持。运行时配置优先级更高。

### Shizuku 集成（可选）

如需增强功能：
1. 安装 [Shizuku 应用](https://github.com/RikkaApps/Shizuku)
2. 启动 Shizuku 服务（需要 ADB 或 root）
3. 在 Shizuku 应用中授权 OpenGUI
4. 自动授予高级权限

## 架构设计

```
┌─────────────────────────────────────────────┐
│              语音接口层                      │
│  SpeechRecognition + VoiceWakeUp            │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│            WebSocket 通信                   │
│  WebSocketService ↔ 后端 AI 服务器           │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│            命令执行层                       │
│  CommandExecutor → Action Handlers          │
│  ├── 点击 / 长按 / 拖拽                      │
│  ├── 输入 / 剪贴板                          │
│  ├── 截图 / 屏幕分析                        │
│  └── 滚动 / 系统操作                        │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         辅助功能服务层                      │
│  MyAccessibilityService                     │
│  ├── GestureExecutor                        │
│  ├── TextInputExecutor                      │
│  ├── ScreenshotCapture                      │
│  └── WindowAnalyzer                         │
└─────────────────────────────────────────────┘
```

详细架构文档请参见 [docs/](docs/)。

## 项目结构

```
opengui-android/
├── app/                              # 主应用模块
│   ├── src/main/
│   │   ├── java/top/yling/ozx/guiagent/
│   │   │   ├── websocket/            # WebSocket 通信
│   │   │   ├── a11y/                 # 辅助功能服务
│   │   │   ├── shizuku/              # Shizuku 集成
│   │   │   ├── task/                 # 任务管理
│   │   │   ├── scheduler/            # 定时任务
│   │   │   ├── ui/                   # UI 组件
│   │   │   ├── util/                 # 工具类
│   │   │   └── provider/             # 内容提供者
│   │   └── res/                      # 资源文件
│   └── libs/                         # 原生库（SDK .aar 文件）
├── hidden_api/                       # 隐藏 API 定义
├── docs/                             # 文档
└── gradle/                           # Gradle 配置
```

## 技术栈

| 组件 | 技术 |
|------|------|
| **语言** | Kotlin 2.1.0 |
| **最低 SDK** | Android 11 (API 30) |
| **目标 SDK** | Android 36 |
| **构建系统** | Gradle 8.13.2 + Kotlin DSL |
| **架构** | MVVM + Coroutines + Flow |
| **语音识别** | 讯飞星火链 SDK |
| **语音唤醒** | 讯飞 AIKit SDK |
| **网络通信** | OkHttp + WebSocket |
| **数据库** | Room（定时任务） |
| **异步处理** | Kotlin 协程 |
| **节点选择** | GKD Selector |

## 权限说明

| 权限 | 用途 | 是否必需 |
|------|------|----------|
| `RECORD_AUDIO` | 语音录制 | 是 |
| `INTERNET` | 后端通信 | 是 |
| `SYSTEM_ALERT_WINDOW` | 悬浮助手 | 是 |
| `BIND_ACCESSIBILITY_SERVICE` | GUI 自动化 | 是 |
| `WRITE_EXTERNAL_STORAGE` | 截图存储 | 是 (API ≤32) |
| `POST_NOTIFICATIONS` | 状态通知 | 是 (API ≥33) |
| `Shizuku` | 增强操作 | 可选 |

## 开发指南

### 构建

```bash
# 清理构建
./gradlew clean

# 构建调试 APK
./gradlew assembleDebug

# 构建发布 APK
./gradlew assembleRelease

# 运行测试
./gradlew test
./gradlew connectedAndroidTest
```

### 代码风格

- **Kotlin**: 遵循 [Android Kotlin 代码风格指南](https://developer.android.com/kotlin/style-guide)
- **协程**: 使用结构化并发
- **命名**: 使用描述性的变量和函数名

## 文档

- [架构概览](docs/DIRECTORY_RESTRUCTURE.md)
- [权限指南](docs/PERMISSION_GUIDE.md)
- [节点点击与 Shizuku 使用](docs/NODE_CLICK_AND_SHIZUKU_USAGE.md)
- [后端集成示例](docs/BACKEND_EXAMPLE.md)
- [语音唤醒集成](docs/WAKEUP_INTEGRATION.md)

## 常见问题

<details>
<summary><b>辅助功能服务无法启用？</b></summary>

部分设备需要在开发者选项中禁用"辅助功能服务限制"。同时检查是否有其他辅助功能服务冲突。
</details>

<details>
<summary><b>语音识别失败？</b></summary>

- 检查网络连接
- 在设置中验证讯飞 SDK 配置
- 确保已授予麦克风权限
- 查看 logcat 中的错误码
</details>

<details>
<summary><b>构建失败，提示缺少 SDK？</b></summary>

确保已下载并将 `.aar` 文件放入 `app/libs/`：
- SparkChain.aar（语音识别必需）
- AIKit.aar（语音唤醒可选）
</details>

<details>
<summary><b>点击操作不生效？</b></summary>

- 验证辅助功能服务已启用
- 尝试安装 Shizuku 以增强功能
- 检查目标应用是否限制自动化
</details>

## 贡献指南

我们欢迎贡献！请遵循以下步骤：

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

请确保：
- 代码遵循项目代码风格指南
- 所有测试通过
- 必要时更新文档

## 许可证

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

## 致谢

- [讯飞星火链](https://www.xfyun.cn/) - 语音识别 SDK
- [Shizuku](https://github.com/RikkaApps/Shizuku) - 增强系统操作
- [GKD](https://github.com/gkd-kit/gkd) - 节点选择库
- [LSPosed HiddenApiBypass](https://github.com/LSPosed/HiddenApiBypass) - 隐藏 API 访问
- [AndroidX](https://developer.android.com/jetpack/androidx) - 现代 Android 库

---

<div align="center">

**Made with ❤️ by the OpenGUI Community**

</div>
