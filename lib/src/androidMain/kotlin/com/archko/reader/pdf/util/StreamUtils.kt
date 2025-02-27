package com.archko.reader.pdf.util

import java.io.*

/**
 * @author: archko 2025/2/26 :14:05
 */
public object StreamUtils {
    private val TAG: Any = "StreamUtils"

    public fun appendStringToFile(text: String?, filePath: String) {
        val file = File(filePath)
        try {
            val fileWriter = FileWriter(file)
            fileWriter.append(text)
            fileWriter.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 将字符串保存到指定的文件中
     */
    public fun saveStringToFile(text: String, filePath: String) {
        val file = File(filePath)
        saveStringToFile(text, file)
    }

    /**
     * 将字符串保存到指定的文件中
     *
     * @param text
     * @param file
     * @return
     * @throws IOException
     */
    public fun saveStringToFile(text: String, file: File): Boolean {
        var `in`: ByteArrayInputStream? = null
        try {
            `in` = ByteArrayInputStream(text.toByteArray(charset("UTF-8")))
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            return false
        }
        return saveStreamToFile(`in`, file)
    }

    /**
     * 将输入流保存到指定的文件中
     */
    @Synchronized
    public fun saveStreamToFile(`in`: InputStream?, filePath: String): Boolean {
        val file = File(filePath)
        return saveStreamToFile(`in`, file)
    }

    /**
     * 将输入流保存到指定的文件中
     */
    @Synchronized
    public fun saveStreamToFile(`in`: InputStream?, file: File): Boolean {
        var fos: FileOutputStream? = null
        try {
            if (file.exists()) {
                file.delete()
            } else {
                val parent = file.getParentFile()
                if (!parent!!.exists()) {
                    parent.mkdirs()
                }
                file.createNewFile()
            }

            fos = FileOutputStream(file)
            copyStream(`in`, fos)
            return true
        } catch (e: Exception) {
        } finally {
            closeStream(fos)
        }
        return false
    }

    //-------------------------------------------------------
    /**
     * 从输入流里面读出字节数组
     */
    @Throws(IOException::class)
    public fun readByteFromStream(`in`: InputStream): ByteArray? {
        var bos: ByteArrayOutputStream? = null
        try {
            bos = ByteArrayOutputStream()

            val buf = ByteArray(1024)
            var len = -1
            while ((`in`.read(buf).also { len = it }) != -1) {
                bos.write(buf, 0, len)
            }
            return bos.toByteArray()
        } finally {
            closeStream(bos)
            closeStream(`in`)
        }
    }

    public fun readString(reader: Reader?): String {
        var bufferedReader: BufferedReader? = null
        val sb = StringBuilder()
        try {
            bufferedReader = BufferedReader(reader)
            var temp: String? = null
            while ((bufferedReader.readLine().also { temp = it }) != null) {
                sb.append(temp)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            closeStream(bufferedReader)
        }
        return sb.toString()
    }

    /**
     * 从文件中读取字符串
     */
    public fun readStringFromFile(filePath: String): String {
        return readStringFromFile(File(filePath))
    }

    /**
     * 从文件中读取字符串
     */
    public fun readStringFromFile(file: File?): String {
        if (file != null && file.exists() && file.isFile() && file.length() > 0) {
            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(file)
                return readStringFromInputStream(fis)
            } catch (e: Exception) {
            } finally {
                closeStream(fis)
            }
        }
        return ""
    }

    /**
     * 从输入流中读取字符串（以 UTF-8 编码）
     * 本方法不会关闭InputStream
     */
    public fun readStringFromInputStream(`in`: InputStream?): String {
        if (`in` == null) {
            return ""
        }
        var bos: ByteArrayOutputStream? = null
        try {
            bos = ByteArrayOutputStream()
            val buffer = ByteArray(512)
            var len: Int
            while ((`in`.read(buffer).also { len = it }) != -1) {
                bos.write(buffer, 0, len)
            }
            return String(bos.toByteArray(), charset("UTF-8"))
        } catch (e: Exception) {
        } finally {
            closeStream(bos)
        }
        return ""
    }

    /**
     * 关闭流
     *
     * @param closeable 可关闭的对象
     */
    public fun closeStream(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * 读取输入流，并将其数据输出到输出流中。
     */
    @Throws(IOException::class)
    public fun copyStream(`in`: InputStream?, out: OutputStream?) {
        val bin = BufferedInputStream(`in`)
        val bout = BufferedOutputStream(out)

        val buffer = ByteArray(4096)

        while (true) {
            val doneLength = bin.read(buffer)
            if (doneLength == -1) {
                break
            }
            bout.write(buffer, 0, doneLength)
        }
        bout.flush()
    }
}
