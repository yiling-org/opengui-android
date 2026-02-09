import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.rikka.refine)
    alias(libs.plugins.ksp)
}

// 从 local.properties 读取配置
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

// 获取配置值的辅助函数，支持默认值
fun getLocalProperty(key: String, defaultValue: String = ""): String {
    return localProperties.getProperty(key, defaultValue)
}

android {
    namespace = "top.yling.ozx.guiagent"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "top.yling.ozx.guiagent"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 从 local.properties 读取配置，注入到 BuildConfig
        buildConfigField("String", "WEBSOCKET_URL", "\"${getLocalProperty("WEBSOCKET_URL", "ws://localhost:8181/mcp/ws")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${getLocalProperty("API_BASE_URL", "http://localhost:8181")}\"")

        // 讯飞 SDK 配置（可选，开源版本可留空）
        buildConfigField("String", "IFLYTEK_APP_ID", "\"${getLocalProperty("IFLYTEK_APP_ID", "")}\"")
        buildConfigField("String", "IFLYTEK_API_KEY", "\"${getLocalProperty("IFLYTEK_API_KEY", "")}\"")
        buildConfigField("String", "IFLYTEK_API_SECRET", "\"${getLocalProperty("IFLYTEK_API_SECRET", "")}\"")
    }

    buildTypes {
        release {
            // 禁用代码混淆（Kotlin suspend + Retrofit 泛型与 R8 不兼容）
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lottie)
    
    // 讯飞 SDK - 需要手动下载后放入 libs 目录
    // SparkChain.aar - 大模型识别SDK
    // 下载地址：https://www.xfyun.cn/doc/spark/%E5%A4%A7%E6%A8%A1%E5%9E%8B%E8%AF%86%E5%88%AB.html
    // AIKit.aar - 语音唤醒等能力SDK（包含IWakeup接口）
    // 下载地址：https://www.xfyun.cn/doc/asr/awaken/Android-SDK.html
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    
    // GKD Selector - 节点选择器（从本地 Maven 仓库）
    implementation(libs.gkd.selector)
    
    // Shizuku API - 高级操作（需要 Shizuku 应用支持）
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    
    // Rikka Refine - 访问隐藏 API
    implementation(libs.rikka.refine.runtime)
    compileOnly(libs.rikka.refine.annotation)
    
    // Hidden API 模块 - 包含系统隐藏 API 接口定义
    compileOnly(project(":hidden_api"))
    
    // LSPosed HiddenApiBypass - 绕过隐藏 API 限制
    implementation(libs.lsposed.hiddenapibypass)
    
    // kotlinx-serialization
    implementation(libs.kotlinx.serialization.json)

    // AndroidX Security - 加密存储
    implementation(libs.androidx.security.crypto)

    // Room Database - 定时任务数据存储
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}