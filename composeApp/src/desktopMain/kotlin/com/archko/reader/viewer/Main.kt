package com.archko.reader.viewer

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
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

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    setSystemLookAndFeel()

    // 处理命令行参数，获取要打开的文件路径
    val initialFilePath = if (args.isNotEmpty()) {
        args[0] // 第一个参数作为文件路径
    } else {
        null
    }

    println("启动应用，初始文件路径: $initialFilePath")

    singleWindowApplication(
        title = "Dragon Viewer",
        state = WindowState(width = 1024.dp, height = 768.dp)
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
            FileScreen(screenWidthInPixels, screenHeightInPixels, viewModel, initialFilePath)
        }

    }
}

class ComposeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}