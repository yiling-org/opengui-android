# 后台启动应用权限配置指南

## 问题说明

**现象**：App 在前台时可以正常打开其他应用，但在后台时无法打开。

**原因**：从 Android 10 开始，系统对后台启动 Activity 有严格限制，特别是小米等国产手机厂商会有额外的限制。

---

## 解决方案

项目已实现多层启动策略（优先级从高到低）：

### 1. 无障碍服务启动（最可靠）✅
- **位置**: `WebSocketClient.kt:796-813`
- **原理**: 无障碍服务可以绕过大部分后台限制
- **配置**: 在设置页面启用无障碍服务即可

### 2. 前台服务上下文启动 ✅
- **位置**: `WebSocketClient.kt:815-826`
- **原理**: 前台服务拥有启动 Activity 的权限
- **当前**: `WebSocketService` 已是前台服务，自动生效

### 3. 透明跳板 Activity + 悬浮窗权限 ✅
- **位置**: `LaunchBridgeActivity.kt` + `WebSocketClient.kt:828-842`
- **原理**: 悬浮窗权限可以让应用在后台显示界面
- **配置**: 需要用户授予悬浮窗权限

### 4. 直接启动（仅前台有效）
- **位置**: `WebSocketClient.kt:844-866`
- **说明**: 兜底方案，仅在应用前台时有效

---

## 权限配置步骤

### 必需权限

#### 1. 无障碍服务（强烈推荐）
```kotlin
// 在 SettingsActivity 中已有开启按钮
binding.openAccessibilityButton.setOnClickListener {
    openAccessibilitySettings()
}
```

#### 2. 悬浮窗权限（Android 6.0+）
```kotlin
// 使用 PermissionHelper 工具类
import top.yling.ozx.guiagent.util.PermissionHelper

// 检查权限
if (!PermissionHelper.hasOverlayPermission(this)) {
    PermissionHelper.requestOverlayPermission(this)
}
```

**在 SettingsActivity 中添加（示例）**：
```kotlin
// 在 setupUI() 方法中添加
binding.checkPermissionsButton.setOnClickListener {
    PermissionHelper.checkAllPermissions(this, showDialog = true)
}
```

#### 3. 电池优化白名单
```kotlin
// 当前已在 SettingsActivity 实现
binding.requestBatteryButton.setOnClickListener {
    WebSocketService.requestIgnoreBatteryOptimizations(this)
}
```

---

## 小米设备特殊配置 ⚠️

小米手机需要额外配置**自启动权限**和**后台弹出界面权限**：

### 自动检测并引导
```kotlin
// 在 MainActivity 或 SettingsActivity 的 onCreate 中添加
if (PermissionHelper.isXiaomiDevice()) {
    PermissionHelper.guideToXiaomiAutoStart(this)
}
```

### 手动设置路径
1. 打开"安全中心"
2. 进入"应用管理"
3. 找到"mysmarter"应用
4. 开启以下权限：
   - ✅ 自启动
   - ✅ 后台弹出界面
   - ✅ 显示悬浮窗

---

## 快速集成方案

### 方案 A：在启动时一次性检查所有权限

在 `MainActivity.onCreate()` 中添加：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 检查所有权限（首次启动时显示引导）
    val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    val isFirstLaunch = prefs.getBoolean("first_launch", true)

    if (isFirstLaunch) {
        PermissionHelper.checkAllPermissions(this, showDialog = true)
        prefs.edit().putBoolean("first_launch", false).apply()
    }
}
```

### 方案 B：在设置页面添加权限检查按钮

修改 `activity_settings.xml`，在电池优化按钮下方添加：

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/checkPermissionsButton"
    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
    android:layout_width="match_parent"
    android:layout_height="44dp"
    android:layout_marginTop="12dp"
    android:text="检查后台启动权限"
    android:textColor="@color/accent_gold"
    app:strokeColor="@color/accent_gold" />
```

在 `SettingsActivity.setupUI()` 中添加：

```kotlin
// 检查后台启动权限
binding.checkPermissionsButton.setOnClickListener {
    val status = PermissionHelper.checkAllPermissions(this, showDialog = false)

    if (!status.overlayPermission) {
        PermissionHelper.requestOverlayPermission(this)
    } else if (!status.batteryOptimization) {
        PermissionHelper.requestIgnoreBatteryOptimizations(this)
    } else if (status.isXiaomiDevice) {
        PermissionHelper.guideToXiaomiAutoStart(this)
    } else {
        Toast.makeText(this, "所有权限已配置", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 验证配置

### 使用诊断工具（推荐）

项目内置了完整的诊断工具，可以自动检测所有配置问题：

```kotlin
import top.yling.ozx.guiagent.util.LaunchDiagnostics

// 在设置页面或测试代码中
val report = LaunchDiagnostics.checkSystemStatus(this)
LaunchDiagnostics.printReport(report)

// 或者在启动应用前进行预检查
val preflightResult = LaunchDiagnostics.preflightCheck(this, "com.example.targetapp")
if (!preflightResult.canProceed) {
    Log.w(TAG, "启动前检查失败，建议先解决配置问题")
}
```

诊断报告示例：
```
==================== 启动诊断报告 ====================
设备信息:
  厂商: Xiaomi
  型号: Mi 10
  Android版本: 31
  是否小米设备: 是

应用状态:
  应用在前台: ✗ 否
  上下文类型: WebSocketService

权限状态:
  无障碍服务已启用: ✓ 是
  无障碍服务实例: ✓ 存在
  悬浮窗权限: ✗ 未授予
  电池优化白名单: ✓ 已加入

建议:
  1. ⚠️ 请授予悬浮窗权限 (SYSTEM_ALERT_WINDOW)
  2. ⚠️ 小米设备需要额外配置：
     - 打开"安全中心" → "应用管理"
     - 找到应用，开启"自启动"
     - 找到应用，开启"后台弹出界面"
======================================================
```

### 测试步骤
1. 使用诊断工具检查配置
2. 根据建议完成所有配置
3. 将应用切换到后台（按 Home 键）
4. 通过 WebSocket 发送 `open_app` 指令
5. 观察目标应用是否成功启动

### 查看详细日志

使用 `adb logcat` 查看完整的启动流程：
```bash
# 查看所有相关日志
adb logcat | grep -E "WebSocketClient|LaunchBridgeActivity|LaunchDiagnostics"

# 只看错误和警告
adb logcat *:E *:W | grep -E "WebSocketClient|LaunchBridgeActivity"
```

**新版本的详细启动日志示例：**
```
========== 开始启动应用: com.android.settings ==========
==================== 启动诊断报告 ====================
[诊断信息...]
======================================================
启动前警告:
  - 应用在后台且无障碍服务未启用，启动可能失败
目标Intent准备完成 - 悬浮窗权限: true
使用无障碍服务启动应用: com.android.settings
无障碍服务启动命令已执行
无障碍服务启动完成: com.android.settings

# 如果所有策略都失败，会输出：
========== 启动失败诊断 ==========
应用包名: com.android.settings
无障碍服务: ✗ 未启用
悬浮窗权限: ✓ 已授予
上下文类型: WebSocketService
Android版本: 31
设备厂商: Xiaomi

已尝试的策略：
- 无障碍服务启动 - 跳过（服务未启用）
✗ PendingIntent 启动 - 失败
✗ 前台服务启动 - 失败
✗ 跳板Activity启动 - 失败
✗ 直接启动 - 失败

建议解决方案：
1. ⚠️ 确保已启用无障碍服务（最重要）
2. ⚠️ 授予悬浮窗权限 (SYSTEM_ALERT_WINDOW)
3. ⚠️ 加入电池优化白名单
4. ⚠️ 小米设备：进入"安全中心" → "应用管理" → 允许"自启动"和"后台弹出界面"
5. 尝试重启应用或设备
===================================
```

---

## 常见问题

### Q: 修复了哪些问题？

**v2 版本（最新）修复：**
1. ✅ **修复了异步启动导致的静默失败问题**
   - 旧版本使用 `Handler.post` 异步启动，即使失败也返回 true
   - 新版本使用 `CountDownLatch` 同步等待启动结果
   - 每个策略都会等待执行完成，确保真实反馈

2. ✅ **新增 PendingIntent 启动策略**
   - PendingIntent 是系统级启动方式，不受后台限制
   - 在某些场景下比直接 startActivity 更可靠

3. ✅ **新增完整的诊断工具**
   - 启动前自动检测所有配置问题
   - 失败时提供详细的诊断报告
   - 明确指出每个策略的执行结果

4. ✅ **优化 Intent Flags**
   - 添加 `FLAG_ACTIVITY_BROUGHT_TO_FRONT`
   - 添加 `FLAG_ACTIVITY_REORDER_TO_FRONT`
   - 确保应用能正确显示到前台

### Q: 为什么无障碍服务启动最可靠？
A: 无障碍服务是系统级服务，拥有更高的权限，可以绕过大部分后台启动限制。

### Q: 是否必须同时配置所有权限？
A: 理论上只需无障碍服务即可，但建议配置所有权限以确保在各种情况下都能正常工作。

### Q: 小米设备配置后仍然无法启动怎么办？
A:
1. 确认已授予"后台弹出界面"权限
2. 检查是否开启了"省电模式"，尝试关闭
3. 在"手机管家" → "应用管理" → "权限管理"中检查所有权限

### Q: 其他国产手机（OPPO/VIVO/华为）怎么办？
A: 可以参考 `PermissionHelper.kt` 中的小米设置方案，为其他厂商添加类似的引导逻辑。

---

## 技术细节

### 启动流程（以无障碍服务为例）
```kotlin
WebSocket 收到 open_app 指令
  ↓
WebSocketClient.openAppByPackage()
  ↓
检查无障碍服务是否启用
  ↓
使用 MyAccessibilityService.startActivity(targetIntent)
  ↓
成功启动目标应用
```

### 相关文件
- `LaunchBridgeActivity.kt` - 透明跳板 Activity
- `WebSocketClient.kt:762-871` - 应用启动逻辑
- `PermissionHelper.kt` - 权限管理工具类
- `AndroidManifest.xml:31` - 悬浮窗权限声明

---

## 总结

通过以上配置，应用可以在后台可靠地启动其他应用。建议按照以下优先级配置：

1. ✅ **启用无障碍服务**（必需，最可靠）
2. ✅ **授予悬浮窗权限**（推荐）
3. ✅ **加入电池优化白名单**（推荐）
4. ✅ **小米设备额外配置自启动权限**（小米设备必需）
