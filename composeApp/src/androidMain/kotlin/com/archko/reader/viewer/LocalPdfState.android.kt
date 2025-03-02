package com.archko.reader.viewer

import android.content.ContentResolver
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.state.LocalPdfState
import com.archko.reader.pdf.util.IntentFile
import com.mohamedrejeb.calf.io.KmpFile
import java.io.File

actual fun LocalPdfState(file: KmpFile): LocalPdfState {
    val path: String = (if (ContentResolver.SCHEME_CONTENT == file.uri.scheme) {
        IntentFile.getFilePathByUri(PdfApp.app!!, file.uri)
    } else {
        file.uri
    }).toString()
    return LocalPdfState(File(path))
}