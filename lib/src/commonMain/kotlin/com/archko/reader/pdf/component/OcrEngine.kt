package com.archko.reader.pdf.component

import androidx.compose.ui.graphics.ImageBitmap

/**
 * OCR引擎接口，用于识别图片中的文本
 */
public expect class OcrEngine {
    /**
     * 识别图片中的文本
     * @param bitmap 要识别的图片
     * @return 识别到的文本
     */
    public fun recognizeText(bitmap: ImageBitmap): String
}
