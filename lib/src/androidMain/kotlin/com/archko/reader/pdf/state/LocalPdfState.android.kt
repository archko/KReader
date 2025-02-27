package com.archko.reader.pdf.state

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import com.archko.reader.pdf.component.Size
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.util.renderPage
import java.io.File
import java.net.URL

@Stable
public actual class LocalPdfState(private val pfd: ParcelFileDescriptor) : PdfState {
    private val renderer = PdfRenderer(pfd)

    public actual override var pageCount: Int = renderer.pageCount
    override var pageSizes: List<Size> = listOf()
        get() = field
        set(value) {
            field = value
        }

    override var outlineItems: List<Item>? = listOf()
        get() = field
        set(value) {
            field = value
        }

    public fun prepareSizes(): List<Size> {
        val list = mutableListOf<Size>()
        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            val size = Size(page.width, page.height, page.index)
            list.add(size)
            page.close()
        }
        return list
    }

    public actual constructor(file: File) : this(
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    ) {
        pageCount = renderer.pageCount
        prepareSizes()
    }

    public actual override fun renderPage(index: Int, viewWidth: Int, viewHeight: Int): Painter =
        renderer.renderPage(index, viewWidth, viewHeight)

    override fun close() {
        renderer.close()
    }

    public companion object {
        /**
         * [Saver] implementation for [PdfState].
         */
        public val Saver: Saver<LocalPdfState, *> = listSaver(
            save = {
                listOf(it.pfd)
            },
            restore = {
                LocalPdfState(it[0])
            }
        )
    }
}

/**
 * Remembers a [LocalPdfState] for the given [pfd].
 *
 * @param pfd
 * @return [LocalPdfState]
 */
@Composable
public fun rememberLocalPdfState(pfd: ParcelFileDescriptor): LocalPdfState {
    val state = rememberSaveable(pfd, saver = LocalPdfState.Saver) {
        LocalPdfState(pfd)
    }

    DisposableEffect(state) {
        onDispose {
            state.close()
        }
    }

    return state
}

/**
 * Remembers a [LocalPdfState] for the given [uri].
 *
 * @param uri
 * @return [LocalPdfState]
 */
@Composable
public fun rememberLocalPdfState(uri: Uri): LocalPdfState {
    require(uri.scheme == "content") { "URI is not a content URI" }

    val context = LocalContext.current

    return rememberLocalPdfState(context.contentResolver.openFileDescriptor(uri, "r")!!)
}

/**
 * Remembers a [LocalPdfState] for the given [url].
 *
 * @param url
 * @return [LocalPdfState]
 */
@Composable
public actual fun rememberLocalPdfState(url: URL): LocalPdfState {
    require(url.file.isNotEmpty()) { "URL does not have a file" }
    require(url.file.endsWith(".pdf")) { "URL does not point to a PDF" }

    return rememberLocalPdfState(Uri.parse(url.toString()))
}

/**
 * Remembers a [LocalPdfState] for the given [file].
 *
 * @param file
 * @return [LocalPdfState]
 */
@Composable
public actual fun rememberLocalPdfState(file: File): LocalPdfState {
    require(file.exists()) { "File does not exist" }
    require(file.isFile) { "File is not a file" }

    return rememberLocalPdfState(
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    )
}