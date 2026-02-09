# 语音唤醒功能集成检查清单

根据[科大讯飞语音唤醒 Android SDK 文档](https://www.xfyun.cn/doc/asr/awaken/Android-SDK.html)整理

## ✅ 已完成的代码实现

- [x] `WakeUpService.kt` - 使用IWakeup接口实现唤醒功能
- [x] `MyApplication.kt` - 添加MSC SDK初始化代码（需取消注释）
- [x] `build.gradle.kts` - 配置依赖（fileTree包含所有aar/jar）
- [x] `AndroidManifest.xml` - 已包含必要权限（RECORD_AUDIO, INTERNET）

## 📋 需要完成的集成步骤

### 1. 下载MSC SDK

- [ ] 访问[科大讯飞控制台](https://console.xfyun.cn/app/myapp)
- [ ] 进入"语音唤醒"服务页面
- [ ] 下载Android SDK（MSC SDK）
- [ ] SDK应包含：
  - `msc.jar` - Java库文件
  - `libmsc.so` - 原生库文件（多个架构）

### 2. 集成SDK文件

#### 2.1 添加msc.jar
- [ ] 将 `msc.jar` 放入 `app/libs/` 目录
- [ ] 确保 `build.gradle.kts` 中包含：
  ```kotlin
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
  ```

#### 2.2 添加libmsc.so（重要！）
根据文档要求，需要：
- [ ] 创建目录：`app/src/main/jniLibs/`
- [ ] 创建架构子目录：
  - `app/src/main/jniLibs/armeabi-v7a/` （推荐，arm架构）
  - `app/src/main/jniLibs/arm64-v8a/` （64位arm架构）
  - `app/src/main/jniLibs/x86/` （如需要模拟器支持）
- [ ] 将对应架构的 `libmsc.so` 放入相应目录

**注意**：文档明确说明需要在 `main` 文件夹下新建 `jniLibs` 并拷贝 `libmsc.so`

### 3. 配置唤醒资源文件

- [ ] 在科大讯飞控制台创建自定义唤醒词"小零小零"
- [ ] 下载生成的唤醒资源文件 `res.bin`
- [ ] 创建目录：`app/src/main/assets/wakeup/`
- [ ] 将 `res.bin` 放入该目录：
  ```
  app/src/main/assets/wakeup/res.bin
  ```

### 4. 初始化MSC SDK

- [ ] 在 `MyApplication.kt` 中取消注释以下代码：
  ```kotlin
  import com.iflytek.cloud.SpeechUtility
  
  // 在onCreate中
  val param = "appid=$IFLYTEK_APP_ID"
  SpeechUtility.createUtility(this, param)
  ```

### 5. 验证集成

- [ ] 同步项目（Sync Project with Gradle Files）
- [ ] 检查编译是否成功
- [ ] 检查日志中是否有 "IWakeup初始化成功"
- [ ] 运行应用，测试唤醒功能

## 🔍 代码检查点

### WakeUpService.kt
- [x] 使用 `IWakeup.createWakeuper()` 创建实例
- [x] 设置 `SpeechConstant.IVW_RES_PATH` 参数
- [x] 设置 `SpeechConstant.IVW_THRESHOLD` 参数
- [x] 实现 `WakeuperListener` 接口
- [x] 在 `onResult` 中处理唤醒事件

### MyApplication.kt
- [ ] 取消注释 `SpeechUtility.createUtility()` 代码
- [ ] 确保导入 `com.iflytek.cloud.SpeechUtility`

### build.gradle.kts
- [x] 包含 `fileTree` 配置以加载libs目录下的文件

### AndroidManifest.xml
- [x] 包含 `RECORD_AUDIO` 权限
- [x] 包含 `INTERNET` 权限

## ⚠️ 常见问题

### 问题1：找不到IWakeup类
**原因**：MSC SDK未正确集成  
**解决**：
- 检查 `msc.jar` 是否在 `app/libs/` 目录
- 检查 `build.gradle.kts` 中的依赖配置
- 重新同步项目

### 问题2：找不到libmsc.so
**原因**：原生库文件未正确放置  
**解决**：
- 检查 `libmsc.so` 是否在 `app/src/main/jniLibs/` 目录下
- 确保目录结构正确：`jniLibs/armeabi-v7a/libmsc.so`
- 检查是否包含目标设备的架构版本

### 问题3：唤醒资源文件路径错误
**原因**：res.bin文件路径不正确  
**解决**：
- 确保文件在 `app/src/main/assets/wakeup/res.bin`
- 检查 `WakeUpService.kt` 中的路径常量：`WAKE_UP_RES_PATH = "assets://wakeup/res.bin"`

### 问题4：SpeechUtility未初始化
**原因**：MyApplication中未初始化MSC SDK  
**解决**：
- 取消注释 `SpeechUtility.createUtility()` 代码
- 确保在 `IWakeup.createWakeuper()` 之前初始化

## 📚 参考文档

- [语音唤醒 Android SDK 文档](https://www.xfyun.cn/doc/asr/awaken/Android-SDK.html)
- [MSC Android API 文档](http://doc.xfyun.cn/msc_android/)
- [错误码查询](https://www.xfyun.cn/doc/errorcode/)

## 🎯 当前状态

代码实现已完成，符合官方文档要求。需要完成：
1. 下载并集成MSC SDK（msc.jar和libmsc.so）
2. 配置唤醒资源文件（res.bin）
3. 在MyApplication中初始化SpeechUtility

完成以上步骤后，唤醒功能即可正常使用。

