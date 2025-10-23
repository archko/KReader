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
            if (osName.contains("mac")) {
                setupMacOSLibraryPath()
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
        val isX64 = arch.contains("x86_64") || arch.contains("amd64")
        
        println("检测到系统架构: $arch (ARM64: $isArm64, X64: $isX64)")
        
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
            
            // 检查主要路径是否存在
            val primaryDir = File(primaryPath)
            val fallbackDir = File(fallbackPath)
            
            val finalPath = when {
                primaryDir.exists() && primaryDir.isDirectory -> {
                    println("使用主要库路径: $primaryPath")
                    "$primaryPath:$fallbackPath"
                }
                fallbackDir.exists() && fallbackDir.isDirectory -> {
                    println("使用备用库路径: $fallbackPath")
                    fallbackPath
                }
                else -> {
                    println("警告: 未找到原生库目录")
                    return
                }
            }
            
            // 更新 java.library.path
            updateLibraryPath(finalPath)
        } else {
            // 开发环境，从源码目录加载
            setupDevelopmentLibraryPath(isArm64)
        }
    }
    
    private fun setupDevelopmentLibraryPath(isArm64: Boolean) {
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
        
        val primaryDir = File(primaryPath)
        val fallbackDir = File(fallbackPath)
        
        val finalPath = when {
            primaryDir.exists() && primaryDir.isDirectory -> {
                println("开发环境使用主要库路径: $primaryPath")
                "$primaryPath:$fallbackPath"
            }
            fallbackDir.exists() && fallbackDir.isDirectory -> {
                println("开发环境使用备用库路径: $fallbackPath")
                fallbackPath
            }
            else -> {
                println("警告: 开发环境未找到原生库目录")
                return
            }
        }
        
        updateLibraryPath(finalPath)
    }
    
    private fun updateLibraryPath(newPath: String) {
        try {
            val currentPath = System.getProperty("java.library.path")
            val updatedPath = if (currentPath.isNullOrEmpty()) {
                newPath
            } else {
                "$newPath:$currentPath"
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