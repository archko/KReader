package com.archko.reader.pdf.util

import android.os.ParcelFileDescriptor
import com.artifex.mupdf.fitz.SeekableInputStream
import com.artifex.mupdf.fitz.SeekableStream
import java.io.FileInputStream
import java.io.IOException

public class PFDSeekableInputStream(pfd: ParcelFileDescriptor) : SeekableInputStream {

    private val inputStream: FileInputStream = FileInputStream(pfd.fileDescriptor)
    private val channel = inputStream.channel

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return inputStream.read(b)
    }

    @Throws(IOException::class)
    override fun seek(offset: Long, whence: Int): Long {
        val newPos = when (whence) {
            SeekableStream.SEEK_SET -> offset
            SeekableStream.SEEK_CUR -> channel.position() + offset
            SeekableStream.SEEK_END -> channel.size() + offset
            else -> offset
        }
        channel.position(newPos)
        return newPos
    }

    @Throws(IOException::class)
    override fun position(): Long {
        return channel.position()
    }

    public fun close() {
        try {
            inputStream.close()
        } catch (_: Exception) {
        }
    }
}
