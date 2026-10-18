package org.orientationjp.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

private const val ZOOM_MIN = 15
private const val ZOOM_MAX = 19
private const val MAX_TILES = 6000

/** Zone qui entoure toutes les balises et tous les chemins, avec une marge. */
private fun areaBox(state: UiState, fallbackLat: Double, fallbackLon: Double): BoundingBox {
    var minLat = 90.0
    var maxLat = -90.0
    var minLon = 180.0
    var maxLon = -180.0
    var any = false
    fun add(la: Double, lo: Double) {
        any = true
        if (la < minLat) minLat = la
        if (la > maxLat) maxLat = la
        if (lo < minLon) minLon = lo
        if (lo > maxLon) maxLon = lo
    }
    for (b in state.beacons) add(b.lat, b.lon)
    for (t in state.trails) for (p in t.points) add(p.lat, p.lon)
    if (!any) {
        add(fallbackLat, fallbackLon)
        return BoundingBox(fallbackLat + 0.008, fallbackLon + 0.012, fallbackLat - 0.008, fallbackLon - 0.012)
    }
    val m = 0.003
    return BoundingBox(maxLat + m, maxLon + m * 1.4, minLat - m, minLon - m * 1.4)
}

private fun download(
    context: Context,
    key: String,
    box: BoundingBox,
    setStatus: (String) -> Unit,
    setBusy: (Boolean) -> Unit,
) {
    try {
        val mv = MapView(context)
        mv.setTileSource(TileSources.forKey(key))
        val cm = CacheManager(mv)
        val count = cm.possibleTilesInArea(box, ZOOM_MIN, ZOOM_MAX)
        if (count > MAX_TILES) {
            setStatus("Zone trop grande : " + count + " tuiles (maximum " + MAX_TILES + ")")
            return
        }
        setBusy(true)
        setStatus("Téléchargement de " + count + " tuiles…")
        cm.downloadAreaAsync(context, box, ZOOM_MIN, ZOOM_MAX, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                setBusy(false)
                setStatus("Zone téléchargée : disponible sans réseau")
            }

            override fun onTaskFailed(errors: Int) {
                setBusy(false)
                setStatus("Terminé avec " + errors + " tuile(s) en erreur : relance avec du réseau")
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                setStatus("Téléchargement : " + progress + " tuiles (zoom " + currentZoomLevel + ")")
            }

            override fun downloadStarted() {}

            override fun setPossibleTilesInArea(total: Int) {}
        })
    } catch (e: Exception) {
        setBusy(false)
        setStatus("Impossible : " + (e.message ?: e.javaClass.simpleName))
    }
}

/** Carte à utiliser sans réseau : on télécharge la zone des balises et des chemins. */
@Composable
fun OfflineMapCard(state: UiState, fallbackLat: Double, fallbackLon: Double) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    AppCard {
        CardTitle("Carte hors ligne")
        if (state.basemap == "osm") {
            BodyText(
                "Le fond OpenStreetMap interdit le téléchargement en masse. " +
                    "Choisis « Plan IGN » ou « Photo aérienne » ci-dessus pour télécharger la zone."
            )
        } else {
            BodyText(
                "Télécharge la zone qui entoure toutes les balises et tous les chemins (zoom 15 à 19). " +
                    "Fais-le avec du Wi-Fi, avant de partir en forêt."
            )
            PrimaryButton("Télécharger la zone", enabled = !busy) {
                download(
                    context,
                    state.basemap,
                    areaBox(state, fallbackLat, fallbackLon),
                    { status = it },
                    { busy = it },
                )
            }
            if (status.isNotEmpty()) BodyText(status, TextDark)
        }
        BodyText("Les balises et les chemins sont déjà gardés en mémoire : ils s'affichent même sans réseau.")
    }
}
