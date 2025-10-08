package com.archko.reader.pdf.util

import com.archko.reader.pdf.util.FileTypeUtils.MAX_SIZE_MB
import java.io.File

/**
 * 文件类型判断工具类
 * @author: archko 2025/1/20
 */
public object FileTypeUtils {

    private const val MAX_SIZE_MB = 30 * 1024 * 1024L

    /**
     * 判断是否为图片文件
     * Android支持的图片格式：JPEG, PNG, GIF, BMP, WebP, HEIF, HEIC
     */
    public fun isImageFile(path: String): Boolean {
        return path.lowercase().let { filePath ->
            filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")
                    || filePath.endsWith(".png") || filePath.endsWith(".gif")
                    || filePath.endsWith(".bmp") || filePath.endsWith(".webp")
                    || filePath.endsWith(".heif") || filePath.endsWith(".heic")
        }
    }

    /**
     * 判断是否为有效的图片文件（包括大小判断）
     * @param file 文件对象
     * @param MAX_SIZE_MB 最大文件大小（MB）
     * @return 是否为有效的图片文件
     */
    public fun isValidImageFile(file: File): Boolean {
        return file.exists()
                && file.isFile
                && isImageFile(file.absolutePath)
                && file.length() <= MAX_SIZE_MB
    }

    public fun isAccetableImageFile(file: File): Boolean {
        return file.exists()
                && file.isFile
                && isImageFile(file.absolutePath)
    }

    /**
     * 判断是否为文档文件
     */
    public fun isDocumentFile(path: String): Boolean {
        return path.lowercase().let { filePath ->
            filePath.endsWith(".pdf") || filePath.endsWith(".epub") ||
                    filePath.endsWith(".mobi") || filePath.endsWith(".xps") ||
                    filePath.endsWith(".fb") || filePath.endsWith(".fb2") ||
                    filePath.endsWith(".pptx") || filePath.endsWith(".docx")
        }
    }

    public fun isTiffFile(path: String): Boolean {
        return path.lowercase().let { filePath ->
            filePath.endsWith(".jfif") || filePath.endsWith(".tiff")
                    || filePath.endsWith(".tif")
        }
    }

    /**
     * 判断是否应该保存进度
     * 只有单文档文件才保存进度
     */
    public fun shouldSaveProgress(paths: List<String>): Boolean {
        return paths.size == 1 && isDocumentFile(paths.first())
    }

    /**
     * 判断是否应该显示大纲功能
     * 只有单文档文件才显示大纲
     */
    public fun shouldShowOutline(paths: List<String>): Boolean {
        return paths.size == 1 && isDocumentFile(paths.first())
    }

    /**
     * 过滤文件列表，移除大于指定大小的文件
     * @param files 文件列表
     * @param MAX_SIZE_MB 最大文件大小（MB）
     * @return 过滤后的文件列表
     */
    public fun filterFilesBySize(files: List<File>): List<File> {
        return files.filter { file ->
            file.exists()
            && file.length() <= MAX_SIZE_MB
        }
    }
} 