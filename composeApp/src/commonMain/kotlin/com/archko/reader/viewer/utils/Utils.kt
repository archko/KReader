package com.archko.reader.viewer.utils

public class Utils {
    companion object {

        fun getFileSize(size: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var index = 0
            var fileSize = size.toDouble()
            while (fileSize >= 1024 && index < units.size - 1) {
                fileSize /= 1024
                index++
            }
            return String.format("%.2f %s", fileSize, units[index])
        }

        /**
         * 格式化时长显示
         */
        fun formatDuration(seconds: Long): String {
            if (seconds < 60) {
                return "${seconds}秒"
            }
            val minutes = seconds / 60
            if (minutes < 60) {
                val remainingSeconds = seconds % 60
                return if (remainingSeconds > 0) {
                    "${minutes}分${remainingSeconds}秒"
                } else {
                    "${minutes}分钟"
                }
            }
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            return if (remainingMinutes > 0) {
                "${hours}小时${remainingMinutes}分钟"
            } else {
                "${hours}小时"
            }
        }
    }
}
