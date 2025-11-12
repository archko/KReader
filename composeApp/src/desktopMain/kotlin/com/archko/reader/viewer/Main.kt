package com.archko.reader.viewer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.util.DebugLogger
import coil3.util.Logger
import com.archko.reader.pdf.cache.CustomImageDataKeyer
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.DriverFactory
import com.archko.reader.pdf.cache.FileUtils
import com.archko.reader.pdf.viewmodel.PdfViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.skiko.setSystemLookAndFeel
import java.awt.Desktop
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.util.Locale
import java.util.Properties
import kotlin.system.exitProcess

class ComposeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

// 单实例管理
object SingleInstanceManager {
    private const val LOCK_FILE = "kreader.lock"
    private const val PORT_FILE = "kreader.port"
    private const val SERVER_PORT_START = 23456

    private var lockFile: RandomAccessFile? = null
    private var fileLock: FileLock? = null
    private var serverSocket: ServerSocket? = null

    private val _fileOpenEvents = MutableSharedFlow<String>()
    val fileOpenEvents: SharedFlow<String> = _fileOpenEvents

    // 发送文件打开事件的公开方法
    fun sendFileOpenEvent(filePath: String) {
        CoroutineScope(Dispatchers.Main).launch {
            _fileOpenEvents.emit(filePath)
        }
    }

    fun tryLockInstance(): Boolean {
        return try {
            val configDir = getConfigDir()
            val lockFileObj = File(configDir, LOCK_FILE)

            lockFile = RandomAccessFile(lockFileObj, "rw")
            val channel = lockFile!!.channel
            fileLock = channel.tryLock()

            if (fileLock != null) {
                // 成功获取锁，启动服务器
                startServer()
                true
            } else {
                // 锁被占用，说明已有实例运行
                lockFile?.close()
                false
            }
        } catch (e: Exception) {
            println("锁定实例失败: ${e.message}")
            false
        }
    }

    private fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 尝试从指定端口开始找可用端口
                var port = SERVER_PORT_START
                while (port < SERVER_PORT_START + 100) {
                    try {
                        serverSocket = ServerSocket(port)
                        break
                    } catch (_: Exception) {
                        port++
                    }
                }

                if (serverSocket == null) {
                    println("无法找到可用端口")
                    return@launch
                }

                // 保存端口号到文件
                val configDir = getConfigDir()
                val portFile = File(configDir, PORT_FILE)
                portFile.writeText(port.toString())

                println("服务器启动在端口: $port")

                while (true) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        CoroutineScope(Dispatchers.IO).launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (serverSocket != null && !serverSocket!!.isClosed) {
                            println("服务器错误: ${e.message}")
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                println("启动服务器失败: ${e.message}")
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)

            val message = reader.readLine()
            if (message != null && message.isNotEmpty()) {
                if (message == "ACTIVATE") {
                    println("收到窗口激活请求")
                    // 发送激活事件
                    CoroutineScope(Dispatchers.Main).launch {
                        _fileOpenEvents.emit("ACTIVATE")
                    }
                } else {
                    println("收到文件打开请求: $message")
                    // 验证文件是否存在且为支持的格式
                    val file = File(message)
                    // 注意：这里的检查应与 main 函数中的逻辑保持一致，只检查是否存在和是否为文件
                    if (file.exists() && file.isFile) {
                        // 发送文件打开事件
                        CoroutineScope(Dispatchers.Main).launch {
                            _fileOpenEvents.emit(file.absolutePath) // 使用绝对路径
                        }
                    } else {
                        val errorMsg = when {
                            !file.exists() -> "ERROR: File not found"
                            !file.isFile -> "ERROR: Not a file"
                            else -> "ERROR: Unknown error"
                        }
                        println("文件验证失败: $message - $errorMsg")
                        writer.println(errorMsg)
                        return
                    }
                }
                writer.println("OK")
            } else {
                writer.println("ERROR: Empty message")
            }
        } catch (e: Exception) {
            println("处理客户端请求失败: ${e.message}")
        } finally {
            try {
                clientSocket.close()
            } catch (_: Exception) {
                // 忽略关闭错误
            }
        }
    }

    fun sendFileToRunningInstance(filePath: String): Boolean {
        return try {
            val configDir = getConfigDir()
            val portFile = File(configDir, PORT_FILE)

            if (!portFile.exists()) {
                println("端口文件不存在")
                return false
            }

            val port = portFile.readText().toInt()
            val socket = Socket("localhost", port)

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.println(filePath)
            val response = reader.readLine()

            socket.close()

            response == "OK"
        } catch (e: Exception) {
            println("发送文件到运行实例失败: ${e.message}")
            false
        }
    }

    fun cleanup() {
        try {
            serverSocket?.close()
            fileLock?.release()
            lockFile?.close()

            // 清理临时文件
            val configDir = getConfigDir()
            File(configDir, LOCK_FILE).delete()
            File(configDir, PORT_FILE).delete()
        } catch (e: Exception) {
            println("清理资源失败: ${e.message}")
        }
    }
}

// 窗口状态管理
private fun getConfigDir(): File {
    return FileUtils.getCacheDirectory()
}

private data class SavedWindowState(
    val placement: WindowPlacement,
    val size: androidx.compose.ui.unit.DpSize,
    val position: androidx.compose.ui.window.WindowPosition
)

private fun loadWindowState(): SavedWindowState {
    val configFile = File(getConfigDir(), "window.properties")

    return if (configFile.exists()) {
        try {
            val properties = Properties()
            properties.load(configFile.inputStream())

            val width = properties.getProperty("width", "1024").toFloat().dp
            val height = properties.getProperty("height", "768").toFloat().dp
            val x = properties.getProperty("x", "0").toFloat().dp
            val y = properties.getProperty("y", "0").toFloat().dp
            val isMaximized = properties.getProperty("isMaximized", "true").toBoolean()

            SavedWindowState(
                placement = if (isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                size = androidx.compose.ui.unit.DpSize(width, height),
                position = androidx.compose.ui.window.WindowPosition(x, y)
            )
        } catch (e: Exception) {
            println("加载窗口状态失败: ${e.message}")
            // 默认全屏
            SavedWindowState(
                placement = WindowPlacement.Maximized,
                size = androidx.compose.ui.unit.DpSize(1024.dp, 768.dp),
                position = androidx.compose.ui.window.WindowPosition.PlatformDefault
            )
        }
    } else {
        // 首次启动，默认全屏
        SavedWindowState(
            placement = WindowPlacement.Maximized,
            size = androidx.compose.ui.unit.DpSize(1024.dp, 768.dp),
            position = androidx.compose.ui.window.WindowPosition.PlatformDefault
        )
    }
}

private fun saveWindowState(windowState: WindowState) {
    try {
        val configFile = File(getConfigDir(), "window.properties")
        val properties = Properties()

        properties.setProperty("width", windowState.size.width.value.toString())
        properties.setProperty("height", windowState.size.height.value.toString())
        // 仅在非最大化时保存位置，否则保存的可能是屏幕外的位置
        if (windowState.placement == WindowPlacement.Floating) {
            properties.setProperty("x", windowState.position.x.value.toString())
            properties.setProperty("y", windowState.position.y.value.toString())
        } else {
            // 最大化时保存 0
            properties.setProperty("x", "0")
            properties.setProperty("y", "0")
        }

        properties.setProperty(
            "isMaximized",
            (windowState.placement == WindowPlacement.Maximized).toString()
        )

        properties.store(configFile.outputStream(), "KReader Window State")
        println("窗口状态已保存")
    } catch (e: Exception) {
        println("保存窗口状态失败: ${e.message}")
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // 初始化原生库加载器
    //NativeLibraryLoader.initialize()

    setSystemLookAndFeel()

    // 详细的启动调试信息
    println("=== KReader 启动 ===")
    // 检查环境变量
    val relevantEnvs = listOf("KREADER_FILE", "JAVA_MAIN_CLASS_PID")
    relevantEnvs.forEach { env ->
        val value = System.getenv(env)
        if (value != null) {
            println("环境变量 $env: $value")
        }
    }
    println("===================")

    // 多种方式获取要打开的文件路径
    fun findFilePath(): String? {
        // 方法1: 命令行参数
        if (args.isNotEmpty()) {
            val filePath = args[0]
            val cleanPath = filePath.trim('"', '\'').replace("\\", "/")
            println("方法1 - 命令行参数: $filePath -> $cleanPath")

            val file = File(cleanPath)
            if (file.exists() && file.isFile) {
                println("✓ 命令行参数文件有效: ${file.absolutePath}")
                return file.absolutePath
            } else {
                println("✗ 命令行参数文件无效: ${file.absolutePath}")
            }
        }

        // 方法2: 系统属性
        val systemProps = listOf(
            "file.to.open",
            "java.awt.desktop.file",
            "apple.laf.useScreenMenuBar.file"
        )

        for (prop in systemProps) {
            val filePath = System.getProperty(prop)
            if (filePath != null) {
                val cleanPath = filePath.trim('"', '\'').replace("\\", "/")
                println("方法2 - 系统属性 $prop: $filePath -> $cleanPath")

                val file = File(cleanPath)
                if (file.exists() && file.isFile) {
                    println("✓ 系统属性文件有效: ${file.absolutePath}")
                    return file.absolutePath
                } else {
                    println("✗ 系统属性文件无效: ${file.absolutePath}")
                }
            }
        }

        // 方法3: 环境变量
        val filePath = System.getenv("KREADER_FILE")
        if (filePath != null) {
            val cleanPath = filePath.trim('"', '\'').replace("\\", "/")
            println("方法3 - 环境变量: $filePath -> $cleanPath")

            val file = File(cleanPath)
            if (file.exists() && file.isFile) {
                println("✓ 环境变量文件有效: ${file.absolutePath}")
                return file.absolutePath
            } else {
                println("✗ 环境变量文件无效: ${file.absolutePath}")
            }
        }

        // 方法4: 检查 Java 命令中是否包含文件路径
        val javaCommand = System.getProperty("sun.java.command")
        println("Java命令: $javaCommand")
        if (javaCommand != null && javaCommand.contains("/") && !javaCommand.endsWith(".jar")) {
            val parts = javaCommand.split(" ")
            for (part in parts) {
                if (part.contains("/") &&
                    (part.endsWith(".pdf")
                            || part.endsWith(".epub")
                            || part.endsWith(".mobi")
                            || part.endsWith(".txt")
                            || part.endsWith(".djvu"))
                ) {
                    val cleanPath = part.trim('"', '\'').replace("\\", "/")
                    println("方法4 - Java命令解析: $part -> $cleanPath")

                    val file = File(cleanPath)
                    if (file.exists() && file.isFile) {
                        println("✓ Java命令文件有效: ${file.absolutePath}")
                        return file.absolutePath
                    } else {
                        println("✗ Java命令文件无效: ${file.absolutePath}")
                    }
                }
            }
        }

        println("所有方法都未找到有效文件路径")
        return null
    }

    val initialFilePath = findFilePath()
    println("启动应用，最终文件路径: $initialFilePath")

    // =======================================================
    // 启用单实例管理逻辑
    // =======================================================
    if (!SingleInstanceManager.tryLockInstance()) {
        // 如果实例已运行，则将文件路径发送给它
        if (initialFilePath != null) {
            println("发送文件到已运行的实例: $initialFilePath")
            if (SingleInstanceManager.sendFileToRunningInstance(initialFilePath)) {
                println("文件已发送到运行中的实例")
                exitProcess(0) // 成功发送后退出新进程
            } else {
                println("发送文件失败，继续启动新实例")
            }
        } else {
            // 如果没有文件路径，尝试激活已运行的实例（防止用户双击应用图标）
            println("尝试激活已运行的实例")
            if (SingleInstanceManager.sendFileToRunningInstance("ACTIVATE")) {
                println("已激活运行中的实例")
                exitProcess(0) // 成功激活后退出新进程
            } else {
                println("激活失败，继续启动新实例")
            }
        }
    }

    // 设置关闭钩子，以确保应用退出时释放文件锁和关闭服务器
    Runtime.getRuntime().addShutdownHook(Thread {
        SingleInstanceManager.cleanup()
    })
    // =======================================================

    application {
        // 设置 macOS 文件打开事件处理
        if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("mac")) {
            try {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
                    desktop.setOpenFileHandler { event ->
                        println("macOS 文件打开事件触发")
                        val files = event.files
                        if (files.isNotEmpty()) {
                            val file = files[0]
                            println("通过 macOS 事件接收到文件: ${file.absolutePath}")

                            // 发送文件打开事件到应用
                            SingleInstanceManager.sendFileOpenEvent(file.absolutePath)
                        }
                    }
                    println("macOS 文件打开事件处理器已设置")
                }
            } catch (e: Exception) {
                println("设置 macOS 文件打开事件处理器失败: ${e.message}")
            }
        }

        // 加载保存的窗口状态
        val savedState = loadWindowState()
        val windowState = rememberWindowState(
            placement = savedState.placement,
            size = savedState.size,
            position = savedState.position
        )

        Window(
            title = "KReader",
            state = windowState,
            onCloseRequest = {
                // 保存窗口状态
                saveWindowState(windowState)
                exitProcess(0)
            }
        ) {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components {
                        add(CustomImageFetcher.Factory())
                        add(CustomImageDataKeyer())
                    }
                    .logger(DebugLogger(minLevel = Logger.Level.Warn))
                    .build()
            }

            // 获取屏幕尺寸信息
            val windowInfo = LocalWindowInfo.current
            val density = LocalDensity.current
            var screenWidthInPixels by remember { mutableStateOf(0) }
            var screenHeightInPixels by remember { mutableStateOf(0) }
            density.run {
                screenWidthInPixels = windowInfo.containerSize.width.toDp().toPx().toInt()
                screenHeightInPixels = windowInfo.containerSize.height.toDp().toPx().toInt()
            }
            println("app.screenHeight:$screenWidthInPixels-$screenHeightInPixels")

            val driverFactory = DriverFactory()
            val database = driverFactory.createRoomDatabase()

            val viewModelStoreOwner = remember { ComposeViewModelStoreOwner() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                val viewModel: PdfViewModel = viewModel()
                viewModel.database = database

                // 文档管理状态
                var currentFilePath by remember { mutableStateOf(initialFilePath) }
                var forceReload by remember { mutableStateOf(false) }

                // =======================================================
                // 启用事件监听逻辑，处理第二个实例发送过来的文件路径
                // =======================================================
                LaunchedEffect(Unit) {
                    SingleInstanceManager.fileOpenEvents.collect { message ->
                        println("收到事件: $message")

                        if (message == "ACTIVATE") {
                            println("激活窗口")
                            windowState.isMinimized = false
                            window.toFront()
                            window.requestFocus()
                        } else {
                            println("收到新文件打开事件: $message")

                            // 检查是否是不同的文件
                            if (currentFilePath != message) {
                                println("切换文档: $currentFilePath -> $message")

                                // 如果当前有打开的文档，先关闭它
                                if (currentFilePath != null) {
                                    println("关闭当前文档: $currentFilePath")
                                }

                                // 更新到新文档
                                currentFilePath = message
                                forceReload = !forceReload // 触发重新加载

                                println("已切换到新文档: $message")
                            } else {
                                println("相同文件，只激活窗口")
                            }

                            windowState.isMinimized = false
                            window.toFront()
                            window.requestFocus()
                        }
                    }
                }

                // 监听文件路径变化，确保 FileScreen 能响应新文件
                LaunchedEffect(currentFilePath, forceReload) {
                    if (currentFilePath != null) {
                        println("LaunchedEffect: 准备加载文档 $currentFilePath")
                    }
                }
                // =======================================================

                // FileScreen 将使用 currentFilePath 来加载文件
                // 使用 key 确保文档切换时正确重新加载
                key(currentFilePath) {
                    FileScreen(viewModel, currentFilePath)
                }
            }
        }
    }
}
