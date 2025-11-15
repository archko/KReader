package com.archko.reader.pdf.util

import java.awt.Desktop
import java.net.URI

/**
 * @author: archko 2025/8/2 :16:48
 */
public actual object HyperLinkUtils {

    public actual fun openSystemBrowser(url: String?) {
        if (url.isNullOrBlank()) {
            println("HyperLinkUtils: URL is null or blank")
            return
        }

        try {
            // 检查 Desktop 是否支持浏览功能
            if (!Desktop.isDesktopSupported()) {
                println("HyperLinkUtils: Desktop is not supported on this platform")
                return
            }

            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                println("HyperLinkUtils: BROWSE action is not supported")
                return
            }

            // 确保 URL 格式正确
            val uri = if (url.startsWith("http://") || url.startsWith("https://")) {
                URI(url)
            } else {
                // 如果没有协议前缀，默认添加 https://
                URI("https://$url")
            }

            desktop.browse(uri)
            println("HyperLinkUtils: Opening URL in browser: $uri")
        } catch (e: Exception) {
            println("HyperLinkUtils: Failed to open URL: $url, error: ${e.message}")
            e.printStackTrace()
        }
    }
}