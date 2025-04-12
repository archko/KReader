package com.archko.reader.pdf.subsampling.tile

import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

private val SVG_TAG: ByteString = "<svg".encodeUtf8()
private val LEFT_ANGLE_BRACKET: ByteString = "<".encodeUtf8()

// https://www.matthewflickinger.com/lab/whatsinagif/bits_and_bytes.asp
private val GIF_HEADER_87A = "GIF87a".encodeUtf8()
private val GIF_HEADER_89A = "GIF89a".encodeUtf8()

private val FTYP_MAJOR_BRAND_AVIF = "avif".encodeUtf8()
private val FTYP_MAJOR_BRAND_AVIS = "avis".encodeUtf8()

/**
 * Copied from coil-svg.
 *
 * Checks if a [source] possibly contains an SVG image.
 *
 * NOTE: There's no guaranteed method to determine if a byte stream is
 * an SVG without attempting to decode it. This method uses heuristics.
 */
internal fun isSvg(source: BufferedSource): Boolean {
    return source.rangeEquals(0, LEFT_ANGLE_BRACKET) &&
            source.indexOf(SVG_TAG, 0, 1024) != -1L
}

/**
 * Copied from coil-gif.
 *
 * Checks if a [source] possibly contains a GIF image.
 */
internal fun isGif(source: BufferedSource): Boolean {
    return source.rangeEquals(0, GIF_HEADER_89A) ||
            source.rangeEquals(0, GIF_HEADER_87A)
}

/**
 * Checks if a [source] possibly contains an AVIF image, which
 * [can't be sub-sampled yet](https://issuetracker.google.com/u/0/issues/392661391).
 *
 * AVIF is an [ISO-BMFF-based format](https://aomediacodec.github.io/av1-avif/#avif-required-boxes):
 * the first box is 'ftyp', and the major brand often 'avif' or 'avis' indicates it’s an AVIF.
 */
internal fun isAvif(source: BufferedSource): Boolean {
    // At least 8 bytes are needed. 4 for reading the box size + 4 for the box type.
    if (!source.request(8)) {
        return false
    }

    @Suppress("NAME_SHADOWING")
    val source = source.peek()

    // Read the box size (bytes 0..3). For a valid 'ftyp'
    // box, at least 16 bytes are expected:
    //  - 8 bytes for size + type
    //  - another 8+ for major brand, minor version, etc.
    val boxSize = source.readInt()
    if (boxSize < 16) return false

    // The box type (bytes 4..7) must be "ftyp".
    val boxType = source.readUtf8(byteCount = 4)
    if (boxType != "ftyp") return false

    // Make sure that the source has enough bytes for
    // the entire remainder can be read.
    val toRead = boxSize - 8
    if (!source.request(toRead.toLong())) return false

    val boxData = source.readByteString(toRead.toLong())
    return (boxData.indexOf(FTYP_MAJOR_BRAND_AVIF) != -1 || boxData.indexOf(FTYP_MAJOR_BRAND_AVIS) != -1)
}

/** Copied from coil-svg. */
internal fun BufferedSource.indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long {
    require(bytes.size > 0) { "bytes are empty" }

    val firstByte = bytes[0]
    val lastIndex = toIndex - bytes.size
    var currentIndex = fromIndex
    while (currentIndex < lastIndex) {
        currentIndex = indexOf(firstByte, currentIndex, lastIndex)
        if (currentIndex == -1L || rangeEquals(currentIndex, bytes)) {
            return currentIndex
        }
        currentIndex++
    }
    return -1
}
