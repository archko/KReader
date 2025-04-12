package ovh.plrapps.mapcompose

import android.content.Context
import ovh.plrapps.mapcompose.core.TileStreamProvider

fun makeTileStreamProvider(appContext: Context) =
    TileStreamProvider { row, col, zoomLvl ->
        try {
            println("getBitmap.provider:$row, $col, $zoomLvl")
            appContext.assets?.open("tiles/mont_blanc/$zoomLvl/$row/$col.jpg")
        } catch (e: Exception) {
            null
        }
    }
