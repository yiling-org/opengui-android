# 节点点击与 Shizuku 功能使用指南

## 📖 概述

本文档描述了从 GKD 项目移植过来的两大功能：
1. **节点点击与信息获取** - 点击屏幕获取节点完整信息
2. **Shizuku 高级操作** - 通过 Shizuku 执行系统级操作

---

## 🎯 节点点击功能

### 功能特性

- ✅ 根据屏幕坐标定位节点
- ✅ 获取节点完整属性（id、text、clickable 等）
- ✅ 获取父节点路径（从根节点到当前节点）
- ✅ 获取子节点信息
- ✅ 自动生成选择器建议
- ✅ 支持 GKD Selector 语法查询

### API 使用

#### 1. 获取坐标处的节点信息

```kotlin
val service = MyAccessibilityService.instance
val jsonResult = service?.getNodeInfoAtPosition(500f, 800f)
// 返回 NodeQueryResult JSON
```

#### 2. 使用选择器查询节点

```kotlin
// 查询所有匹配的节点
val nodes = service?.queryNodesBySelector("[text='跳过']")

// 查询第一个匹配的节点
val node = service?.queryFirstNodeBySelector("[clickable=true][text^='广告']")
```

#### 3. 点击选择器匹配的节点

```kotlin
service?.clickBySelector("[vid='btn_skip']") { success ->
    if (success) {
        Log.d(TAG, "点击成功")
    }
}
```

#### 4. 获取节点快照

```kotlin
val snapshot = service?.getNodeSnapshot()
// 返回 SnapshotNodeInfo JSON
```

### 选择器语法

| 语法 | 说明 | 示例 |
|------|------|------|
| `[attr=value]` | 精确匹配 | `[text='跳过']` |
| `[attr^=value]` | 开头匹配 | `[text^='广告']` |
| `[attr$=value]` | 结尾匹配 | `[text$='秒后']` |
| `[attr*=value]` | 包含匹配 | `[text*='跳过']` |
| `[attr!=value]` | 不等于 | `[text!='确定']` |
| `>` | 直接子节点 | `[vid='container'] > Button` |
| `<` | 父节点 | `[text='跳过'] < RelativeLayout` |

### 返回数据结构

```kotlin
// 节点查询结果
data class NodeQueryResult(
    val success: Boolean,
    val error: String?,
    val nodeInfo: ClickedNodeInfo?,
    val queryTimeMs: Long,
)

// 点击节点信息
data class ClickedNodeInfo(
    val node: NodeInfo,           // 当前节点
    val ancestorPath: List<NodeInfo>, // 父节点路径
    val children: List<NodeInfo>, // 子节点
    val selectorSuggestion: String?, // 选择器建议
    val position: NodePosition,   // 位置信息
)

// 节点属性
data class AttrInfo(
    val id: String?,    // viewIdResourceName
    val vid: String?,   // 简化的 viewId
    val name: String?,  // className
    val text: String?,  // 文本内容
    val clickable: Boolean,
    val left: Int, val top: Int,
    val right: Int, val bottom: Int,
    // ... 更多属性
)
```

---

## 🔧 Shizuku 功能

### 前置条件

1. 安装 [Shizuku](https://shizuku.rikka.app/) 应用
2. 启动 Shizuku 服务（通过 ADB 或 root）
3. 授予应用 Shizuku 权限

### 初始化

```kotlin
// 在 Application 或 Activity 中初始化
ShizukuApi.init()

// 检查可用性
if (ShizukuApi.isAvailable()) {
    // 请求权限
    ShizukuApi.requestPermission()
}
```

### 连接服务

```kotlin
lifecycleScope.launch {
    val context = ShizukuApi.connect()
    if (context != null) {
        // 连接成功，可以使用高级功能
    }
}
```

### 高级操作

#### 1. 精准点击（绕过无障碍限制）

```kotlin
val context = ShizukuApi.shizukuContextFlow.value
context?.tap(500f, 800f)  // 点击
context?.tap(500f, 800f, 1000)  // 长按 1 秒
```

#### 2. 滑动操作

```kotlin
context?.swipe(500f, 1000f, 500f, 300f, 500)  // 向上滑动
```

#### 3. 执行 Shell 命令

```kotlin
val result = context?.execCommand("dumpsys activity top")
if (result?.ok == true) {
    Log.d(TAG, result.result)
}
```

#### 4. 按键操作

```kotlin
context?.key(KeyEvent.KEYCODE_BACK)  // 返回键
context?.key(KeyEvent.KEYCODE_HOME)  // Home 键
```

### 监听权限状态

```kotlin
lifecycleScope.launch {
    ShizukuApi.shizukuGrantedFlow.collect { granted ->
        if (granted) {
            // 权限已授予
        }
    }
}
```

---

## 📦 依赖配置

### build.gradle.kts

```kotlin
dependencies {
    // GKD Selector（从本地 Maven 仓库）
    implementation("li.songe:selector-jvm:1.11.6")
    
    // Shizuku API
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    
    // kotlinx-serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

### settings.gradle.kts

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()  // 用于 GKD selector
        // ... 其他仓库
    }
}
```

### AndroidManifest.xml

```xml
<!-- Shizuku Provider -->
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

---

## 📚 文件结构

```
app/src/main/java/top/yling/ozx/guiagent/
├── a11y/
│   ├── A11yContext.kt      # 无障碍上下文（节点缓存、遍历）
│   ├── A11yExt.kt          # 无障碍扩展方法
│   ├── NodeExplorer.kt     # 节点探索器（坐标查找、选择器查询）
│   └── data/
│       ├── AttrInfo.kt     # 节点属性
│       ├── NodeInfo.kt     # 节点信息
│       └── ClickedNodeInfo.kt  # 点击节点完整信息
├── shizuku/
│   ├── ShizukuApi.kt       # Shizuku API 入口
│   ├── ShizukuContext.kt   # Shizuku 上下文
│   ├── UserService.kt      # 用户服务实现
│   └── SafeInputManager.kt # 安全输入管理器
└── util/
    └── AndroidTarget.kt    # Android 版本检查
```

---

## ⚠️ 注意事项

1. **GKD Selector 依赖**：需要先将 GKD 的 selector 模块发布到本地 Maven 仓库
   ```bash
   cd /path/to/gkd
   ./gradlew :selector:publishToMavenLocal
   ```

2. **Shizuku 权限**：Shizuku 功能需要用户手动授权，请在应用中提供清晰的引导

3. **节点缓存**：为提高性能，节点信息会被缓存。页面切换时会自动清除，也可手动调用 `clearNodeCache()`

4. **线程安全**：Shizuku 的 `tap`、`swipe` 等操作需要在工作线程中执行

---

## 🔗 参考链接

- [GKD 官方文档](https://gkd.li/)
- [GKD 选择器语法](https://gkd.li/guide/selector)
- [Shizuku 官方文档](https://shizuku.rikka.app/)
- [GKD GitHub](https://github.com/gkd-kit/gkd)

