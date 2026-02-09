# 编译验证说明

## 验证步骤

### 1. 在 Android Studio 中验证

1. **同步项目**
   - 点击 "Sync Project with Gradle Files" 按钮
   - 或使用快捷键：`Cmd+Shift+O` (Mac) / `Ctrl+Shift+O` (Windows)

2. **检查依赖**
   - 在 Project 视图中，展开 `External Libraries`
   - 应该能看到：
     - `AIKit.aar`
     - `SparkChain.aar`

3. **检查导入**
   - 打开 `WakeUpService.kt`
   - 检查 `import com.iflytek.cloud.*` 是否没有红色下划线
   - 如果显示错误，说明 AIKit.aar 可能不包含 MSC SDK 的类

### 2. 使用 Gradle 命令验证

在终端中运行：

```bash
# 清理并编译
./gradlew clean build

# 或者只编译 debug 版本
./gradlew assembleDebug

# 检查依赖树
./gradlew :app:dependencies | grep -i "aikit\|sparkchain"
```

### 3. 检查编译错误

如果编译失败，常见原因：

#### 错误1：找不到 IWakeup 类
```
Unresolved reference: IWakeup
```

**原因**：AIKit.aar 可能不包含 MSC SDK 的 IWakeup 接口

**解决方案**：
- AIKit.aar 可能只包含部分功能，需要单独下载 MSC SDK
- 从[科大讯飞控制台](https://console.xfyun.cn/app/myapp)下载完整的 MSC SDK
- 将 `msc.jar` 和 `libmsc.so` 添加到项目中

#### 错误2：找不到 SpeechConstant
```
Unresolved reference: SpeechConstant
```

**原因**：同上，需要 MSC SDK

#### 错误3：找不到 SpeechUtility
```
Unresolved reference: SpeechUtility
```

**原因**：MyApplication.kt 中需要初始化 MSC SDK

**解决方案**：
- 在 `MyApplication.kt` 中取消注释 MSC SDK 初始化代码
- 确保已导入：`import com.iflytek.cloud.SpeechUtility`

## 验证清单

- [ ] `app/libs/AIKit.aar` 文件存在
- [ ] `app/build.gradle.kts` 中包含 `fileTree` 配置
- [ ] 项目同步成功，无红色错误
- [ ] `WakeUpService.kt` 中的导入语句无错误
- [ ] 如果使用 IWakeup，需要 MSC SDK（msc.jar 和 libmsc.so）

## 注意事项

1. **AIKit.aar vs MSC SDK**
   - AIKit.aar 可能是一个封装库，不一定包含完整的 MSC SDK
   - 如果 AIKit.aar 不包含 IWakeup 接口，需要单独下载 MSC SDK

2. **依赖冲突**
   - 如果同时使用 SparkChain.aar 和 MSC SDK，确保它们兼容
   - 检查是否有类冲突或版本不匹配

3. **原生库**
   - 如果使用 MSC SDK，需要确保 `libmsc.so` 在正确的架构目录下
   - 目录结构：`app/src/main/jniLibs/armeabi-v7a/libmsc.so`

## 如果 AIKit.aar 不包含 IWakeup

如果验证发现 AIKit.aar 不包含 IWakeup 接口，需要：

1. 下载完整的 MSC SDK（包含 msc.jar 和 libmsc.so）
2. 将文件添加到项目：
   ```
   app/libs/msc.jar
   app/src/main/jniLibs/armeabi-v7a/libmsc.so
   app/src/main/jniLibs/arm64-v8a/libmsc.so
   ```
3. 在 `build.gradle.kts` 中确保包含：
   ```kotlin
   implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
   ```
4. 在 `MyApplication.kt` 中初始化 MSC SDK

