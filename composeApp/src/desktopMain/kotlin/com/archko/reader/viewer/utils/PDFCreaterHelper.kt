package com.archko.reader.viewer.utils

import com.archko.reader.image.HeifLoader
import com.archko.reader.pdf.cache.FileUtils
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.Rect
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * @author: archko 2025/10/21 :1:03 PM
 */
object PDFCreaterHelper {

    /**
     * A4: 210x297
     * A3：297×420
     * A2：420×594
     * A1：594×841
     * A0：841×1189 mm
     */
    const val OPTS: String =
        "compress-images;compress;incremental;linearize;pretty;compress-fonts;garbage"
    private const val PAPER_WIDTH = 1080f
    private const val PAPER_HEIGHT = 1800f
    private const val PAPER_PADDING = 40f
    private const val PAPER_FONT_SIZE = 17f

    /**
     * 如果是原图的高宽,不经过缩放,pdf的页面高宽设置与图片大小一致,得到的pdf会很大.
     * 图片是否超过指定值,都应该做一次压缩
     */
    fun createPdfFromImages(pdfPath: String?, imagePaths: List<String>): Boolean {
        //Log.d("TAG", String.format("imagePaths:%s", imagePaths))
        var mDocument: PDFDocument? = null
        try {
            mDocument = PDFDocument.openDocument(pdfPath) as PDFDocument
        } catch (e: Exception) {
            print("could not open:$pdfPath")
        }
        if (mDocument == null) {
            mDocument = PDFDocument()
        }

        val resultPaths = processLargeImage(imagePaths)

        //空白页面必须是-1,否则会崩溃,但插入-1的位置的页面会成为最后一个,所以追加的时候就全部用-1就行了.
        var index = -1
        for (path in resultPaths) {
            val page = addPage(path, mDocument, index++)

            mDocument.insertPage(-1, page)
        }
        mDocument.save(pdfPath, OPTS)
        print(String.format("save,%s,%s", mDocument.toString(), mDocument.countPages()))
        val dir = getCacheDir("cache")
        if (dir.isDirectory) {
            dir.deleteRecursively()
        }
        return mDocument.countPages() > 0
    }

    /**
     * used for k2pdf
     */
    fun createPdfFromFormatedImages(
        pdfPath: String?, imagePaths: List<String>
    ): Boolean {
        print(String.format("imagePaths:%s", imagePaths))
        var mDocument: PDFDocument? = null
        try {
            mDocument = PDFDocument.openDocument(pdfPath) as PDFDocument
        } catch (e: Exception) {
            print("could not open:$pdfPath")
        }
        if (mDocument == null) {
            mDocument = PDFDocument()
        }

        var index = 0
        for (path in imagePaths) {
            val page = addPage(path, mDocument, index++)

            mDocument.insertPage(-1, page)
            index++
        }
        mDocument.save(pdfPath, OPTS)
        return mDocument.countPages() > 0
    }

    private fun addPage(
        path: String,
        mDocument: PDFDocument,
        index: Int
    ): PDFObject? {
        val image = Image(path)
        val resources = mDocument.newDictionary()
        val xobj = mDocument.newDictionary()
        val obj = mDocument.addImage(image)
        xobj.put("I", obj)
        resources.put("XObject", xobj)

        val w = image.width
        val h = image.height
        val mediabox = Rect(0f, 0f, w.toFloat(), h.toFloat())
        val contents = "q $w 0 0 $h 0 0 cm /I Do Q\n"
        val page = mDocument.addPage(mediabox, 0, resources, contents)
        print(String.format("index:%s,page,%s,w:%s,h:%s", index, contents, w, h))
        return page
    }

    /**
     * 获取用户主目录
     */
    fun getCacheDir(name: String): File {
        return FileUtils.getCacheDirectory(name)
    }

    /**
     * 将大图片切割成小图片,以长图片切割,不处理宽图片
     */
    private fun processLargeImage(imagePaths: List<String>): List<String> {
        val maxHeight = 6000

        val result = arrayListOf<String>()
        for (path in imagePaths) {
            try {
                val image = Image(path)
                val width = image.width
                val height = image.height

                if (height > maxHeight) {
                    //split image,maxheight=PAPER_HEIGHT
                    splitImages(result, path, width, height)
                } else {
                    if (isSupportedImageForCreater(path)) {
                        result.add(path)
                    } else {
                        convertImageToJpeg(result, path)
                    }
                }
                image.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    /**
     * 检查是否为支持的图片格式
     */
    private fun isSupportedImageForCreater(path: String): Boolean {
        val extension = File(path).extension.lowercase()
        return extension in listOf("jpg", "jpeg", "png")
    }

    private fun isHeifFormat(path: String): Boolean {
        val extension = File(path).extension.lowercase()
        return extension == "heic" || extension == "heif"
    }

    /**
     * 默认不支持bmp,svg,heic,webp这些直接转换,所以先解析为png
     * HEIF格式使用HeifLoader解码，其他格式使用MuPDF
     */
    private fun convertImageToJpeg(result: java.util.ArrayList<String>, path: String) {
        val cacheDir = getCacheDir("cache")
        val file = File(cacheDir, System.currentTimeMillis().toString() + ".png")

        if (isHeifFormat(path)) {
            // 使用HeifLoader解码HEIF图片
            val heifLoader = HeifLoader()
            try {
                heifLoader.openHeif(path)
                val heifInfo = heifLoader.heifInfo
                if (heifInfo != null) {
                    // 解码整个图片
                    val bitmap = heifLoader.decodeRegionToBitmap(
                        0,
                        0,
                        heifInfo.width,
                        heifInfo.height,
                        1.0f
                    )

                    if (bitmap != null) {
                        // 保存为PNG
                        ImageIO.write(bitmap, "png", file)
                        print("convertImageToJpeg (HEIF) path:${file.absolutePath}")
                        result.add(file.absolutePath)
                    } else {
                        print("Failed to decode HEIF bitmap")
                    }
                } else {
                    print("Failed to get HEIF info")
                }
            } catch (e: Exception) {
                print("Failed to open HEIF file:$e")
            } finally {
                heifLoader.close()
            }
        } else {
            try {
                val image = Image(path)
                val pixmap = image.toPixmap()

                val cacheDir = getCacheDir("cache")

                val file = File(cacheDir, System.currentTimeMillis().toString() + ".jpg")
                pixmap.saveAsPNG(file.absolutePath)

                pixmap.destroy()
                image.destroy()

                print("convertImageToJpeg path:${file.absolutePath}")
                result.add(file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果转换失败，尝试直接添加原文件
                result.add(path)
            }
        }
    }

    private fun splitImages(
        result: ArrayList<String>,
        path: String,
        width: Int,
        height: Int,
    ) {
        try {
            // 使用 Java BufferedImage 读取图片
            val bufferedImage = ImageIO.read(File(path))

            var top = 0
            var bottom = PAPER_HEIGHT.toInt()

            while (bottom < height) {
                splitImage(bufferedImage, top, bottom, width, result)
                top = bottom
                bottom += PAPER_HEIGHT.toInt()
            }

            if (top < height) {
                splitImage(bufferedImage, top, height, width, result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun splitImage(
        bufferedImage: BufferedImage,
        top: Int,
        bottom: Int,
        width: Int,
        result: ArrayList<String>
    ) {
        try {
            // 使用 BufferedImage 的 getSubimage 方法裁剪
            val croppedImage = bufferedImage.getSubimage(0, top, width, bottom - top)

            val cacheDir = getCacheDir("cache")

            val file = File(cacheDir, System.currentTimeMillis().toString() + ".jpg")
            ImageIO.write(croppedImage, "jpg", file)

            val height = bottom - top
            print("new file:height:$height, path:${file.absolutePath}")
            result.add(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var canExtract: Boolean = true

    fun extractToImages(
        screenWidth: Int, dir: String, pdfPath: String,
        start: Int,
        end: Int
    ): Int {
        try {
            print("extractToImages:$screenWidth, start:$start, end:$end dir:$dir, dst:$pdfPath")
            val mupdfDocument = Document.openDocument(pdfPath)
            val count: Int = mupdfDocument.countPages()
            var startPage = start
            if (startPage < 0) {
                startPage = 0
            } else if (startPage >= count) {
                startPage = 0
            }
            var endPage = end
            if (end > count) {
                endPage = count
            } else if (endPage < 0) {
                endPage = count
            }
            for (i in startPage until endPage) {
                if (!canExtract) {
                    print("extractToImages.stop")
                    return i
                }
                val page = mupdfDocument.loadPage(i)
                if (null != page) {
                    val pageWidth = page.bounds.x1 - page.bounds.x0
                    val pageHeight = page.bounds.y1 - page.bounds.y0

                    var exportWidth = screenWidth
                    if (exportWidth == -1) {
                        exportWidth = pageWidth.toInt()
                    }
                    val scale = exportWidth / pageWidth
                    val width = exportWidth
                    val height = pageHeight * scale
                    val ctm = Matrix(scale)
                    val pixmap = page.toPixmap(ctm, ColorSpace.DeviceRGB, false)
                    page.destroy()
                    pixmap.saveAsJPEG("$dir/${i + 1}.jpg", 90)
                    pixmap.destroy()
                }
                print("extractToImages:page:${i + 1}.jpg")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -2
        }
        return 0
    }


    fun extractToHtml(
        start: Int, end: Int, path: String, pdfPath: String
    ): Boolean {
        try {
            val mupdfDocument = Document.openDocument(pdfPath)
            val count: Int = mupdfDocument.countPages()
            var startPage = start
            if (startPage < 0) {
                startPage = 0
            } else if (startPage >= count) {
                startPage = 0
            }
            var endPage = end
            if (end > count) {
                endPage = count
            } else if (endPage < 0) {
                endPage = count
            }
            val stringBuilder = StringBuilder()
            for (i in startPage until endPage) {
                val page = mupdfDocument.loadPage(i)
                if (null != page) {
                    val content =
                        String(page.textAsHtml2("preserve-whitespace,inhibit-spaces,preserve-images"))
                    stringBuilder.append(content)
                    print(
                        String.format(
                            "============%s-content:%s==========",
                            i,
                            content,
                        )
                    )
                    page.destroy()
                }
            }
            // 将所有内容写入文件
            File(path).writeText(stringBuilder.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    // =================== encrypt/decrypt ===================

    /**
     * 保存时添加密码保护
     */
    fun encryptPDF(
        inputFile: String?, outputFile: String?,
        userPassword: String?, ownerPassword: String?
    ): Boolean {
        try {
            val doc: Document? = Document.openDocument(inputFile)

            if (doc !is PDFDocument) {
                System.err.println("输入文件不是PDF格式")
                return false
            }

            if (doc.needsPassword()) {
                println("原文档需要密码验证")
                // 这里需要提供原文档的密码
                // pdfDoc.authenticatePassword("original_password");
            }

            val options = StringBuilder()
            // 设置加密算法 (AES 256位是最安全的)
            options.append("encrypt=aes-256")

            // 设置用户密码（打开文档时需要）
            if (userPassword != null && !userPassword.isEmpty()) {
                options.append(",user-password=").append(userPassword)
            }

            // 设置所有者密码（拥有完整权限）
            if (ownerPassword != null && !ownerPassword.isEmpty()) {
                options.append(",owner-password=").append(ownerPassword)
            }

            // 设置权限（-1表示所有权限）
            options.append(",permissions=-1")

            println("保存选项: $options")

            // 保存加密后的PDF
            doc.save(outputFile, options.toString())
            println("PDF加密成功，保存到: $outputFile")
            return true
        } catch (e: java.lang.Exception) {
            System.err.println("加密PDF时出错: " + e.message)
        }
        return false
    }

    /**
     * 移除PDF密码保护（解密）
     */
    fun decryptPDF(inputFile: String?, outputFile: String?, password: String?): Boolean {
        try {
            val doc: Document? = Document.openDocument(inputFile)
            if (doc !is PDFDocument) {
                System.err.println("输入文件不是PDF格式")
                return false
            }

            // 验证密码（如果需要）
            if (doc.needsPassword()) {
                if (!doc.authenticatePassword(password)) {
                    System.err.println("密码验证失败，无法解密")
                    return false
                }
                println("密码验证成功")
            }

            // 构建保存选项字符串 - 移除加密
            val options = "encrypt=no,decrypt=yes"

            // 保存解密后的PDF
            doc.save(outputFile, options)
            println("PDF解密成功，保存到: $outputFile")
            return true
        } catch (e: java.lang.Exception) {
            System.err.println("解密PDF时出错: " + e.message)
        }
        return false
    }

    /**
     * 修改PDF密码
     */
    fun changePDFPassword(
        inputFile: String?, outputFile: String?,
        oldPassword: String?, newUserPassword: String?, newOwnerPassword: String?
    ): Boolean {
        try {
            val doc: Document? = Document.openDocument(inputFile)
            if (doc !is PDFDocument) {
                System.err.println("输入文件不是PDF格式")
                return false
            }

            // 验证原密码（如果需要）
            if (doc.needsPassword()) {
                if (!doc.authenticatePassword(oldPassword)) {
                    System.err.println("原密码验证失败")
                    return false
                }
                println("原密码验证成功")
            }

            // 构建保存选项字符串
            val options = StringBuilder()

            // 设置新的加密算法
            options.append("encrypt=aes-256")

            // 设置新密码
            if (newUserPassword != null && !newUserPassword.isEmpty()) {
                options.append(",user-password=").append(newUserPassword)
            }
            if (newOwnerPassword != null && !newOwnerPassword.isEmpty()) {
                options.append(",owner-password=").append(newOwnerPassword)
            }

            // 设置权限
            options.append(",permissions=-1")

            // 保存修改密码后的PDF
            doc.save(outputFile, options.toString())
            println("PDF密码修改成功，保存到: $outputFile")
            return true
        } catch (e: java.lang.Exception) {
            System.err.println("修改PDF密码时出错: " + e.message)
        }
        return false
    }

    // =================== split PDF ===================

    /**
     * 解析拆分范围字符串
     * 例如: "1-10,11-20" -> [(1,10), (11,20)]
     */
    private fun parseRanges(rangeInput: String, maxPage: Int): List<Pair<Int, Int>>? {
        try {
            val ranges = mutableListOf<Pair<Int, Int>>()
            val parts = rangeInput.split(",").map { it.trim() }

            for (part in parts) {
                if (part.isEmpty()) continue

                val rangeParts = part.split("-").map { it.trim() }
                when (rangeParts.size) {
                    1 -> {
                        // 单页，如 "5"
                        val page = rangeParts[0].toInt()
                        if (page < 1 || page > maxPage) return null
                        ranges.add(Pair(page, page))
                    }

                    2 -> {
                        // 范围，如 "1-10"
                        val start = rangeParts[0].toInt()
                        val end = rangeParts[1].toInt()
                        if (start < 1 || end > maxPage || start > end) return null
                        ranges.add(Pair(start, end))
                    }

                    else -> return null
                }
            }

            return ranges
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 拆分PDF文件 - 使用临时文件和删除页面的方式
     * @param inputFile 输入PDF文件路径
     * @param outputBaseName 输出文件基础名称
     * @param rangeInput 拆分范围，如 "1-10,11-20"
     * @return 成功拆分的文件数量，失败返回-1
     */
    fun splitPDF(
        outDir: String,
        inputFile: String,
        outputBaseName: String,
        rangeInput: String
    ): Int {
        try {
            val checkDoc = Document.openDocument(inputFile)
            if (checkDoc !is PDFDocument) {
                System.err.println("输入文件不是PDF格式")
                return -1
            }
            val totalPages = checkDoc.countPages()
            checkDoc.destroy()

            println("源PDF总页数: $totalPages")

            val ranges = parseRanges(rangeInput, totalPages)
            if (ranges == null || ranges.isEmpty()) {
                System.err.println("无效的页面范围")
                return -1
            }

            var successCount = 0

            for ((index, range) in ranges.withIndex()) {
                val (startPage, endPage) = range
                println("处理范围: $startPage-$endPage")

                try {
                    // 创建临时文件
                    val tempFile = File.createTempFile("pdf_split_", ".pdf")

                    // 先保存一份完整的副本到临时文件
                    val sourceDoc = Document.openDocument(inputFile) as PDFDocument
                    sourceDoc.save(tempFile.absolutePath, "compress")
                    sourceDoc.destroy()

                    // 重新打开临时文件进行编辑
                    val editDoc = Document.openDocument(tempFile.absolutePath) as PDFDocument

                    // 构建要保留的页面列表（0-based index）
                    val pagesToKeep = (startPage - 1 until endPage).toSet()

                    // 从后往前删除不需要的页面
                    for (pageIndex in totalPages - 1 downTo 0) {
                        if (pageIndex !in pagesToKeep) {
                            editDoc.deletePage(pageIndex)
                        }
                    }

                    println("保留页面: $startPage-$endPage, 当前文档页数: ${editDoc.countPages()}")

                    // 保存最终文件
                    val outputFileName = if (ranges.size == 1) {
                        "$outputBaseName.pdf"
                    } else {
                        "${outputBaseName}_${index + 1}_p${startPage}-${endPage}.pdf"
                    }
                    val outputFile = File(outDir, outputFileName)

                    // 使用 clean 选项，这会清理未使用的对象
                    editDoc.save(outputFile.absolutePath, "clean,compress,garbage")
                    editDoc.destroy()

                    // 删除临时文件
                    tempFile.delete()

                    println("已保存: ${outputFile.absolutePath}")
                    successCount++

                } catch (e: Exception) {
                    System.err.println("处理范围 $startPage-$endPage 时出错: ${e.message}")
                    e.printStackTrace()
                }
            }

            return successCount
        } catch (e: Exception) {
            System.err.println("拆分PDF时出错: ${e.message}")
            e.printStackTrace()
            return -1
        }
    }

    /**
     * 合并多个PDF文件
     * @param outputFile 输出PDF文件的完整路径
     * @param pdfFiles 要合并的PDF文件路径列表（按顺序）
     * @return 成功合并的文件数量，失败返回-1
     */
    fun mergePDF(
        outputFile: String,
        pdfFiles: List<String>
    ): Int {
        try {
            if (pdfFiles.isEmpty()) {
                System.err.println("没有要合并的PDF文件")
                return -1
            }

            val firstPdfPath = pdfFiles[0]
            val mergedDoc = Document.openDocument(firstPdfPath) as PDFDocument

            var successCount = 0
            println("以文件 $firstPdfPath 为基础（${mergedDoc.countPages()}页），开始合并后续文件...")

            for ((index, pdfPath) in pdfFiles.withIndex().filter { it.index >= 1 }) {
                try {
                    val sourceDoc = Document.openDocument(pdfPath)
                    if (sourceDoc !is PDFDocument) {
                        System.err.println("文件 $pdfPath 不是有效的PDF格式")
                        sourceDoc.destroy()
                        continue
                    }

                    val pageCount = sourceDoc.countPages()
                    println("正在合并文件 ${index + 1}/${pdfFiles.size}: $pdfPath (${pageCount}页)")

                    // 将源文档的所有页面 graft 到合并文档末尾
                    for (pageIndex in 0 until pageCount) {
                        mergedDoc.graftPage(-1, sourceDoc, pageIndex)
                    }

                    sourceDoc.destroy()
                    successCount++
                } catch (e: Exception) {
                    System.err.println("处理文件 $pdfPath 时出错: ${e.message}")
                    e.printStackTrace()
                }
            }

            mergedDoc.save(outputFile, "incremental")
            mergedDoc.destroy()

            println("PDF合并成功: $outputFile (合并了 ${successCount + 1} 个文件)")
            return successCount
        } catch (e: Exception) {
            System.err.println("合并PDF时出错: ${e.message}")
            e.printStackTrace()
            return -1
        }
    }
}