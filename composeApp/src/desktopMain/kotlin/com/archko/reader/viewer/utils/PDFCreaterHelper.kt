package com.archko.reader.viewer.utils

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.Rect
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.StringBuilder

/**
 * @author: archko 2025/10/21 :1:03 PM
 */
public object PDFCreaterHelper {

    // =================== encrypt/decrypt ===================

    /**
     * 保存时添加密码保护
     */
    public fun encryptPDF(
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
    public fun decryptPDF(inputFile: String?, outputFile: String?, password: String?): Boolean  {
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
    public fun changePDFPassword(
        inputFile: String?, outputFile: String?,
        oldPassword: String?, newUserPassword: String?, newOwnerPassword: String?
    ): Boolean  {
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
}