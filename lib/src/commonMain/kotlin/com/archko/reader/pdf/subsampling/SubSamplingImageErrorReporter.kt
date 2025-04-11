package com.archko.reader.pdf.subsampling

import java.io.IOException

public interface SubSamplingImageErrorReporter {

    /** Called when loading of an [imageSource] fails. */
    public fun onImageLoadingFailed(e: IOException, imageSource: SubSamplingImageSource): Unit =
        Unit

    public companion object {
        public val NoOpInRelease: SubSamplingImageErrorReporter =
            object : SubSamplingImageErrorReporter {
                override fun onImageLoadingFailed(
                    e: IOException,
                    imageSource: SubSamplingImageSource
                ) {
                    /*if (BuildConfig.DEBUG) {
                      // I'm not entirely convinced with this, but I think failure in loading of bitmaps from
                      // local storage is not a good sign and should be surfaced ASAP in debug builds. Please
                      // file an issue on https://github.com/saket/telephoto/issues if you think otherwise.
                      throw e
                    }*/
                }
            }
    }
}
