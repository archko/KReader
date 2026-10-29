# 文档视图重构设计（LOD / 硬件加速 / 动态切边）

## 一、目标与不变量

重写 `DocumentView / DocumentCanvas / Page / PageNode`，把"整篇文档展开成一张大图、在其中滚动并解码 tile"这个模型做正确：

1. **基础布局不含 zoom**。布局只由 `viewportSize / orientation / columnCount / 每页几何（含切边）` 决定；zoom 与 offset 只属于视口。缩放不重排。
2. **tile 身份是页内局部的**：`(pageIndex, geometryId, level, row, col)`。文档位置、页宽、缩放都不参与 key；布局变化不会让 tile 失效。
3. **每页主尺寸统一**：垂直滚动时所有页 `baseWidth` 相同（= 视口宽 / 列数）；水平滚动时所有页 `baseHeight` 相同（= 视口高）。切边改变内容宽高比，只改变另一维，主尺寸不变。
4. **缩放/平移只更新图层变换**：捏合期间不重排、不重绘内容、不重解码；只有跨 LOD 档位或视口范围变化才更新 tile 集合。
5. **切边是版本化几何**：只让受影响页的 tile 失效，不清全局缓存。
6. **功能与旧实现一致**：滚动、翻页点击、双击、链接、文本选择、划线、搜索高亮、朗读边框、跳页、恢复、方向/列数、TTS 页码、存档。

实现约定：

- 保留 `PageViewState / Page / PageNode` 类名，仅替换内部语义；对外 `MobileDocumentView / DesktopDocumentView / onSaveDocument` 签名不变。
- 解码仍为单线程，不改变 `DecodeService` 的线程模型。
- 旧存档坐标不做兼容迁移。

## 二、坐标空间

```
Content space   原始页像素中的内容区域（切边后）
Document space  基础布局，zoom = 1 时的文档像素
Screen space    screen = doc * zoom + offset
Tile space      (page, geometryId, level, row, col) → content 源矩形 / doc 目标矩形
```

```kotlin
class ViewportState {
    var viewSize: IntSize              // 屏幕
    var zoom: Float
    var offset: Offset                 // 屏幕像素平移
    var docWidth: Float
    var docHeight: Float

    fun toDoc(screen: Offset): Offset
    fun toScreen(doc: Offset): Offset
    val docRect: Rect                  // 可见文档区域
    fun clampOffset(target: Offset): Offset
}
```

所有交互（点击、链接、选择、标注、搜索高亮、翻页、存档）统一走 `toDoc/toScreen`，不再出现 `width * scaleRatio` 这类散落换算。

## 三、组件职责

### 1. `DocumentView`（Compose 外壳）
`MobileDocumentView / DesktopDocumentView` 负责：
- 手势（`CommonGestureHandler`）：只改 `vZoom / offset`，通过 `PageViewState.updateViewport(zoom, offset, request)` 同步。
- 生命周期、存档、跳页、TTS、搜索、切边事件转发。
- 布局变化（视口尺寸、方向、列数、切边、懒切边结果）后同步 `offset = pageViewState.viewOffset`。

`DocumentViewCanvas`：
```
Box(fillMaxSize)
  Box(graphicsLayer {
        translationX = offset.x; translationY = offset.y
        scaleX = zoom; scaleY = zoom
        transformOrigin = (0,0); clip = false
  }) {
    Canvas { revision; drawVisiblePages() }   // 文档坐标
  }
  Canvas { offset; zoom; revision; drawOverlay() }  // 屏幕坐标
```

- 内容层只读 `revision` 与 tile 快照，不读 zoom/offset；捏合只更新图层矩阵。
- 覆盖层读取 offset/zoom（Compose state）与 `revision`，在捏合时重绘，但只处理可见页，且描边保持屏幕像素。
- 不设置 `compositingStrategy = Offscreen`。

### 2. `PageViewState`（文档编排）
持有：`viewport`、`pages: List<Page>`、`pageToRender`、`decodeService`、搜索/朗读/标注状态、`revision`、`layoutVersion`。

职责：
- `updateViewSize(size, zoom, orientation)`：视口尺寸/方向变化 → 重排；zoom 变化 → 只更新视口。
- `updateColumnCount / setCropEnabled`：重排（保留锚点）。
- `updateViewport(zoom, offset, request)`：手势路径；`request=false` 时只更新视口，不刷新 tile（捏合中）。
- `updateOffset / updateVisiblePages`：兼容旧调用，更新视口并刷新 tile。
- `updateVisibleNow`：计算预加载文档区域、可见页、每页可见 tile；请求解码；加载链接；触发懒切边探测。
- `drawVisiblePages / drawOverlay`。
- `handleClick`、`offsetForPage`、`offsetForDoc`、`firstVisiblePageIndex`。

### 3. `ViewportState`
视口变换与边界；`docRect` 用于可见页/可见 tile 计算。

### 4. `PageGeometry`
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

### 5. `Page`
纯布局 + 页内 LOD，不含 zoom 尺寸：

```kotlin
class Page {
    var baseRect: Rect                    // 文档空间
    var geometry: PageGeometry
    val visibleTiles: List<PageNode>      // 当前档
    // fallbackTiles / coarseNodes 内部维护

    var currentSelection: TextSelection?
    var links: List<Hyperlink>

    fun updateLayout(rect)
    fun updateGeometry(g)                 // geometry id 变化才清 tile
    fun updateVisibleTiles(visibleDoc, zoom, request)
    fun drawTiles(scope)
    fun levelFor(zoom): Int
}
```

- 页内网格由内容尺寸决定，不依赖布局尺寸。
- 维护当前档 tile、**上一档 fallback tile**、粗档 tile，逐块替换。
- 链接/文本选择命中测试走 `geometry.docToOriginal`。

### 6. `PageNode` / `TileId`

```kotlin
data class TileId(page, geometryId, level, row, col) {
    val key = "tile/$page/$geometryId/$level/$row/$col"
}

class PageNode(
    val id: TileId,
    val fraction: Rect,       // 页内归一化矩形
    val srcRect: Rect,        // level 纹理像素区域
    val outWidth: Int,
    val outHeight: Int,
    val textureScale: Float,
    val crop: Boolean,
    var bitmap: BitmapState?,
    var queued: Boolean
) {
    fun docRect(base: Rect): Rect
}
```

- key 稳定，不含文档位置与 zoom。
- 解码请求由 `Page.updateVisibleTiles` 发出，不在 draw 阶段发请求。
- 缓存命中时同步 `acquire` 并直接显示，避免排队。

## 四、基础布局

```
Vertical（单列）:
  baseWidth  = viewportWidth
  baseHeight = baseWidth * contentHeight / contentWidth
  pageRect   = (0, top, baseWidth, top + baseHeight)

Vertical（columnCount = C）:
  baseWidth = viewportWidth / C
  pageRect  = (col * baseWidth, rowTop, +baseWidth, +rowHeight)
  rowTop   += max(row 内各页 baseHeight)

Horizontal:
  baseHeight = viewportHeight
  baseWidth  = baseHeight * contentWidth / contentHeight
  pageRect   = (left, 0, +baseWidth, +baseHeight)
```

- `docWidth / docHeight` 为文档总尺寸，不含 zoom。
- 滚动边界：`contentSize = docSize * zoom`，`min = min(0, viewport - content)`。
- 可见页：线性/区间扫描（页数规模下开销可忽略），用于预加载。
- 多列只用于垂直方向。

## 五、LOD 金字塔

### 1. 网格在 content 空间

```
CT         = 512                          // level 0 每 tile 对应的内容像素
baseCols   = max(1, ceil(C.width  / CT))
baseRows   = max(1, ceil(C.height / CT))

cols(L) = max(1, baseCols shl L)     // L >= 0
        = max(1, baseCols shr -L)    // L < 0
rows(L) 同理

textureScale(L) = 2^L
tileOutputPx    = tileContentSize * textureScale(L) <= CT
```

- 网格只依赖 `C`（`geometry.id`）。列数、横竖屏、视口尺寸变化不改变 tile id。
- 允许负 level：大页在 fit 缩放下就需要低于 1:1 的纹理分辨率。
- tile 的 doc 矩形 = `baseRect` × `fraction`。
- 解码源矩形 = 内容矩形（原始页坐标）× `textureScale(L)`；`DecoderAdapter.decodeNode` 的 `scale` 即 `textureScale(L)`，解码器接口不变。
- `tileOutputPx` 超过 4096 时跳过该 tile（防御）。

### 2. 档位选择（逐页）

```
baseScale = baseWidth / C.width        // 水平模式用 baseHeight / C.height
needed    = baseScale * zoom
level     = clamp(ceil(log2(needed)), MIN_TILE_LEVEL, MAX_TILE_LEVEL)
```

- `textureScale >= needed`，纹理分辨率在屏幕需求的 1~2 倍之间。
- 档位只决定取哪一层 tile，不影响布局与 tile 索引。
- 切换档位时当前档 tile 移入 fallback，不释放位图（引用交给缓存），新档逐块到位后覆盖。

### 3. 兜底策略

1. **上一档 fallback**：档位切换时保留上一档可见 tile，按其 doc 矩形绘制；新档到位后覆盖，下次切档/离开页面时释放。
2. **祖先兜底**：当前 tile 无位图时，从 `level-1` 向上查找缓存中的祖先 tile，在祖先的 doc 矩形绘制。
3. **粗档优先（冷启动/跳页/LongStrip）**：请求当前档时同时为可见区域请求 `level-2` 粗档，先入队先解码；粗档到位即先出图，再逐块替换为当前档。粗档 tile 固定尺寸，不会产生整页巨型纹理。

### 4. 超长图 / 单张 TIFF / 长条漫画

- 不做整页 thumbnail，不做整页 `renderPage`；用可见区域的粗档 tile 兜底。
- `baseRows` 可能很大，但只是索引空间，`visibleTiles` 始终只有几十个。
- TIFF 走 `decodeRegionToBitmap`，图片走 `BitmapRegionDecoder`，PDF/Djvu 走区域渲染。
- 需要时可由高一档 tile 下采样生成粗档并缓存。
- 纹理尺寸上限 4096；超长文档的 floating origin 作为后续硬化项（见第十三节）。

## 六、动态切边

### 1. 几何版本

- `PageGeometry.id` 由内容矩形（left/top/width/height）哈希得到。
- 关闭再开启切边若内容矩形相同，`id` 相同，可直接命中旧缓存。
- 切边探测结果在后台写入 `APage.cropBounds`，回主线程后再构建新 `PageGeometry`。

### 2. 两种切边模式

- **预切边**：`APage.cropBounds` 在首次布局前已知（持久化/预扫），构造 `Page` 时直接使用，只布局一次，无跳动。
- **懒切边**：可见页且 `cropBounds == null` 时，提交 `TaskType.CROP` 探测任务（最低优先级）；`DecoderAdapter.processCrop` 检测成功后写回 `APage.cropBounds`，回调主线程更新该页 geometry 并重排。

### 3. 重排原语

所有布局变化（视口尺寸、方向、列数、切边、懒切边结果）统一走：

```
anchor = (pageIndex, contentFx, contentFy, anchorScreenPos)
computeRects()
offset = anchorScreenPos - newDocPoint * zoom
clampOffset + updateVisible
```

- 锚点使用**内容比例**，切边改变页高也不会漂移。
- 页面高度变化导致上方页面位移时，锚点公式自动补偿。
- 只对 `geometry.id` 变化的页清空 tile；其他页仅 `baseRect` 平移，不重解码。
- 懒切边结果按 50ms 窗口批量合并，一次重排。

## 七、渲染与硬件加速

- 内容层：`graphicsLayer` 应用 `translation + scale`；缩放/平移只更新图层属性，沿用已有纹理，由 GPU 重采样。
- 内容绘制在文档坐标；`drawTiles` 不读 zoom/offset。
- 绘制失效来源：`revision`（可见集合/档位变化）、`PageNode.bitmap`（tile 到位）。
- 覆盖层在屏幕坐标绘制：链接框、搜索高亮、文本选择、标注路径、朗读边框、页面分隔线；只画可见页；描边宽度按屏幕像素。
- 视口外页面/瓦片不进入绘制集合，控制 overdraw。
- `clip = false` 允许内容越出视口节点绘制；手势与绘制节点分离，避免变换影响命中测试。

## 八、解码与缓存

- 单解码线程，使用现有 `DecodeService`；不改变线程模型。
- 解码请求只在 `updateVisibleTiles` 中发出；**不在 draw 阶段发请求**。
- 捏合中 `request=false`：只改视口，不刷新 tile；松手后一次性刷新。
- 平移/惯性中 `request=true`：新出现的页/瓦片即时请求；key 去重避免重复。
- 缓存 key：`tile/page/geometryId/level/row/col`；同一档来回缩放可命中。
- `ImageCache.pageCache` 继续供缩略图对话框、封面、文本工具栏使用；viewer 的 tile 缓存使用独立 key 空间，不合并。
- 页面离开可见/预加载区域时释放 `PageNode` 对位图的引用；缓存由 LRU 决定淘汰。

## 九、交互与功能等价

| 功能 | 实现 |
|---|---|
| 平移 / 捏合 / 惯性 | 手势保留；读写 `ViewportState`，边界 `docSize * zoom` |
| 单击翻页（1/4 区、keepPx） | `ViewUtils.handleTapGesture`，屏幕空间平移后 clamp |
| 双击工具栏 | `handleTapGestureInternal`，沿用 |
| 页码回调 | `ViewUtils.firstPage` → `firstVisiblePageIndex` |
| URL/页内链接 | `Page.findLinkAtPoint` + `geometry.docToOriginal` |
| 文本选择 + 工具栏 | 屏幕 → 文档 → 原始页坐标；`TextSelector` 不变；overlay 绘制 |
| 划线/批注 | `drawingPoints` 为页内内容比例；overlay 绘制；数据格式不变 |
| 搜索高亮 / 当前结果 | `DocQuad` 原始页坐标 → 文档 → 屏幕 |
| 朗读边框 / TTS 页 | overlay + `speakingPageIndex` |
| 跳页 / 恢复 / offsetY | `offsetForPage` / `offsetForDoc` |
| 切边开关 / 懒切 | `setCropEnabled` + geometry + 重排 |
| 方向 / 列数 | 重排，保留 zoom 与锚点 |
| 缩略图对话框 | 继续使用 `ImageCache.pageCache` |
| 页面分隔线 | overlay |
| 存档 | 文档坐标 + zoom + 方向 + 切边 |
| Reflow | 独立 `ReflowView`，不在本次范围 |

## 十、存档

保存：

```
pageIndex, scrollX(docX), scrollY(docY), zoom, orientation, reflow, crop
```

- `docX = -offset.x / zoom`，`docY = -offset.y / zoom`。
- 恢复：`offset = -doc * zoom`，再 clamp。
- 不再兼容旧版本屏幕像素语义。

## 十一、生命周期 / 线程

- `PageNode` 位图在主线程赋值；后台只做解码与 `ImageCache.putNode`。
- `shutdown()`：取消解码服务、回收所有页 tile、关闭 decoder、清理缓存。
- 懒切边探测、tile 解码均在 `DecodeService` 单线程队列中；CROP 任务优先级最低。
- 链接、文本选择、搜索 quad 使用原始页坐标，切边只影响映射，不影响数据；标注沿用旧实现的"内容比例"语义。

## 十二、文件级重构清单

| 文件 | 职责 |
|---|---|
| `ViewportState.kt`（新增） | 视口变换、可见文档矩形、边界 clamp |
| `PageGeometry.kt`（新增） | 内容矩形、geometry id、原图↔文档坐标 |
| `PageViewState.kt` | 文档编排：布局、可见页、tile 调度、切边探测、overlay 绘制 |
| `Page.kt` | 文档空间布局 + 页内 LOD + 上一档/粗档 fallback |
| `PageNode.kt` | tile 精灵：`TileId`、fraction、srcRect、bitmap |
| `PageNodePool.kt` | 旧对象池废弃，保留空实现 |
| `DocumentView.kt` | 手势、effects、存档、双层 Canvas |
| `ViewUtils.kt` | 文档空间的点击/翻页/可见页计算 |
| `DecoderAdapter.kt` | `processCrop` 写回 `cropBounds`；`decodeNode` 接口不变 |
| `MobileDocumentView.kt` / `DesktopDocumentView.kt` | 尺寸/缩放变化后同步 `offset` |

## 十三、验证清单与后续硬化

验证：

- 捏合全过程不重排、不重绘内容、不清空；松手跨档只短暂变糊。
- 1↔2 列、横竖屏切换 tile id 不变；旧档 fallback；切回命中缓存。
- 预切边首屏无跳动；懒切边锚点不跳、zoom 不重置、其他页不重解码。
- 当前页上方/下方页面懒切边完成时，视口内容不移动。
- 长条 TIFF 无整页纹理请求；跳页中部先出粗档；30x 平移不抖。
- 双页两页都解码；翻页后预取下一 spread。
- 划线/选择/高亮在切边、缩放、双页下位置正确。
- 缩略图对话框、封面、文本工具栏不受 viewer 缓存改动影响。

后续硬化（不改变上述结构）：

- floating origin：超长文档以锚点页/视口为原点，offset 保持有界，绝对位置用 Double。
- 解码队列优先级与 epoch 取消：在不增加线程的前提下，按"当前档可见 > 粗档 > 相邻页"排序并丢弃过期请求。
- 标注坐标改为原始页坐标，切边开关时位置稳定。
