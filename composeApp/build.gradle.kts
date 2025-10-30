import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
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
            implementation(libs.ktor.serialization.kotlinx.json)
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
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get().toString()
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
    android.applicationVariants.all {
        val variant = this
        outputs.all {
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                //修改apk名称
                this.outputFileName = "KReader-${variant.versionName}.apk"
            }
        }
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.archko.reader.viewer.MainKt"
        
        // 为打包的应用配置 JVM 参数，让应用能找到 dylib 文件
        jvmArgs += listOf(
            "-Djava.library.path=\$APPDIR/../Resources/macos-aarch64:\$APPDIR/../Resources/macos-x64"
        )

        nativeDistributions {
            modules("java.instrument", "java.sql", "jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KReader"
            packageVersion = "1.1.0"

            // 应用描述
            description = "A PDF and document reader application"
            copyright = "© 2025 KReader. All rights reserved."
            vendor = "KReader"

            // macOS 特定配置
            macOS {
                bundleID = "com.archko.reader.viewer"
                // 使用生成的 ICNS 图标文件
                iconFile.set(project.file("src/desktopMain/resources/app_icon.icns"))

                packageName = "KReader"

                // 使用完整的 Info.plist 文件配置文件关联
                infoPlist {
                    extraKeysRawXml = file("src/desktopMain/resources/Info.plist").readText()
                        .substringAfter("<dict>")
                        .substringBeforeLast("</dict>")
                }
            }
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

// 复制 dylib 文件到应用包内
tasks.register<Copy>("copyNativeLibs") {
    // 复制两个架构的 dylib 文件到应用包的 Resources 目录
    from("src/commonMain/resources/macos-aarch64") {
        into("macos-aarch64")
    }
    from("src/commonMain/resources/macos-x64") {
        into("macos-x64")
    }
    
    // 目标路径为应用包的 Contents/Resources 目录
    into(layout.buildDirectory.dir("compose/binaries/main/app/KReader.app/Contents/Resources"))
    
    doFirst {
        val targetDir = layout.buildDirectory.dir("compose/binaries/main/app/KReader.app/Contents/Resources").get().asFile
        targetDir.mkdirs()
        println("Copying native libraries to: ${targetDir.absolutePath}")
    }
    
    // 只在 macOS 上执行
    onlyIf {
        System.getProperty("os.name").lowercase(Locale.getDefault()).contains("mac")
    }
}

// 新版本 Compose Multiplatform 的任务名称
// 确保在创建应用包时复制 dylib 文件
tasks.matching { it.name == "createDistributable" || it.name == "prepareAppResources" }.configureEach {
    finalizedBy("copyNativeLibs")
}

// 确保在打包 DMG 前复制了文件
tasks.matching { it.name == "packageDmg" || it.name == "packageDistributionForCurrentOS" }.configureEach {
    dependsOn("copyNativeLibs")
}

// 为了确保文件被正确复制，也在相关任务后执行
tasks.matching { it.name.contains("packageUberJar") || it.name.contains("runDistributable") }.configureEach {
    finalizedBy("copyNativeLibs")
}