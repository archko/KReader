package com.archko.reader.viewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.cache.APageSizeLoader
import com.archko.reader.pdf.cache.CustomImageFetcher
import com.archko.reader.pdf.cache.ReflowCacheLoader
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.entity.DocumentInfo
import com.archko.reader.pdf.entity.Recent
import com.archko.reader.pdf.util.FileTypeUtils
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.pdf.util.getAbsolutePath
import com.archko.reader.pdf.util.getExtension
import com.archko.reader.pdf.util.inferName
import com.archko.reader.pdf.util.normalizePath
import com.archko.reader.pdf.viewmodel.AIViewModel
import com.archko.reader.pdf.viewmodel.BookmarkViewModel
import com.archko.reader.pdf.viewmodel.PdfViewModel
import com.archko.reader.pdf.viewmodel.ReadingStatsViewModel
import com.archko.reader.viewer.dialog.BookInfoDialog
import com.archko.reader.viewer.tts.TtsTempProgressHelper
import com.archko.reader.viewer.viewmodel.FontViewModel
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

data class OpenDocRequest(val documents: List<DocumentInfo>, val page: Int?)

@Composable
fun FileScreen(
    viewModel: PdfViewModel,
    fontViewModel: FontViewModel,
    bookmarkViewModel: BookmarkViewModel,
    readingStatsViewModel: ReadingStatsViewModel,
    aiViewModel: AIViewModel,
    modifier: Modifier = Modifier,
    onShowBottomBarChanged: (Boolean) -> Unit = {},
    externalDocument: DocumentInfo? = null,
    onExternalPathConsumed: () -> Unit = {},
    onCloseDocument: () -> Unit = {},
    hasStoragePermission: Boolean = false
) {
    Theme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val recentList by viewModel.recentList.collectAsState()
        val hasMoreData by viewModel.hasMoreData.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        var openDocRequest by remember { mutableStateOf<OpenDocRequest?>(null) }

        var showBookInfoDialog by remember { mutableStateOf<Recent?>(null) }

        val isDarkTheme = isSystemInDarkTheme()
        val gridState = rememberLazyGridState()

        LaunchedEffect(Unit) {
            if (recentList.isEmpty()) {
                val tempProgressList = TtsTempProgressHelper.getAllTempProgress(context)
                if (tempProgressList.isNotEmpty()) {
                    println("FileScreen: 发现${tempProgressList.size}个临时进度，开始恢复")
                    for (tempProgress in tempProgressList) {
                        try {
                            viewModel.updateRecent(
                                page = tempProgress.page.toLong(),
                                tempProgress.path
                            )

                            println("FileScreen: 恢复临时进度成功: ${tempProgress.path}, page=${tempProgress.page}")
                        } catch (e: Exception) {
                            println("FileScreen: 恢复临时进度失败: ${tempProgress.path}, error=${e.message}")
                        }
                    }
                    TtsTempProgressHelper.clearAllTempProgress(context)
                }
                viewModel.loadRecents()
            }
        }

        LaunchedEffect(externalDocument) {
            if (externalDocument != null) {
                scope.launch {
                    var mimeType: String? = null
                    var ext: String? = null
                    var fileSize = externalDocument.fileSize

                    if (externalDocument.hasUri()) {
                        val uri = Uri.parse(externalDocument.uri)
                        val (queriedExt, queriedSize) = queryContentResolver(context, uri)
                        ext = queriedExt
                        if (queriedSize > 0) fileSize = queriedSize
                        mimeType = context.contentResolver.getType(uri)
                    }

                    val path = externalDocument.path
                    if (ext == null && path != null) {
                        val normalizedPath = normalizePath(path)
                        val recent = viewModel.database?.recentDao()?.getRecent(normalizedPath)
                        ext = recent?.ext ?: path.getExtension()
                    }
                    ext = ext ?: ""

                    when {
                        mimeType != null && FileTypeUtils.isImageMimeType(mimeType) ||
                                ext.isNotEmpty() && FileTypeUtils.isImageExtension(ext) -> {
                            openDocRequest = OpenDocRequest(listOf(externalDocument), 0)
                        }
                        mimeType != null && FileTypeUtils.isDocumentMimeType(mimeType) ||
                                ext.isNotEmpty() && FileTypeUtils.isDocumentExtension(ext) -> {
                            val docs = listOf(externalDocument)
                            if (FileTypeUtils.shouldSaveProgress(docs)) {
                                viewModel.getRecent(path ?: externalDocument.uri ?: "")
                                val startPage = viewModel.recent?.page?.toInt() ?: 0
                                openDocRequest = OpenDocRequest(docs, startPage)
                            } else {
                                openDocRequest = OpenDocRequest(docs, 0)
                            }
                        }
                        mimeType != null && FileTypeUtils.isTiffMimeType(mimeType) ||
                                ext.isNotEmpty() && FileTypeUtils.isTiffExtension(ext) -> {
                            openDocRequest = OpenDocRequest(listOf(externalDocument), 0)
                        }
                    }
                    onExternalPathConsumed()
                }
            }
        }

        BackHandler(enabled = openDocRequest != null) {
            openDocRequest = null
            onCloseDocument()
            // 恢复状态栏显示
            val activity = context as? ComponentActivity
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    show(WindowInsetsCompat.Type.statusBars())
                    show(WindowInsetsCompat.Type.navigationBars())
                    // 根据主题设置状态栏文字颜色
                    isAppearanceLightStatusBars = !isDarkTheme
                }
            }
        }

        // 书本信息对话框
        showBookInfoDialog?.let { recent ->
            BookInfoDialog(
                recent = recent,
                readingStatsViewModel = readingStatsViewModel,
                bookmarkViewModel = bookmarkViewModel,
                onDismiss = { showBookInfoDialog = null },
                onRead = { bookRecent ->
                    val path = getAbsolutePath(bookRecent.path)
                    val file = File(path)
                    if (file.exists()) {
                        val fileSize = bookRecent.size ?: file.length()
                        scope.launch {
                            val docInfo = DocumentInfo(path = file.absolutePath, fileSize = fileSize)
                            val docs = listOf(docInfo)
                            if (FileTypeUtils.shouldSaveProgress(docs)) {
                                viewModel.getRecent(bookRecent.path!!)
                                val startPage = viewModel.recent?.page?.toInt() ?: 0
                                openDocRequest = OpenDocRequest(docs, startPage)
                            } else {
                                openDocRequest = OpenDocRequest(docs, 0)
                            }
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "File Not Found!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showBookInfoDialog = null
                }
            )
        }

        val pickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uriPathPairs = mutableListOf<Pair<Uri, String?>>()
                try {
                    val oneUri = result.data?.data
                    if (oneUri != null) {
                        val path = IntentFile.getPath(PdfApp.app!!, oneUri)
                        uriPathPairs.add(oneUri to path)
                    } else {
                        for (index in 0 until (result.data?.clipData?.itemCount ?: 0)) {
                            val uri = result.data?.clipData?.getItemAt(index)?.uri
                            if (uri != null) {
                                val path = IntentFile.getPath(PdfApp.app!!, uri)
                                uriPathPairs.add(uri to path)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                scope.launch {
                    if (uriPathPairs.isNotEmpty()) {
                        val imageDocs = mutableListOf<DocumentInfo>()
                        val documentDocs = mutableListOf<DocumentInfo>()
                        val tiffDocs = mutableListOf<DocumentInfo>()

                        for ((uri, path) in uriPathPairs) {
                            val (resolvedExt, resolvedSize) = queryContentResolver(context, uri)
                            val mimeType = context.contentResolver.getType(uri)

                            var ext = resolvedExt
                            var fileSize = resolvedSize
                            if (ext == null && path != null) {
                                val recent = viewModel.database?.recentDao()?.getRecent(path)
                                ext = recent?.ext ?: path.getExtension()
                                if (fileSize <= 0) fileSize = recent?.size ?: 0L
                            }

                            val doc = DocumentInfo(
                                uri = uri.toString(),
                                path = path,
                                fileSize = fileSize,
                                mimeType = mimeType,
                                ext = ext
                            )
                            val extStr = ext ?: ""

                            when {
                                mimeType != null && FileTypeUtils.isImageMimeType(mimeType) ||
                                        extStr.isNotEmpty() && FileTypeUtils.isImageExtension(extStr) -> {
                                    imageDocs.add(doc)
                                }
                                mimeType != null && FileTypeUtils.isDocumentMimeType(mimeType) ||
                                        extStr.isNotEmpty() && FileTypeUtils.isDocumentExtension(extStr) ||
                                        path == null -> {
                                    documentDocs.add(doc)
                                }
                                mimeType != null && FileTypeUtils.isTiffMimeType(mimeType) ||
                                        extStr.isNotEmpty() && FileTypeUtils.isTiffExtension(extStr) -> {
                                    tiffDocs.add(doc)
                                }
                                path != null && FileTypeUtils.isAccetableImageFile(File(path)) -> {
                                    imageDocs.add(doc)
                                }
                                path != null && FileTypeUtils.isDocumentFile(path) -> {
                                    documentDocs.add(doc)
                                }
                                path != null && FileTypeUtils.isTiffFile(path) -> {
                                    tiffDocs.add(doc)
                                }
                            }
                        }

                        if (imageDocs.isNotEmpty()) {
                            openDocRequest = OpenDocRequest(listOf(imageDocs.first()), 0)
                        } else if (documentDocs.isNotEmpty()) {
                            val firstDoc = documentDocs.first()
                            val docs = listOf(firstDoc)
                            if (FileTypeUtils.shouldSaveProgress(docs)) {
                                viewModel.getRecent(firstDoc.path ?: firstDoc.uri ?: "")
                                val startPage = viewModel.recent?.page?.toInt() ?: 0
                                openDocRequest = OpenDocRequest(docs, startPage)
                            } else {
                                openDocRequest = OpenDocRequest(docs, 0)
                            }
                        } else if (tiffDocs.isNotEmpty()) {
                            openDocRequest = OpenDocRequest(listOf(tiffDocs.first()), 0)
                        } else {
                            uriPathPairs.firstOrNull()?.let { (uri, path) ->
                                val displayPath = path ?: uri.toString()
                                Toast.makeText(
                                    PdfApp.app,
                                    getString(Res.string.unsupported_document, displayPath),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        val directoryPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { treeUri ->
            if (treeUri != null) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                scope.launch {
                    val docs = scanDirectoryImages(context, treeUri)
                    if (docs.isNotEmpty()) {
                        openDocRequest = OpenDocRequest(docs, 0)
                    }
                }
            }
        }

        fun selectFiles() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                type = "*/*"
            }
            pickerLauncher.launch(intent)
        }

        Surface(
            modifier = modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (openDocRequest == null) {
                onShowBottomBarChanged(true)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
                    ) {
                        if (recentList.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.clear() },
                                modifier = Modifier.align(Alignment.CenterStart),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(Res.string.clear_history))
                            }
                        }
                        Button(
                            onClick = { selectFiles() },
                            modifier = Modifier.align(Alignment.Center),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(Res.string.select_pdf))
                        }
                        Button(
                            onClick = { directoryPickerLauncher.launch(null) },
                            modifier = Modifier.align(Alignment.CenterEnd),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(Res.string.album))
                        }
                    }

                    if (!hasStoragePermission) {
                        Text(
                            text = stringResource(Res.string.no_storage_permission),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (recentList.isNotEmpty()) {
                        // 监听滚动到底部自动加载更多
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
                                .collect { visibleItems ->
                                    if (visibleItems.isNotEmpty()) {
                                        val lastVisibleItem = visibleItems.last()
                                        val totalItems = recentList.size

                                        // 当滚动到最后几个项目时，自动加载更多
                                        if (lastVisibleItem.index >= totalItems - 3 && hasMoreData && !isLoading) {
                                            viewModel.loadMoreRecents()
                                        }
                                    }
                                }
                        }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(100.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 56.dp)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(0.dp))
                            }

                            items(
                                count = recentList.size,
                                key = { index -> recentList[index].path ?: "$index" }
                            ) { i ->
                                RecentItem(
                                    recent = recentList[i],
                                    onClick = {
                                        val path = getAbsolutePath(it.path)
                                        val file = File(path)
                                        if (file.exists()) {
                                            val fileSize = it.size ?: file.length()
                                            scope.launch {
                                                val docInfo = DocumentInfo(path = file.absolutePath, fileSize = fileSize)
                                                val docs = listOf(docInfo)
                                                if (FileTypeUtils.shouldSaveProgress(docs)) {
                                                    viewModel.getRecent(it.path!!)
                                                    val startPage =
                                                        viewModel.recent?.page?.toInt() ?: 0
                                                    openDocRequest =
                                                        OpenDocRequest(docs, startPage)
                                                } else {
                                                    openDocRequest = OpenDocRequest(docs, 0)
                                                }
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "File Not Found!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onInfo = { recent ->
                                        showBookInfoDialog = recent
                                    },
                                    onDelete = {
                                        // 删除历史记录
                                        viewModel.deleteRecent(recentList[i])
                                    },
                                    onDeleteCache = {
                                        // 异步删除缓存文件
                                        scope.launch {
                                            val path = getAbsolutePath(recentList[i].path)
                                            CustomImageFetcher.deleteCache(path)
                                            APageSizeLoader.deletePageSizeFromFile(path)
                                            ReflowCacheLoader.deleteReflowCache(File(path))
                                        }
                                    }
                                )
                            }

                            if (hasMoreData) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoading) {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .height(24.dp)
                                                    .padding(horizontal = 16.dp),
                                            )
                                        } else {
                                            Button(
                                                onClick = { viewModel.loadMoreRecents() },
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.load_more),
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            } else {
                onShowBottomBarChanged(false)
                CustomView(
                    documents = openDocRequest!!.documents,
                    progressPage = openDocRequest!!.page,
                    onSaveDocument = { page, pageCount, zoom, scrollX, scrollY, scrollOri, reflow, crop ->
                        viewModel.updateRecent(
                            page = page.toLong(),
                            pageCount = pageCount.toLong(),
                            zoom = zoom,
                            crop = crop,
                            scrollX,
                            scrollY,
                            scrollOri,
                            reflow,
                        )
                    },
                    onCloseDocument = {
                        openDocRequest = null
                        onCloseDocument()
                    },
                    initialScrollX = viewModel.recent?.scrollX ?: 0L,
                    initialScrollY = viewModel.recent?.scrollY ?: 0L,
                    initialZoom = viewModel.recent?.zoom ?: 1.0,
                    scrollOri = viewModel.recent?.scrollOri ?: 0,
                    reflow = viewModel.recent?.reflow ?: 0L,
                    crop = 0L == viewModel.recent?.crop,
                    fontViewModel = fontViewModel,
                    bookmarkViewModel = bookmarkViewModel,
                    readingStatsViewModel = readingStatsViewModel,
                    aiViewModel = aiViewModel
                )
            }
        }
    }
}

private fun queryContentResolver(context: android.content.Context, uri: Uri): Pair<String?, Long> {
    var ext: String? = null
    var fileSize = 0L
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    val name = it.getString(nameIdx)
                    ext = name?.substringAfterLast('.', "")?.lowercase()
                }
                if (sizeIdx >= 0 && !it.isNull(sizeIdx)) {
                    fileSize = it.getLong(sizeIdx)
                }
            }
        }
    } catch (_: Exception) {
    }
    return ext to fileSize
}

private fun scanDirectoryImages(context: android.content.Context, treeUri: Uri): List<DocumentInfo> {
    val docs = mutableListOf<DocumentInfoWithDate>() // 临时包装结构用于排序
    val documentId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
    
    // 1. 将 COLUMN_LAST_MODIFIED 加入 projection
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    
    // 注意：sortOrder 传 null，靠后续内存排序
    val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
    cursor?.use {
        val mimeTypeIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val docIdIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val sizeIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val lastModifiedIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        while (it.moveToNext()) {
            if (mimeTypeIdx < 0 || docIdIdx < 0) continue
            val mimeType = it.getString(mimeTypeIdx)
            if (mimeType != null && mimeType.startsWith("image/")) {
                val docId = it.getString(docIdIdx)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val displayName = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                
                // 读取修改时间（如果没有记录，默认为 0L）
                val lastModified = if (lastModifiedIdx >= 0 && !it.isNull(lastModifiedIdx)) {
                    it.getLong(lastModifiedIdx)
                } else {
                    0L
                }

                val ext = displayName.substringAfterLast('.', "").lowercase()
                if (FileTypeUtils.isImageExtension(ext) || FileTypeUtils.isTiffExtension(ext)) {
                    docs.add(
                        DocumentInfoWithDate(
                            info = DocumentInfo(
                                uri = docUri.toString(),
                                path = displayName,
                                fileSize = size,
                                mimeType = mimeType,
                                ext = ext
                            ),
                            lastModified = lastModified
                        )
                    )
                }
            }
        }
    }

    // 2. 在内存中按最后修改时间降序排序，并返回 DocumentInfo 列表
    return docs.sortedByDescending { it.lastModified }.map { it.info }
}

// 辅助包装类
private data class DocumentInfoWithDate(
    val info: DocumentInfo,
    val lastModified: Long
)

@Composable
private fun RecentItem(
    recent: Recent,
    onClick: (Recent) -> Unit,
    onInfo: (Recent) -> Unit,
    onDelete: (Recent) -> Unit,
    onDeleteCache: (Recent) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = { onClick(recent) },
                onLongClick = { showMenu = true }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f / 1.3f)
        ) {
            val leftWidth = 15.dp  // 书脊宽度
            val topHeight = 10.dp  // 顶部厚度

            // 1. 底层封面图：向下和向右偏移，留出位置
            AsyncImage(
                model = recent.path?.let { CustomImageData(getAbsolutePath(it), 160, 200) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = leftWidth, top = topHeight),
                alignment = Alignment.Center
            )

            // 2. 顶部厚度条
            Image(
                painter = painterResource(Res.drawable.components_thumbnail_top),
                contentDescription = null,
                contentScale = ContentScale.FillBounds, // 强制填满容器，防止缩放缝隙
                modifier = Modifier
                    .align(Alignment.TopEnd) // 靠右对齐
                    .fillMaxWidth()
                    .height(topHeight)
                    .padding(start = leftWidth) // 给左侧“角”留出位置
            )

            // 3. 左侧书脊条
            Image(
                painter = painterResource(Res.drawable.components_thumbnail_left),
                contentDescription = null,
                contentScale = ContentScale.FillBounds, // 强制填满容器
                modifier = Modifier
                    .align(Alignment.BottomStart) // 靠下对齐
                    .width(leftWidth)
                    .fillMaxHeight()
                    .padding(top = topHeight) // 给顶部“角”留出位置
            )

            // 4. 关键的“角”图片：不使用 offset，直接放在左上角，并确保大小与两条边完全一致
            Image(
                painter = painterResource(Res.drawable.components_thumbnail_corner),
                contentDescription = null,
                contentScale = ContentScale.FillBounds, // 关键：让图片拉伸填满这块小正方形/长方形
                modifier = Modifier
                    .size(width = leftWidth, height = topHeight)
                    .align(Alignment.TopStart)
            )

            // 4. 页码进度
            Text(
                text = "${recent.page?.plus(1)}/${recent.pageCount}",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 12.sp,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            modifier = Modifier.padding(2.dp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            text = "${recent.path?.inferName()}",
            fontSize = 13.sp,
            lineHeight = 15.sp, // 行间距更紧凑
            overflow = TextOverflow.Ellipsis
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.book_info)) },
                onClick = {
                    showMenu = false
                    onInfo(recent)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.delete_history)) },
                onClick = {
                    showMenu = false
                    onDelete(recent)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.delete_cache)) },
                onClick = {
                    showMenu = false
                    onDeleteCache(recent)
                }
            )
        }
    }
}