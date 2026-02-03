package com.archko.reader.pdf.state

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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * @author: archko 2026/2/3 :08:47
 */
public class AnnotationManager(public val normalizePath: String) {
    public val decodeScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    // 原始数据：Map<页码, 路径列表>
    private val _annotations = mutableStateMapOf<Int, MutableList<AnnotationPath>>()
    public val annotations: Map<Int, List<AnnotationPath>> = _annotations

    // 撤销/重做栈：记录的是"操作指令"
    private val undoStack = mutableStateListOf<UndoAction>()
    private val redoStack = mutableStateListOf<UndoAction>()

    // 用于 UI 判断按钮是否可用
    public val canUndo: Boolean get() = undoStack.isNotEmpty()
    public val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        decodeScope.launch {
            loadFromFile()
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
            }
        }
    }

    public fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeAt(redoStack.size - 1)
        when (action) {
            is UndoAction.Add -> {
                _annotations.getOrPut(action.pageIndex) { mutableListOf() }.add(action.path)
                undoStack.add(action)
            }
        }
    }

    // 持久化：转换为 JSON
    public fun toJson(): String {
        return try {
            buildJsonObject {
                put("normalizePath", normalizePath)
                put("anno", buildJsonArray {
                    _annotations.forEach { (pageIndex, paths) ->
                        paths.forEach { path ->
                            add(buildJsonObject {
                                put("page", pageIndex)
                                put("points", buildJsonArray {
                                    path.points.forEach { offset ->
                                        add(buildJsonObject {
                                            put("x", offset.x)
                                            put("y", offset.y)
                                        })
                                    }
                                })
                                // 序列化 config
                                put("config", buildJsonObject {
                                    put("color", path.config.color.value.toLong())
                                    put("strokeWidth", path.config.strokeWidth)
                                    put("drawType", path.config.drawType.name)
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

    // 保存到文件
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

    // 从文件加载
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

    // 从 JSON 解析
    private fun fromJson(json: String) {
        try {
            val jsonObj = Json.parseToJsonElement(json).jsonObject
            val annoArray = jsonObj["anno"]?.jsonArray
            
            annoArray?.let { array ->
                // 先清空现有数据
                _annotations.clear()
                
                array.forEach { itemObj ->
                    val item = itemObj.jsonObject
                    val pageIndex = item["page"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                    val pointsArray = item["points"]?.jsonArray
                    val configObj = item["config"]?.jsonObject
                    
                    if (pointsArray != null && configObj != null) {
                        val points = pointsArray.map { pointObj ->
                            val point = pointObj.jsonObject
                            val x = point["x"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                            val y = point["y"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                            Offset(x, y)
                        }
                        
                        val colorValue = configObj["color"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0xFF0000FF
                        val strokeWidth = configObj["strokeWidth"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 4f
                        val drawTypeStr = configObj["drawType"]?.jsonPrimitive?.content ?: "CURVE"
                        val drawType = DrawType.valueOf(drawTypeStr)
                        
                        val config = PathConfig(
                            color = Color(colorValue),
                            strokeWidth = strokeWidth,
                            drawType = drawType
                        )
                        
                        val annotationPath = AnnotationPath(points, config)
                        _annotations.getOrPut(pageIndex) { mutableListOf() }.add(annotationPath)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 获取注解缓存文件
    private fun getAnnotationCacheFile(): File {
        return File(getCacheDirectory("anno"), "${normalizePath.hashCode()}.json")
    }
}
