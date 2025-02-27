package com.archko.reader.viewer

import com.archko.reader.pdf.state.LocalPdfState
import com.mohamedrejeb.calf.io.KmpFile

expect fun LocalPdfState(file: KmpFile): LocalPdfState