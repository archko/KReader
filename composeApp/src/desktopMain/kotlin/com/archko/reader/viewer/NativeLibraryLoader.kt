package com.archko.reader.viewer

import java.io.File
import java.util.Locale

/**
 * 原生库加载器，用于在不同架构的 macOS 上正确加载 dylib 文件
 */
object NativeLibraryLoader {

    private var initialized = false

    fun initialize() {
        if (initialized) return

        try {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            when {
                osName.contains("mac") -> setupMacOSLibraryPath()
                osName.contains("win") -> setupWindowsLibraryPath()
                osName.contains("linux") -> setupLinuxLibraryPath()
                else -> println("不支持的操作系统: $osName")
            }
            initialized = true
            println("原生库路径初始化完成")
        } catch (e: Exception) {
            println("初始化原生库路径失败: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupMacOSLibraryPath() {
        val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())
        val isArm64 = arch.contains("aarch64") || arch.contains("arm64")

        println("检测到 macOS 系统架构: $arch (ARM64: $isArm64)")

        // 获取应用包中的资源路径
        val baseResourcePath = System.getProperty("com.archko.reader.native.lib.path")
        if (baseResourcePath != null) {
            // 运行在打包的应用中
            val primaryPath = if (isArm64) {
                "$baseResourcePath/macos-aarch64"
            } else {
                "$baseResourcePath/macos-x64"
            }

            val fallbackPath = if (isArm64) {
                "$baseResourcePath/macos-x64"
            } else {
                "$baseResourcePath/macos-aarch64"
            }

            setupLibraryPath(primaryPath, fallbackPath, "macOS")
        } else {
            // 开发环境
            setupDevelopmentLibraryPathMacOS(isArm64)
        }
    }

    private fun setupDevelopmentLibraryPathMacOS(isArm64: Boolean) {
        val userDir = System.getProperty("user.dir")
        val resourcesPath = "$userDir/composeApp/src/commonMain/resources"

        val primaryPath = if (isArm64) {
            "$resourcesPath/macos-aarch64"
        } else {
            "$resourcesPath/macos-x64"
        }

        val fallbackPath = if (isArm64) {
            "$resourcesPath/macos-x64"
        } else {
            "$resourcesPath/macos-aarch64"
        }

        setupLibraryPath(primaryPath, fallbackPath, "macOS 开发环境")
    }

    private fun setupWindowsLibraryPath() {
        val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())
        val isX64 = arch.contains("amd64") || arch.contains("x86_64")
        val isX86 = arch.contains("x86") && !arch.contains("x86_64")

        println("检测到 Windows 系统架构: $arch (X64: $isX64, X86: $isX86)")

        // 获取应用包中的资源路径
        val baseResourcePath = System.getProperty("com.archko.reader.native.lib.path")
        if (baseResourcePath != null) {
            // 运行在打包的应用中
            val primaryPath = when {
                isX64 -> "$baseResourcePath/windows-x64"
                isX86 -> "$baseResourcePath/windows-x86"
                else -> "$baseResourcePath/windows-x64" // 默认使用 x64
            }

            val fallbackPath = if (isX64) {
                "$baseResourcePath/windows-x86"
            } else {
                "$baseResourcePath/windows-x64"
            }

            setupLibraryPath(primaryPath, fallbackPath, "Windows")
        } else {
            // 开发环境
            setupDevelopmentLibraryPathWindows(isX64, isX86)
        }
    }

    private fun setupLinuxLibraryPath() {
        val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())
        val isX64 = arch.contains("amd64") || arch.contains("x86_64")
        val isArm64 = arch.contains("aarch64") || arch.contains("arm64")

        println("检测到 Linux 系统架构: $arch (X64: $isX64, ARM64: $isArm64)")

        // 获取应用包中的资源路径
        val baseResourcePath = System.getProperty("com.archko.reader.native.lib.path")
        if (baseResourcePath != null) {
            // 运行在打包的应用中
            val primaryPath = when {
                isArm64 -> "$baseResourcePath/linux-aarch64"
                isX64 -> "$baseResourcePath/linux-x64"
                else -> "$baseResourcePath/linux-x64" // 默认使用 x64
            }

            val fallbackPath = if (isArm64) {
                "$baseResourcePath/linux-x64"
            } else {
                "$baseResourcePath/linux-aarch64"
            }

            setupLibraryPath(primaryPath, fallbackPath, "Linux")
        } else {
            // 开发环境
            setupDevelopmentLibraryPathLinux(isX64, isArm64)
        }
    }

    private fun setupDevelopmentLibraryPathWindows(isX64: Boolean, isX86: Boolean) {
        val userDir = System.getProperty("user.dir")
        val resourcesPath = "$userDir/composeApp/src/commonMain/resources"

        val primaryPath = when {
            isX64 -> "$resourcesPath/windows-x64"
            isX86 -> "$resourcesPath/windows-x86"
            else -> "$resourcesPath/windows-x64"
        }

        val fallbackPath = if (isX64) {
            "$resourcesPath/windows-x86"
        } else {
            "$resourcesPath/windows-x64"
        }

        setupLibraryPath(primaryPath, fallbackPath, "Windows 开发环境")
    }

    private fun setupDevelopmentLibraryPathLinux(isX64: Boolean, isArm64: Boolean) {
        val userDir = System.getProperty("user.dir")
        val resourcesPath = "$userDir/composeApp/src/commonMain/resources"

        val primaryPath = if (isArm64) {
            "$resourcesPath/linux-aarch64"
        } else {
            "$resourcesPath/linux-x64"
        }

        val fallbackPath = if (isArm64) {
            "$resourcesPath/linux-x64"
        } else {
            "$resourcesPath/linux-aarch64"
        }

        setupLibraryPath(primaryPath, fallbackPath, "Linux 开发环境")
    }

    private fun setupLibraryPath(primaryPath: String, fallbackPath: String, platform: String) {
        val primaryDir = File(primaryPath)
        val fallbackDir = File(fallbackPath)

        val finalPath = when {
            primaryDir.exists() && primaryDir.isDirectory -> {
                println("$platform 使用主要库路径: $primaryPath")
                if (fallbackDir.exists() && fallbackDir.isDirectory) {
                    "$primaryPath${File.pathSeparator}$fallbackPath"
                } else {
                    primaryPath
                }
            }

            fallbackDir.exists() && fallbackDir.isDirectory -> {
                println("$platform 使用备用库路径: $fallbackPath")
                fallbackPath
            }

            else -> {
                println("警告: $platform 未找到原生库目录")
                return
            }
        }

        updateLibraryPath(finalPath)
    }

    private fun updateLibraryPath(newPath: String) {
        try {
            val currentPath = System.getProperty("java.library.path")
            val pathSeparator = File.pathSeparator
            val updatedPath = if (currentPath.isNullOrEmpty()) {
                newPath
            } else {
                "$newPath$pathSeparator$currentPath"
            }

            System.setProperty("java.library.path", updatedPath)
            println("更新 java.library.path: $updatedPath")

            // 重置 ClassLoader 的库路径缓存
            val fieldSysPath = ClassLoader::class.java.getDeclaredField("sys_paths")
            fieldSysPath.isAccessible = true
            fieldSysPath.set(null, null)

            println("原生库路径更新成功")
        } catch (e: Exception) {
            println("更新库路径失败: ${e.message}")
            e.printStackTrace()
        }
    }
}