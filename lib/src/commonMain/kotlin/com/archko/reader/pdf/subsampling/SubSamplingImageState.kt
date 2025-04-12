@file:Suppress("NAME_SHADOWING")

package com.archko.reader.pdf.subsampling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.archko.reader.pdf.subsampling.internal.AndroidImageDecoderFactoryParams
import com.archko.reader.pdf.subsampling.internal.ImageRegionDecoder
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomableContentTransformation
import me.saket.telephoto.zoomable.ZoomableState
import java.io.IOException

@Composable
public fun rememberSubSamplingImageState(
    imageSource: SubSamplingImageSource,
    zoomableState: ZoomableState,
    viewportSize: IntSize = IntSize.Zero,
    errorReporter: SubSamplingImageErrorReporter = SubSamplingImageErrorReporter.NoOpInRelease
): SubSamplingImageState {
    val state = rememberSubSamplingImageState(
        imageSource = imageSource,
        transformation = { zoomableState.contentTransformation },
        viewportSize = viewportSize,
        errorReporter = errorReporter,
    )

    // SubSamplingImage will apply the transformations on its own.
    DisposableEffect(zoomableState) {
        val previousValue = zoomableState.autoApplyTransformations
        zoomableState.autoApplyTransformations = false
        onDispose {
            zoomableState.autoApplyTransformations = previousValue
        }
    }

    check(state is RealSubSamplingImageState)
    zoomableState.setContentLocation(
        ZoomableContentLocation.unscaledAndTopLeftAligned(state.imageOrPreviewSize?.toSize())
    )
    return state
}

@Composable
internal fun rememberSubSamplingImageState(
    imageSource: SubSamplingImageSource,
    transformation: () -> ZoomableContentTransformation,
    viewportSize: IntSize = IntSize.Zero,
    errorReporter: SubSamplingImageErrorReporter = SubSamplingImageErrorReporter.NoOpInRelease,
): SubSamplingImageState {
    val transformation by rememberUpdatedState(transformation)
    val state = remember(imageSource) {
        RealSubSamplingImageState(imageSource, transformation)
    }.also {
        it.decoder = createImageRegionDecoder(imageSource, viewportSize, errorReporter)
    }

    state.LoadImageTilesEffect()
    DisposableEffect(imageSource) {
        onDispose {
            imageSource.close()
        }
    }
    return state
}

@Composable
private fun createImageRegionDecoder(
    imageSource: SubSamplingImageSource,
    viewportSize: IntSize,
    errorReporter: SubSamplingImageErrorReporter
): ImageRegionDecoder? {
    val errorReporter by rememberUpdatedState(errorReporter)
    var decoder by remember(imageSource) { mutableStateOf<ImageRegionDecoder?>(null) }

    if (!LocalInspectionMode.current && viewportSize != IntSize.Zero) {
        LaunchedEffect(imageSource, viewportSize) {
            try {
                decoder = imageSource.decoder().create(
                    AndroidImageDecoderFactoryParams(
                        viewportSize = viewportSize,
                    )
                )
            } catch (e: IOException) {
                errorReporter.onImageLoadingFailed(e, imageSource)
            }
        }
        DisposableEffect(imageSource) {
            onDispose {
                decoder?.close()
                decoder = null
            }
        }
    }

    return decoder
}

/** State for [SubSamplingImage]. */
@Stable
public sealed interface SubSamplingImageState {
    /** Raw size of the image, without any scaling applied. */
    public val imageSize: IntSize?

    /**
     * Whether the image is loaded and displayed, but not necessarily in its full quality.
     *
     * Also see [isImageDisplayedInFullQuality].
     */
    public val isImageDisplayed: Boolean

    /** Whether the image is loaded and displayed in its full quality. */
    public val isImageDisplayedInFullQuality: Boolean

    @Deprecated("Use isImageDisplayed instead", ReplaceWith("isImageDisplayed"))
    public val isImageLoaded: Boolean get() = isImageDisplayed
}
