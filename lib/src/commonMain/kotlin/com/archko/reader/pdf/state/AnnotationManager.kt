package com.archko.reader.pdf.state

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.archko.reader.pdf.cache.getCacheDirectory
import com.archko.reader.pdf.component.AnnotationPath
import com.archko.reader.pdf.component.DrawType
import com.archko.reader.pdf.component.PathConfig
import com.archko.reader.pdf.util.normalizePath
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

    private val _annotations = mutableStateMapOf<Int, MutableList<AnnotationPath>>()
    public val annotations: Map<Int, List<AnnotationPath>> = _annotations

    private val _annotationsFlow = MutableStateFlow<Map<Int, List<AnnotationPath>>>(emptyMap())
    public val annotationsFlow: StateFlow<Map<Int, List<AnnotationPath>>> = _annotationsFlow.asStateFlow()

    // 撤销/重做栈：记录的是"操作指令"
    private val undoStack = mutableStateListOf<UndoAction>()
    private val redoStack = mutableStateListOf<UndoAction>()

    // 用于 UI 判断按钮是否可用
    public val canUndo: Boolean get() = undoStack.isNotEmpty()
    public val canRedo: Boolean get() = redoStack.isNotEmpty()

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
        _annotations.getOrPut(pageIndex) { mutableListOf() }.add(path)
        undoStack.add(UndoAction.Add(pageIndex, path))
        redoStack.clear() // 新操作会清空重做栈

        updateAnnotationsFlow()

        decodeScope.launch {
            saveToFile()
        }
    }

    public fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeAt(undoStack.size - 1)
        when (action) {
            is UndoAction.Add -> {
                _annotations[action.pageIndex]?.remove(action.path)
                redoStack.add(action)
                
                updateAnnotationsFlow()
            }
        }
    }

    public fun deletePaths(pageIndex: Int) {
        _annotations.remove(pageIndex)
        undoStack.clear()
        redoStack.clear()
        
        updateAnnotationsFlow()

        decodeScope.launch {
            saveToFile()
        }
    }

    public fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeAt(redoStack.size - 1)
        when (action) {
            is UndoAction.Add -> {
                _annotations.getOrPut(action.pageIndex) { mutableListOf() }.add(action.path)
                undoStack.add(action)
                
                updateAnnotationsFlow()
            }
        }
    }

    public fun toJson(): String {
        val normalizePath = normalizePath(path)
        val file = File(path)
        val size = file.length()
        return try {
            buildJsonObject {
                put("normalizePath", normalizePath)
                put("size", size)
                put("anno", buildJsonArray {
                    _annotations.forEach { (pageIndex, paths) ->
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
                                                put("c", path.config.color.value.toString(16))
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
            _annotations.clear()
            val file = File(path)
            val size = file.length()
            val nPath = normalizePath(path)
            val jsonObj = Json.parseToJsonElement(json).jsonObject
            if (nPath != jsonObj["normalizePath"]?.jsonPrimitive?.content) {
                println("new nPath:$nPath")
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

                                val colorStr = configObj["c"]?.jsonPrimitive?.content
                                val colorValue = colorStr?.toULongOrNull(16)
                                val color = colorValue?.let { Color(it) } ?: Color(0xFFff0000)
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
                                _annotations.getOrPut(pageIndex) { mutableListOf() }
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
        val normalizePath = normalizePath(path)
        return File(getCacheDirectory("anno"), "${normalizePath.hashCode()}.json")
    }

    private fun updateAnnotationsFlow() {
        val immutableMap = _annotations.mapValues { (_, paths) ->
            paths.toList()
        }
        _annotationsFlow.value = immutableMap
    }
}
