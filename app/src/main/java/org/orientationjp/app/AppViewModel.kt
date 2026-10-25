package org.orientationjp.app

import android.Manifest
import android.app.Application
import android.content.Intent
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
import kotlin.math.max

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val cache = DataCache(app)
    private val recStore = RecordingStore(app)

    private val _state = MutableStateFlow(
        UiState(
            screen = if (prefs.name.isNotBlank()) Screen.Map else Screen.Login,
            loggedIn = prefs.name.isNotBlank(),
            username = prefs.name,
            privateMode = prefs.privateMode,
            basemap = prefs.basemap,
            showOthers = prefs.showOthers,
        )
    )
    private val tracker = LocationTracker(
        app,
        onLocation = { loc -> onLocation(loc) },
        onProviderOff = { _state.update { it.copy(gpsStatus = "GPS désactivé : active la localisation") } },
    )

    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    /** Cap du téléphone (degrés, nord = 0) : uniquement quand la flèche vers la balise est affichée. */
    val heading: StateFlow<Float?> = _heading.asStateFlow()
    private val compass = CompassTracker(app) { h -> _heading.value = h }
    private var compassOn = false

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
    private var offCount = 0
    private var lastReplanMs = 0L

    init {
        // trace en cours sauvegardée (plantage, téléphone éteint...) : on la reprend, en pause
        val saved = recStore.load()
        if (saved != null) {
            _state.update { it.copy(recording = true, recordPaused = true, recorded = saved.second, recordKind = saved.first) }
            startRecordingService()
            say("Enregistrement repris : " + saved.second.size + " points (en pause). « Reprendre » pour continuer, « Fin » pour enregistrer.")
        }
        // balises et chemins en mémoire : l'app fonctionne même sans réseau
        viewModelScope.launch(Dispatchers.IO) {
            loadCache()
            BugReporter.sendPending(prefs.name)
        }
        if (_state.value.loggedIn) startSession()
    }

    private fun loadCache() {
        try {
            cache.beaconsJson()?.let { json ->
                val list = SupabaseApi.parseBeacons(json)
                _state.update { st -> if (st.beacons.isEmpty()) st.copy(beacons = list) else st }
            }
        } catch (e: Exception) {
            BugReporter.report("cache-balises", e)
        }
        try {
            cache.trailsJson()?.let { json ->
                val list = SupabaseApi.parseTrails(json)
                if (_state.value.trails.isEmpty() && graph == null) {
                    graph = TrailGraph(list)
                    _state.update { st -> if (st.trails.isEmpty()) st.copy(trails = list) else st }
                    recomputeRoute(true)
                }
            }
        } catch (e: Exception) {
            BugReporter.report("cache-chemins", e)
        }
    }

    private fun startRecordingService() {
        try {
            ContextCompat.startForegroundService(getApplication(), Intent(getApplication(), RecordingService::class.java))
        } catch (e: Exception) {
        }
    }

    private fun stopRecordingService() {
        try {
            getApplication<Application>().stopService(Intent(getApplication(), RecordingService::class.java))
        } catch (e: Exception) {
        }
    }

    fun setBasemap(key: String) {
        prefs.basemap = key
        _state.update { it.copy(basemap = key) }
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
        if (e !is ApiException && e !is java.io.IOException) BugReporter.report("inattendu", e)
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
        if (n.isEmpty() || n.length > Config.MAX_NAME) {
            say("Entre un pseudo (" + Config.MAX_NAME + " caractères maximum)")
            return
        }
        prefs.name = n
        prefs.privateMode = private
        _state.update { it.copy(username = n, privateMode = private, loggedIn = true, screen = Screen.Map) }
        startSession()
    }

    fun logout() {
        recStore.clear()
        stopRecordingService()
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
                val u = _state.value.username.ifEmpty { "?" }
                withContext(Dispatchers.IO) { BugReporter.sendPending(u) }
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
                val np = GeoPt(loc.latitude, loc.longitude)
                _state.update { it.copy(recorded = it.recorded + np) }
                recStore.append(np)
            }
        }
        recomputeRoute(false)
        trackCourse(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
        updateCompass()
    }

    /** La boussole ne tourne que lorsque la flèche vers la balise visée est affichée (moins de 60 m). */
    private fun updateCompass() {
        val st = _state.value
        val b = st.aimed
        val d = if (b != null) st.distanceTo(b) else null
        val need = d != null && d <= Config.ARROW_RADIUS_M
        if (need && !compassOn) {
            compassOn = compass.start(st.myLat, st.myLon)
        } else if (!need && compassOn) {
            compass.stop()
            compassOn = false
            _heading.value = null
        }
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
        compass.stop()
        super.onCleared()
    }

    // ------------------------------------------------------------------ réseau
    private suspend fun refreshBeacons() {
        try {
            val list = withContext(Dispatchers.IO) {
                val json = SupabaseApi.fetchBeaconsJson()
                cache.saveBeacons(json)
                SupabaseApi.parseBeacons(json)
            }
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

    fun setShowOthers(on: Boolean) {
        prefs.showOthers = on
        _state.update { it.copy(showOthers = on, runners = if (on) it.runners else emptyList()) }
        if (on) viewModelScope.launch { refreshRunners() }
        say(if (on) "Autres coureurs visibles" else "Autres coureurs masqués")
    }

    private suspend fun refreshRunners() {
        if (!_state.value.showOthers) return
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
        updateCompass()
    }

    // ------------------------------------------------------------------ chemins et itinéraire
    private suspend fun refreshTrails() {
        try {
            val list = withContext(Dispatchers.IO) {
                val json = SupabaseApi.fetchTrailsJson()
                cache.saveTrails(json)
                SupabaseApi.parseTrails(json)
            }
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
        _state.update { it.copy(finish = GeoPt(lat, lon), pickingFinish = false, course = null, courseProgress = 0.0, courseVisited = emptySet(), courseDone = false) }
        say("Arrivée placée")
    }

    fun clearCourse() {
        _state.update { it.copy(course = null, finish = null, pickingFinish = false, courseProgress = 0.0, courseVisited = emptySet(), courseDone = false) }
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
                offCount = 0
                lastReplanMs = System.currentTimeMillis()
                _state.update { it.copy(course = c, courseProgress = 0.0, courseVisited = emptySet(), courseDone = false) }
                say("Parcours : " + Geo.format(c.lengthM) + " · " + Geo.formatTime(c.timeS))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BugReporter.report("calcul-parcours", e)
                say("Erreur de calcul : " + (e.message ?: e.javaClass.simpleName).take(80))
            }
        }
    }

    // ------------------------------------------------------------------ suivi dynamique du parcours
    /**
     * Appelé à chaque position GPS : avance la progression le long du tracé (le parcouru passe en gris),
     * détecte les balises atteintes et recalcule le parcours si le coureur s'écarte du tracé.
     */
    private fun trackCourse(lat: Double, lon: Double, accuracy: Float?) {
        val st = _state.value
        val course = st.course ?: return
        if (st.courseDone) return
        val geo = course.geometry
        var visited = st.courseVisited
        var progress = st.courseProgress
        var replan = false

        // 1. balises atteintes
        val reach = Config.BEACON_REACH_M
        val order = course.order
        val next = order.indexOfFirst { it.id !in visited }
        for (k in order.indices) {
            val b = order[k]
            if (b.id in visited) continue
            if (Geo.distance(lat, lon, b.lat, b.lon) <= reach) {
                visited = visited + b.id
                if (k == next) {
                    if (k < geo.stopCum.size) progress = max(progress, geo.stopCum[k])
                    say("Balise n°" + b.number + " atteinte")
                } else {
                    replan = true
                    say("Balise n°" + b.number + " atteinte (hors ordre) : parcours recalculé")
                }
            }
        }

        // 2. arrivée
        if (order.all { it.id in visited } && Geo.distance(lat, lon, course.finish.lat, course.finish.lon) <= reach) {
            _state.update { it.copy(courseVisited = visited, courseProgress = geo.total, courseDone = true) }
            say("Parcours terminé !")
            return
        }

        // 3. progression le long du tracé (fenêtre vers l'avant : le tracé peut repasser au même endroit)
        val thr = max(Config.DEVIATION_MIN_M, 2.5 * (accuracy ?: 0f).toDouble())
        val near = geo.nearest(lat, lon, progress - 40.0, progress + 250.0)
        var off = false
        if (near != null && near.dist <= thr) {
            progress = max(progress, near.progress)
        } else {
            off = true
        }
        offCount = if (off) offCount + 1 else 0

        // 4. grosse déviation prolongée : on recalcule depuis la position actuelle
        val now = System.currentTimeMillis()
        if (!replan && offCount >= Config.DEVIATION_SAMPLES && now - lastReplanMs > Config.REPLAN_MIN_INTERVAL_MS) {
            replan = true
        }

        if (progress != st.courseProgress || visited != st.courseVisited) {
            _state.update { it.copy(courseProgress = progress, courseVisited = visited) }
        }
        if (replan) replanCourse(course, lat, lon, visited)
    }

    private fun replanCourse(old: Course, lat: Double, lon: Double, visited: Set<Long>) {
        offCount = 0
        lastReplanMs = System.currentTimeMillis()
        val remaining = old.order.filter { it.id !in visited }
        val g = graph
        viewModelScope.launch {
            try {
                val c = withContext(Dispatchers.Default) { CoursePlanner.plan(g, GeoPt(lat, lon), remaining, old.finish) }
                var applied = false
                _state.update { cur ->
                    if (cur.course === old && !cur.courseDone) {
                        applied = true
                        cur.copy(course = c, courseProgress = 0.0, courseVisited = cur.courseVisited + visited)
                    } else {
                        cur
                    }
                }
                if (applied) say("Parcours recalculé depuis ta position")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BugReporter.report("recalcul-parcours", e)
                say("Erreur de recalcul : " + (e.message ?: e.javaClass.simpleName).take(80))
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
        recStore.begin(kind)
        for (p in first) recStore.append(p)
        startRecordingService()
        say(if (kind == "barrier") "Mode obstacle : marche le long de la rivière / clôture" else "Mode créateur : marche le long du chemin")
    }

    fun toggleRecordPause() {
        _state.update { it.copy(recordPaused = !it.recordPaused) }
    }

    fun cancelRecording() {
        recStore.clear()
        stopRecordingService()
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
                recStore.clear()
                stopRecordingService()
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

    fun clearBugs() {
        val pw = adminPassword
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { SupabaseApi.adminClearBugs(pw) }
                say("Journal effacé")
                refreshAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }

    fun lockAdmin() {
        adminPassword = ""
        _state.update { it.copy(adminUnlocked = false, adminStats = null, bugs = emptyList()) }
    }

    fun refreshAdmin() {
        if (adminPassword.isEmpty()) return
        viewModelScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) { SupabaseApi.adminStats(adminPassword) }
                val bugs = withContext(Dispatchers.IO) { SupabaseApi.adminBugs(adminPassword) }
                _state.update { it.copy(adminStats = stats, bugs = bugs) }
                refreshBeacons()
                refreshTrails()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }

    /** Position choisie par l'admin : 0 = centre de la carte, 1 = ma position GPS. Null si indisponible. */
    private fun pickPosition(source: Int): Pair<Double, Double>? {
        if (source == 1) {
            val st = _state.value
            val la = st.myLat
            val lo = st.myLon
            if (la == null || lo == null) {
                say("Position GPS indisponible")
                return null
            }
            return Pair(la, lo)
        }
        return Pair(mapLat, mapLon)
    }

    private fun parseNumber(text: String): Long? {
        val n = text.trim().toLongOrNull()
        return if (n != null && n in 1..9999) n else null
    }

    fun addBeacon(name: String, description: String, pattern: String, numberText: String, positionSource: Int) {
        if (!pattern.contains('1')) {
            say("Coche au moins un poinçon")
            return
        }
        var number: Long? = null
        if (numberText.isNotBlank()) {
            number = parseNumber(numberText)
            if (number == null) {
                say("Numéro invalide (1 à 9999)")
                return
            }
        }
        val pos = pickPosition(positionSource) ?: return
        val pw = adminPassword
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseApi.adminAddBeacon(pw, name.trim(), pos.first, pos.second, pattern, description.trim(), number)
                }
                say("Balise ajoutée")
                refreshAdmin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e, force = true)
            }
        }
    }

    /** move : 0 = garder la position, 1 = centre de la carte, 2 = ma position GPS. */
    fun updateBeacon(b: Beacon, name: String, description: String, pattern: String, numberText: String, move: Int) {
        if (!pattern.contains('1')) {
            say("Coche au moins un poinçon")
            return
        }
        val number = parseNumber(numberText)
        if (number == null) {
            say("Numéro invalide (1 à 9999)")
            return
        }
        var lat = b.lat
        var lon = b.lon
        if (move != 0) {
            val pos = pickPosition(if (move == 2) 1 else 0) ?: return
            lat = pos.first
            lon = pos.second
        }
        val pw = adminPassword
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseApi.adminUpdateBeacon(pw, b.id, name.trim(), lat, lon, pattern, description.trim(), number)
                }
                say("Balise modifiée")
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
