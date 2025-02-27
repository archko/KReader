package com.archko.reader.viewer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform