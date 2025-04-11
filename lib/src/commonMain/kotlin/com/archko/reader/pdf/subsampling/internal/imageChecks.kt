package com.archko.reader.pdf.subsampling.internal

import com.archko.reader.pdf.subsampling.SubSamplingImageSource

/**
 * Check whether an image source can be sub-sampled and decoded using [com.archko.reader.pdf.state.AndroidImageRegionDecoder].
 */
@Deprecated(
    message = "Moved to another package",
    replaceWith = ReplaceWith(
        "canBeSubSampled(context)",
        "me.saket.telephoto.subsamplingimage.util.canBeSubSampled"
    )
)
public fun SubSamplingImageSource.canBeSubSampled(): Boolean {
    return true
}

/** Check whether an image source exists and has non-zero bytes. */
@Deprecated(
    message = "Moved to another package",
    replaceWith = ReplaceWith("exists(context)", "me.saket.telephoto.subsamplingimage.util.exists")
)
public fun SubSamplingImageSource.exists(): Boolean {
    return true
}
