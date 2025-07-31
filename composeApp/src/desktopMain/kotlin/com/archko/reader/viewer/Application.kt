//package com.archko.reader.viewer
//
//import androidx.compose.foundation.BorderStroke
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.focusable
//import androidx.compose.foundation.gestures.Orientation
//import androidx.compose.foundation.gestures.detectTapGestures
//import androidx.compose.foundation.gestures.scrollBy
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxHeight
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.grid.GridCells
//import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
//import androidx.compose.foundation.lazy.rememberLazyListState
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.text.KeyboardActions
//import androidx.compose.foundation.text.KeyboardOptions
//import androidx.compose.material3.Button
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.material3.TextField
//import androidx.compose.material3.VerticalDivider
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.DisposableEffect
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.derivedStateOf
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableFloatStateOf
//import androidx.compose.runtime.mutableIntStateOf
//import androidx.compose.runtime.mutableStateListOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.referentialEqualityPolicy
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.runtime.saveable.rememberSaveable
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.ExperimentalComposeUiApi
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.shadow
//import androidx.compose.ui.focus.FocusRequester
//import androidx.compose.ui.focus.focusRequester
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.input.key.Key
//import androidx.compose.ui.input.key.KeyEventType
//import androidx.compose.ui.input.key.key
//import androidx.compose.ui.input.key.onKeyEvent
//import androidx.compose.ui.input.key.type
//import androidx.compose.ui.input.pointer.PointerEventType
//import androidx.compose.ui.input.pointer.onPointerEvent
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.layout.onSizeChanged
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.platform.LocalFocusManager
//import androidx.compose.ui.text.input.ImeAction
//import androidx.compose.ui.text.input.KeyboardType
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.Dp
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.LifecycleEventObserver
//import androidx.lifecycle.LifecycleOwner
//import androidx.lifecycle.compose.LocalLifecycleOwner
//import coil3.compose.AsyncImage
//import com.archko.reader.pdf.cache.ImageCache
//import com.archko.reader.pdf.component.PdfColumn
//import com.archko.reader.pdf.entity.CustomImageData
//import com.archko.reader.pdf.entity.Recent
//import com.archko.reader.pdf.scrollbar.DraggableScrollbar
//import com.archko.reader.pdf.scrollbar.rememberDraggableScroller
//import com.archko.reader.pdf.scrollbar.scrollbarState
//import com.archko.reader.pdf.state.LocalPdfState
//import com.archko.reader.pdf.util.inferName
//import com.archko.reader.pdf.viewmodel.PdfViewModel
//import com.mohamedrejeb.calf.io.KmpFile
//import com.mohamedrejeb.calf.picker.FilePickerFileType
//import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
//import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.launch
//import kreader.composeapp.generated.resources.Res
//import kreader.composeapp.generated.resources.ic_back
//import kreader.composeapp.generated.resources.ic_zoom_in
//import kreader.composeapp.generated.resources.ic_zoom_out
//import org.jetbrains.compose.resources.painterResource
//import java.io.File
//import java.util.Locale
//
//@Composable
//fun App(
//    screenWidthInPixels: Int,
//    screenHeightInPixels: Int,
//    viewModel: PdfViewModel,
//) {
//    Theme {
//        var pdf: LocalPdfState? by remember {
//            mutableStateOf(null, referentialEqualityPolicy())
//        }
//
//        val scope = rememberCoroutineScope()
//
//        val recentList by viewModel.recentList.collectAsState()
//        LaunchedEffect(Unit) {
//            val recents = viewModel.loadRecents()
//            println("recents:$recents")
//        }
//
//        if (pdf == null) {
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally,
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color.White)
//                    .padding(paddingValues = PaddingValues(90.dp, 0.dp, 90.dp, 0.dp))
//            ) {
//                val pickerLauncher = rememberFilePickerLauncher(
//                    type = FilePickerFileType.All,
//                    selectionMode = FilePickerSelectionMode.Single
//                ) { files ->
//                    scope.launch {
//                        files.singleOrNull()?.let { file ->
//                            pdf = LocalPdfState(file)
//                            loadProgress(viewModel, file, pdf)
//                        }
//                    }
//                }
//
//                Spacer(
//                    modifier = Modifier.height(16.dp)
//                )
//                Button(
//                    onClick = pickerLauncher::launch
//                ) {
//                    Text("Select PDF file")
//                }
//
//                Spacer(
//                    modifier = Modifier.height(16.dp)
//                )
//
//                if (recentList.isNotEmpty()) {
//                    Row(modifier = Modifier.align(Alignment.Start)) {
//                        Button(
//                            onClick = { viewModel.clear() }
//                        ) {
//                            Text("Clear")
//                        }
//                    }
//
//                    Spacer(
//                        modifier = Modifier.height(16.dp)
//                    )
//
//                    LazyVerticalGrid(
//                        columns = GridCells.FixedSize(140.dp),
//                        verticalArrangement = Arrangement.spacedBy(16.dp),
//                        horizontalArrangement = Arrangement.spacedBy(16.dp)
//                    ) {
//                        items(
//                            count = recentList.size,
//                            key = { index -> "$index" }
//                        ) { i ->
//                            recentItem(recentList[i]) {
//                                val file = KmpFile(File(it.path))
//                                pdf = LocalPdfState(file)
//                                loadProgress(viewModel, file, pdf)
//                            }
//                        }
//                    }
//                }
//            }
//        } else {
//            if (pdf!!.pageCount < 1) {
//                Text(
//                    fontSize = 16.sp,
//                    maxLines = 1,
//                    overflow = TextOverflow.Ellipsis,
//                    color = Color.White,
//                    text = "Error"
//                )
//                return@Theme
//            }
//
//            PdfScreen(
//                screenWidth = screenWidthInPixels,
//                screenHeight = screenHeightInPixels,
//                pdf = pdf!!,
//                scope = scope,
//                viewModel = viewModel,
//                onClickBack = { pdf = null }
//            )
//        }
//    }
//}
//
//private fun loadProgress(
//    viewModel: PdfViewModel,
//    file: KmpFile,
//    pdf: LocalPdfState?
//) {
//    if (pdf != null) {
//        viewModel.insertOrUpdate(file.file.absolutePath, pdf.pageCount.toLong())
//    }
//}
//
//@Composable
//private fun Dp.toIntPx(): Int {
//    return with(LocalDensity.current) { this@toIntPx.roundToPx() }
//}
//
//@Composable
//private fun recentItem(recent: Recent, click: (Recent) -> Unit) {
//    Column(
//        modifier = Modifier.fillMaxSize()
//            .padding(1.dp)
//            .clickable { click(recent) }) {
//        Box(
//            modifier = Modifier.fillMaxWidth().height(180.dp)
//                .shadow(
//                    elevation = 8.dp,
//                    shape = RoundedCornerShape(8.dp),
//                    clip = false,
//                    ambientColor = Color.Black.copy(alpha = 0.2f),
//                    spotColor = Color.Black.copy(alpha = 0.4f)
//                )
//                .border(BorderStroke(1.dp, Color.LightGray))
//        ) {
//            AsyncImage(
//                model = recent.path?.let {
//                    CustomImageData(
//                        it,
//                        180.dp.toIntPx(),
//                        135.dp.toIntPx()
//                    )
//                },
//                contentDescription = null,
//                contentScale = ContentScale.Crop,
//                modifier = Modifier.fillMaxSize()
//            )
//            Text(
//                modifier = Modifier
//                    .align(Alignment.BottomEnd)
//                    .padding(2.dp),
//                color = Color.Blue,
//                maxLines = 1,
//                text = "${recent.page?.plus(1)}/${recent.pageCount}",
//                fontSize = 11.sp,
//                overflow = TextOverflow.Ellipsis
//            )
//        }
//
//        Text(
//            modifier = Modifier.padding(2.dp),
//            color = Color.Black, maxLines = 2,
//            text = "${recent.path?.inferName()}",
//            fontSize = 13.sp,
//            overflow = TextOverflow.Ellipsis
//        )
//    }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun SearchTextField(scope: CoroutineScope) {
//    var text by remember { mutableStateOf("") }
//    val focusManager = LocalFocusManager.current
//    val focusRequester = remember { FocusRequester() }
//
//    TextField(
//        value = text,
//        maxLines = 1,
//        onValueChange = { newText ->
//            text = newText
//        },
//        modifier = Modifier
//            .width(160.dp)
//            .focusRequester(focusRequester),
//        leadingIcon = {
//            /*Icon(
//                imageVector = Icons.Default.Search,
//                contentDescription = "Search"
//            )*/
//        },
//        placeholder = {
//            Text(text = "")
//        },
//        keyboardOptions = KeyboardOptions(
//            keyboardType = KeyboardType.Text,
//            imeAction = ImeAction.Search
//        ),
//        keyboardActions = KeyboardActions(
//            onSearch = {
//                // 在这里添加搜索逻辑，比如打印搜索内容
//                println("Searching for: ${text.lowercase(Locale.getDefault())}")
//                focusManager.clearFocus()
//            }
//        )
//    )
//}
//
//@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
//@Composable
//private fun PdfScreen(
//    screenWidth: Int,
//    screenHeight: Int,
//    pdf: LocalPdfState,
//    onClickBack: () -> Unit,
//    scope: CoroutineScope,
//    viewModel: PdfViewModel,
//    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
//) {
//    var scale by rememberSaveable { mutableFloatStateOf(1f) }
//    val lazyListState = rememberLazyListState()
//    val tocLazyListState = rememberLazyListState()
//    val focusRequester1 = FocusRequester()
//    var width by remember { mutableIntStateOf(screenWidth) }
//    var height by remember { mutableIntStateOf(screenHeight) }
//    val tocVisibile = remember { mutableStateOf(false) }
//    val currentPage by remember {
//        derivedStateOf { lazyListState.firstVisibleItemIndex + 1 }
//    }
//    val scrollbarState = lazyListState.scrollbarState(
//        itemsAvailable = pdf.pageCount,
//    )
//
//    // 在组合完成后请求焦点
//    LaunchedEffect(Unit) {
//        focusRequester1.requestFocus()
//        println("launch.progress:${viewModel.progress}")
//        viewModel.progress?.page?.let { lazyListState.scrollToItem(it.toInt()) }
//    }
//
//    DisposableEffect(pdf) {
//        val observer = LifecycleEventObserver { _, event ->
//            //println("event:$event")
//            if (event == Lifecycle.Event.ON_PAUSE) {
//                /*viewModel.updateProgress(
//                    lazyListState.firstVisibleItemIndex.toLong(),
//                    pdf.pageCount.toLong(),
//                    1.0,
//                    1
//                )*/
//            }
//        }
//
//        lifecycleOwner.lifecycle.addObserver(observer)
//        onDispose {
//            //println("onDispose")
//            /*viewModel.updateProgress(
//                lazyListState.firstVisibleItemIndex.toLong(),
//                pdf.pageCount.toLong(),
//                1.0,
//                1
//            )*/
//            lifecycleOwner.lifecycle.removeObserver(observer)
//            ImageCache.clear()
//        }
//    }
//
//    Scaffold(modifier = Modifier.background(Color.White)) { paddingValues ->
//        @Composable
//        fun screen() {
//            Box(
//                modifier = Modifier.fillMaxSize()
//                    .background(Color.Transparent)
//                    .padding(paddingValues)
//            ) {
//                PdfColumn(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        //.padding(end = 8.dp) // 为滚动条留出空间
//                        .onSizeChanged {
//                            width = it.width
//                            height = it.height
//                            println("app.LazyColumn:$width-$height, $screenWidth-$screenHeight")
//                        }
//                        .pointerInput(Unit) {
//                            detectTapGestures(
//                                onTap = {
//                                    scope.launch {
//                                        focusRequester1.requestFocus()
//                                    }
//                                }
//                            )
//                        }
//                        .focusRequester(focusRequester1)
//                        .focusable()
//                        .onKeyEvent { event ->
//                            // 处理按键按下事件
//                            if (event.type == KeyEventType.KeyDown) {
//                                //println("${event.type}, ${event.key}")
//                                when (event.key) {
//                                    Key.Enter,
//                                    Key.Spacebar -> {
//                                        scope.launch {
//                                            lazyListState.scrollBy(height.toFloat() - 10)
//                                        }
//                                        return@onKeyEvent true
//                                    }
//
//                                    Key.PageUp -> {
//                                        scope.launch {
//                                            lazyListState.scrollBy(-height.toFloat() + 10)
//                                        }
//                                        return@onKeyEvent true
//                                    }
//
//                                    Key.PageDown -> {
//                                        scope.launch {
//                                            lazyListState.scrollBy(height.toFloat() - 10)
//                                        }
//                                        return@onKeyEvent true
//                                    }
//
//                                    Key.DirectionUp -> {
//                                        scope.launch {
//                                            lazyListState.scrollBy(-120f)
//                                        }
//                                        return@onKeyEvent true
//                                    }
//
//                                    Key.DirectionDown -> {
//                                        scope.launch {
//                                            lazyListState.scrollBy(120f)
//                                        }
//                                        return@onKeyEvent true
//                                    }
//
//                                    else -> return@onKeyEvent false
//                                }
//                            } else {
//                                return@onKeyEvent false // 返回 false 表示事件未处理
//                            }
//                        },
//                    //.scale(scale),
//                    viewWidth = width,
//                    viewHeight = height,
//                    state = pdf,
//                    lazyListState = lazyListState
//                )
//                lazyListState.DraggableScrollbar(
//                    modifier = Modifier
//                        .fillMaxHeight()
//                        .padding(horizontal = 2.dp)
//                        .align(Alignment.CenterEnd),
//                    state = scrollbarState,
//                    orientation = Orientation.Vertical,
//                    onThumbMoved = lazyListState.rememberDraggableScroller(
//                        itemsAvailable = pdf.pageCount,
//                    ),
//                )
//            }
//        }
//
//        @Composable
//        fun toc() {
//            if (pdf.outlineItems == null || pdf.outlineItems!!.isEmpty()) {
//                Box(modifier = Modifier.background(Color.White)) {
//                    Text(
//                        modifier = Modifier.width(240.dp).fillMaxHeight().align(Alignment.Center),
//                        fontSize = 24.sp,
//                        color = Color.Black,
//                        text = "No Outline"
//                    )
//                }
//            } else {
//                val hoverStates = remember {
//                    mutableStateListOf<Boolean>().apply {
//                        repeat(pdf.outlineItems!!.size) {
//                            add(false)
//                        }
//                    }
//                }
//                val clickStates = remember {
//                    mutableStateListOf<Boolean>().apply {
//                        repeat(pdf.outlineItems!!.size) {
//                            add(false)
//                        }
//                    }
//                }
//                LazyColumn(
//                    modifier = Modifier.width(300.dp)
//                        .background(Color.White)
//                        .fillMaxHeight()
//                        .focusable(),
//                    state = tocLazyListState,
//                ) {
//                    items(
//                        count = pdf.outlineItems!!.size,
//                        key = { index -> "$index" }
//                    ) { i ->
//                        val isHovered = hoverStates[i]
//                        val isClicked = clickStates[i]
//
//                        // 根据悬停和点击状态设置背景色
//                        val backgroundColor = when {
//                            isClicked -> Color.Gray
//                            isHovered -> Color.LightGray
//                            else -> Color.Transparent
//                        }
//                        Row(
//                            modifier = Modifier.background(backgroundColor)
//                                .padding(4.dp)
//                                .onPointerEvent(PointerEventType.Enter) {
//                                    hoverStates[i] = true
//                                }
//                                .onPointerEvent(PointerEventType.Exit) {
//                                    hoverStates[i] = false
//                                }
//                                .clickable {
//                                    clickStates.forEach { clickStates[i] = false }
//                                    clickStates[i] = !clickStates[i]
//                                    scope.launch {
//                                        lazyListState.scrollToItem(pdf.outlineItems!![i].page)
//                                    }
//                                }
//                        ) {
//                            Text(
//                                modifier = Modifier.weight(1f),
//                                fontSize = 14.sp,
//                                color = Color.Black,
//                                maxLines = 1,
//                                overflow = TextOverflow.Ellipsis,
//                                text = pdf.outlineItems!![i].title.toString()
//                            )
//                            Text(
//                                modifier = Modifier,
//                                fontSize = 12.sp,
//                                color = Color.Black,
//                                text = pdf.outlineItems!![i].page.toString()
//                            )
//                            VerticalDivider(
//                                thickness = 0.5.dp,
//                                modifier = Modifier.fillMaxHeight()
//                            )
//                        }
//                    }
//                }
//            }
//        }
//
//        Column {
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                // 左边部分，使用 Modifier.weight 让其可伸缩
//                Row(
//                    modifier = Modifier.weight(1f),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    IconButton(onClick = onClickBack) {
//                        Icon(
//                            painter = painterResource(Res.drawable.ic_back),
//                            contentDescription = null
//                        )
//                    }
//                    Column(
//                        modifier = Modifier.padding(start = 8.dp)
//                    ) {
//                        Text(
//                            fontSize = 16.sp,
//                            maxLines = 1,
//                            overflow = TextOverflow.Ellipsis,
//                            color = Color.White,
//                            text = "${viewModel.progress?.path}"
//                        )
//                        Text(
//                            fontSize = 16.sp,
//                            color = Color.White,
//                            text = "$currentPage/${pdf.pageCount}"
//                        )
//                    }
//                }
//
//                Row(
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    IconButton(onClick = {
//                        scope.launch {
//                            //ImageCache.clear()
//                            tocVisibile.value = !tocVisibile.value
//                            if (!tocVisibile.value) {
//                                focusRequester1.requestFocus()
//                            }
//                        }
//                    }) {
//                        Icon(
//                            painter = painterResource(Res.drawable.ic_back),
//                            contentDescription = null
//                        )
//                    }
//                    SearchTextField(scope)
//                    IconButton(onClick = { scale -= 0.1f }) {
//                        Icon(
//                            painter = painterResource(Res.drawable.ic_zoom_out),
//                            contentDescription = null
//                        )
//                    }
//
//                    IconButton(onClick = { scale += 0.1f }) {
//                        Icon(
//                            painter = painterResource(Res.drawable.ic_zoom_in),
//                            contentDescription = null
//                        )
//                    }
//                }
//            }
//        }
//        if (tocVisibile.value) {
//            Box(
//                modifier = Modifier.fillMaxSize()
//                    .background(Color.Transparent)
//            ) {
//                screen()
//                toc()
//                /*HorizontalDivider(
//                    modifier = Modifier.width(1.dp),
//                    thickness = 1.dp,
//                    color = Color.Gray,
//                )*/
//            }
//        } else {
//            screen()
//        }
//    }
//}