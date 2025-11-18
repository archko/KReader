package com.archko.reader.viewer

import kotlinx.serialization.Serializable

/**
 * @author: archko 2025/11/16 :10:06
 */
@Serializable
data class WebdavUser(
    val name: String,
    val pass: String,
    val host: String,
    val path: String
) {
    override fun toString(): String {
        return "WebdavUser(name='$name', pass='$pass', host='$host', path='$path')"
    }
}
