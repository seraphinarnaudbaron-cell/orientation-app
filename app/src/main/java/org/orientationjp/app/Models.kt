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

data class Runner(val name: String, val lat: Double, val lon: Double)

data class AdminStats(
    val beacons: Int,
    val activeNow: Int,
    val launches: Int,
    val distinctUsers: Int,
    val validations: Int,
)

enum class Screen { Login, Map, Settings, Admin }

data class UiState(
    val screen: Screen = Screen.Login,
    val loggedIn: Boolean = false,
    val username: String = "",
    val privateMode: Boolean = false,
    val beacons: List<Beacon> = emptyList(),
    val runners: List<Runner> = emptyList(),
    val validated: Set<Long> = emptySet(),
    val myLat: Double? = null,
    val myLon: Double? = null,
    val gpsStatus: String = "GPS en attente…",
    val online: Boolean? = null,
    val target: Beacon? = null,
    val recenterTick: Int = 0,
    val adminUnlocked: Boolean = false,
    val adminStats: AdminStats? = null,
    val lastError: String = "",
) {
    fun distanceTo(b: Beacon): Double? {
        val la = myLat
        val lo = myLon
        return if (la != null && lo != null) Geo.distance(la, lo, b.lat, b.lon) else null
    }
}

data class ValidationResult(val ok: Boolean, val message: String)
