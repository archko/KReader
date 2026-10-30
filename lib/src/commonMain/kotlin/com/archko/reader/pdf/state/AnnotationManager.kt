package com.archko.reader.pdf.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.archko.reader.pdf.cache.getCacheDirectory
import com.archko.reader.pdf.component.AnnotationPath
import com.archko.reader.pdf.component.DrawType
import com.archko.reader.pdf.component.PathConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * @author: archko 2026/2/3 :08:47
 */
public class AnnotationManager(public val path: String) {
    public val decodeScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    // 使用 mutableStateMapOf 并直接暴露，保持 Compose 响应性
    public val annotations: MutableMap<Int, MutableList<AnnotationPath>> =
        mutableStateMapOf()

    private val _annotationsFlow = MutableStateFlow<Map<Int, List<AnnotationPath>>>(emptyMap())
    public val annotationsFlow: StateFlow<Map<Int, List<AnnotationPath>>> =
        _annotationsFlow.asStateFlow()

    // 撤销/重做栈：记录的是"操作指令"
    private val undoStack = mutableStateListOf<UndoAction>()
    private val redoStack = mutableStateListOf<UndoAction>()

    // 用于 UI 判断按钮是否可用 - 使用 derivedStateOf 使其成为可观察的状态
    public val canUndo: Boolean by derivedStateOf { undoStack.isNotEmpty() }
    public val canRedo: Boolean by derivedStateOf { redoStack.isNotEmpty() }

    init {
        decodeScope.launch {
            loadFromFile()
            updateAnnotationsFlow()
        }
    }

    public sealed class UndoAction {
        public data class Add(val pageIndex: Int, val path: AnnotationPath) : UndoAction()
        // 未来可以扩展 Delete, Clear 等
    }

    public fun addPath(pageIndex: Int, path: AnnotationPath) {
        val list = annotations.getOrPut(pageIndex) { mutableListOf() }
        list.add(path)
        // 内部 list 是普通 MutableList，直接 add 不会通知 Compose；
        // 重新 put 成新的 list，确保画完松手后立即重绘（draw 已不再每帧执行）。
        annotations[pageIndex] = list.toMutableList()
        undoStack.add(UndoAction.Add(pageIndex, path))
        redoStack.clear() // 新操作会清空重做栈

        updateAnnotationsFlow()

        decodeScope.launch {
            saveToFile()
        }
    }

    public fun undo() {
        if (undoStack.isEmpty()) return
        when (val action = undoStack.removeAt(undoStack.size - 1)) {
            is UndoAction.Add -> {
                val list = annotations[action.pageIndex]
                if (list != null) {
                    list.remove(action.path)
                    // 强制触发 Compose 重组：先移除再重新添加
                    if (list.isEmpty()) {
                        annotations.remove(action.pageIndex)
                    } else {
                        val pageIndex = action.pageIndex
                        val newList = list.toMutableList()
                        annotations.remove(pageIndex)
                        annotations[pageIndex] = newList
                    }
                }
                redoStack.add(action)

                updateAnnotationsFlow()

                decodeScope.launch {
                    saveToFile()
                }
            }
        }
    }

    public fun deletePaths(pageIndex: Int) {
        annotations.remove(pageIndex)
        undoStack.clear()
        redoStack.clear()

        updateAnnotationsFlow()

        decodeScope.launch {
            saveToFile()
        }
    }

    public fun redo() {
        if (redoStack.isEmpty()) return
        when (val action = redoStack.removeAt(redoStack.size - 1)) {
            is UndoAction.Add -> {
                val pageIndex = action.pageIndex
                val list = annotations.getOrPut(pageIndex) { mutableListOf() }
                list.add(action.path)
                // 强制触发 Compose 重组：先移除再重新添加
                val newList = list.toMutableList()
                annotations.remove(pageIndex)
                annotations[pageIndex] = newList
                undoStack.add(action)

                updateAnnotationsFlow()

                decodeScope.launch {
                    saveToFile()
                }
            }
        }
    }

    public fun toJson(): String {
        val file = File(path)
        val size = file.length()
        val fileName = file.name
        return try {
            buildJsonObject {
                put("fileName", fileName)
                put("size", size)
                put("anno", buildJsonArray {
                    annotations.forEach { (pageIndex, paths) ->
                        if (paths.isNotEmpty()) {
                            add(buildJsonObject {
                                put("page", pageIndex)
                                put("paths", buildJsonArray {
                                    paths.forEach { path ->
                                        add(buildJsonObject {
                                            put("points", buildJsonArray {
                                                path.points.forEach { offset ->
                                                    add(buildJsonObject {
                                                        put("x", offset.x)
                                                        put("y", offset.y)
                                                    })
                                                }
                                            })
                                            put("config", buildJsonObject {
                                                // Compose Color.value 是 64 位 packed 值，ARGB 在高 32 位；
                                                // 这里保存真正的 32 位 ARGB，避免存成 00000000（透明）。
                                                put(
                                                    "c",
                                                    ((path.config.color.value shr 32) and 0xFFFFFFFFUL)
                                                        .toString(16).padStart(8, '0')
                                                )
                                                put("s", path.config.strokeWidth)
                                                put("d", path.config.drawType.name)
                                            })
                                        })
                                    }
                                })
                            })
                        }
                    }
                })
            }.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "{}"
        }
    }

    public fun saveToFile() {
        try {
            val saveFile = getAnnotationCacheFile()
            val content = toJson()
            println("save.${saveFile.absolutePath}")
            saveFile.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public fun loadFromFile(): Boolean {
        return try {
            val saveFile = getAnnotationCacheFile()
            if (!saveFile.exists()) {
                return false
            }
            val content = saveFile.readText(Charsets.UTF_8)
            println("load.${saveFile.absolutePath}")
            if (content.isNotEmpty()) {
                fromJson(content)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun fromJson(json: String) {
        try {
            annotations.clear()
            val file = File(path)
            val size = file.length()
            val fileName = file.name
            val jsonObj = Json.parseToJsonElement(json).jsonObject
            if (fileName != jsonObj["fileName"]?.jsonPrimitive?.content) {
                println("new fileName:$fileName")
                getAnnotationCacheFile().delete()
                return
            }
            if (size != jsonObj["size"]?.jsonPrimitive?.longOrNull) {
                println("new filesize:$size")
                getAnnotationCacheFile().delete()
                return
            }
            val annoArray = jsonObj["anno"]?.jsonArray

            annoArray?.let { array ->
                array.forEach { pageObj ->
                    val pageItem = pageObj.jsonObject
                    val pageIndex =
                        pageItem["page"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                    val pathsArray = pageItem["paths"]?.jsonArray

                    pathsArray?.let { paths ->
                        paths.forEach { pathObj ->
                            val path = pathObj.jsonObject
                            val pointsArray = path["points"]?.jsonArray
                            val configObj = path["config"]?.jsonObject

                            if (pointsArray != null && configObj != null) {
                                val points = pointsArray.map { pointObj ->
                                    val point = pointObj.jsonObject
                                    val x =
                                        point["x"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                                    val y =
                                        point["y"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                                    Offset(x, y)
                                }

                                val colorStr = configObj["c"]?.jsonPrimitive?.content ?: "ffff0000"
                                val rawColor = colorStr.toULongOrNull(16) ?: 0xFFff0000UL
                                // 兼容两种旧格式：
                                // 1) 16 位：完整 packed value，ARGB 在高 32 位；
                                // 2) 8 位：ARGB；
                                // 3) 历史 bug 写成的 00000000：无法还原颜色，回退为默认红色。
                                val argb = if (colorStr.length > 8) {
                                    (rawColor shr 32) and 0xFFFFFFFFUL
                                } else {
                                    rawColor and 0xFFFFFFFFUL
                                }
                                // 旧 bug 写成的透明色无法还原，使用默认红色（PathConfig 默认 Color.Red）
                                val visibleArgb = if (argb == 0UL) 0xFFff0000UL else argb
                                val color = Color(visibleArgb.toLong())
                                val strokeWidth =
                                    configObj["s"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 4f
                                val drawTypeStr = configObj["d"]?.jsonPrimitive?.content ?: "CURVE"
                                val drawType = DrawType.valueOf(drawTypeStr)

                                val config = PathConfig(
                                    color = color,
                                    strokeWidth = strokeWidth,
                                    drawType = drawType
                                )

                                val annotationPath = AnnotationPath(points, config)
                                annotations.getOrPut(pageIndex) { mutableListOf() }
                                    .add(annotationPath)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAnnotationCacheFile(): File {
        val file = File(path)
        val fileName = file.nameWithoutExtension
        return File(getCacheDirectory("anno"), "$fileName.json")
    }

    private fun updateAnnotationsFlow() {
        val immutableMap = annotations.mapValues { (_, paths) ->
            paths.toList()
        }
        _annotationsFlow.value = immutableMap
    }
}
