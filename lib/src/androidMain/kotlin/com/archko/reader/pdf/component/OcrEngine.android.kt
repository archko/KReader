package com.archko.reader.pdf.component

import androidx.compose.ui.graphics.ImageBitmap

/**
 * OCR引擎接口，用于识别图片中的文本
 */
public actual class OcrEngine {

    //private val ppocrv5ncnn = PPOCRv5Ncnn()

    /**
     * 识别图片中的文本
     * @param bitmap 要识别的图片
     * @return 识别到的文本
     */
    public actual fun recognizeText(bitmap: ImageBitmap): String {
        //val result: String? = ppocrv5ncnn.detectAndRecognize(bitmap.)
        //return result
        return ""
    }
}
