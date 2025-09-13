package com.archko.reader.pdf.util

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * @author: archko 2025/9/13 :09:07
 */
public class Utils {
    public companion object {
        public fun getScreenWidthPixelWithOrientation(context: Context): Int {
            val dm = DisplayMetrics()
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getMetrics(dm)
            val width = dm.widthPixels
            return width
        }

        public fun getScreenHeightPixelWithOrientation(context: Context): Int {
            val dm = DisplayMetrics()
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getMetrics(dm)
            val width = dm.heightPixels
            return width
        }

        public fun getDensityDpi(context: Context): Int {
            val dm = DisplayMetrics()
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getMetrics(dm)
            return dm.densityDpi
        }
    }
}