package org.orientationjp.app

data class Beacon(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val pattern: String,
    val description: String,
) {
    val title: String get() = if (name.isBlank()) "Balise $id" else name
}

data class GeoPt(val lat: Double, val lon: Double)

/** Trace enregistrée par l'admin en marchant. */
data class Trail(val id: Long, val name: String, val points: List<GeoPt>) {
    val length: Double get() = pathLength(points)
}

/** Itinéraire calculé sur le réseau de chemins. start/end = position du coureur et de la balise. */
data class Route(val start: GeoPt, val points: List<GeoPt>, val end: GeoPt, val lengthM: Double)

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
) {
    fun distanceTo(b: Beacon): Double? {
        val la = myLat
        val lo = myLon
        return if (la != null && lo != null) Geo.distance(la, lo, b.lat, b.lon) else null
    }
}
