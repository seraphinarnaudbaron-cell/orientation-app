package org.orientationjp.app

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/** Tuiles WMTS de la Géoplateforme de l'IGN (données ouvertes, gratuites, téléchargeables pour un usage hors ligne). */
class IgnTileSource(
    name: String,
    private val layer: String,
    private val format: String,
    ext: String,
    maxZoom: Int,
) : OnlineTileSourceBase(name, 0, maxZoom, 256, ext, arrayOf("https://data.geopf.fr/wmts"), "© IGN") {

    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=" + layer +
            "&STYLE=normal&FORMAT=" + format + "&TILEMATRIXSET=PM&TILEMATRIX=" + z + "&TILEROW=" + y + "&TILECOL=" + x
    }
}

object TileSources {
    val KEYS = listOf("osm", "plan", "photo")
    val LABELS = listOf("Plan OSM", "Plan IGN", "Photo aérienne")

    fun forKey(key: String): ITileSource = when (key) {
        "plan" -> IgnTileSource("PlanIGN", "GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", "image/png", ".png", 19)
        "photo" -> IgnTileSource("PhotoIGN", "ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg", ".jpg", 19)
        else -> TileSourceFactory.MAPNIK
    }
}
