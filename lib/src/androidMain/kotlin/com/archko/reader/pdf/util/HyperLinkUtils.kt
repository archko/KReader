package com.archko.reader.pdf.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import androidx.compose.ui.geometry.Rect
import com.archko.reader.pdf.entity.Hyperlink
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Location
import com.artifex.mupdf.fitz.Page

/**
 * @author: archko 2025/8/2 :16:48
 */
public class HyperLinkUtils {
    public companion object {

        public const val LINKTYPE_PAGE: Int = 0
        public const val LINKTYPE_URL: Int = 1

        //documentview
        public fun mapPointToPage(page: Page, atX: Float, atY: Float): Hyperlink? {
            if (null == page.links) {
                return null
            }
            /*for (hyper in page.links) {
                if (null != hyper.bounds && hyper.bounds!!.contains(atX.toInt(), atY.toInt())) {
                    return hyper
                }
            }*/
            return null
        }

        //controller
        public fun mapPointToPage(
            doc: Document?,
            pdfPage: Page,
            atX: Float,
            atY: Float
        ): Hyperlink? {
            if (null == doc) {
                return null
            }
            val links: Array<Link>? = pdfPage.links
            if (links.isNullOrEmpty()) {
                return null
            }
            for (link in links) {
                if (link.bounds.contains(atX, atY)) {
                    val hyper = Hyperlink()
                    val loc: Location = doc.resolveLink(link)
                    val page: Int = doc.pageNumberFromLocation(loc)
                    hyper.page = page
                    if (page >= 0) {
                        hyper.bbox = Rect(0f, 0f, 0f, 0f)
                        hyper.url = null
                        hyper.linkType = LINKTYPE_PAGE
                    } else {
                        hyper.bbox = null
                        hyper.url = link.uri
                        hyper.linkType = LINKTYPE_URL
                    }
                    return hyper
                }
            }
            return null
        }

        public fun openSystemBrowser(context: Context?, url: String?) {
            if (context != null && !TextUtils.isEmpty(url)) {
                try {
                    val intent = Intent()
                    intent.setAction(Intent.ACTION_VIEW)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val uri = Uri.parse(url)
                    intent.setData(uri)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}