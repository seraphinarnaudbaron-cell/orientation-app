package org.orientationjp.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(
        UiState(
            screen = if (prefs.name.isNotBlank()) Screen.Map else Screen.Login,
            loggedIn = prefs.name.isNotBlank(),
            username = prefs.name,
            privateMode = prefs.privateMode,
        )
    )
    private val tracker = LocationTracker(
        app,
        onLocation = { loc -> onLocation(loc) },
        onProviderOff = { _state.update { it.copy(gpsStatus = "GPS désactivé : active la localisation") } },
    )

    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    // Dernière position de la carte (conservée quand on change d'écran)
    var mapLat: Double = Config.DEFAULT_LAT
        private set
    var mapLon: Double = Config.DEFAULT_LON
        private set
    var mapZoom: Double = Config.DEFAULT_ZOOM
        private set

    private var loopsStarted = false
    private var adminPassword = ""
    private var firstFixDone = false
    private var graph: TrailGraph? = null
    private var routeJob: Job? = null
    private var lastRouteLat = Double.NaN
    private var lastRouteLon = Double.NaN

    init {
        if (_state.value.loggedIn) startSession()
    }

    // ------------------------------------------------------------------ utilitaires
    private fun say(text: String) {
        _messages.trySend(text)
    }

    fun rememberMap(lat: Double, lon: Double, zoom: Double) {
        mapLat = lat
        mapLon = lon
        mapZoom = zoom
    }

    /** force = true pour une action demandée par l'utilisateur : on affiche toujours l'erreur. */
    private fun onNetworkError(e: Exception, force: Boolean = false) {
        val wasOffline = _state.value.online == false
        val detail = e.javaClass.simpleName + ": " + (e.message ?: "").take(70)
        _state.update { it.copy(online = false, lastError = detail) }
        if (e is ApiException) {
            val m = e.message.orEmpty()
            if (m.contains("schema cache") || m.contains("Could not find")) {
                say("Base non initialisée : exécute supabase_setup.sql dans Supabase")
            } else {
                say(m.take(90))
            }
        } else if (force) {
            say("Erreur : " + detail.take(90))
        } else if (!wasOffline) {
            say("Serveur injoignable : " + detail.take(90))
        }
    }

    // ------------------------------------------------------------------ navigation
    fun go(screen: Screen) {
        _state.update { it.copy(screen = screen) }
    }

    fun back() {
        when (_state.value.screen) {
            Screen.Admin -> {
                if (!_state.value.recording) lockAdmin()
                go(Screen.Settings)
            }
            Screen.Settings -> go(Screen.Map)
            else -> {}
        }
    }

    // ------------------------------------------------------------------ session
    fun login(name: String, private: Boolean) {
        val n = name.trim()
        if (n.isEmpty() || n.length > 30) {
            say("Entre un nom (30 caractères max)")
            return
        }
        prefs.name = n
        prefs.privateMode = private
        _state.update { it.copy(username = n, privateMode = private, loggedIn = true, screen = Screen.Map) }
        startSession()
    }

    fun logout() {
        val n = _state.value.username
        if (n.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    SupabaseApi.clearPosition(n)
                } catch (e: Exception) {
                }
            }
        }
        prefs.name = ""
        _state.update { it.copy(username = "", loggedIn = false, target = null, route = null, recording = false, recorded = emptyList(), screen = Screen.Login) }
    }

    fun setPrivate(value: Boolean) {
        prefs.privateMode = value
        _state.update { it.copy(privateMode = value) }
        val n = _state.value.username
        if (value) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    SupabaseApi.clearPosition(n)
                } catch (e: Exception) {
                }
            }
            say("Mode privé : ta position n'est plus partagée")
        } else {
            say("Position partagée")
            viewModelScope.launch { pushPosition() }
        }
    }

    private fun startSession() {
        if (loopsStarted) return
        loopsStarted = true
        val name = _state.value.username
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SupabaseApi.logEvent(name, "launch")
            } catch (e: Exception) {
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshBeacons()
                refreshTrails()
                delay(60_000L)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshRunners()
                pushPosition()
                delay(Config.UPDATE_INTERVAL_S * 1000L)
            }
        }
    }

    // ------------------------------------------------------------------ GPS
    private fun hasLocationPermission(): Boolean {
        val ctx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted && hasLocationPermission()) {
            _state.update { it.copy(gpsStatus = "GPS en attente d'un signal…") }
            tracker.start()
        } else {
            _state.update { it.copy(gpsStatus = "Localisation refusée (réglages Android)") }
        }
    }

    private fun onLocation(loc: Location) {
        val first = !firstFixDone
        firstFixDone = true
        _state.update {
            it.copy(
                myLat = loc.latitude,
                myLon = loc.longitude,
                gpsStatus = "GPS actif",
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                recenterTick = if (first) it.recenterTick + 1 else it.recenterTick,
            )
        }
        if (first) viewModelScope.launch { pushPosition() }

        // Mode créateur : on ajoute le point si la précision est correcte et qu'on a bougé d'au moins 2,5 m
        val st = _state.value
        if (st.recording && !st.recordPaused && (!loc.hasAccuracy() || loc.accuracy <= 12f)) {
            val last = st.recorded.lastOrNull()
            if (last == null || Geo.distance(last.lat, last.lon, loc.latitude, loc.longitude) >= 2.5) {
                _state.update { it.copy(recorded = it.recorded + GeoPt(loc.latitude, loc.longitude)) }
            }
        }
        recomputeRoute(false)
    }

    fun requestRecenter() {
        if (_state.value.myLat == null) {
            say("Position pas encore disponible")
        } else {
            _state.update { it.copy(recenterTick = it.recenterTick + 1) }
        }
    }

    override fun onCleared() {
        tracker.stop()
        super.onCleared()
    }

    // ------------------------------------------------------------------ réseau
    private suspend fun refreshBeacons() {
        try {
            val list = withContext(Dispatchers.IO) { SupabaseApi.getBeacons() }
            _state.update { st ->
                val still = st.target?.let { t -> list.firstOrNull { it.id == t.id } }
                st.copy(
                    beacons = list,
                    online = true,
                    lastError = "",
                    target = still,
                    route = if (still == null) null else st.route,
                    course = st.course?.let { c -> if (c.order.all { o -> list.any { it.id == o.id } }) c else null },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onNetworkError(e)
        }
    }

    private suspend fun refreshRunners() {
        try {
            val list = withContext(Dispatchers.IO) { SupabaseApi.getRunners() }
            _state.update { st -> st.copy(runners = list.filter { it.name != st.username }, online = true, lastError = "") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onNetworkError(e)
        }
    }

    private suspend fun pushPosition() {
        val st = _state.value
        val la = st.myLat
        val lo = st.myLon
        if (!st.loggedIn || st.privateMode || la == null || lo == null) return
        try {
            withContext(Dispatchers.IO) { SupabaseApi.pushPosition(st.username, la, lo) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onNetworkError(e)
        }
    }

    // ------------------------------------------------------------------ balises
    fun setTarget(b: Beacon?) {
        _state.update { it.copy(target = b, route = null) }
        if (b != null) say("Direction : " + b.title)
        recomputeRoute(true)
    }

    // ------------------------------------------------------------------ chemins et itinéraire
    private suspend fun refreshTrails() {
        try {
            val list = withContext(Dispatchers.IO) { SupabaseApi.getTrails() }
            if (list != _state.value.trails) {
                val g = withContext(Dispatchers.Default) { TrailGraph(list) }
                graph = g
                _state.update { it.copy(trails = list) }
                recomputeRoute(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onNetworkError(e)
        }
    }

    /** Recalcule l'itinéraire sur le réseau de chemins (si on a bougé de 3 m ou si force). */
    private fun recomputeRoute(force: Boolean) {
        val st = _state.value
        val t = st.target
        val la = st.myLat
        val lo = st.myLon
        val g = graph
        if (t == null || la == null || lo == null || g == null) {
            if (st.route != null) _state.update { it.copy(route = null) }
            return
        }
        if (!force && !lastRouteLat.isNaN() && Geo.distance(lastRouteLat, lastRouteLon, la, lo) < 3.0) return
        lastRouteLat = la
        lastRouteLon = lo
        routeJob?.cancel()
        routeJob = viewModelScope.launch(Dispatchers.Default) {
            val r = g.route(la, lo, t.lat, t.lon)
            _state.update { cur -> if (cur.target?.id == t.id) cur.copy(route = r) else cur }
        }
    }

    // ------------------------------------------------------------------ parcours (plusieurs balises + arrivée)
    fun startPickingFinish() {
        _state.update { it.copy(pickingFinish = true) }
        say("Touche la carte pour placer l'arrivée")
    }

    fun cancelPickingFinish() {
        _state.update { it.copy(pickingFinish = false) }
    }

    fun setFinish(lat: Double, lon: Double) {
        _state.update { it.copy(finish = GeoPt(lat, lon), pickingFinish = false, course = null) }
        say("Arrivée placée")
    }

    fun clearCourse() {
        _state.update { it.copy(course = null, finish = null, pickingFinish = false) }
    }

    fun planCourse(numbersText: String) {
        val st = _state.value
        val nums = Regex("\\d+").findAll(numbersText).map { it.value.toLong() }.toList().distinct()
        if (nums.isEmpty()) {
            say("Entre au moins un numéro de balise")
            return
        }
        if (nums.size > CoursePlanner.MAX_BEACONS) {
            say("Maximum " + CoursePlanner.MAX_BEACONS + " balises")
            return
        }
        val missing = nums.filter { n -> st.beacons.none { it.number == n } }
        if (missing.isNotEmpty()) {
            say("Balise introuvable : " + missing.joinToString(", "))
            return
        }
        val finish = st.finish
        if (finish == null) {
            say("Place d'abord l'arrivée sur la carte")
            return
        }
        val la = st.myLat
        val lo = st.myLon
        if (la == null || lo == null) {
            say("Position GPS indisponible : le départ est ta position")
            return
        }
        val chosen = nums.map { n -> st.beacons.first { it.number == n } }
        val g = graph
        viewModelScope.launch {
            try {
                val c = withContext(Dispatchers.Default) { CoursePlanner.plan(g, GeoPt(la, lo), chosen, finish) }
                _state.update { it.copy(course = c) }
                say("Parcours : " + Geo.format(c.lengthM) + " · " + Geo.formatTime(c.timeS))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Erreur de calcul : " + (e.message ?: e.javaClass.simpleName).take(80))
            }
        }
    }

    // ------------------------------------------------------------------ mode créateur de chemin
    fun startRecording(kind: String) {
        val st = _state.value
        if (!st.adminUnlocked) {
            say("Connecte-toi en admin d'abord")
            return
        }
        val la = st.myLat
        val lo = st.myLon
        val first = if (la != null && lo != null) listOf(GeoPt(la, lo)) else emptyList()
        _state.update { it.copy(recording = true, recordPaused = false, recorded = first, recordKind = kind, screen = Screen.Map) }
        say(if (kind == "barrier") "Mode obstacle : marche le long de la rivière / clôture" else "Mode créateur : marche le long du chemin")
    }

    fun toggleRecordPause() {
        _state.update { it.copy(recordPaused = !it.recordPaused) }
    }

    fun cancelRecording() {
        _state.update { it.copy(recording = false, recordPaused = false, recorded = emptyList()) }
        say("Enregistrement annulé")
    }

    fun finishRecording(name: String) {
        val pts = _state.value.recorded
        if (pts.size < 5 || pathLength(pts) < 10.0) {
            say("Trop court : marche au moins 10 m")
            return
        }
        val pw = adminPassword
        if (pw.isEmpty()) {
            say("Reconnecte-toi en admin pour enregistrer")
            return
        }
        val kind = _state.value.recordKind
        val n = name.trim().ifEmpty { if (kind == "barrier") "Obstacle" else "Chemin" }
        viewModelScope.launch {
            try {
                // Altitudes du terrain (IGN) : indispensables pour tenir compte du dénivelé
                var toSend = pts
                if (kind == "path") {
                    val z = withContext(Dispatchers.IO) { ElevationApi.fetch(pts) }
                    if (z != null && z.size == pts.size) {
                        toSend = pts.mapIndexed { i, p -> GeoPt(p.lat, p.lon, z[i]) }
                    } else {
                        say("Altitude indisponible : enregistré en 2D (sans dénivelé)")
                    }
                }
                withContext(Dispatchers.IO) { SupabaseApi.adminAddTrail(pw, n, toSend, kind) }
                _state.update { it.copy(recording = false, recordPaused = false, recorded = emptyList()) }
                say((if (kind == "barrier") "Obstacle enregistré (" else "Chemin enregistré (") + Geo.format(pathLength(pts)) + ")")
                refreshTrails()
                refreshAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // la trace reste en mémoire : on peut réessayer
                onNetworkError(e, force = true)
            }
        }
    }

    fun deleteTrail(t: Trail) {
        val pw = adminPassword
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SupabaseApi.adminDeleteTrail(pw, t.id) }
                say("Chemin supprimé")
                refreshTrails()
                refreshAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }

    // ------------------------------------------------------------------ admin
    fun unlockAdmin(password: String) {
        if (password.isEmpty()) {
            say("Entre le mot de passe")
            return
        }
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { SupabaseApi.adminCheck(password) }
                if (ok) {
                    adminPassword = password
                    _state.update { it.copy(adminUnlocked = true) }
                    refreshAdmin()
                } else {
                    say("Mot de passe incorrect")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }

    fun lockAdmin() {
        adminPassword = ""
        _state.update { it.copy(adminUnlocked = false, adminStats = null) }
    }

    fun refreshAdmin() {
        if (adminPassword.isEmpty()) return
        viewModelScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) { SupabaseApi.adminStats(adminPassword) }
                _state.update { it.copy(adminStats = stats) }
                refreshBeacons()
                refreshTrails()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }

    fun addBeacon(name: String, description: String, pattern: String) {
        if (!pattern.contains('1')) {
            say("Coche au moins un poinçon")
            return
        }
        val pw = adminPassword
        val lat = mapLat
        val lon = mapLon
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SupabaseApi.adminAddBeacon(pw, name.trim(), lat, lon, pattern, description.trim()) }
                say("Balise ajoutée")
                refreshAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }

    fun deleteBeacon(b: Beacon) {
        val pw = adminPassword
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SupabaseApi.adminDeleteBeacon(pw, b.id) }
                say("Balise supprimée")
                refreshAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }
}
