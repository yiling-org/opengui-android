# 目录结构重组方案

> 作者: @shanwb
> 创建时间: 2025-01-19

## 一、当前目录结构

```
top/yling/ozx/guiagent/
├── a11y/                    # 无障碍相关
│   ├── A11yContext.kt
│   ├── A11yExt.kt
│   ├── NodeExplorer.kt
│   └── data/
│       ├── AttrInfo.kt
│       ├── ClickedNodeInfo.kt
│       └── NodeInfo.kt
├── intervention/            # 人工干预
│   └── InterventionOverlayManager.kt
├── model/                   # 数据模型
│   ├── AuthModels.kt
│   ├── ChatModels.kt
│   ├── ConfigModels.kt
│   └── SkillModels.kt
├── network/                 # 网络层
│   ├── ApiService.kt
│   └── RetrofitClient.kt
├── provider/                # ContentProvider
│   └── ConfigProvider.kt
├── services/                # 系统服务
│   ├── BrowserService.kt
│   ├── CalendarService.kt
│   ├── ContactPickerService.kt
│   ├── ContactsService.kt
│   ├── NotificationService.kt
│   ├── PhoneCallService.kt
│   ├── SmsMessage.kt
│   ├── SmsService.kt
│   └── SqliteService.kt
├── shizuku/                 # Shizuku 集成
│   ├── PermissionGranter.kt
│   ├── SafeActivityManager.kt
│   ├── SafeActivityTaskManager.kt
│   ├── SafeAppOpsService.kt
│   ├── SafeInputManager.kt
│   ├── SafePackageManager.kt
│   ├── ShizukuApi.kt
│   ├── ShizukuContext.kt
│   └── UserService.kt
├── sync/                    # 应用同步
│   ├── AppChangeReceiver.kt
│   ├── InstalledAppCollector.kt
│   └── InstalledAppInfo.kt
├── task/                    # 任务管理
│   ├── TaskInfo.kt
│   └── TaskManager.kt
├── ui/                      # UI 组件
│   ├── AIStatusIndicatorView.kt
│   ├── DynamicIslandView.kt
│   ├── ScreenBorderGlowView.kt
│   └── StatusIndicatorView.kt
├── util/                    # 工具类
│   ├── AndroidTarget.kt
│   ├── AppSettings.kt
│   ├── DeviceUtils.kt
│   ├── ImageCompressionConfig.kt
│   ├── LaunchDiagnostics.kt
│   ├── PermissionHelper.kt
│   ├── StringEncryption.kt
│   ├── TokenManager.kt
│   ├── TtsHelper.kt
│   └── VirtualDisplayManager.kt
├── websocket/               # WebSocket 通信
│   ├── AccessibilityCommand.kt
│   ├── AppLauncher.kt
│   ├── CommandExecutor.kt
│   ├── WebSocketClient.kt
│   ├── WebSocketService.kt
│   └── handler/             # 31个处理器
│       ├── ActionContext.kt
│       ├── ActionHandler.kt
│       ├── ClickHandler.kt
│       ├── TypeHandler.kt
│       └── ... (27个其他Handler)
└── [主包下散落文件]          # 23个 Activity/Service
    ├── AccessibilityTestActivity.kt
    ├── AgentListActivity.kt
    ├── AgentOverlayService.kt
    ├── ChatActivity.kt
    ├── FollowUpAnswerActivity.kt
    ├── LaunchBridgeActivity.kt
    ├── LifeAssistantActivity.kt
    ├── LoginActivity.kt
    ├── MainActivity.kt
    ├── MyAccessibilityService.kt
    ├── MyApplication.kt
    ├── PermissionGuideActivity.kt
    ├── RegisterActivity.kt
    ├── SettingsActivity.kt
    ├── SkillEditActivity.kt
    ├── SkillListActivity.kt
    ├── SpeechRecognitionService.kt
    ├── TaskHistoryActivity.kt
    ├── VirtualDisplayGestureHelper.kt
    ├── VirtualDisplayTestActivity.kt
    ├── WakeUpService.kt
    └── WaveformView.kt
```

## 二、新目录结构设计

### 设计原则

1. **分层清晰**: core(核心) → transport(传输) → platform(平台) → app(应用)
2. **职责单一**: 每个目录只负责一类功能
3. **易于扩展**: 新增功能可以快速定位放置位置
4. **开源友好**: 核心能力与应用层分离，方便二次开发

### 新目录结构

```
top/yling/ozx/guiagent/
│
├── core/                              # 【核心能力层】GUI 自动化核心
│   ├── accessibility/                 # 无障碍服务核心
│   │   ├── AccessibilityServiceCore.kt  # 无障碍服务基类（从 MyAccessibilityService 抽取）
│   │   ├── A11yContext.kt             # ← a11y/
│   │   ├── A11yExt.kt                 # ← a11y/
│   │   └── data/                      # 节点数据模型
│   │       ├── AttrInfo.kt            # ← a11y/data/
│   │       ├── ClickedNodeInfo.kt     # ← a11y/data/
│   │       └── NodeInfo.kt            # ← a11y/data/
│   │
│   ├── node/                          # 节点分析
│   │   └── NodeExplorer.kt            # ← a11y/
│   │
│   ├── action/                        # Action 执行器
│   │   ├── ActionHandler.kt           # ← websocket/handler/
│   │   ├── ActionContext.kt           # ← websocket/handler/
│   │   ├── ActionExecutor.kt          # ← websocket/CommandExecutor.kt (重命名)
│   │   └── handlers/                  # 具体处理器
│   │       ├── ClickHandler.kt        # ← websocket/handler/
│   │       ├── TypeHandler.kt         # ← websocket/handler/
│   │       ├── ScrollHandler.kt       # ← websocket/handler/
│   │       ├── DragHandler.kt         # ← websocket/handler/
│   │       ├── ScreenshotHandler.kt   # ← websocket/handler/
│   │       ├── OpenAppHandler.kt      # ← websocket/handler/
│   │       └── ... (其他 Handler)
│   │
│   └── screenshot/                    # 截图能力
│       └── ScreenshotCapture.kt       # （后续从 MyAccessibilityService 拆分）
│
├── transport/                         # 【传输层】通信协议
│   ├── protocol/                      # 协议定义
│   │   └── AccessibilityCommand.kt    # ← websocket/
│   │
│   └── websocket/                     # WebSocket 实现
│       ├── WebSocketClient.kt         # ← websocket/
│       └── WebSocketService.kt        # ← websocket/
│
├── platform/                          # 【平台能力层】系统级能力
│   ├── shizuku/                       # Shizuku 增强（保持不变）
│   │   ├── ShizukuApi.kt
│   │   ├── ShizukuContext.kt
│   │   ├── PermissionGranter.kt
│   │   ├── SafeActivityManager.kt
│   │   ├── SafeActivityTaskManager.kt
│   │   ├── SafeAppOpsService.kt
│   │   ├── SafeInputManager.kt
│   │   ├── SafePackageManager.kt
│   │   └── UserService.kt
│   │
│   ├── services/                      # 系统服务封装（保持不变）
│   │   ├── BrowserService.kt
│   │   ├── CalendarService.kt
│   │   ├── ContactPickerService.kt
│   │   ├── ContactsService.kt
│   │   ├── NotificationService.kt
│   │   ├── PhoneCallService.kt
│   │   ├── SmsMessage.kt
│   │   ├── SmsService.kt
│   │   └── SqliteService.kt
│   │
│   └── sync/                          # 应用同步（保持不变）
│       ├── AppChangeReceiver.kt
│       ├── InstalledAppCollector.kt
│       └── InstalledAppInfo.kt
│
├── speech/                            # 【语音模块】（可选，后续扩展）
│   └── api/                           # 语音接口定义
│       └── SpeechRecognizer.kt        # （后续创建）
│
├── app/                               # 【应用层】UI 和业务逻辑
│   ├── activity/                      # 所有 Activity
│   │   ├── MainActivity.kt            # ← 主包
│   │   ├── LoginActivity.kt           # ← 主包
│   │   ├── RegisterActivity.kt        # ← 主包
│   │   ├── ChatActivity.kt            # ← 主包
│   │   ├── SettingsActivity.kt        # ← 主包
│   │   ├── PermissionGuideActivity.kt # ← 主包
│   │   ├── TaskHistoryActivity.kt     # ← 主包
│   │   ├── SkillListActivity.kt       # ← 主包
│   │   ├── SkillEditActivity.kt       # ← 主包
│   │   ├── LifeAssistantActivity.kt   # ← 主包
│   │   ├── AgentListActivity.kt       # ← 主包
│   │   ├── FollowUpAnswerActivity.kt  # ← 主包
│   │   ├── AccessibilityTestActivity.kt
│   │   ├── VirtualDisplayTestActivity.kt
│   │   └── LaunchBridgeActivity.kt
│   │
│   ├── service/                       # 应用服务
│   │   ├── MyAccessibilityService.kt  # ← 主包
│   │   ├── AgentOverlayService.kt     # ← 主包
│   │   ├── WakeUpService.kt           # ← 主包
│   │   └── SpeechRecognitionService.kt # ← 主包
│   │
│   ├── ui/                            # UI 组件（保持不变）
│   │   ├── AIStatusIndicatorView.kt
│   │   ├── DynamicIslandView.kt
│   │   ├── ScreenBorderGlowView.kt
│   │   ├── StatusIndicatorView.kt
│   │   └── WaveformView.kt            # ← 主包
│   │
│   ├── task/                          # 任务管理（保持不变）
│   │   ├── TaskInfo.kt
│   │   └── TaskManager.kt
│   │
│   ├── intervention/                  # 人工干预（保持不变）
│   │   └── InterventionOverlayManager.kt
│   │
│   └── helper/                        # 应用级辅助类
│       ├── AppLauncher.kt             # ← websocket/
│       └── VirtualDisplayGestureHelper.kt # ← 主包
│
├── data/                              # 【数据层】
│   ├── model/                         # 数据模型（保持不变）
│   │   ├── AuthModels.kt
│   │   ├── ChatModels.kt
│   │   ├── ConfigModels.kt
│   │   └── SkillModels.kt
│   │
│   ├── network/                       # 网络客户端（保持不变）
│   │   ├── ApiService.kt
│   │   └── RetrofitClient.kt
│   │
│   └── provider/                      # ContentProvider（保持不变）
│       └── ConfigProvider.kt
│
├── util/                              # 【工具层】（保持不变）
│   ├── AndroidTarget.kt
│   ├── AppSettings.kt
│   ├── DeviceUtils.kt
│   ├── ImageCompressionConfig.kt
│   ├── LaunchDiagnostics.kt
│   ├── PermissionHelper.kt
│   ├── StringEncryption.kt
│   ├── TokenManager.kt
│   ├── TtsHelper.kt
│   └── VirtualDisplayManager.kt
│
└── MyApplication.kt                   # 应用入口（保持在主包）
```

## 三、目录职责说明

| 目录 | 职责 | 开源重要性 |
|------|------|-----------|
| `core/` | GUI 自动化核心能力，包括无障碍服务、节点分析、Action 执行 | ⭐⭐⭐⭐⭐ 核心 |
| `transport/` | 通信协议和实现，WebSocket 客户端 | ⭐⭐⭐⭐ 重要 |
| `platform/` | 平台级能力，Shizuku 集成、系统服务封装 | ⭐⭐⭐⭐ 重要 |
| `speech/` | 语音识别模块（可选） | ⭐⭐ 可选 |
| `app/` | 应用层，Activity、Service、UI 组件 | ⭐⭐⭐ 示例 |
| `data/` | 数据层，模型、网络、存储 | ⭐⭐⭐ 通用 |
| `util/` | 工具类 | ⭐⭐⭐ 通用 |

## 四、文件移动清单

### 4.1 创建新目录

```bash
# core 层
mkdir -p core/accessibility/data
mkdir -p core/node
mkdir -p core/action/handlers
mkdir -p core/screenshot

# transport 层
mkdir -p transport/protocol
mkdir -p transport/websocket

# platform 层
mkdir -p platform/shizuku
mkdir -p platform/services
mkdir -p platform/sync

# speech 层
mkdir -p speech/api

# app 层
mkdir -p app/activity
mkdir -p app/service
mkdir -p app/ui
mkdir -p app/task
mkdir -p app/intervention
mkdir -p app/helper

# data 层
mkdir -p data/model
mkdir -p data/network
mkdir -p data/provider
```

### 4.2 文件移动映射

| 源路径 | 目标路径 | 说明 |
|--------|----------|------|
| `a11y/A11yContext.kt` | `core/accessibility/` | 无障碍上下文 |
| `a11y/A11yExt.kt` | `core/accessibility/` | 扩展函数 |
| `a11y/data/*` | `core/accessibility/data/` | 节点数据类 |
| `a11y/NodeExplorer.kt` | `core/node/` | 节点分析 |
| `websocket/handler/ActionHandler.kt` | `core/action/` | 处理器接口 |
| `websocket/handler/ActionContext.kt` | `core/action/` | 执行上下文 |
| `websocket/CommandExecutor.kt` | `core/action/ActionExecutor.kt` | 执行器（重命名）|
| `websocket/handler/*Handler.kt` | `core/action/handlers/` | 具体处理器 |
| `websocket/AccessibilityCommand.kt` | `transport/protocol/` | 协议定义 |
| `websocket/WebSocketClient.kt` | `transport/websocket/` | WS 客户端 |
| `websocket/WebSocketService.kt` | `transport/websocket/` | WS 服务 |
| `shizuku/*` | `platform/shizuku/` | Shizuku |
| `services/*` | `platform/services/` | 系统服务 |
| `sync/*` | `platform/sync/` | 应用同步 |
| `*Activity.kt` | `app/activity/` | Activity |
| `*Service.kt` (应用级) | `app/service/` | 应用服务 |
| `ui/*` | `app/ui/` | UI 组件 |
| `task/*` | `app/task/` | 任务管理 |
| `intervention/*` | `app/intervention/` | 人工干预 |
| `websocket/AppLauncher.kt` | `app/helper/` | 应用启动器 |
| `VirtualDisplayGestureHelper.kt` | `app/helper/` | 手势辅助 |
| `WaveformView.kt` | `app/ui/` | 波形视图 |
| `model/*` | `data/model/` | 数据模型 |
| `network/*` | `data/network/` | 网络 |
| `provider/*` | `data/provider/` | Provider |

## 五、注意事项

1. **package 声明需要同步修改**：移动文件后需要更新每个文件的 `package` 声明
2. **import 语句需要更新**：所有引用这些类的地方需要更新 import
3. **AndroidManifest.xml 需要更新**：Activity 和 Service 的声明路径需要更新
4. **ProGuard 规则需要更新**：如果有混淆规则，需要同步更新

## 六、执行步骤

1. 创建新目录结构
2. 移动文件到新目录
3. 更新 package 声明
4. 更新 import 语句
5. 更新 AndroidManifest.xml
6. 编译验证
7. 删除旧的空目录
