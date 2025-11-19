package com.archko.reader.pdf.entity

import kotlinx.serialization.Serializable

/**
 * @author: archko 2025/11/16 :10:06
 */
@Serializable
public data class WebdavUser(
    val name: String,
    val pass: String,
    val host: String,
    val path: String
) {
    override fun toString(): String {
        return "WebdavUser(name='$name', host='$host', path='$path')"
    }
}