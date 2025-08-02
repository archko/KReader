package com.archko.reader.pdf.util

import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.entity.OutlineLink
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import java.util.regex.Pattern

internal fun Document.loadOutlineItems(): List<Item> {
    val list = loadOutline(this)
    return processOutline(list)
}

internal fun Document.loadOutline(core: Document): List<OutlineLink> {
    val outlines = core.loadOutline()
    val links: MutableList<OutlineLink> = ArrayList()
    outlines?.run { downOutline(outlines, links) }
    return links
}

internal fun Document.pageNumberFromLocation(node: Outline?): Int {
    return pageNumberFromLocation(resolveLink(node))
}

internal fun Document.downOutline(outlines: Array<Outline>, links: MutableList<OutlineLink>) {
    for (outline in outlines) {
        if (outline.title != null) {
            val page = pageNumberFromLocation(outline)
            val link = OutlineLink(outline.title, outline.uri, 0)
            link.targetPage = page
            if (outline.down != null) {
                val child: Array<Outline> = outline.down
                downOutline(child, links)
            }
            links.add(link)
        }
    }
}

private val pattern_str = "(#page=)(\\d+)(&)"
private val pattern: Pattern = Pattern.compile(pattern_str)

internal fun processOutline(outlines: List<OutlineLink>?): MutableList<Item> {
    val items = mutableListOf<Item>()
    if (!outlines.isNullOrEmpty()) {
        for (i in outlines.indices) {
            val outline = outlines.get(i)
            if (null != outline.targetUrl && "" != outline.targetUrl) {
                val matcher = pattern.matcher(outline.targetUrl)
                if (matcher.find()) {
                    val page = Integer.parseInt(
                        matcher.group(0).replace("#page=", "")
                            .replace("&", "")
                    )
                    val item = Item(outline.title, page)
                    items.add(item)
                } else {
                    try {
                        val page = Integer.parseInt(outline.targetUrl)
                        val item = Item(outline.title, page)
                        items.add(item)
                    } catch (_: NumberFormatException) {
                    }
                }
            } else if (outline.targetPage > 0) {
                val item = Item(outline.title, outline.targetPage)
                items.add(item)
            }

            //if (outline.down != null) {
            //    processOutline(outline.down);
            //}
        }
    }
    return items
}
