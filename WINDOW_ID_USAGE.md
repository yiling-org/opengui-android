# 窗口标识增强功能使用说明

## 概述

`getCurrentWindowId()` 方法已增强，现在可以获取更详细的窗口指纹信息，用于精确识别和追踪用户当前所在的页面/窗口。

## 功能特性

### 1. 多级标识符
- **identifier** (精细标识): 包含页面特征，适用于精确匹配
- **stableId** (粗粒度标识): 仅包含包名和类名，适用于页面级匹配
- **legacyIdentifier** (兼容旧格式): 保持向后兼容

### 2. 页面指纹信息
- **titleText**: 页面标题文本（从前4层节点提取）
- **keyResourceIds**: 关键控件ID列表（前10个）
- **pageFeature**: 组合的页面特征字符串

### 3. 基础窗口信息
- **windowId**: 窗口ID
- **packageName**: 应用包名
- **className**: Activity类名
- **windowTitle**: 窗口标题（Android 7.0+）

## API 使用

### WebSocket 指令

```json
{
    "action": "get_window_id"
}
```

### 返回数据示例

```json
{
    "success": true,
    "message": "获取窗口标识成功",
    "data": {
        // 基础信息
        "windowId": "12345",
        "packageName": "com.example.app",
        "className": "com.example.app.MainActivity",
        "windowTitle": "首页",

        // 页面特征
        "titleText": "欢迎使用",
        "keyResourceIds": ["toolbar", "content_frame", "bottom_nav"],
        "pageFeature": "title:123456789|ids:toolbar,content_frame,bottom_nav",

        // 标识符（推荐使用）
        "identifier": "com.example.app/com.example.app.MainActivity|title:123456789|ids:toolbar,content_frame,bottom_nav",
        "stableId": "com.example.app/com.example.app.MainActivity",
        "legacyIdentifier": "com.example.app:com.example.app.MainActivity@12345"
    }
}
```

## 使用场景

### 1. 精确页面识别
使用 `identifier` 进行精确匹配，可以区分同一 Activity 的不同状态：

```kotlin
// 示例：判断是否在同一个页面
val currentId = getCurrentWindowId()?.get("identifier")
if (currentId == savedId) {
    // 仍在同一页面（包括页面内容相同）
}
```

### 2. 粗粒度页面匹配
使用 `stableId` 进行页面级匹配，忽略页面内容变化：

```kotlin
// 示例：判断是否在同一个 Activity
val currentStableId = getCurrentWindowId()?.get("stableId")
if (currentStableId == "com.example.app/com.example.app.MainActivity") {
    // 在 MainActivity，但可能内容不同
}
```

### 3. 页面变化监控
结合 `onAccessibilityEvent` 监听页面切换：

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
        val windowInfo = getCurrentWindowId()
        // 处理页面切换
        onPageChanged(windowInfo)
    }
}
```

### 4. 任务上下文追踪
记录用户操作时所在的窗口：

```kotlin
val windowInfo = getCurrentWindowId()
taskLog.add(TaskStep(
    action = "click",
    windowId = windowInfo?.get("identifier"),
    packageName = windowInfo?.get("packageName"),
    timestamp = System.currentTimeMillis()
))
```

## 性能优化

### 节点遍历深度限制
- 标题文本查找：最多4层
- Resource ID 查找：最多5层、最多10个ID

### 节点回收
所有 `AccessibilityNodeInfo` 都正确回收，避免内存泄漏

### Windows API 优先
在 Android 5.0+ 上优先使用 `windows` API 获取更准确的活动窗口

## 兼容性

| 特性 | 最低API | 说明 |
|------|---------|------|
| 基础功能 | API 14+ | 包名、类名、窗口ID |
| Windows API | API 21+ | 更准确的活动窗口获取 |
| 窗口标题 | API 24+ | 系统提供的窗口标题 |

## 注意事项

1. **权限要求**: 需要启用无障碍服务
2. **性能影响**: 页面特征提取涉及节点遍历，建议按需调用
3. **标识符稳定性**:
   - `stableId` 在同一页面保持不变
   - `identifier` 在页面内容变化时会改变
4. **隐私考虑**: 标题文本使用哈希存储，避免泄露敏感信息

## 调试日志

启用后会输出详细日志：

```
D/MyAccessibilityService: 获取当前窗口标识（增强版）
D/MyAccessibilityService: 窗口标识: com.example.app/MainActivity|title:123456789|ids:toolbar,content
D/MyAccessibilityService:   - 包名: com.example.app
D/MyAccessibilityService:   - 类名: com.example.MainActivity
D/MyAccessibilityService:   - 标题: 首页
D/MyAccessibilityService:   - 页面特征: title:123456789|ids:toolbar,content
```

## 与旧版本对比

| 字段 | 旧版本 | 新版本 |
|------|--------|--------|
| 返回类型 | `Map<String, String?>` | `Map<String, Any?>` |
| 标识符 | 单一 identifier | 三级标识符 |
| 页面特征 | ❌ | ✅ |
| 窗口标题 | ❌ | ✅ |
| 控件ID | ❌ | ✅ |
| 向后兼容 | - | 保留 legacyIdentifier |

## 示例：页面切换检测

```kotlin
class PageTracker {
    private var lastWindowId: String? = null
    private var lastPageFeature: String? = null

    fun onWindowChanged(service: MyAccessibilityService) {
        val windowInfo = service.getCurrentWindowId() ?: return

        val currentId = windowInfo["identifier"] as? String
        val currentFeature = windowInfo["pageFeature"] as? String

        when {
            // 完全不同的页面
            currentId != lastWindowId -> {
                onPageChanged(windowInfo)
            }
            // 同一页面但内容变化
            currentFeature != lastPageFeature -> {
                onPageContentChanged(windowInfo)
            }
        }

        lastWindowId = currentId
        lastPageFeature = currentFeature
    }
}
```
