# 文档视图设计（LOD / 图层变换 / 动态切边）

## 一、核心模型

整篇文档在文档坐标系中展开，缩放与滚动只属于视口：

- 基础布局不包含 zoom，只由 `viewportSize / orientation / columnCount / 每页几何` 决定。
- 缩放、平移只更新 `graphicsLayer` 的变换矩阵，不重新布局、不重组。
- 页面内容是页内 LOD tile 网格，tile 身份与文档位置、页宽、缩放无关。
- 切边是版本化几何，只影响对应页的 tile，不清全局缓存。
- 解码为单线程队列，绘制阶段不发解码请求。

设计规则：

1. 垂直滚动时所有页 `baseWidth` 相同（视口宽 / 列数）；水平滚动时所有页 `baseHeight` 相同（视口高）。切边只改变另一维。
2. tile key 为 `(pageIndex, geometryId, level, row, col)`。
3. 每页始终有一张整页缩略图作为兜底，当前档 tile 覆盖其上。
4. 捏合过程中只改变视口与图层矩阵，不刷新 tile；松手后再按当前档刷新。
5. 布局变化（视口、方向、列数、切边）统一通过锚点重排，锚点用内容比例。

## 二、坐标空间

```
Content space   原始页像素中的内容区域（切边后）
Document space  基础布局，zoom = 1 时的文档像素
Screen space    screen = doc * zoom + offset
Tile space      (pageIndex, geometryId, level, row, col) → content 源矩形 / doc 目标矩形
```

`ViewportState` 负责三件事：

```kotlin
class ViewportState {
    var viewSize: IntSize      // 屏幕尺寸
    var zoom: Float
    var offset: Offset         // 屏幕像素平移
    var docWidth: Float
    var docHeight: Float

    val docRect: Rect          // 可见文档区域
    fun toDoc(screen: Offset): Offset
    fun toScreen(doc: Offset): Offset
    fun clampOffset(target: Offset): Offset
}
```

- `docRect = toDoc(0,0) .. toDoc(viewSize)`。
- 滚动边界：`scaledWidth = docWidth * zoom`，`minX = min(0, viewSize.width - scaledWidth)`，Y 同理。
- 点击、链接、文本选择、标注、搜索高亮、翻页、存档统一走 `toDoc / toScreen`。

## 三、组件与数据流

### 1. 入口外壳

`MobileDocumentView` / `DesktopDocumentView`：

- 用 `rememberDocumentViewState(...)` 创建 `DocumentViewState`。
- 用 `Box + onSizeChanged` 把 `viewSize` 写入状态，并同步 `offset = pageViewState.viewOffset`。
- 组合 `CommonGestureHandler`、`DocumentViewCanvas`、`TextActionToolbarWrapper`。
- 桌面端额外接入滚轮、键盘、焦点处理。

`DocumentViewState` 持有一份视图状态：

```kotlin
class DocumentViewState(
    viewSize, offset, vZoom, orientation, toPage, isJumping,
    pageViewState,
    isTextSelecting, selectedPage, showTextActionToolbar,
    selectionStartPos, selectionEndPos,
    drawingPoints, activeDrawingPage,
    flingJob, lastTapTime, tapDelayJob, scope
)
```

`DocumentViewEffects` 负责副作用：

- `columnCount / crop / orientation / viewSize / jumpToPage / speakingPageIndex / searchHighlightQuads` 变化时同步到 `PageViewState`。
- 设置页内链接、URL 链接回调。
- `snapshotFlow { firstVisiblePageIndex() }` → `onPageChanged`。
- 生命周期 `ON_PAUSE` 与 `onDispose` 时存档；`onDispose` 还会取消延时任务、调用 `onCloseDocument`、`shutdown()`、清理 `ImageCache`。

### 2. PageViewState（文档编排）

持有：

- `viewport: ViewportState`
- `pages: List<Page>`、`pageToRender: List<Page>`
- `docWidth / docHeight`、`viewOffset`、`vZoom`、`viewSize`
- `revision`：可见页或可见 tile 集合变化，用于内容层失效
- `layoutVersion`：重排完成
- `overlayZoom`：覆盖层重录时使用的 zoom 快照
- `isInteracting`：手势/fling 活动标记
- `decodeService`、`mainScope`
- 朗读、搜索高亮、标注、切边探测状态
- `onPageLinkClick / onUrlLinkClick / onDecodeCompleted / onLayoutApplied`
- `wantedPagesRef`：预加载页集合，解码线程用它判断任务是否过期

主要职责：

- `updateViewSize`：视口尺寸/方向变化重排；仅 zoom 变化时只更新视口并刷新可见 tile。
- `updateColumnCount / setCropEnabled`：重排并保留锚点。
- `updateViewport(zoom, offset, request)`：手势路径，只更新视口字段；`request=true` 时调度可见性刷新，`request=false` 时按 16ms 节流检查是否需要重新扫描。
- `updateOffset / updateVisiblePages`：跳页与外部定位，更新视口并立即刷新。
- `updateVisibleNow`：扫描可见页、维护预加载页、请求缩略图与 tile、触发切边探测。
- `drawVisiblePages / drawOverlay`。
- `handleClick / offsetForPage / offsetForDoc / firstVisiblePageIndex`。

### 3. PageGeometry

内容几何的不可变快照：

```kotlin
class PageGeometry(
    naturalWidth: Float, naturalHeight: Float,
    cropBounds: Rect?, cropEnabled: Boolean
) {
    val left, top, width, height: Float   // 内容矩形（原始页坐标）
    val id: Int                           // 内容矩形哈希，作为 tile 版本
    fun originalToDoc(x, y, base): Offset
    fun docToOriginal(x, y, base): Offset
    fun originalRectToDoc(rect, base): Rect
    fun originalToDocY(y, base): Float
}
```

- `id` 由 left/top/width/height 计算；关闭再开启切边若内容矩形相同，`id` 相同。
- 懒切边结果写回 `APage.cropBounds` 后，回到主线程构建新的 `PageGeometry`。

### 4. Page

纯布局 + 页内 LOD，不含 zoom 尺寸：

```kotlin
class Page {
    var baseRect: Rect                 // 文档空间
    var geometry: PageGeometry
    private var currentLevel: Int
    private val tileNodes: List<PageNode>
    private var visibleTiles: List<PageNode>
    private var fallbackTiles: List<PageNode>

    var currentSelection: TextSelection?
    var links: List<Hyperlink>

    fun updateLayout(rect)
    fun updateGeometry(g)
    fun levelFor(zoom): Int
    fun updateVisibleTiles(visibleDoc, zoom, request): Boolean
    fun requestThumbnail()
    fun clearVisibleTiles()
    fun drawTiles(scope)

    fun loadText()
    fun loadLinks()
    fun findLinkAtPoint(docX, docY)
    fun startTextSelection(docX, docY)
    fun updateTextSelection(docX, docY)
    fun endTextSelection()
    fun recycle()
}
```

- `updateLayout` 只改 `baseRect` 并重置可见范围，不丢 tile。
- `updateGeometry` 在 geometry 变化时释放 tile、fallback、缩略图，重置档位与选择。
- 页内网格由内容尺寸决定，不依赖布局尺寸。

### 5. PageNode

```kotlin
class PageNode(
    val key: Long,          // level/row/col 打包，用于页内查找
    val cacheKey: String,   // tile/page/geometryId/level/row/col
    val level: Int,
    val row: Int,
    val col: Int,
    val fraction: Rect,     // 页内归一化矩形
    val srcRect: Rect,      // level 纹理像素区域
    val outWidth: Int,
    val outHeight: Int,
    val textureScale: Float,
    val crop: Boolean
) {
    var bitmap: BitmapState?   // Compose state
    val queued: AtomicBoolean
    val removed: AtomicBoolean
    var requestAt: Long
    fun docRect(base: Rect): Rect
}
```

- `bitmap` 是 `mutableStateOf`，tile 到位会让内容层失效。
- `removed` 由释放路径设置，解码线程执行前可据此跳过过期任务。
- `docRect = baseRect × fraction`。

### 6. DecodeService / Decoder

`Decoder` 接口提供三种任务：

- `TaskType.PAGE`：整页缩略图。
- `TaskType.NODE`：LOD tile 区域。
- `TaskType.CROP`：切边探测。

`DecoderAdapter` 把 `ImageDecoder` 适配为 `Decoder`：

- `decodePage`：按 `calculateThumbnailSize` 生成缩略图，超大页逐步降低基准尺寸。
- `decodeNode`：调用 `renderPageRegion(srcRect, pageIndex, textureScale, ..., outWidth, outHeight)`。
- `processCrop`：渲染小图后由 `SmartCropUtils` 检测切边并写回 `APage.cropBounds`。

## 四、基础布局

`PageViewState.computeRects()`：

```
Vertical（单列）：
  baseWidth  = viewportWidth
  baseHeight = baseWidth * contentHeight / contentWidth
  pageRect   = (0, top, baseWidth, top + baseHeight)

Vertical（columnCount = C）：
  columnWidth = viewportWidth / C
  pageRect    = (col * columnWidth, rowTop, +columnWidth, +columnWidth * contentHeight / contentWidth)
  rowTop     += max(row 内各页 baseHeight)

Horizontal：
  baseHeight = viewportHeight
  baseWidth  = baseHeight * contentWidth / contentHeight
  pageRect   = (left, 0, left + baseWidth, baseHeight)
```

- `docWidth / docHeight` 为文档总尺寸，不含 zoom。
- 重排统一走锚点：

```
anchor = (pageIndex, fx, fy, anchorScreenPos)
computeRects()
offset = anchorScreenPos - newDocPoint * zoom
clampOffset + updateVisibleNow
```

- 锚点使用视口左上角在锚点页内的内容比例，切边改变页高也不漂移。
- 可见页扫描：垂直/水平单列用二分定位，再向前后扩展；多列模式逐页线性检测。

## 五、LOD

### 1. 页内网格（content 空间）

```
TILE_CONTENT_PX      = 1024
TILE_LONG_CONTENT_PX = 1536      // 长条页在长边方向使用
LEVEL_STEPS_PER_OCTAVE = 8
textureScale(L) = 2^(L / 8)
MIN_TILE_LEVEL = -48
MAX_TILE_LEVEL = 64

cols(L) = max(1, ceil(contentWidth  * textureScale(L) / targetWidth))
rows(L) = max(1, ceil(contentHeight * textureScale(L) / targetHeight))
```

- 普通页目标 1024；`width > height * 3` 时长边用 1536，`height > width * 3` 时同理。
- 网格只依赖 `geometry.id` 与 level，列数、横竖屏、视口尺寸变化不改变 tile id。
- tile 的 content 边界与纹理边界都取整，相邻 tile 共用同一条边界，绘制时统一 `round`，避免接缝重叠。
- 单 tile 输出超过 4096 时跳过该 tile。

### 2. 档位选择（逐页）

```
vertical = orientation == Vertical || columnCount > 1
baseMain    = vertical ? baseWidth : baseHeight
contentMain = vertical ? contentWidth : contentHeight
baseScale   = baseMain / contentMain
needed      = baseScale * zoom
idealLevel  = ceil(log2(needed) * LEVEL_STEPS_PER_OCTAVE)
```

档位带迟滞，避免缩放停在档位边界时反复重建：

```
当前档位的 textureScale 记为 currentScale
若 needed <= currentScale * 1.10 且 currentScale <= needed * 1.30，则沿用 currentLevel
否则取 idealLevel 并 clamp 到 [MIN_TILE_LEVEL, MAX_TILE_LEVEL]
```

- 允许最多 10% 欠采样、30% 过采样，档位只在真正超出区间时切换。
- 捏合过程中不切档（`request=false`），松手后才按迟滞规则决定是否切换。
- 切档时旧档可见 tile 移入 `fallbackTiles` 保留位图，新档 tile 就绪后覆盖并释放旧档。

### 3. 页内兜底

绘制顺序（自下而上）：

1. **页面缩略图**：每页一张整页低分辨率图，缓存于 `ImageCache` 页缓存。
2. **上一档 fallback**：切档期间保留的旧档可见 tile。
3. **当前档 tile**：已解码且属于当前可见范围的 tile。

绘制规则：

- 整页缩略图始终作为底图：放大后相邻 tile 在非整数缩放下会出现亚像素接缝，底图可避免接缝直接露出阅读背景。
- `visibleTiles` 未全部就绪时才绘制 fallback。
- 页面缩略图首次可见时请求一次；解码完成后回调主线程挂到 `Page`，并补加载链接。

### 4. 可见 tile 生命周期

`Page.updateVisibleTiles(visibleDoc, zoom, request)`：

- `request=false` 且当前档位有效时沿用 `currentLevel`，避免捏合逐帧重算档位。
- 根据可见文档矩形与页内网格计算 `colStart..colEnd / rowStart..rowEnd`。
- 范围不变时只做 `requestPendingDecodes`，不重建列表。
- 范围变化时复用已有节点，缺失节点新建并请求解码；可见 tile 集合变化会让 `PageViewState.revision++`，确保 draw 先观察到新节点的 `bitmap == null`。
- 未解码且离开范围的节点立即释放；已解码的离屏节点保留 `MAX_RETAINED_TILES = 4` 作为回滚缓冲，其余释放。
- 新档可见 tile 全部就绪后释放 fallback。

## 六、渲染

### 1. 图层结构

```
Box(fillMaxSize)
  Box(graphicsLayer {
        translationX = offset.x
        translationY = offset.y
        scaleX = zoom
        scaleY = zoom
        transformOrigin = (0, 0)
        clip = false
  }) {
    Canvas { revision; drawVisiblePages() }   // 内容层，文档坐标
    Canvas { drawOverlay() }                  // 覆盖层，文档坐标
  }
```

- 内容层与覆盖层共用一个变换图层，都以文档坐标绘制。
- 平移/缩放只更新 `offset / vZoom` 这两个图层属性，不触发重组；`offset / vZoom` 只在 `graphicsLayer` 块中读取。
- 覆盖层独立成一张 Canvas：tile 解码只让内容层失效，不连带重算链接框、选区、搜索高亮。
- 不使用 `compositingStrategy = Offscreen`；`clip = false` 允许内容越出视口节点绘制。

### 2. 内容层

`PageViewState.drawVisiblePages` 只遍历 `pageToRender`：

```
for (page in pageToRender) page.drawTiles(drawScope)
```

`Page.drawTiles` 依次绘制缩略图（按规则）、fallback（按规则）、可见 tile。

- draw 不读 zoom / offset。
- 绘制失效来源：`revision`、`pageToRender`、`PageNode.bitmap`、页面缩略图状态。
- 视口外页面与 tile 不进入绘制集合。

### 3. 覆盖层

`PageViewState.drawOverlay` 只遍历 `pageToRender`，绘制：

- 链接高亮框
- 文本选择 quad
- 搜索高亮与当前结果
- 朗读页边框
- 标注路径（页内内容比例）
- 页面分隔线

- 覆盖层同样在文档坐标绘制，描边宽度除以 `overlayZoom` 保持屏幕像素。
- `overlayZoom` 只在可见性刷新时更新，捏合过程中不更新，因此捏合不会因覆盖层读取 zoom 而让 Canvas 逐帧失效。
- 标注路径按 `baseRect` 与点列表缓存，布局不变时不重建 Path。

## 七、手势与视口更新

`CommonGestureHandler` 按 `GestureMode` 分支：

### VIEW

- `awaitEachGesture` 处理按下、移动、抬起。
- 按下：`beginInteraction()`、取消进行中的 fling。
- 双指：`vZoom` 更新，围绕 centroid 计算新 offset，`updateViewport(..., request=false)`；不切档、不重建 tile。
- 单指：超过 touchSlop 后进入拖动，累积 pan，`updateViewport(..., request = 距上次请求 >= 100ms)`。
- 抬起：用 `VelocityTracker` 计算速度；未拖动时进入点击处理。

### 视口更新策略

`PageViewState.updateViewport`：

- 写入 viewport 的 zoom/offset、`viewOffset`、总尺寸，并记录交互时间。
- `request=true`：通过 `mainScope.launch { yield(); updateVisibleNow(true) }` 调度，合并同一帧内的多次请求。
- `request=false`：最多每 16ms 检查一次；只有可见矩形超出上次扫描范围（可能进入新页）时才重新扫描。
- 捏合期间 `request=false`，不刷新 tile；松手后一次 `request=true` 按当前档刷新。

### 点击与翻页

`handleTapGestureInternal`：

- 300ms 内第二次点击视为双击，垂直方向 1/4~3/4 区域触发 `onDoubleTapToolbar`。
- 单击延迟 300ms：先尝试链接点击；未命中则 `ViewUtils.handleTapGesture` 做屏幕空间翻页（保留 `tap_page_keep_dp`）；仍未命中则回调 `onTapNonPageArea`。
- 翻页后通过 `updateOffset` 更新视口。

### 惯性

`performFling`：

- `exponentialDecay`，`frictionMultiplier = 0.35`，`absVelocityThreshold = 0.5`，初速度乘 1.1。
- 每帧 clamp offset；tile 请求每 100ms 一次。
- 顶到边界且速度继续向外时取消动画。
- 动画结束调用 `endInteraction()`。

### SELECTION / DRAW

- SELECTION：`detectDragGestures`，屏幕 → 文档 → 原始页坐标，调用 `Page.startTextSelection / updateTextSelection / endTextSelection`，完成后显示文本工具栏。
- DRAW：记录页内归一化点；`DrawType.LINE` 走直线点集，否则自由绘制；实时更新 `activeDrawingAnno`，抬手写入 `AnnotationManager`。

## 八、解码与缓存

### 1. 线程与队列

- `DecodeService` 使用单个常驻线程 `Decode-Worker-Thread`。
- Android 上解码线程为 `THREAD_PRIORITY_DEFAULT`，避免和 RenderThread/UI 抢调度；桌面为略高于普通线程。
- 队列优先级：`PAGE > NODE > CROP`。
- NODE 队列后进先出，新请求对应最新视口，先处理积压旧任务只会浪费 CPU。
- 提交 NODE 前移除同 key 的排队任务，避免重试导致重复入队。
- `pendingJobs` 保存每个 key 的最新任务实例；执行前若发现已被替换则直接跳过。
- 执行前调用 `callback.shouldRender`；页面已离开预加载范围、节点已释放、或已 shutdown 时不解码。

### 2. 请求时机

- 只在可见性刷新时请求：`PageViewState.updateVisibleNow` → `Page.updateVisibleTiles` / `Page.requestThumbnail`。
- 绘制阶段只读 bitmap，不发解码请求。
- 可见 tile 3 秒仍未拿到位图会重置排队标记并重新请求。
- 解码在 worker 线程完成；`ImageCache.putNode/putPage` 后通过 `mainScope` 回到主线程，`acquire()` 成功后赋给 `PageNode.bitmap` 或缩略图。

### 3. 缓存

- `ImageCache` 分 tile 缓存与页缓存，默认上限分别为 256MB / 48MB。
- tile key：`tile/page/geometryId/level/row/col`。
- `BitmapState` 内置引用计数；从缓存取出后必须 `acquire()`，离开绘制/可见集合时 `release()`。
- 主缓存查询使用 `ConcurrentHashMap`，主线程读路径无锁；候选池按时间淘汰。
- 已解码的离屏 tile 由缓存统一保留，页内只保留 `MAX_RETAINED_TILES = 4`。
- 页面离开预加载范围时释放页内 tile 与缩略图引用。

## 九、动态切边

- `PageGeometry.id` 由内容矩形哈希得到，切边版本由 id 表达。
- 预切边：`APage.cropBounds` 已知时，首次布局直接使用，只排一次。
- 懒切边：可见页且 `cropBounds == null` 时提交最低优先级 CROP 任务。
- `DecoderAdapter.processCrop` 检测成功后写回 `APage.cropBounds`。
- 回调主线程后：
  - 记录待应用页，50ms 窗口内批量合并。
  - 若最近 300ms 内有交互，延后 120ms 再应用，避免运动中改布局。
  - 应用时只对 `geometry.id` 变化的页 `updateGeometry`，然后统一 `relayout()`。
- `relayout` 使用内容比例锚点，切边导致页高变化不会让视口漂移；未变化页不重新解码。

## 十、功能映射

| 功能 | 实现 |
|---|---|
| 平移 / 捏合 / 惯性 | `CommonGestureHandler` → `ViewportState` |
| 点击翻页 | `ViewUtils.handleTapGesture`，屏幕空间 + `tap_page_keep_dp` |
| 双击工具栏 | `handleTapGestureInternal`，中间区域 |
| 页码回调 | `snapshotFlow { firstVisiblePageIndex() }` |
| URL / 页内链接 | `Page.findLinkAtPoint` + `geometry.docToOriginal` |
| 文本选择 + 工具栏 | SELECTION 手势 + `Page` 选区 + overlay |
| 划线 / 批注 | DRAW 手势 + 页内归一化点 + `AnnotationManager` |
| 搜索高亮 / 当前结果 | `DocQuad` 原始页坐标 → 文档坐标 → overlay |
| 朗读边框 / TTS 页 | 覆盖层 + `speakingPageIndex` |
| 跳页 / 恢复 / offsetY | `offsetForPage` / `offsetForDoc` |
| 切边开关 / 懒切边 | `setCropEnabled` + `PageGeometry` + 锚点重排 |
| 方向 / 列数 | 重排，保留 zoom 与锚点 |
| 页面分隔线 | 覆盖层 |
| 存档 | 文档坐标 + zoom + 方向 + 切边 |
| Reflow | 独立 `ReflowView` |

## 十一、存档与生命周期

存档字段：

```
pageIndex, pageCount, docX, docY, zoom, orientation, reflow, crop
```

- `docX = -offset.x / zoom`，`docY = -offset.y / zoom`。
- 恢复时 `offset = -doc * zoom`，再 clamp。
- `ON_PAUSE` 与 `onDispose` 时保存。
- `onDispose`：
  - 取消延时任务
  - 保存一次
  - `onCloseDocument`
  - `PageViewState.shutdown()`：取消解码服务、回收页 tile、关闭 decoder、清理缓存
  - `ImageCache.clear()`

线程约定：

- 布局、可见性、tile 请求、bitmap 赋值都在主线程。
- 解码与 `ImageCache.put*` 在 worker 线程。
- 懒切边、tile 解码、缩略图解码共用 `DecodeService` 单线程队列，CROP 最低优先级。
- 链接、文本、搜索 quad 使用原始页坐标；切边只影响坐标映射，不影响数据。
- 标注使用页内内容比例，切边与缩放时位置稳定。
