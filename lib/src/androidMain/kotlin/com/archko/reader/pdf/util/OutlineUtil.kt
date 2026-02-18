package com.archko.reader.pdf.util

import com.archko.reader.image.DjvuOutline
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
    outlines?.run { downOutline(outlines, links, 0) }
    return links
}

internal fun Document.pageNumberFromLocation(node: Outline?): Int {
    return pageNumberFromLocation(resolveLink(node))
}

internal fun Document.downOutline(outlines: Array<Outline>, links: MutableList<OutlineLink>, level: Int = 0) {
    for (outline in outlines) {
        if (outline.title != null) {
            val page = pageNumberFromLocation(outline)
            val link = OutlineLink(outline.title, outline.uri, level)
            link.targetPage = page
            if (outline.down != null) {
                val child: Array<Outline> = outline.down
                downOutline(child, links, level + 1)
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
                    val item = Item(outline.title, page, outline.level)
                    items.add(item)
                } else {
                    try {
                        val page = Integer.parseInt(outline.targetUrl)
                        val item = Item(outline.title, page, outline.level)
                        items.add(item)
                    } catch (_: NumberFormatException) {
                    }
                }
            } else if (outline.targetPage > 0) {
                val item = Item(outline.title, outline.targetPage, outline.level)
                items.add(item)
            }

            //if (outline.down != null) {
            //    processOutline(outline.down);
            //}
        }
    }
    return items
}

internal fun convertDjvuOutlinesToItems(djvuOutlines: List<DjvuOutline>?): List<Item> {
    val items = mutableListOf<Item>()
    if (djvuOutlines.isNullOrEmpty()) {
        return items
    }

    for (outline in djvuOutlines) {
        flattenDjvuOutline(outline, items, 0)
    }

    return items
}

private fun flattenDjvuOutline(outline: DjvuOutline, items: MutableList<Item>, level: Int = 0) {
    if (outline.page >= 0) {
        val item = Item(outline.title, outline.page, level)
        items.add(item)
    }

    for (child in outline.children) {
        flattenDjvuOutline(child, items, level + 1)
    }
}
