# XueHuaSDK

XueHua IM 的 Kotlin Multiplatform SDK，支持 Android、iOS、JVM、JS 与 Wasm。

## 模块结构

| 模块        | 说明                                                                               |
|-----------|----------------------------------------------------------------------------------|
| `:shared` | 对外发布的 KMP SDK（网络、同步、数据库、事件等）                                                     |
| `:proto`  | OpenIM `.proto` 定义，使用 [Square Wire](https://square.github.io/wire/) 生成 Kotlin 代码 |

## 环境要求

- JDK 17+
- Gradle 9.5+（见 `gradle/wrapper/gradle-wrapper.properties`）
- Android Studio / IntelliJ IDEA（推荐）

## 依赖版本管理：`gradle/libs.versions.toml`

本项目使用 Gradle **Version Catalog** 统一管理插件与依赖版本，配置文件位于：

```
gradle/libs.versions.toml
```

### 目录结构说明

`libs.versions.toml` 分为三段：

```toml
[versions]
# 版本号集中定义
kotlin = "2.4.0"
wire = "6.4.0"
ktor = "3.5.0"
# ...

[libraries]
# 依赖别名，引用 [versions] 中的 version.ref
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
wire-runtime = { module = "com.squareup.wire:wire-runtime", version.ref = "wire" }
# ...

[plugins]
# Gradle 插件别名
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
wire = { id = "com.squareup.wire", version.ref = "wire" }
# ...
```

### 在本仓库中的用法

**根工程** `build.gradle.kts` — 声明插件（不立即应用）：

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.wire) apply false
    // ...
}
```

**子模块** `shared/build.gradle.kts` — 应用插件与依赖：

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.wire.runtime)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}
```

**Android 编译参数** 同样来自 catalog：

```kotlin
android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
}
```

> `settings.gradle.kts` 中已启用 `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`，因此可通过 `libs.xxx` 类型安全访问 catalog 条目。

### 新增或升级依赖

1. 在 `[versions]` 增加或修改版本号
2. 在 `[libraries]` 或 `[plugins]` 增加别名
3. 在对应模块的 `build.gradle.kts` 中使用 `libs.xxx` 引用

示例 — 添加 OkHttp 版本：

```toml
# gradle/libs.versions.toml
[versions]
okhttp = "4.12.0"

[libraries]
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

```kotlin
// shared/build.gradle.kts
androidMain.dependencies {
    implementation(libs.okhttp)
}
```

## 构建

```bash
# 编译 JVM
./gradlew :shared:compileKotlinJvm

# 运行 JVM 单元测试
./gradlew :shared:jvmTest

# 编译 iOS / Wasm
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinWasmJs

# 全量 assemble
./gradlew :shared:assemble
```

## 发布到本地 Maven

```bash
./gradlew :shared:publishToMavenLocal
```

发布坐标（见 `shared/build.gradle.kts`）：

- **groupId:** `io.github.matkurban`
- **artifactId:** `xuehuasdk`
- **version:** `1.0.0`

## 在业务项目中引入 SDK

### 方式一：Version Catalog（推荐）

在业务工程的 `gradle/libs.versions.toml` 中声明 SDK 版本：

```toml
[versions]
xuehuasdk = "1.0.0"

[libraries]
xuehuasdk = { module = "io.github.matkurban:xuehuasdk", version.ref = "xuehuasdk" }
```

`shared/build.gradle.kts` 依赖：

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.xuehuasdk)
        }
    }
}
```

Android 额外依赖（若使用 AAR 变体）：

```kotlin
dependencies {
    implementation(libs.xuehuasdk)
}
```

### 方式二：直接坐标

```kotlin
// commonMain
implementation("io.github.matkurban:xuehuasdk:1.0.0")
```

### 方式三：本地模块 / composite build

```kotlin
// settings.gradle.kts
includeBuild("../XueHuaSDK")

// build.gradle.kts
implementation(project(":shared"))
```

## 快速开始

```kotlin
import com.kurban.xuehuaim.sdk.OpenIM
import com.kurban.xuehuaim.sdk.config.InitConfig

OpenIM.initialize(
    InitConfig(
        apiAddr = "https://your-api.example.com",
        wsAddr = "wss://your-ws.example.com",
        authAddr = "https://your-auth.example.com",
    ),
)

val imManager = OpenIM.iMManager
// imManager.login(...)

// API 命名与 openim_sdk (Dart) 一致，例如：
// imManager.messageManager.createVideoMessageFromBytes(...)
// imManager.groupManager.getGroupMemberList(groupID, filter, offset, count)
// imManager.friendshipManager.getFriendList(filterBlack = true)
// imManager.momentsManager.getMomentList(ownerUserID = null, pageNumber = 1, showNumber = 20)
// imManager.favoriteManager.addMessage(message)
// imManager.userManager.deleteAccount(currentPassword)
```

## 主要技术栈

| 能力       | 库                     | catalog 别名                                |
|----------|-----------------------|-------------------------------------------|
| 网络       | Ktor Client           | `libs.ktor.client.*`                      |
| Protobuf | Square Wire           | `libs.wire.runtime` / `libs.plugins.wire` |
| 本地数据库    | SQLDelight            | `libs.sqldelight.*`                       |
| 序列化      | kotlinx.serialization | `libs.kotlinx.serialization.json`         |
| DI       | Koin                  | `libs.koin.core`                          |
| 协程       | kotlinx.coroutines    | `libs.kotlinx.coroutines.core`            |

## 许可证

请根据项目实际情况补充许可证信息。
