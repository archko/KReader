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
            implementation(libs.reorderable)

            api(libs.coil.kt)
            api(libs.coil.kt.compose)
            api(libs.coil.kt.okhttp)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        desktopMain.dependencies {
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
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

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.archko.reader.viewer.MainKt"
        
        // 为打包的应用配置 JVM 参数，让应用能找到 native 库文件
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        val targetArch = project.findProperty("target.arch")?.toString() 
            ?: System.getProperty("target.arch") 
            ?: System.getenv("TARGET_ARCH")
            
        if (osName.contains("mac")) {
            when (targetArch) {
                "x64", "intel" -> {
                    jvmArgs += listOf("-Djava.library.path=\$APPDIR/../Resources/macos-x64")
                }
                "aarch64", "arm64", "arm" -> {
                    jvmArgs += listOf("-Djava.library.path=\$APPDIR/../Resources/macos-aarch64")
                }
                "universal", "all" -> {
                    jvmArgs += listOf("-Djava.library.path=\$APPDIR/../Resources/macos-aarch64:\$APPDIR/../Resources/macos-x64")
                }
                else -> {
                    // 默认包含所有架构路径，运行时会自动选择
                    jvmArgs += listOf("-Djava.library.path=\$APPDIR/../Resources/macos-aarch64:\$APPDIR/../Resources/macos-x64")
                }
            }
        } else if (osName.contains("win")) {
            jvmArgs += listOf("-Djava.library.path=\$APPDIR/windows-x64")
        }

        nativeDistributions {
            modules("java.instrument", "java.sql", "jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KReader"
            packageVersion = "1.2.0"

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
        } else if (osName.contains("win")) {
            if (arch.contains("amd64") || arch.contains("x86_64")) {
                libDir = "${projectDir}/src/commonMain/resources/windows-x64"
            }
        }

        if (libDir != null) {
            val existingPath = System.getProperty("java.library.path")
            val pathSeparator = if (osName.contains("win")) ";" else ":"
            val newPath = if (existingPath.isNullOrEmpty()) {
                libDir
            } else {
                "$existingPath$pathSeparator$libDir"
            }
            systemProperty("java.library.path", newPath)
        }
    }
}

// 排除 native 库文件被打进 JAR
tasks.withType<Jar> {
    exclude("macos-aarch64/**")
    exclude("macos-x64/**")
    exclude("windows-x64/**")
}

// 复制 native 库文件到应用包内 - 根据目标架构选择性复制
tasks.register<Copy>("copyNativeLibs") {
    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
    val currentArch = System.getProperty("os.arch").lowercase(Locale.getDefault())
    
    // 从系统属性或环境变量获取目标架构配置
    val targetArch = project.findProperty("target.arch")?.toString() 
        ?: System.getProperty("target.arch") 
        ?: System.getenv("TARGET_ARCH")
    
    if (osName.contains("mac")) {
        // macOS: 根据目标架构复制对应的 dylib
        when (targetArch) {
            "x64", "intel" -> {
                // 只复制 Intel 架构的 dylib
                from("src/commonMain/resources/macos-x64") {
                    into("macos-x64")
                }
                println("Copying Intel (x64) dylib only")
            }
            "aarch64", "arm64", "arm" -> {
                // 只复制 ARM 架构的 dylib
                from("src/commonMain/resources/macos-aarch64") {
                    into("macos-aarch64")
                }
                println("Copying ARM (aarch64) dylib only")
            }
            "universal", "all" -> {
                // 复制所有架构的 dylib（Universal 包）
                from("src/commonMain/resources/macos-aarch64") {
                    into("macos-aarch64")
                }
                from("src/commonMain/resources/macos-x64") {
                    into("macos-x64")
                }
                println("Copying both Intel and ARM dylib for Universal build")
            }
            else -> {
                // 默认：根据当前运行的架构决定
                if (currentArch.contains("aarch64") || currentArch.contains("arm64")) {
                    from("src/commonMain/resources/macos-aarch64") {
                        into("macos-aarch64")
                    }
                    println("Auto-detected ARM architecture, copying ARM dylib")
                } else {
                    from("src/commonMain/resources/macos-x64") {
                        into("macos-x64")
                    }
                    println("Auto-detected Intel architecture, copying Intel dylib")
                }
            }
        }
        
        into(layout.buildDirectory.dir("compose/binaries/main/app/KReader.app/Contents/Resources"))
        
        doFirst {
            val targetDir = layout.buildDirectory.dir("compose/binaries/main/app/KReader.app/Contents/Resources").get().asFile
            targetDir.mkdirs()
            println("Target architecture: ${targetArch ?: "auto-detected"}")
            println("Copying native libraries to: ${targetDir.absolutePath}")
        }
    } else if (osName.contains("win")) {
        // Windows: 复制 dll 文件到应用目录
        from("src/commonMain/resources/windows-x64") {
            into("windows-x64")
        }
        
        into(layout.buildDirectory.dir("compose/binaries/main/app"))
        
        doFirst {
            val targetDir = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
            targetDir.mkdirs()
            println("Copying Windows x64 dll to: ${targetDir.absolutePath}")
        }
    }
    
    // 只在对应平台上执行
    onlyIf {
        osName.contains("mac") || osName.contains("win")
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

tasks.register("createDistributableUniversal") {
    group = "distribution"
    description = "Create universal distributable with both architectures"
    
    doFirst {
        project.extra["target.arch"] = "universal"
        System.setProperty("target.arch", "universal")
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
        include("uninstall-file-associations.bat")
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