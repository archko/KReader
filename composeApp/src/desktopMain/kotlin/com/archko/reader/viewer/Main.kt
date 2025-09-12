package com.archko.reader.viewer

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlin.system.exitProcess
import java.io.File
import java.util.*
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.util.DebugLogger
import coil3.util.Logger
import com.archko.reader.pdf.cache.DriverFactory
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.viewmodel.PdfViewModel
import org.jetbrains.skiko.setSystemLookAndFeel

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
    
    // 文件打开事件流
    private val _fileOpenEvents = MutableSharedFlow<String>()
    val fileOpenEvents: SharedFlow<String> = _fileOpenEvents
    
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
                    } catch (e: Exception) {
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
                
                // 监听连接
                while (true) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        CoroutineScope(Dispatchers.IO).launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!serverSocket!!.isClosed) {
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
            
            val filePath = reader.readLine()
            if (filePath != null && filePath.isNotEmpty()) {
                println("收到文件打开请求: $filePath")
                // 发送文件打开事件
                CoroutineScope(Dispatchers.Main).launch {
                    _fileOpenEvents.emit(filePath)
                }
                writer.println("OK")
            } else {
                writer.println("ERROR")
            }
        } catch (e: Exception) {
            println("处理客户端请求失败: ${e.message}")
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
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
    val userHome = System.getProperty("user.home")
    val configDir = File(userHome, ".kreader")
    if (!configDir.exists()) {
        configDir.mkdirs()
    }
    return configDir
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
        properties.setProperty("x", windowState.position.x.value.toString())
        properties.setProperty("y", windowState.position.y.value.toString())
        properties.setProperty("isMaximized", (windowState.placement == WindowPlacement.Maximized).toString())
        
        properties.store(configFile.outputStream(), "KReader Window State")
        println("窗口状态已保存")
    } catch (e: Exception) {
        println("保存窗口状态失败: ${e.message}")
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    setSystemLookAndFeel()

    // 处理命令行参数，获取要打开的文件路径
    val initialFilePath = if (args.isNotEmpty()) {
        val filePath = args[0]
        // 处理可能的引号包围的路径
        val cleanPath = filePath.trim('"', '\'')
        println("原始参数: $filePath")
        println("清理后路径: $cleanPath")
        
        // 验证文件是否存在
        val file = File(cleanPath)
        if (file.exists()) {
            println("文件存在: ${file.absolutePath}")
            cleanPath
        } else {
            println("文件不存在: ${file.absolutePath}")
            null
        }
    } else {
        null
    }

    println("启动应用，最终文件路径: $initialFilePath")
    println("命令行参数数量: ${args.size}")
    args.forEachIndexed { index, arg ->
        println("参数[$index]: $arg")
    }

    // 尝试获取单实例锁
    if (!SingleInstanceManager.tryLockInstance()) {
        // 已有实例运行，发送文件路径给运行中的实例
        if (initialFilePath != null) {
            println("发送文件到已运行的实例: $initialFilePath")
            if (SingleInstanceManager.sendFileToRunningInstance(initialFilePath)) {
                println("文件已发送到运行中的实例")
                exitProcess(0)
            } else {
                println("发送文件失败，继续启动新实例")
            }
        } else {
            println("已有实例运行，退出")
            exitProcess(0)
        }
    }

    // 设置关闭钩子
    Runtime.getRuntime().addShutdownHook(Thread {
        SingleInstanceManager.cleanup()
    })

    application {
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
                    .components { add(CustomImageFetcher.Factory()) }
                    .logger(DebugLogger(minLevel = Logger.Level.Warn))
                    .build()
            }

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
                
                // 监听文件打开事件
                var currentFilePath by remember { mutableStateOf(initialFilePath) }
                
                LaunchedEffect(Unit) {
                    SingleInstanceManager.fileOpenEvents.collect { newFilePath ->
                        println("收到新文件打开事件: $newFilePath")
                        currentFilePath = newFilePath
                        
                        // 将窗口置于前台
                        windowState.isMinimized = false
                    }
                }
                
                FileScreen(screenWidthInPixels, screenHeightInPixels, viewModel, currentFilePath)
            }
        }
    }
}