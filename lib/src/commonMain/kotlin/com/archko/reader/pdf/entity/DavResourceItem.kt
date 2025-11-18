package com.archko.reader.pdf.entity

import io.github.triangleofice.dav4kmp.DavResource

/**
 * @author: archko 2025/11/18 :17:15
 */
public data class DavResourceItem(
    public var resource: DavResource,
    public var isDirectory: Boolean
)