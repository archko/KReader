import org.gradle.kotlin.dsl.implementation
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose.android)
        }
        commonMain.dependencies {
            implementation(project(":lib"))

            implementation(libs.androidx.navigation.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            implementation(libs.filePicker)

            api(libs.coil.kt)
            api(libs.coil.kt.compose)
            api(libs.coil.kt.okhttp)
        }
        desktopMain.dependencies {
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.archko.reader.viewer"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.archko.reader.viewer"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
    signingConfigs {
        named("debug") {
            storeFile = rootProject.file("composeApp/release_key.jks")
            storePassword = ""
            keyAlias = ""
            keyPassword = ""
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }

        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.archko.reader.viewer.MainKt"

        nativeDistributions {
            modules("java.instrument", "java.sql", "jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Dragon Viewer"
            packageVersion = "1.0.0"
        }
    }
}

// 根据操作系统和架构设置 java.library.path
tasks.withType<JavaExec> {
    doFirst {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())

        var libDir: String? = null
        if (osName.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                libDir = "${projectDir}/src/commonMain/resources/macos-aarch64"
            } else if (arch.contains("x86_64")) {
                libDir = "${projectDir}/src/commonMain/resources/macos-x64"
            }
        }

        if (libDir != null) {
            val existingPath = System.getProperty("java.library.path")
            val newPath = if (existingPath.isNullOrEmpty()) {
                libDir
            } else {
                "$existingPath:$libDir"
            }
            systemProperty("java.library.path", newPath)
        }
    }
}

// 排除 dylib 文件被打进 JAR
tasks.withType<Jar> {
    exclude("macos-aarch64/**")
    exclude("macos-x64/**")
}

// 复制 dylib 文件到 JAR 输出目录
tasks.register<Copy>("copyDylibFiles") {
    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
    val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())

    if (osName.contains("mac")) {
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            from("src/commonMain/resources/macos-aarch64")
        } else if (arch.contains("x86_64")) {
            from("src/commonMain/resources/macos-x64")
        }
    }
    // 目标路径为.app包内的目录
    val targetDir = layout.buildDirectory.dir("app").get().asFile
    into(targetDir)

    // 确保目标目录存在
    doFirst {
        targetDir.mkdirs()
    }
}

// 确保此任务在打包应用之前运行
tasks.named("assemble") { dependsOn("copyDylibFiles") }