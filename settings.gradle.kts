pluginManagement {
    repositories {
        // Google 官方仓库优先（用于获取最新版 AGP）
        google()
        mavenCentral()
        gradlePluginPortal()
        // 阿里云镜像作为备选
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 本地 Maven 仓库（用于 GKD selector 本地构建）
        mavenLocal()

        // ============================================================
        // GKD Selector 依赖配置说明：
        // 方案一：使用本地 Maven 仓库（推荐开源用户使用）
        //   1. 克隆 GKD 项目：git clone https://github.com/gkd-kit/gkd
        //   2. 构建并发布到本地：./gradlew :selector:publishToMavenLocal
        //   3. 依赖会从 mavenLocal() 解析
        //
        // 方案二：使用 JitPack（如果 GKD 作者发布到 JitPack）
        //   依赖格式：implementation("com.github.gkd-kit:gkd:selector:TAG")
        //
        // 方案三：使用私有 Maven 仓库（需要配置认证）
        //   在 ~/.gradle/gradle.properties 中配置：
        //   aliyunRespId=your_repo_id
        //   aliyunPackagesUsername=your_username
        //   aliyunPackagesPassword=your_password
        // ============================================================

        // 私有 Maven 仓库（可选，需要在 gradle.properties 中配置认证信息）
        val aliyunRespId = providers.gradleProperty("aliyunRespId").getOrElse("")
        if (aliyunRespId.isNotEmpty()) {
            maven {
                url = java.net.URI("https://packages.aliyun.com/64b4aa5f0ce788fc1c0865c9/maven/$aliyunRespId")
                credentials {
                    username = providers.gradleProperty("aliyunPackagesUsername").getOrElse("")
                    password = providers.gradleProperty("aliyunPackagesPassword").getOrElse("")
                }
            }
        }

        // 阿里云镜像（加速国内访问）
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }

        // 官方仓库
        google()
        mavenCentral()

        // JitPack（用于 GitHub 项目依赖）
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

// 项目名称（改名时需同步修改 app/src/main/res/values/strings.xml 中的 app_name 配置）
rootProject.name = "opengui-android"
include(":app")
include(":hidden_api")
