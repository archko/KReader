package com.archko.reader.viewer

import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.state.LocalPdfState
import com.archko.reader.pdf.util.IntentFile
import com.mohamedrejeb.calf.io.KmpFile
import java.io.File

actual fun LocalPdfState(file: KmpFile): LocalPdfState {
    val path = IntentFile.getFilePathByUri(PdfApp.app!!, file.uri)
    return LocalPdfState(File(path))
}