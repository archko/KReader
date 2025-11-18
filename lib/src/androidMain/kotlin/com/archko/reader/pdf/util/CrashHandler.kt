package com.archko.reader.pdf.util

import android.os.Build
import android.os.Environment
import android.text.format.DateFormat
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

/**
 * @author: archko 2025/10/8 :06:36
 */
public class CrashHandler : Thread.UncaughtExceptionHandler {

    private val defaultUEH: Thread.UncaughtExceptionHandler =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val result: Writer = StringWriter()
        val printWriter = PrintWriter(result)

        // Inject some info about android version and the device, since google can't provide them in the developer console
        val trace = ex.stackTrace
        val trace2 = arrayOfNulls<StackTraceElement>(trace.size + 3)
        System.arraycopy(trace, 0, trace2, 0, trace.size)
        trace2[trace.size + 0] = StackTraceElement("Android", "MODEL", Build.MODEL, -1)
        trace2[trace.size + 1] = StackTraceElement("Android", "VERSION", Build.VERSION.RELEASE, -1)
        trace2[trace.size + 2] = StackTraceElement("Android", "FINGERPRINT", Build.FINGERPRINT, -1)
        ex.stackTrace = trace2
        ex.printStackTrace(printWriter)
        val stacktrace = result.toString()
        printWriter.close()
        Log.e(TAG, stacktrace)

        // Save the log on SD card if available
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val dir = FileUtils.getStorageDir("amupdf")
            var sdcardPath = Environment.getExternalStorageDirectory().path
            if (dir.exists()) {
                sdcardPath = dir.absolutePath
            }
            writeLog(stacktrace, "$sdcardPath/kreader_crash")
            writeLogcat("$sdcardPath/kreader_logcat")
        }
        defaultUEH.uncaughtException(thread, ex)
    }

    private fun writeLog(log: String, name: String) {
        val timestamp = DateFormat.format("yyyyMMdd_kkmmss", System.currentTimeMillis())
        val filename = name + "_" + timestamp + ".log"
        val stream: FileOutputStream
        stream = try {
            FileOutputStream(filename)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return
        }
        val output = OutputStreamWriter(stream)
        val bw = BufferedWriter(output)
        try {
            bw.write(log)
            bw.newLine()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                bw.close()
                output.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun writeLogcat(name: String) {
        val timestamp = DateFormat.format("yyyyMMdd_kkmmss", System.currentTimeMillis())
        val filename = name + "_" + timestamp + ".log"
        try {
            doWriteLogcat(filename)
        } catch (_: Exception) {
        }
    }

    public companion object {
        private const val TAG = "CrashHandler"

        public fun doWriteLogcat(filename: String?) {
            val args = arrayOf("logcat", "-v", "time", "-d")
            val process = Runtime.getRuntime().exec(args)
            val input = InputStreamReader(process.inputStream)
            val fileStream: FileOutputStream = try {
                FileOutputStream(filename)
            } catch (_: FileNotFoundException) {
                return
            }
            val output = OutputStreamWriter(fileStream)
            val br = BufferedReader(input)
            val bw = BufferedWriter(output)
            try {
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    bw.write(line)
                    bw.newLine()
                }
            } catch (_: Exception) {
            } finally {
                bw.close()
                output.close()
                br.close()
                input.close()
            }
        }
    }
}