import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.mavenPublish)
}

val protoGeneratedDir =
    project(":proto").layout.buildDirectory.dir("generated/sources/proto/main/protokt")

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
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest { }
    }

    sourceSets {
        val databaseMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir("src/commonMain/database/kotlin")
            dependencies {
                implementation(libs.sqldelight.coroutines.extensions)
            }
        }
        val protoMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir(protoGeneratedDir)
            kotlin.srcDir("src/commonMain/proto-kotlin/kotlin")
            dependencies {
                implementation(libs.protokt.runtime)
            }
        }

        commonMain {
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
            dependsOn(protoMain)
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
            dependsOn(protoMain)
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.sqlite.jdbc)
                implementation(libs.logback.classic)
            }
        }
        jsMain {
            dependsOn(protoMain)
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.core)
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(":proto:generateProto")
}

tasks.configureEach {
    dependsOn(":proto:generateProto")
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

mavenPublishing {
    coordinates("io.github.matkurban", "xuehuasdk", "1.0.0")
    configure(KotlinMultiplatform())
    pom {
        name.set("XueHuaSDK")
        description.set("XueHua IM Kotlin Multiplatform SDK")
    }
}
