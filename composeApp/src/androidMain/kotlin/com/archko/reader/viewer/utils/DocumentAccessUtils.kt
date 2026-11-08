package com.archko.reader.viewer.utils

import com.archko.reader.pdf.entity.DocumentInfo
import com.archko.reader.pdf.util.getAbsolutePath
import com.archko.reader.pdf.util.getExtension
import java.io.File
import java.io.FileInputStream

/**
 * 判断文件路径是否“真的”可读。
 *
 * 注意：不能只依赖权限状态或 [File.exists]/[File.canRead] 判断。
 * 部分设备即使已经授予“所有文件访问权限”，访问某些目录（例如 Android/data）仍然会被拒绝，
 * 只有真正打开并读取文件才能确认路径可用。
 */
fun isPathReadable(path: String?): Boolean {
    if (path.isNullOrEmpty()) return false
    if (path.startsWith("content://") || path.startsWith("file://")) return false

    var input: FileInputStream? = null
    return try {
        val file = File(path)
        if (!file.exists() || !file.isFile) return false
        input = FileInputStream(file)
        // 读取一个字节，真正触发文件读取，避免 canRead() 在部分设备上的误报
        if (file.length() > 0L) {
            input.read()
        }
        true
    } catch (e: Exception) {
        false
    } finally {
        try {
            input?.close()
        } catch (_: Exception) {
        }
    }
}

/**
 * 判断 [DocumentInfo] 中的文件路径是否实际可读。
 * 历史记录中保存的路径可能是去掉存储根目录后的相对路径，这里统一转换成绝对路径再校验。
 */
fun isDocumentPathReadable(document: DocumentInfo): Boolean {
    val rawPath = document.path ?: return false
    if (rawPath.startsWith("content://") || rawPath.startsWith("file://")) return false
    return isPathReadable(getAbsolutePath(rawPath))
}

/**
 * 按“路径优先”的规则解析文档的打开方式：
 * 1. 文档带有文件路径，且该路径实际可读 -> 使用文件路径打开（清空 uri）；
 * 2. 路径不可读，但存在 uri -> 保留 uri，由解码器通过 ContentResolver 打开；
 * 3. 其它情况原样返回。
 *
 * 使用 uri 打开原本只是为了避免没有磁盘访问权限的问题，
 * 因此只要能确认路径真的可读，就应该优先使用路径，减少对 uri 授权的依赖。
 */
fun resolveDocumentForOpen(document: DocumentInfo): DocumentInfo {
    val rawPath = document.path
    if (!rawPath.isNullOrEmpty() &&
        !rawPath.startsWith("content://") &&
        !rawPath.startsWith("file://")
    ) {
        val absolutePath = getAbsolutePath(rawPath)
        if (isPathReadable(absolutePath)) {
            val file = File(absolutePath)
            return document.copy(
                uri = null,
                path = absolutePath,
                fileSize = document.fileSize.takeIf { it > 0L } ?: file.length(),
                ext = document.ext?.takeIf { it.isNotEmpty() } ?: absolutePath.getExtension()
            )
        }
    }

    // 兼容历史数据：path 字段里直接保存了 content:// 地址
    if (!document.hasUri()) {
        val contentPath = rawPath?.takeIf { it.startsWith("content://") }
        if (contentPath != null) {
            return document.copy(uri = contentPath, path = null)
        }
    }
    return document
}
