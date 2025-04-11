package com.archko.reader.pdf.subsampling.internal.tile

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import kotlinx.collections.immutable.ImmutableList

internal val ImageSemanticStateKey =
    SemanticsPropertyKey<SubSamplingImageSemanticState>("ImageSemanticState")
internal var SemanticsPropertyReceiver.imageSemanticState by ImageSemanticStateKey

internal data class SubSamplingImageSemanticState(
    val isImageDisplayed: Boolean,
    val isImageDisplayedInFullQuality: Boolean,
    val tiles: ImmutableList<ViewportImageTile>,
)
