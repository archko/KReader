import org.gradle.kotlin.dsl.api
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
            jvmTarget.set(JvmTarget.JVM_21)
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

            implementation(libs.reorderable)

            api(libs.coil.kt)
            api(libs.coil.kt.compose)
            api(libs.coil.kt.okhttp)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
        }
        desktopMain.dependencies {
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.filePicker)
            implementation(libs.sonner)
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

val extractNativeLibs by tasks.registering(Copy::class) {
    dependsOn(configurations.getByName("desktopRuntimeClasspath"))
    from({
        configurations.getByName("desktopRuntimeClasspath").map { zipTree(it) }
    }) {
        include("macos-aarch64/**", "macos-x64/**", "linux-x64/**", "windows-64/**")
    }
    into(layout.buildDirectory.dir("nativeLibs"))
    outputs.dir(layout.buildDirectory.dir("nativeLibs"))
}

compose.desktop {
    application {
        mainClass = "com.archko.reader.viewer.MainKt"
        
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        val currentArch = System.getProperty("os.arch").lowercase(Locale.getDefault())
        val targetArch = project.findProperty("target.arch")?.toString()
            ?: System.getProperty("target.arch")
            ?: System.getenv("TARGET_ARCH")
            
        val archDir = when {
            targetArch in listOf("x64", "intel") -> "macos-x64"
            targetArch in listOf("aarch64", "arm64", "arm") -> "macos-aarch64"
            currentArch.contains("aarch64") || currentArch.contains("arm64") -> "macos-aarch64"
            else -> "macos-x64"
        }
            
        if (osName.contains("mac")) {
            jvmArgs += listOf("-Djava.library.path=\$APPDIR/../Resources/$archDir")
        } else if (osName.contains("win")) {
            jvmArgs += listOf("-Djava.library.path=\$APPDIR/windows-x64")
        }

        nativeDistributions {
            modules("java.instrument", "java.sql", "jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KReader"
            packageVersion = "1.2.3"

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

            // Windows 特定配置
            windows {
                packageName = "KReader"
                // 使用 PNG 图标文件，Compose Multiplatform 会自动转换为 ICO
                iconFile.set(project.file("src/desktopMain/resources/ic_launcher.png"))
                
                // Windows 安装程序配置
                menuGroup = "KReader"
                upgradeUuid = "61DAB35E-17CB-43B8-B24B-AB99F7A9B7A5"
                
                // 桌面快捷方式
                shortcut = true
                // 开始菜单快捷方式
                menu = true
            }
        }
    }
}

tasks.withType<JavaExec> {
    dependsOn("extractNativeLibs")
    doFirst {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())

        val libDirs = mutableListOf<String>()
        if (osName.contains("mac")) {
            val archDir = if (arch.contains("aarch64") || arch.contains("arm64")) "macos-aarch64" else "macos-x64"
            libDirs.add("${projectDir}/src/commonMain/resources/$archDir")
            libDirs.add("${layout.buildDirectory.dir("nativeLibs/$archDir").get().asFile.absolutePath}")
        } else if (osName.contains("win")) {
            libDirs.add("${layout.buildDirectory.dir("nativeLibs/windows-64").get().asFile.absolutePath}")
        }

        if (libDirs.isNotEmpty()) {
            val existingPath = System.getProperty("java.library.path")
            val pathSeparator = if (osName.contains("win")) ";" else ":"
            val newPath = if (existingPath.isNullOrEmpty()) {
                libDirs.joinToString(pathSeparator)
            } else {
                "$existingPath$pathSeparator${libDirs.joinToString(pathSeparator)}"
            }
            systemProperty("java.library.path", newPath)
        }
    }
}

tasks.withType<Jar> {
    exclude("macos-aarch64/**")
    exclude("macos-x64/**")
    exclude("windows-x64/**")
    exclude("linux-x64/**")
}

tasks.register<Copy>("copyNativeLibs") {
    dependsOn("extractNativeLibs")
    
    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
    val currentArch = System.getProperty("os.arch").lowercase(Locale.getDefault())
    val targetArch = project.findProperty("target.arch")?.toString()
        ?: System.getProperty("target.arch")
        ?: System.getenv("TARGET_ARCH")

    if (osName.contains("mac")) {
        val archDir = when {
            targetArch in listOf("x64", "intel") -> "macos-x64"
            targetArch in listOf("aarch64", "arm64", "arm") -> "macos-aarch64"
            currentArch.contains("aarch64") || currentArch.contains("arm64") -> "macos-aarch64"
            else -> "macos-x64"
        }
        
        from(layout.buildDirectory.dir("nativeLibs/$archDir")) {
            into(archDir)
        }
        from("src/commonMain/resources/$archDir") {
            include("libmupdf_java64.dylib")
            into(archDir)
        }
        
        into(layout.buildDirectory.dir("compose/binaries/main/app/KReader.app/Contents/Resources"))
        
        doFirst {
            val targetDir = layout.buildDirectory.dir("compose/binaries/main/app/KReader.app/Contents/Resources").get().asFile
            targetDir.mkdirs()
        }
    } else if (osName.contains("win")) {
        from(layout.buildDirectory.dir("nativeLibs/windows-64")) {
            into("windows-64")
        }
        into(layout.buildDirectory.dir("compose/binaries/main/app"))
        
        doFirst {
            val targetDir = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
            targetDir.mkdirs()
        }
    }
    
    onlyIf { osName.contains("mac") || osName.contains("win") }
}

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

// 创建特定架构的构建任务
tasks.register("createDistributableIntel") {
    group = "distribution"
    description = "Create distributable for Intel (x64) architecture"
    
    doFirst {
        project.extra["target.arch"] = "x64"
        System.setProperty("target.arch", "x64")
    }
    
    finalizedBy("createDistributable")
}

tasks.register("createDistributableArm") {
    group = "distribution"
    description = "Create distributable for ARM (aarch64) architecture"
    
    doFirst {
        project.extra["target.arch"] = "aarch64"
        System.setProperty("target.arch", "aarch64")
    }
    
    finalizedBy("createDistributable")
}

tasks.register("createDistributableWindows") {
    group = "distribution"
    description = "Create distributable for Windows"
    
    doFirst {
        project.extra["target.arch"] = "windows"
        System.setProperty("target.arch", "windows")
    }
    
    finalizedBy("createDistributable")
}

// 复制 Windows 文件关联相关文件
tasks.register<Copy>("copyWindowsFileAssociations") {
    from("src/desktopMain/resources") {
        include("file-associations.reg")
        include("install-file-associations.bat")
        include("Windows-File-Associations-README.txt")
    }
    into(layout.buildDirectory.dir("compose/binaries/main/app"))
    
    onlyIf {
        System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")
    }
}

// 确保在 Windows 构建时复制文件关联文件
tasks.matching { it.name == "createDistributable" || it.name == "packageMsi" }.configureEach {
    if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")) {
        finalizedBy("copyWindowsFileAssociations")
    }
}