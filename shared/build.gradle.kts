import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.mavenPublish)
    signing
}

val syncWireSources = tasks.register<Sync>("syncWireSources") {
    description = "syncWireSources"
    dependsOn(":proto:generateMainProtos")
    from(project(":proto").layout.buildDirectory.dir("generated/source/wire"))
    into(layout.buildDirectory.dir("generated/wire"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "XueHuaSDK"
            isStatic = true
            linkerOpts.add("-lsqlite3")
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.kurban.xuehuaim.sdk"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withHostTest { }
    }

    sourceSets {
        val databaseMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir("src/databaseMain/kotlin")
            dependencies {
                implementation(libs.sqldelight.coroutines.extensions)
            }
        }

        commonMain {
            kotlin.srcDir(syncWireSources.map { it.destinationDir })
            dependencies {
                implementation(libs.kotlin.logging)
                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.websockets)
                implementation(libs.okio)
                implementation(libs.wire.runtime)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain {
            dependsOn(databaseMain)
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.android.driver)
            }
        }
        iosMain {
            dependsOn(databaseMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native.driver)
            }
        }
        jvmMain {
            dependsOn(databaseMain)
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.sqlite.jdbc)
                implementation(libs.logback.classic)
            }
        }
        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.core)
        }
    }
}

sqldelight {
    databases {
        create("OpenIMDatabase") {
            packageName.set("com.kurban.xuehuaim.sdk.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            srcDirs("src/commonMain/sqldelight")
        }
    }
}

signing{
    useGpgCmd()
    sign(publishing.publications["kotlinMultiplatform"])
}


mavenPublishing {
    coordinates("io.github.matkurban", "xuehuasdk", "1.0.0")
    configure(KotlinMultiplatform())
    pom {
        name.set("XueHuaSDK")
        description.set("XueHua IM Kotlin Multiplatform SDK")
    }
}
