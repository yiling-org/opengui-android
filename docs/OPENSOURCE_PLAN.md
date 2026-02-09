# Android GUI Agent 开源整理方案

> 作者: @shanwb
> 创建时间: 2025-01-19
> 版本: v1.0

---

## 目录

- [一、当前代码质量评估](#一当前代码质量评估)
- [二、开源整理核心思路](#二开源整理核心思路)
- [三、详细调整方案](#三详细调整方案)
- [四、开源发布检查清单](#四开源发布检查清单)
- [五、推荐实施路径](#五推荐实施路径)
- [六、开源命名建议](#六开源命名建议)

---

## 一、当前代码质量评估

### 1.1 项目概览

| 属性 | 值 |
|------|-----|
| **项目名称** | 小零 (Xiaoling) |
| **包名** | `top.yling.ozx.guiagent` |
| **编程语言** | Kotlin (100%) |
| **最低 SDK** | Android 11 (API 30) |
| **目标 SDK** | Android 36 |
| **构建工具** | Gradle 8.13.2 + Kotlin DSL |
| **代码行数** | ~30,000 行 |
| **源文件数** | 109 个 Kotlin 文件 |

### 1.2 优势亮点

| 维度 | 评分 | 说明 |
|------|------|------|
| **技术栈** | ⭐⭐⭐⭐⭐ | 100% Kotlin + Coroutines + Flow，现代化实践 |
| **架构设计** | ⭐⭐⭐⭐ | 分层清晰，Handler 模式优雅，易于扩展 |
| **功能完整性** | ⭐⭐⭐⭐⭐ | 覆盖 GUI 自动化全场景 + 系统级操作 |
| **风控规避** | ⭐⭐⭐⭐ | 随机延迟、模拟真人行为 |
| **容错设计** | ⭐⭐⭐⭐ | Shizuku + A11y 双策略回退 |

### 1.3 需改进问题

| 问题 | 严重程度 | 影响 | 解决方案 |
|------|---------|------|---------|
| 大文件问题 (`MainActivity` 4200+ 行) | 🔴 高 | 可维护性差 | 拆分为多个组件 |
| 硬编码服务器地址 | 🔴 高 | 无法切换环境 | 提取到 BuildConfig |
| 讯飞 SDK 强耦合 | 🔴 高 | 阻碍开源使用 | 接口抽象 + 可选依赖 |
| 私有 Maven 仓库依赖 | 🔴 高 | 开源用户无法构建 | 替换或内联 |
| 测试覆盖不足 (6 个测试文件) | 🟡 中 | 重构风险 | 补充单元测试 |
| 缺少 KDoc 注释 | 🟡 中 | 上手成本高 | 补充文档注释 |

### 1.4 核心架构分析

```
┌─────────────────────────────────────────────────────────────────┐
│                      WebSocket 层                                │
│  WebSocketService (前台服务) → WebSocketClient (通信管理)         │
└───────────────────┬─────────────────────────────────────────────┘
                    │ 接收服务端指令
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                   指令解析与执行层                                │
│  CommandExecutor (调度中心) → ActionHandler (处理器接口)          │
│    ├── ClickHandler                                             │
│    ├── TypeHandler                                              │
│    ├── ScreenshotHandler                                        │
│    ├── ScrollHandler / DragHandler                              │
│    └── OpenAppHandler / PressBackHandler ...                    │
└───────────────────┬─────────────────────────────────────────────┘
                    │ 调用无障碍 API
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│              MyAccessibilityService (核心执行层)                  │
│  - 手势模拟 (GestureDescription)                                 │
│  - 文本输入 (ACTION_SET_TEXT / Clipboard)                        │
│  - 截图 (takeScreenshot API)                                     │
│  - 节点查询与分析 (NodeExplorer)                                  │
│  - Shizuku 集成 (高权限操作)                                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、开源整理核心思路

### 2.1 整体策略

```
┌─────────────────────────────────────────────────────────────────┐
│                     开源版本目标                                  │
│  1. 可独立运行（不依赖私有服务）                                   │
│  2. 可灵活扩展（支持自定义服务端/语音识别）                         │
│  3. 高代码质量（清晰架构 + 完善文档 + 测试覆盖）                    │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  核心层抽取    │    │  外部依赖解耦  │    │  文档与示例   │
│  (可复用)      │    │  (可替换)      │    │  (易上手)     │
└───────────────┘    └───────────────┘    └───────────────┘
```

### 2.2 模块化重构目标

```
开源后目录结构:
gui-agent-android/
├── core/                       # 核心模块（无障碍能力）
│   ├── accessibility/          # 无障碍服务实现
│   ├── action/                 # Action 执行器
│   ├── node/                   # 节点分析
│   └── screenshot/             # 截图能力
│
├── protocol/                   # 协议模块（可独立发布）
│   ├── command/                # 指令定义
│   └── message/                # 消息协议
│
├── transport/                  # 传输层（可替换）
│   ├── websocket/              # WebSocket 实现
│   └── grpc/                   # gRPC 实现（可选）
│
├── speech/                     # 语音模块（可选、可替换）
│   ├── api/                    # 语音识别接口
│   ├── iflytek/                # 讯飞实现（可选依赖）
│   └── whisper/                # Whisper 实现（可选）
│
├── shizuku/                    # Shizuku 增强（可选）
│
├── app/                        # Demo 应用
│   ├── ui/                     # UI 组件
│   └── sample/                 # 示例代码
│
├── docs/                       # 文档
│   ├── QUICK_START.md
│   ├── ARCHITECTURE.md
│   ├── PROTOCOL.md
│   └── CUSTOMIZATION.md
│
└── server/                     # 示例服务端（简化版）
    └── mock-server/            # Mock 服务端
```

---

## 三、详细调整方案

### 3.1 第一阶段：敏感信息清理 (优先级: P0)

#### 3.1.1 硬编码清理清单

| 类型 | 位置 | 处理方式 |
|------|------|---------|
| 服务器地址 | `WebSocketClient.kt` | 提取到 `BuildConfig` |
| API Key | `StringEncryption.kt` | 移除或环境变量 |
| 讯飞 AppId | `MyApplication.kt` | 移至 `local.properties` |
| 私有 Maven 仓库 | `build.gradle.kts` | 移除或公开依赖 |

**调整示例**：

```kotlin
// 调整前 (WebSocketClient.kt)
private val serverUrl = "ws://115.190.247.135:8181/ws"

// 调整后
private val serverUrl = BuildConfig.WEBSOCKET_URL
```

```groovy
// build.gradle.kts 新增
buildTypes {
    debug {
        buildConfigField("String", "WEBSOCKET_URL", "\"ws://localhost:8181/ws\"")
    }
    release {
        buildConfigField("String", "WEBSOCKET_URL",
            "\"${project.findProperty("WEBSOCKET_URL") ?: "ws://localhost:8181/ws"}\"")
    }
}
```

#### 3.1.2 local.properties.example 模板

```properties
# 服务器配置
WEBSOCKET_URL=ws://your-server:8181/ws

# 讯飞语音 SDK（可选）
IFLYTEK_APP_ID=your_app_id
IFLYTEK_API_KEY=your_api_key
IFLYTEK_API_SECRET=your_api_secret

# 其他配置
DEBUG_MODE=true
```

#### 3.1.3 GKD Selector 依赖处理

当前依赖阿里云私服：
```kotlin
maven { url = uri("https://packages.aliyun.com/xxx/snapshots") }
implementation("li.songe:gkd-selector:1.11.6-SNAPSHOT")
```

**解决方案**（按优先级）：
1. Fork GKD Selector 到公开仓库，发布到 Maven Central
2. 使用 JitPack 发布：`implementation("com.github.user:gkd-selector:version")`
3. 将必要代码内联（需评估 GPL License 兼容性）

---

### 3.2 第二阶段：大文件拆分 (优先级: P0)

#### 3.2.1 MainActivity 拆分方案

**当前问题**：`MainActivity.kt` 4200+ 行，职责过多

```
当前职责:
├── 语音录音管理
├── WebSocket 连接管理
├── 任务状态显示
├── 权限检查
├── UI 事件处理
└── 生命周期管理
```

**拆分为**：

```kotlin
// 1. MainViewModel.kt - 状态管理 (~200行)
class MainViewModel : ViewModel() {
    // 状态
    val taskState: StateFlow<TaskState>
    val connectionState: StateFlow<ConnectionState>
    val recordingState: StateFlow<RecordingState>

    // 操作
    fun startRecording() { ... }
    fun sendTask(content: String) { ... }
    fun cancelTask() { ... }
}

// 2. VoiceRecordingManager.kt - 语音录音 (~300行)
class VoiceRecordingManager(
    private val context: Context,
    private val speechRecognizer: SpeechRecognizer,
    private val onResult: (String) -> Unit
) {
    fun startRecording() { ... }
    fun stopRecording() { ... }
    fun release() { ... }
}

// 3. PermissionChecker.kt - 权限检查 (~150行)
class PermissionChecker(private val activity: Activity) {
    fun checkAccessibilityService(): Boolean
    fun checkOverlayPermission(): Boolean
    fun checkShizukuPermission(): Boolean
    fun requestPermissions(permissions: List<String>)
    fun openAccessibilitySettings()
}

// 4. ConnectionManager.kt - 连接管理 (~200行)
class ConnectionManager(
    private val context: Context,
    private val agentConnection: AgentConnection
) {
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun reconnect()
}

// 5. MainActivity.kt - 简化后 (~500行)
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var voiceManager: VoiceRecordingManager
    private lateinit var permissionChecker: PermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        observeState()
        checkPermissions()
    }

    private fun setupUI() { ... }
    private fun observeState() { ... }
    private fun checkPermissions() { ... }
}
```

#### 3.2.2 MyAccessibilityService 拆分方案

**当前问题**：2100+ 行，职责混杂

**拆分为**：

```kotlin
// 1. GestureExecutor.kt - 手势执行 (~300行)
class GestureExecutor(private val service: AccessibilityService) {

    fun click(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        // 随机点击时长 50-180ms 模拟真人
        val clickDuration = (50..180).random().toLong()
        val path = Path().apply { moveTo(x, y) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration))
            .build()

        service.dispatchGesture(gesture, gestureCallback(callback), null)
    }

    fun longPress(x: Float, y: Float, duration: Long = 1000L, callback: ((Boolean) -> Unit)? = null)
    fun scroll(direction: Direction, startX: Float, startY: Float, distance: Float, callback: ((Boolean) -> Unit)? = null)
    fun drag(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500L, callback: ((Boolean) -> Unit)? = null)

    private fun gestureCallback(callback: ((Boolean) -> Unit)?): GestureResultCallback { ... }
}

// 2. TextInputExecutor.kt - 文本输入 (~200行)
class TextInputExecutor(
    private val service: AccessibilityService,
    private val clipboardManager: ClipboardManager
) {

    fun type(text: String): Boolean {
        // 风控优化：输入前随机延迟
        Thread.sleep((200..500).random().toLong())

        val focusedNode = service.rootInActiveWindow?.findFocus(FOCUS_INPUT)
            ?: return false

        // 策略1: ACTION_SET_TEXT
        if (trySetText(focusedNode, text)) return true

        // 策略2: 剪贴板粘贴
        return tryPaste(focusedNode, text)
    }

    private fun trySetText(node: AccessibilityNodeInfo, text: String): Boolean { ... }
    private fun tryPaste(node: AccessibilityNodeInfo, text: String): Boolean { ... }
}

// 3. ScreenshotCapture.kt - 截图 (~250行)
class ScreenshotCapture(
    private val service: AccessibilityService,
    private val virtualDisplayManager: VirtualDisplayManager?
) {
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    fun capture(callback: (Bitmap?) -> Unit) {
        // 优先使用虚拟屏幕（后台模式）
        if (virtualDisplayManager?.isCreated == true) {
            captureVirtualDisplay(callback)
            return
        }

        // 使用系统 API
        captureSystem(callback)
    }

    private fun captureSystem(callback: (Bitmap?) -> Unit) { ... }
    private fun captureVirtualDisplay(callback: (Bitmap?) -> Unit) { ... }
}

// 4. WindowAnalyzer.kt - 窗口分析 (~400行)
class WindowAnalyzer(private val service: AccessibilityService) {

    fun getCurrentWindowId(): Map<String, Any?> {
        val rootNode = service.rootInActiveWindow ?: return emptyMap()

        return mapOf(
            "packageName" to rootNode.packageName?.toString(),
            "className" to rootNode.className?.toString(),
            "topTitle" to findTopTitle(rootNode),
            "pageFeature" to generatePageFeature(rootNode),
            "contentFingerprint" to generateContentFingerprint(rootNode)
        )
    }

    private fun findTopTitle(root: AccessibilityNodeInfo): String? { ... }
    private fun generatePageFeature(root: AccessibilityNodeInfo): String { ... }
    private fun generateContentFingerprint(root: AccessibilityNodeInfo): String { ... }
}

// 5. SystemActionExecutor.kt - 系统操作 (~100行)
class SystemActionExecutor(private val service: AccessibilityService) {
    fun pressHome() = service.performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressBack() = service.performGlobalAction(GLOBAL_ACTION_BACK)
    fun openNotifications() = service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings() = service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun openRecents() = service.performGlobalAction(GLOBAL_ACTION_RECENTS)
}

// 6. MyAccessibilityService.kt - 协调器 (~300行)
class MyAccessibilityService : AccessibilityService() {

    // 组件
    lateinit var gestureExecutor: GestureExecutor
    lateinit var textInputExecutor: TextInputExecutor
    lateinit var screenshotCapture: ScreenshotCapture
    lateinit var windowAnalyzer: WindowAnalyzer
    lateinit var systemActionExecutor: SystemActionExecutor

    // Shizuku 增强（可选）
    private var shizukuContext: ShizukuContext? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        initComponents()
    }

    private fun initComponents() {
        gestureExecutor = GestureExecutor(this)
        textInputExecutor = TextInputExecutor(this, getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
        screenshotCapture = ScreenshotCapture(this, VirtualDisplayManager.instanceOrNull)
        windowAnalyzer = WindowAnalyzer(this)
        systemActionExecutor = SystemActionExecutor(this)

        // 尝试连接 Shizuku
        tryConnectShizuku()
    }

    // 对外暴露的便捷方法
    fun click(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        // 优先使用 Shizuku
        shizukuContext?.let { ctx ->
            ctx.tap(x, y) { success ->
                if (!success) gestureExecutor.click(x, y, callback)
                else callback?.invoke(true)
            }
            return
        }
        gestureExecutor.click(x, y, callback)
    }

    fun type(text: String) = textInputExecutor.type(text)
    fun takeScreenshot(callback: (Bitmap?) -> Unit) = screenshotCapture.capture(callback)
    fun getCurrentWindowId() = windowAnalyzer.getCurrentWindowId()
}
```

---

### 3.3 第三阶段：依赖解耦 (优先级: P1)

#### 3.3.1 语音识别接口抽象

**定义接口**：

```kotlin
// speech/api/SpeechRecognizer.kt
interface SpeechRecognizer {

    val isListening: StateFlow<Boolean>

    fun startListening(config: RecognitionConfig = RecognitionConfig.DEFAULT)
    fun stopListening()
    fun cancel()
    fun release()

    fun setCallback(callback: SpeechCallback)

    interface SpeechCallback {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onEndOfSpeech()
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(error: SpeechError)
    }

    data class RecognitionConfig(
        val language: String = "zh-CN",
        val maxDuration: Long = 60_000L,
        val partialResults: Boolean = true
    ) {
        companion object {
            val DEFAULT = RecognitionConfig()
        }
    }
}

// speech/api/SpeechError.kt
sealed class SpeechError(val message: String) {
    object NoPermission : SpeechError("缺少录音权限")
    object NetworkError : SpeechError("网络错误")
    object NoMatch : SpeechError("未识别到语音")
    object Timeout : SpeechError("识别超时")
    data class Unknown(val code: Int, override val message: String) : SpeechError(message)
}

// speech/api/WakeWordDetector.kt
interface WakeWordDetector {

    val isDetecting: StateFlow<Boolean>

    fun start(wakeWord: String = "小零小零")
    fun stop()
    fun release()

    fun setCallback(callback: WakeWordCallback)

    interface WakeWordCallback {
        fun onWakeWordDetected(wakeWord: String)
        fun onError(error: String)
    }
}
```

**讯飞实现（可选模块）**：

```kotlin
// speech/iflytek/IFlytekSpeechRecognizer.kt
class IFlytekSpeechRecognizer(
    private val context: Context,
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String
) : SpeechRecognizer {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var callback: SpeechRecognizer.SpeechCallback? = null
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        // 初始化讯飞 SDK
        SpeechUtility.createUtility(context, "appid=$appId")
    }

    override fun startListening(config: SpeechRecognizer.RecognitionConfig) {
        // 讯飞 SDK 调用
    }

    override fun setCallback(callback: SpeechRecognizer.SpeechCallback) {
        this.callback = callback
    }

    // ... 其他实现
}
```

**Mock 实现（开发测试）**：

```kotlin
// speech/mock/MockSpeechRecognizer.kt
class MockSpeechRecognizer : SpeechRecognizer {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var callback: SpeechRecognizer.SpeechCallback? = null

    override fun startListening(config: SpeechRecognizer.RecognitionConfig) {
        _isListening.value = true
        callback?.onReadyForSpeech()

        // 模拟 2 秒后返回结果
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            callback?.onResult("打开微信")
            _isListening.value = false
        }
    }

    override fun setCallback(callback: SpeechRecognizer.SpeechCallback) {
        this.callback = callback
    }

    // ... 其他实现
}
```

**依赖注入配置**：

```kotlin
// di/SpeechModule.kt
object SpeechModule {

    fun provideSpeechRecognizer(context: Context): SpeechRecognizer {
        // 优先使用讯飞（如果配置了）
        val appId = BuildConfig.IFLYTEK_APP_ID
        if (appId.isNotEmpty()) {
            return IFlytekSpeechRecognizer(
                context = context,
                appId = appId,
                apiKey = BuildConfig.IFLYTEK_API_KEY,
                apiSecret = BuildConfig.IFLYTEK_API_SECRET
            )
        }

        // 回退到 Mock 实现
        return MockSpeechRecognizer()
    }
}
```

#### 3.3.2 服务端通信接口抽象

**定义接口**：

```kotlin
// transport/api/AgentConnection.kt
interface AgentConnection {

    val connectionState: StateFlow<ConnectionState>
    val messages: Flow<ServerMessage>

    suspend fun connect(config: ConnectionConfig)
    suspend fun disconnect()
    suspend fun send(message: ClientMessage)

    fun isConnected(): Boolean
}

// transport/api/ConnectionState.kt
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val clientId: String) : ConnectionState()
    data class Error(val error: Throwable, val retryCount: Int) : ConnectionState()
    object Reconnecting : ConnectionState()
}

// transport/api/ConnectionConfig.kt
data class ConnectionConfig(
    val serverUrl: String,
    val clientId: String,
    val token: String? = null,
    val heartbeatInterval: Long = 30_000L,
    val reconnectMaxDelay: Long = 30_000L,
    val reconnectMaxRetries: Int = Int.MAX_VALUE
)

// transport/api/Messages.kt
sealed class ServerMessage {
    data class AgentResponse(val taskId: String, val content: String) : ServerMessage()
    data class AgentComplete(val taskId: String, val result: String) : ServerMessage()
    data class AgentError(val taskId: String, val error: String) : ServerMessage()
    data class AccessibilityCommand(val reqId: String, val action: String, val params: Map<String, Any?>) : ServerMessage()
    // ... 其他消息类型
}

sealed class ClientMessage {
    data class DeviceInfo(val clientId: String, val deviceInfo: Map<String, Any>) : ClientMessage()
    data class AgentRequest(val taskId: String, val content: String) : ClientMessage()
    data class CommandResponse(val resId: String, val success: Boolean, val data: Map<String, Any?>) : ClientMessage()
    data class Heartbeat(val clientId: String, val timestamp: Long) : ClientMessage()
    // ... 其他消息类型
}
```

**WebSocket 实现**：

```kotlin
// transport/websocket/WebSocketAgentConnection.kt
class WebSocketAgentConnection(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) : AgentConnection {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<ServerMessage>()
    override val messages: Flow<ServerMessage> = _messages.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var config: ConnectionConfig? = null

    override suspend fun connect(config: ConnectionConfig) {
        this.config = config
        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url(buildUrl(config))
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    override suspend fun send(message: ClientMessage) {
        val json = Json.encodeToString(message)
        webSocket?.send(json)
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.Connected(config!!.clientId)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = parseMessage(text)
            runBlocking { _messages.emit(message) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.value = ConnectionState.Error(t, 0)
            scheduleReconnect()
        }
    }

    // ... 其他实现
}
```

---

### 3.4 第四阶段：文档完善 (优先级: P1)

#### 3.4.1 文档结构

```
docs/
├── README.md                   # 项目介绍（英文）
├── README_CN.md                # 项目介绍（中文）
├── QUICK_START.md              # 5分钟快速开始
├── ARCHITECTURE.md             # 架构设计文档
├── PROTOCOL.md                 # 通信协议文档
├── CUSTOMIZATION.md            # 定制化指南
├── API_REFERENCE.md            # API 参考
├── CONTRIBUTING.md             # 贡献指南
├── CHANGELOG.md                # 更新日志
├── FAQ.md                      # 常见问题
└── examples/                   # 示例代码
    ├── custom-server/          # 自定义服务端示例
    ├── custom-speech/          # 自定义语音识别示例
    └── standalone-mode/        # 独立模式示例
```

#### 3.4.2 README.md 模板

```markdown
# Android GUI Agent

<p align="center">
  <img src="docs/images/logo.png" width="200" />
</p>

<p align="center">
  <a href="./README_CN.md">中文</a> | English
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B-green" />
  <img src="https://img.shields.io/badge/Kotlin-100%25-blue" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-yellow" />
</p>

An intelligent Android GUI automation agent that can understand natural language instructions and execute complex UI operations autonomously.

## Features

- 🤖 **Natural Language Control** - Describe tasks in plain language
- 📱 **Full GUI Automation** - Click, type, scroll, drag, and more
- 🔍 **Intelligent Screen Analysis** - AI-powered UI understanding
- 🔌 **Extensible Architecture** - Easy to customize and extend
- 🛡️ **Anti-Detection** - Human-like operation patterns

## Quick Start

See [QUICK_START.md](docs/QUICK_START.md) for detailed instructions.

## Architecture

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for design details.

## License

Apache License 2.0
```

#### 3.4.3 QUICK_START.md 模板

```markdown
# 快速开始

## 环境要求

- Android Studio Koala (2024.1.1) 或更高版本
- JDK 17+
- Android 11+ 设备（API 30+）
- （可选）Shizuku 应用

## 1. 克隆项目

```bash
git clone https://github.com/xxx/android-gui-agent.git
cd android-gui-agent
```

## 2. 配置

复制配置文件模板：

```bash
cp local.properties.example local.properties
```

编辑 `local.properties`：

```properties
# 必填：服务端地址
WEBSOCKET_URL=ws://your-server:8181/ws

# 可选：讯飞语音 SDK
IFLYTEK_APP_ID=
IFLYTEK_API_KEY=
IFLYTEK_API_SECRET=
```

## 3. 构建运行

```bash
./gradlew :app:installDebug
```

## 4. 授权设置

1. 打开 App → 设置
2. 点击「启用无障碍服务」
3. 在系统设置中开启「Android GUI Agent」
4. 返回 App，授予悬浮窗权限
5. （可选）安装 Shizuku 并授权

## 5. 开始使用

1. 点击主界面的麦克风按钮
2. 说出你的指令，如「打开微信」
3. 等待 Agent 执行操作

## 常见问题

### Q: 无障碍服务无法启用？

A: 部分设备需要在开发者选项中关闭「无障碍服务限制」

### Q: 点击无响应？

A: 尝试安装 Shizuku 获取增强能力

更多问题请查看 [FAQ.md](FAQ.md)
```

---

### 3.5 第五阶段：测试补充 (优先级: P2)

#### 3.5.1 单元测试计划

| 模块 | 测试重点 | 覆盖率目标 | 优先级 |
|------|---------|-----------|--------|
| `CommandExecutor` | 指令路由、Handler 注册 | 80% | P0 |
| `ActionHandler` | 各类 Handler 逻辑 | 70% | P0 |
| `NodeExplorer` | 节点查找算法 | 80% | P1 |
| `ImageCompression` | 压缩参数、输出大小 | 90% | P1 |
| `Protocol` | 消息序列化/反序列化 | 95% | P0 |
| `WindowAnalyzer` | 页面指纹生成 | 70% | P2 |

#### 3.5.2 测试示例

```kotlin
// CommandExecutorTest.kt
@RunWith(MockitoJUnitRunner::class)
class CommandExecutorTest {

    @Mock
    private lateinit var mockService: MyAccessibilityService

    @Mock
    private lateinit var mockContext: Context

    private lateinit var executor: CommandExecutor

    @Before
    fun setup() {
        executor = CommandExecutor(mockContext)
    }

    @Test
    fun `click handler should be registered`() {
        val command = AccessibilityCommand(
            action = "click",
            params = CommandParams(x = 100f, y = 200f)
        )

        var result: CommandResult? = null
        executor.execute(mockService, command) { result = it }

        assertNotNull(result)
        // 验证调用了 service.click()
        verify(mockService).click(eq(100f), eq(200f), any())
    }

    @Test
    fun `unknown action should return error`() {
        val command = AccessibilityCommand(action = "unknown_action")

        var result: CommandResult? = null
        executor.execute(mockService, command) { result = it }

        assertNotNull(result)
        assertFalse(result!!.success)
        assertTrue(result.message.contains("未知指令"))
    }

    @Test
    fun `type handler should require content param`() {
        val command = AccessibilityCommand(
            action = "type",
            params = CommandParams() // 缺少 content
        )

        var result: CommandResult? = null
        executor.execute(mockService, command) { result = it }

        assertNotNull(result)
        assertFalse(result!!.success)
        assertTrue(result.message.contains("content"))
    }
}

// ProtocolSerializationTest.kt
class ProtocolSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `AccessibilityCommand should deserialize correctly`() {
        val jsonStr = """
            {
                "action": "click",
                "params": {"x": 100.0, "y": 200.0, "isLast": true},
                "reqId": "req-123"
            }
        """.trimIndent()

        val command = json.decodeFromString<AccessibilityCommand>(jsonStr)

        assertEquals("click", command.action)
        assertEquals(100f, command.params?.x)
        assertEquals(200f, command.params?.y)
        assertTrue(command.params?.isLast == true)
        assertEquals("req-123", command.reqId)
    }

    @Test
    fun `CommandResult should serialize correctly`() {
        val result = CommandResult(
            success = true,
            message = "点击成功",
            data = mapOf(
                "image" to "base64...",
                "imageWidth" to 1440,
                "imageHeight" to 3200
            )
        )

        val jsonStr = json.encodeToString(result)

        assertTrue(jsonStr.contains("\"success\":true"))
        assertTrue(jsonStr.contains("\"message\":\"点击成功\""))
    }
}
```

---

### 3.6 第六阶段：示例服务端 (优先级: P2)

提供简化版 Mock 服务端，方便开源用户快速体验。

#### 3.6.1 Kotlin + Ktor 实现

```kotlin
// server/src/main/kotlin/MockServer.kt
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.*

fun main() {
    embeddedServer(Netty, port = 8181) {
        install(WebSockets)

        routing {
            webSocket("/ws") {
                val clientId = call.parameters["clientId"] ?: "unknown"
                println("Client connected: $clientId")

                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            handleMessage(this, frame.readText())
                        }
                    }
                } finally {
                    println("Client disconnected: $clientId")
                }
            }
        }
    }.start(wait = true)
}

suspend fun DefaultWebSocketServerSession.handleMessage(text: String) {
    val json = Json { ignoreUnknownKeys = true }
    val message = json.parseToJsonElement(text).jsonObject

    when (message["type"]?.jsonPrimitive?.content) {
        "agent" -> handleAgentRequest(message)
        "client_response" -> handleClientResponse(message)
        "heartbeat" -> send(Frame.Text("""{"type":"pong"}"""))
    }
}

suspend fun DefaultWebSocketServerSession.handleAgentRequest(message: JsonObject) {
    val content = message["data"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: return
    val taskId = "task-${System.currentTimeMillis()}"

    // 简单的指令解析
    val operations = parseInstructions(content)

    // 逐个发送操作指令
    for ((index, op) in operations.withIndex()) {
        val isLast = index == operations.lastIndex
        val command = buildCommand(op, isLast)
        send(Frame.Text(command))

        // 等待客户端响应
        // 实际实现需要更完善的请求-响应匹配
    }

    // 发送完成消息
    send(Frame.Text("""{"type":"agent_complete","taskId":"$taskId","data":{"message":"任务完成"}}"""))
}

fun parseInstructions(content: String): List<Operation> {
    // 简单的规则匹配
    return when {
        content.contains("打开") -> {
            val appName = content.replace("打开", "").trim()
            listOf(Operation.OpenApp(appName))
        }
        content.contains("点击") -> {
            // 需要配合截图分析
            listOf(Operation.Screenshot, Operation.Click(500f, 500f))
        }
        else -> listOf(Operation.Screenshot)
    }
}

sealed class Operation {
    data class OpenApp(val name: String) : Operation()
    data class Click(val x: Float, val y: Float) : Operation()
    data class Type(val text: String) : Operation()
    object Screenshot : Operation()
}

fun buildCommand(op: Operation, isLast: Boolean): String {
    val reqId = "req-${System.currentTimeMillis()}"
    return when (op) {
        is Operation.OpenApp -> """
            {"action":"open_app","params":{"appName":"${op.name}","isLast":$isLast},"reqId":"$reqId"}
        """.trimIndent()
        is Operation.Click -> """
            {"action":"click","params":{"x":${op.x},"y":${op.y},"isLast":$isLast},"reqId":"$reqId"}
        """.trimIndent()
        is Operation.Type -> """
            {"action":"type","params":{"content":"${op.text}","isLast":$isLast},"reqId":"$reqId"}
        """.trimIndent()
        Operation.Screenshot -> """
            {"action":"screenshot","params":{"isLast":$isLast},"reqId":"$reqId"}
        """.trimIndent()
    }
}
```

#### 3.6.2 Docker 部署

```dockerfile
# server/Dockerfile
FROM openjdk:17-slim

WORKDIR /app
COPY build/libs/mock-server.jar /app/server.jar

EXPOSE 8181

CMD ["java", "-jar", "server.jar"]
```

```yaml
# server/docker-compose.yml
version: '3.8'
services:
  mock-server:
    build: .
    ports:
      - "8181:8181"
    restart: unless-stopped
```

---

## 四、开源发布检查清单

### 4.1 代码清理

- [ ] 移除所有硬编码服务器地址
- [ ] 移除所有 API Key / Secret
- [ ] 移除内部日志 / 调试代码
- [ ] 移除未使用的代码和资源
- [ ] 统一代码风格 (ktlint / detekt)
- [ ] 移除内部注释和 TODO

### 4.2 依赖处理

- [ ] 替换私有 Maven 仓库依赖
- [ ] 讯飞 SDK 改为可选依赖
- [ ] GKD Selector 依赖处理
- [ ] 确保所有依赖 License 兼容
- [ ] 生成依赖清单 (DEPENDENCIES.md)

### 4.3 文档完善

- [ ] 完成 README.md (中英文)
- [ ] 完成 QUICK_START.md
- [ ] 完成 ARCHITECTURE.md
- [ ] 完成 PROTOCOL.md
- [ ] 完成 CUSTOMIZATION.md
- [ ] 完成 API_REFERENCE.md
- [ ] 添加 License 文件 (Apache 2.0)
- [ ] 添加 CONTRIBUTING.md
- [ ] 添加 CHANGELOG.md
- [ ] 添加 CODE_OF_CONDUCT.md

### 4.4 质量保证

- [ ] 核心模块单元测试覆盖 >50%
- [ ] CI/CD 配置 (GitHub Actions)
- [ ] 代码静态分析通过 (detekt)
- [ ] ProGuard 规则验证
- [ ] 安全扫描通过

### 4.5 示例验证

- [ ] Mock 服务端可独立运行
- [ ] Demo App 可独立构建运行
- [ ] 无服务端模式可用（本地测试）
- [ ] 所有示例代码可运行

### 4.6 发布准备

- [ ] 选择开源协议 (推荐 Apache 2.0)
- [ ] 创建 GitHub 仓库
- [ ] 配置 Issue 模板
- [ ] 配置 PR 模板
- [ ] 设置 Branch 保护规则
- [ ] 准备发布公告

---

## 五、推荐实施路径

### 时间线规划

```
Week 1-2: 敏感信息清理 + 依赖处理
├── Day 1-2: 硬编码清理
├── Day 3-4: 私有依赖替换
├── Day 5-7: 构建验证
└── Day 8-10: 代码审查

Week 3-4: 大文件拆分
├── Day 11-14: MainActivity 拆分
├── Day 15-18: MyAccessibilityService 拆分
└── Day 19-21: 补充 KDoc 注释

Week 5-6: 接口抽象 + 模块解耦
├── Day 22-25: 语音识别接口化
├── Day 26-29: 服务端通信接口化
└── Day 30-32: 可选模块拆分

Week 7-8: 文档 + 测试 + 示例
├── Day 33-37: 完善文档
├── Day 38-42: 补充单元测试
└── Day 43-45: Mock 服务端

Week 9: 最终验证 + 发布
├── Day 46-48: 全流程验证
├── Day 49-50: CI/CD 配置
└── Day 51: 正式发布
```

### 里程碑

| 里程碑 | 目标 | 验收标准 |
|--------|------|---------|
| M1 | 代码可公开 | 无敏感信息，可独立构建 |
| M2 | 架构清晰 | 大文件拆分完成，注释完善 |
| M3 | 可扩展 | 接口抽象完成，模块解耦 |
| M4 | 可使用 | 文档完善，示例可运行 |
| M5 | 可维护 | 测试覆盖，CI/CD 配置 |

---

## 六、开源命名建议

| 方案 | 名称 | 说明 | 推荐度 |
|------|------|------|--------|
| A | **AndroidGUIAgent** | 直接描述功能，易于理解 | ⭐⭐⭐⭐⭐ |
| B | **AutoPilot** | 自动驾驶隐喻，简洁 | ⭐⭐⭐⭐ |
| C | **Manus-Android** | 拉丁语"手"，呼应 Manus AI | ⭐⭐⭐ |
| D | **OpenAssist** | 开放助手，强调开源 | ⭐⭐⭐⭐ |
| E | **UIRobot** | UI 机器人，直观 | ⭐⭐⭐ |
| F | **SmartTouch** | 智能触控，强调能力 | ⭐⭐⭐ |

**最终推荐**：`AndroidGUIAgent` 或 `OpenAssist`

- `AndroidGUIAgent`：技术准确，SEO 友好
- `OpenAssist`：简洁易记，强调开源属性

---

## 附录

### A. 当前代码统计

| 指标 | 数值 |
|------|------|
| 源文件数量 | 109 个 Kotlin 文件 |
| 代码行数 | ~30,000 行 |
| Activity 数量 | 28 个 |
| Service 数量 | 3 个 |
| Handler 数量 | 33 个 |
| 布局文件数量 | 28 个 |
| 第三方库数量 | 20+ 个 |

### B. 核心文件路径索引

```
WebSocket 通信层
├── websocket/WebSocketService.kt          - 前台服务
├── websocket/WebSocketClient.kt           - WebSocket 客户端
├── websocket/CommandExecutor.kt           - 指令调度中心
└── websocket/AccessibilityCommand.kt      - 数据模型

Handler 处理器层
├── websocket/handler/ActionHandler.kt     - 处理器接口
├── websocket/handler/ClickHandler.kt      - 点击处理
├── websocket/handler/TypeHandler.kt       - 输入处理
├── websocket/handler/ScreenshotHandler.kt - 截图处理
└── websocket/handler/...                  - 其他 Handler

无障碍服务层
├── MyAccessibilityService.kt              - 核心服务 (2173 行)
├── a11y/NodeExplorer.kt                   - 节点查询
└── a11y/A11yContext.kt                    - 无障碍上下文

Shizuku 集成
├── shizuku/ShizukuApi.kt                  - Shizuku 封装
├── shizuku/SafeInputManager.kt            - 输入管理
└── shizuku/SafeActivityManager.kt         - Activity 管理

工具类
├── util/ImageCompressionConfig.kt         - 图片压缩
├── util/VirtualDisplayManager.kt          - 虚拟屏幕
└── util/DeviceUtils.kt                    - 设备工具
```

### C. License 兼容性

| 依赖 | License | 兼容 Apache 2.0 |
|------|---------|----------------|
| Kotlin | Apache 2.0 | ✅ |
| OkHttp | Apache 2.0 | ✅ |
| Retrofit | Apache 2.0 | ✅ |
| Shizuku | Apache 2.0 | ✅ |
| GKD Selector | GPL v3 | ⚠️ 需要评估 |
| Lottie | Apache 2.0 | ✅ |

---

*本文档将随改造进度持续更新*
