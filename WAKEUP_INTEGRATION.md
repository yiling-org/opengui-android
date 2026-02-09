# 语音唤醒功能集成说明

## 概述

本项目已实现基于科大讯飞官方 IWakeup 接口的语音唤醒功能。需要集成 MSC SDK 才能使用。

## 集成步骤

### 1. 下载 MSC SDK

1. 访问 [科大讯飞开放平台](https://console.xfyun.cn/app/myapp)
2. 登录后，进入"语音唤醒"服务页面
3. 下载 Android SDK（MSC SDK）
4. SDK 包含以下文件：
   - `msc.jar` - Java 库文件
   - `libmsc.so` - 原生库文件（多个架构：armeabi-v7a, arm64-v8a, x86, x86_64）

### 2. 配置唤醒词

1. 在科大讯飞控制台的"语音唤醒"服务中，创建自定义唤醒词
2. 设置唤醒词为"小零小零"
3. 下载生成的唤醒资源文件 `res.bin`

### 3. 集成到项目

#### 3.1 添加 SDK 文件

将下载的 MSC SDK 文件放入项目：

```
app/libs/
  ├── msc.jar          # 从MSC SDK中复制
  └── libmsc.so        # 从MSC SDK中复制（各架构版本）
```

#### 3.2 添加原生库

在 `app/build.gradle.kts` 中确保已配置：

```kotlin
android {
    // ...
    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']  // 如果使用jniLibs目录
        }
    }
}
```

或者将 `libmsc.so` 文件放入：
```
app/src/main/jniLibs/
  ├── armeabi-v7a/
  │   └── libmsc.so
  ├── arm64-v8a/
  │   └── libmsc.so
  └── ...
```

#### 3.3 添加唤醒资源文件

1. 创建目录：`app/src/main/assets/wakeup/`
2. 将下载的 `res.bin` 文件放入该目录：
   ```
   app/src/main/assets/wakeup/res.bin
   ```

#### 3.4 初始化 MSC SDK

在 `MyApplication.kt` 中，取消注释 MSC SDK 初始化代码：

```kotlin
// 初始化讯飞 MSC SDK（用于语音唤醒IWakeup接口）
val param = "appid=$IFLYTEK_APP_ID"
SpeechUtility.createUtility(this, param)
```

注意：需要导入：
```kotlin
import com.iflytek.cloud.SpeechUtility
```

### 4. 验证集成

1. 编译项目，确保没有编译错误
2. 运行应用，检查日志中是否有 "IWakeup初始化成功"
3. 说"小零小零"测试唤醒功能

## 常见问题

### 问题1：找不到 IWakeup 类

**原因**：MSC SDK 未正确集成

**解决**：
- 检查 `msc.jar` 是否在 `app/libs/` 目录
- 检查 `build.gradle.kts` 中是否包含 `implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))`
- 重新同步项目（Sync Project）

### 问题2：找不到 libmsc.so

**原因**：原生库文件未正确放置

**解决**：
- 检查 `libmsc.so` 是否在正确的架构目录下
- 确保目录结构正确：`app/src/main/jniLibs/armeabi-v7a/libmsc.so`

### 问题3：唤醒资源文件路径错误

**原因**：`res.bin` 文件路径不正确

**解决**：
- 确保文件在 `app/src/main/assets/wakeup/res.bin`
- 检查 `WakeUpService.kt` 中的路径常量：`WAKE_UP_RES_PATH = "assets://wakeup/res.bin"`

### 问题4：唤醒不响应

**原因**：唤醒门限值设置不当

**解决**：
- 调整 `WakeUpService.kt` 中的 `WAKE_UP_THRESHOLD` 值
- 范围：0-100，值越大越难唤醒
- 建议值：1450-1550

### 问题5：编译错误：找不到 SpeechUtility

**原因**：MSC SDK 未集成或导入错误

**解决**：
- 确保已下载并集成 MSC SDK
- 检查导入语句：`import com.iflytek.cloud.SpeechUtility`

## 代码说明

### WakeUpService.kt

主要功能：
- 使用 `IWakeup` 接口监听唤醒词
- 检测到唤醒后播放"在"的TTS回复
- 自动启动语音识别，识别用户语音
- 识别完成后调用 `sendToAgent`

关键配置：
- `WAKE_UP_RES_PATH`: 唤醒资源文件路径
- `WAKE_UP_THRESHOLD`: 唤醒门限值（0:1450）

### MyApplication.kt

需要初始化 MSC SDK：
```kotlin
SpeechUtility.createUtility(this, "appid=$IFLYTEK_APP_ID")
```

## 参考文档

- [科大讯飞语音唤醒 Android SDK 文档](https://www.xfyun.cn/doc/asr/awaken/Android-SDK.html)
- [MSC Android API 文档](http://doc.xfyun.cn/msc_android/)
- [错误码查询](https://www.xfyun.cn/doc/errorcode/)

## 注意事项

1. MSC SDK 和 SparkChain SDK 可以同时使用，互不冲突
2. 唤醒资源文件 `res.bin` 必须与配置的唤醒词匹配
3. 确保应用有录音权限（RECORD_AUDIO）
4. 唤醒功能需要网络连接（首次使用或更新资源时）

