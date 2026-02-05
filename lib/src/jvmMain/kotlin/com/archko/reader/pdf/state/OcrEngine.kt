package com.archko.reader.pdf.state

import androidx.compose.ui.graphics.ImageBitmap

/**
 * OCR引擎接口，用于识别图片中的文本
 */
public actual class OcrEngine {

    /**
     * 识别图片中的文本
     * @param bitmap 要识别的图片
     * @return 识别到的文本
     */
    public actual fun recognizeText(bitmap: ImageBitmap): String {
        return ""
    }
}