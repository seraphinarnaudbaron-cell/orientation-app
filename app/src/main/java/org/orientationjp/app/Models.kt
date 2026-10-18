package org.orientationjp.app

data class Beacon(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val pattern: String,
    val description: String,
    /** Numéro choisi par l'admin (affiché aux coureurs et utilisé pour composer un parcours). */
    val number: Long = id,
) {
    val title: String get() = if (name.isBlank()) "Balise $id" else name
}

data class GeoPt(val lat: Double, val lon: Double, val ele: Double = Double.NaN)

/** Trace enregistrée par l'admin : un chemin ("path") ou un obstacle infranchissable ("barrier"). */
data class Trail(val id: Long, val name: String, val points: List<GeoPt>, val kind: String = "path") {
    val isBarrier: Boolean get() = kind == "barrier"
    val length: Double get() = pathLength(points)
}

/** Itinéraire sur le réseau de chemins. timeS = durée estimée à pied (formule de Tobler : dénivelé pris en compte). */
data class Route(
    val start: GeoPt,
    val points: List<GeoPt>,
    val end: GeoPt,
    val lengthM: Double,
    val timeS: Double = 0.0,
    val ascent: Double = 0.0,
    val descent: Double = 0.0,
)

/** Un morceau de parcours à dessiner : plein (sur un chemin) ou pointillé (raccord / hors chemin). */
data class CourseSeg(val points: List<GeoPt>, val dashed: Boolean)

data class CourseLeg(val from: String, val to: String, val lengthM: Double, val timeS: Double, val onTrails: Boolean)

data class Course(
    val order: List<Beacon>,
    val finish: GeoPt,
    val segments: List<CourseSeg>,
    val legs: List<CourseLeg>,
    val lengthM: Double,
    val timeS: Double,
    val ascent: Double,
    val descent: Double,
)

fun pathLength(points: List<GeoPt>): Double {
    var total = 0.0
    for (i in 1 until points.size) {
        total += Geo.distance(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
    }
    return total
}

data class Runner(val name: String, val lat: Double, val lon: Double)

data class AdminStats(
    val beacons: Int,
    val trails: Int,
    val activeNow: Int,
    val launches: Int,
    val distinctUsers: Int,
)

enum class Screen { Login, Map, Settings, Admin }

data class UiState(
    val screen: Screen = Screen.Login,
    val loggedIn: Boolean = false,
    val username: String = "",
    val privateMode: Boolean = false,
    val beacons: List<Beacon> = emptyList(),
    val runners: List<Runner> = emptyList(),
    val myLat: Double? = null,
    val myLon: Double? = null,
    val gpsStatus: String = "GPS en attente…",
    val online: Boolean? = null,
    val target: Beacon? = null,
    val recenterTick: Int = 0,
    val adminUnlocked: Boolean = false,
    val adminStats: AdminStats? = null,
    val lastError: String = "",
    val trails: List<Trail> = emptyList(),
    val route: Route? = null,
    val recording: Boolean = false,
    val recordPaused: Boolean = false,
    val recorded: List<GeoPt> = emptyList(),
    val recordKind: String = "path",
    val accuracyM: Float? = null,
    val finish: GeoPt? = null,
    val pickingFinish: Boolean = false,
    val course: Course? = null,
    val basemap: String = "osm",
) {
    fun distanceTo(b: Beacon): Double? {
        val la = myLat
        val lo = myLon
        return if (la != null && lo != null) Geo.distance(la, lo, b.lat, b.lon) else null
    }
}
