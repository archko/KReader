package com.archko.reader.pdf.util

import java.io.File

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp",
    "heif", "heic",
    "dng", "arw", "nef", "cr2", "cr3", "raf", "orf",
    "sr2", "srw", "x3f", "pef", "3fr", "rw2", "nrw", "crw"
)

private val DOCUMENT_EXTENSIONS = setOf(
    "pdf", "epub", "mobi", "xps", "fb", "fb2",
    "pptx", "docx", "djvu", "djv", "txt", "md",
    "html", "xhtml", "svg"
)

private val TIFF_EXTENSIONS = setOf("jfif", "tiff", "tif")

/**
 * 文件类型判断工具类
 * @author: archko 2025/1/20
 */
public object FileTypeUtils {

    private const val MAX_SIZE_MB = 120 * 1024 * 1024L

    public fun isImageExtension(ext: String): Boolean {
        return ext.lowercase() in IMAGE_EXTENSIONS
    }

    public fun isDocumentExtension(ext: String): Boolean {
        return ext.lowercase() in DOCUMENT_EXTENSIONS
    }

    public fun isTiffExtension(ext: String): Boolean {
        return ext.lowercase() in TIFF_EXTENSIONS
    }

    public fun isImageMimeType(mimeType: String): Boolean {
        val mt = mimeType.lowercase()
        return (mt.startsWith("image/") && mt != "image/tiff" && mt != "image/jfif" && mt != "image/svg+xml" && mt != "image/vnd.djvu")
                || mt.startsWith("raw/")
    }

    public fun isDocumentMimeType(mimeType: String): Boolean {
        val mt = mimeType.lowercase()
        return when {
            mt in setOf(
                "application/pdf", "application/epub+zip", "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain", "text/html", "text/markdown", "image/svg+xml",
                "application/x-mobipocket-ebook", "application/vnd.ms-xpsdocument",
                "application/x-fictionbook+xml", "image/vnd.djvu",
                "application/vnd.comicbook+zip", "application/x-cbz"
            ) -> true
            mt.startsWith("text/") -> true
            else -> false
        }
    }

    public fun isTiffMimeType(mimeType: String): Boolean {
        val mt = mimeType.lowercase()
        return mt in setOf("image/tiff", "image/jfif")
    }

    /**
     * 判断是否为图片文件
     * Android支持的图片格式：JPEG, PNG, GIF, BMP, WebP, HEIF, HEIC
     */
    public fun isImageFile(path: String): Boolean {
        return isSupportedImageFile(path)
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
     * 检测文件是否为HEIF格式
     */
    public fun isHeifFormat(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension == "heic" || extension == "heif"
    }

    /**
     * 判断是否为文档文件
     */
    public fun isDocumentFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return isDocumentExtension(ext)
    }

    public fun isTiffFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return isTiffExtension(ext)
    }

    public fun isDjvuFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext == "djvu" || ext == "djv"
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

    public fun isReflowable(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("cbz", "epub", "mobi", "pptx", "docx", "xlsx", "html", "xhtml", "txt", "md")
    }

    public fun isSupportedImageForCreater(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "gif")
    }
}

public expect fun isSupportedImageFile(path: String): Boolean