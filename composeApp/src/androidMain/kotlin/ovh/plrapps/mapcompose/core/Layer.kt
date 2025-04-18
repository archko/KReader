package ovh.plrapps.mapcompose.core

internal data class Layer(
    val id: String,
    val tileStreamProvider: TileStreamProvider,
    val alpha: Float = 1f
)
