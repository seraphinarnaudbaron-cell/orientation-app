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
            validated = prefs.validated,
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

    private fun onNetworkError(e: Exception) {
        val wasOffline = _state.value.online == false
        _state.update { it.copy(online = false) }
        if (e is ApiException) {
            val m = e.message.orEmpty()
            if (m.contains("schema cache") || m.contains("Could not find")) {
                say("Base non initialisée : exécute supabase_setup.sql dans Supabase")
            } else {
                say(m.take(90))
            }
        } else if (!wasOffline) {
            say("Serveur injoignable (connexion coupée ou base en pause)")
        }
    }

    // ------------------------------------------------------------------ navigation
    fun go(screen: Screen) {
        _state.update { it.copy(screen = screen) }
    }

    fun back() {
        when (_state.value.screen) {
            Screen.Admin -> {
                lockAdmin()
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
        _state.update { it.copy(username = "", loggedIn = false, target = null, screen = Screen.Login) }
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
                recenterTick = if (first) it.recenterTick + 1 else it.recenterTick,
            )
        }
        if (first) viewModelScope.launch { pushPosition() }
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
                st.copy(beacons = list, online = true, target = still)
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
            _state.update { st -> st.copy(runners = list.filter { it.name != st.username }, online = true) }
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
        _state.update { it.copy(target = b) }
        if (b != null) say("Direction : " + b.title)
    }

    /** pattern : 9 caractères '0'/'1' reproduits par le coureur. */
    fun validate(b: Beacon, pattern: String): ValidationResult {
        val st = _state.value
        if (b.id in st.validated) return ValidationResult(true, "Déjà validée")
        if (Config.REQUIRE_PROXIMITY) {
            val d = st.distanceTo(b) ?: return ValidationResult(false, "Position GPS indisponible")
            if (d > Config.BEACON_REACH_M) {
                return ValidationResult(false, "Trop loin (" + Geo.format(d) + "), max " + Config.BEACON_REACH_M.toInt() + " m")
            }
        }
        if (pattern != b.pattern) return ValidationResult(false, "Ce n'est pas le bon motif, regarde bien.")
        val newSet = st.validated + b.id
        prefs.validated = newSet
        _state.update { it.copy(validated = newSet, target = if (it.target?.id == b.id) null else it.target) }
        val n = st.username
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SupabaseApi.logEvent(n, "beacon_validated")
            } catch (e: Exception) {
            }
        }
        return ValidationResult(true, "Balise validée !")
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
                onNetworkError(e)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onNetworkError(e)
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
                onNetworkError(e)
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
                onNetworkError(e)
            }
        }
    }
}
