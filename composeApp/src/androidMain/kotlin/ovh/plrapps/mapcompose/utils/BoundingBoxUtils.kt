package ovh.plrapps.mapcompose.utils

import ovh.plrapps.mapcompose.api.BoundingBox

internal fun BoundingBox.scaleAxis(xAxisMultiplier: Double): BoundingBox {
    return BoundingBox(xLeft * xAxisMultiplier, yTop, xRight * xAxisMultiplier, yBottom)
}