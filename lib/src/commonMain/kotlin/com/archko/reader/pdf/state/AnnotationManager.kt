package com.archko.reader.pdf.state

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.archko.reader.pdf.component.AnnotationPath

/**
 * @author: archko 2026/2/3 :08:47
 */
public class AnnotationManager(public val fileHash: String) {
    // 原始数据：Map<页码, 路径列表>
    private val _annotations = mutableStateMapOf<Int, MutableList<AnnotationPath>>()
    public val annotations: Map<Int, List<AnnotationPath>> = _annotations

    // 撤销/重做栈：记录的是“操作指令”
    private val undoStack = mutableStateListOf<UndoAction>()
    private val redoStack = mutableStateListOf<UndoAction>()

    // 用于 UI 判断按钮是否可用
    public val canUndo: Boolean get() = undoStack.isNotEmpty()
    public val canRedo: Boolean get() = redoStack.isNotEmpty()

    public sealed class UndoAction {
        public data class Add(val pageIndex: Int, val path: AnnotationPath) : UndoAction()
        // 未来可以扩展 Delete, Clear 等
    }

    public fun addPath(pageIndex: Int, path: AnnotationPath) {
        _annotations.getOrPut(pageIndex) { mutableListOf() }.add(path)
        undoStack.add(UndoAction.Add(pageIndex, path))
        redoStack.clear() // 新操作会清空重做栈
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
        // 使用 gson 或 kotlinx-serialization 将 _annotations 序列化
        return ""
    }
}