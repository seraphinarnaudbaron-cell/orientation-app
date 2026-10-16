package org.orientationjp.app

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mv = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(vm.mapZoom)
            controller.setCenter(GeoPoint(vm.mapLat, vm.mapLon))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    vm.rememberMap(mapCenter.latitude, mapCenter.longitude, zoomLevelDouble)
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    vm.rememberMap(mapCenter.latitude, mapCenter.longitude, zoomLevelDouble)
                    return false
                }
            })
        }
    }

    DisposableEffect(lifecycleOwner, mv) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mv.onPause()
        }
    }

    var selected by remember { mutableStateOf<Beacon?>(null) }
    var showList by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }
    var trailName by remember { mutableStateOf("") }

    // Pendant l'enregistrement d'un chemin : écran allumé (le GPS doit rester actif)
    val view = LocalView.current
    DisposableEffect(state.recording) {
        view.keepScreenOn = state.recording
        onDispose { view.keepScreenOn = false }
    }

    // Recentrage sur ma position quand on le demande (pas au simple retour sur l'écran)
    var seenTick by remember { mutableStateOf(state.recenterTick) }
    LaunchedEffect(state.recenterTick) {
        if (state.recenterTick != seenTick) {
            seenTick = state.recenterTick
            val la = state.myLat
            val lo = state.myLon
            if (la != null && lo != null) mv.controller.animateTo(GeoPoint(la, lo))
        }
    }

    fun focusBeacon(b: Beacon) {
        if (mv.zoomLevelDouble < 17.0) mv.controller.setZoom(17.0)
        mv.controller.animateTo(GeoPoint(b.lat, b.lon))
        selected = b
    }

    fun search() {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return
        val found = state.beacons.firstOrNull { (it.title + " " + it.description + " " + it.id).lowercase().contains(q) }
        if (found == null) {
            Toast.makeText(context, "Aucune balise trouvée", Toast.LENGTH_SHORT).show()
        } else {
            focusBeacon(found)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mv },
            update = { view -> syncOverlays(view, state) { selected = it } },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- barre du haut + bandeau de navigation
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundField(
                        value = query,
                        onValueChange = { query = it },
                        hint = "Chercher une balise",
                        modifier = Modifier.weight(1f),
                        imeAction = ImeAction.Search,
                        onAction = { search() },
                    )
                    GhostButton("Liste") { showList = true }
                    GhostButton("Menu") { vm.go(Screen.Settings) }
                }
            }

            val target = state.target
            if (target != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Green, RoundedCornerShape(16.dp))
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        targetText(state, target),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    Text(
                        "Annuler",
                        color = Color(0xFFFFC27A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { vm.setTarget(null) }
                            .padding(12.dp),
                    )
                }
            }

            if (state.recording) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFC62828), RoundedCornerShape(16.dp))
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "● REC " + (if (state.recordPaused) "(pause) " else "") +
                            Geo.format(pathLength(state.recorded)) + " · " + state.recorded.size + " pts",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    Text(
                        if (state.recordPaused) "Reprendre" else "Pause",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { vm.toggleRecordPause() }.padding(10.dp),
                    )
                    Text(
                        "Fin",
                        color = Color(0xFFFFE082),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showNameDialog = true }.padding(10.dp),
                    )
                    Text(
                        "Annuler",
                        color = Color(0xFFFFCDD2),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { vm.cancelRecording() }.padding(10.dp),
                    )
                }
            }
        }

        // ---- boutons flottants
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 14.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundFab("+") { mv.controller.zoomIn() }
            RoundFab("-") { mv.controller.zoomOut() }
            RoundFab("Moi") { vm.requestRecenter() }
        }

        // ---- statut
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(10.dp)
                .fillMaxWidth(),
        ) {
            Text(
                statusText(state),
                color = TextDark,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Nom du chemin") },
            text = {
                RoundField(
                    value = trailName,
                    onValueChange = { trailName = it },
                    hint = "ex. Sentier de la rivière",
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.finishRecording(trailName)
                    trailName = ""
                    showNameDialog = false
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Continuer") }
            },
        )
    }

    selected?.let { b ->
        BeaconSheet(
            beacon = b,
            state = state,
            onGo = {
                vm.setTarget(b)
                selected = null
            },
            onDismiss = { selected = null },
        )
    }

    if (showList) {
        BeaconListSheet(
            state = state,
            onPick = { b ->
                showList = false
                focusBeacon(b)
            },
            onDismiss = { showList = false },
        )
    }
}

private fun targetText(state: UiState, t: Beacon): String {
    val la = state.myLat
    val lo = state.myLon
    if (la == null || lo == null) return t.title + " · position inconnue"
    val d = Geo.distance(la, lo, t.lat, t.lon)
    if (d <= Config.BEACON_REACH_M) return t.title + " : tu y es !"
    val route = state.route
    if (route != null) return t.title + " · " + Geo.format(route.lengthM) + " par les chemins"
    val brg = Geo.bearing(la, lo, t.lat, t.lon)
    val base = t.title + " · " + Geo.format(d) + " · cap " + brg.toInt() + "° " + Geo.compass(brg)
    return if (state.trails.isNotEmpty()) base + " (hors chemins)" else base
}

private fun statusText(state: UiState): String {
    val parts = ArrayList<String>()
    parts.add(state.gpsStatus)
    if (state.privateMode) {
        parts.add("mode privé")
    } else if (state.online == false) {
        parts.add("hors ligne" + (if (state.lastError.isNotEmpty()) " (" + state.lastError + ")" else ""))
    } else if (state.online == true) {
        val n = state.runners.size
        parts.add(n.toString() + " autre" + (if (n > 1) "s" else "") + " en ligne")
    }
    if (state.beacons.isNotEmpty()) {
        parts.add(state.beacons.size.toString() + " balise" + (if (state.beacons.size > 1) "s" else ""))
    }
    return parts.joinToString(" · ")
}

private fun syncOverlays(mv: MapView, state: UiState, onBeacon: (Beacon) -> Unit) {
    val ctx = mv.context
    mv.overlays.clear()

    val la = state.myLat
    val lo = state.myLon
    val target = state.target

    // Réseau de chemins : visible seulement en mode admin (pour voir ce qui est déjà couvert)
    if (state.adminUnlocked) {
        for (t in state.trails) {
            mv.overlays.add(solidLine(t.points, "#992D6A4F", 6f))
        }
    }

    // Trace en cours d'enregistrement
    if (state.recording && state.recorded.size >= 2) {
        mv.overlays.add(solidLine(state.recorded, "#E53935", 9f))
    }

    // Itinéraire vers la balise visée
    if (target != null && la != null && lo != null) {
        val route = state.route
        if (route != null && route.points.isNotEmpty()) {
            mv.overlays.add(dashedLine(route.start, route.points.first()))
            mv.overlays.add(solidLine(route.points, "#F77F00", 10f))
            mv.overlays.add(dashedLine(route.points.last(), route.end))
        } else {
            mv.overlays.add(dashedLine(GeoPt(la, lo), GeoPt(target.lat, target.lon)))
        }
    }

    for (r in state.runners) {
        val m = Marker(mv)
        m.position = GeoPoint(r.lat, r.lon)
        m.icon = ContextCompat.getDrawable(ctx, R.drawable.user)
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        m.title = r.name
        mv.overlays.add(m)
    }

    for (b in state.beacons) {
        val m = Marker(mv)
        m.position = GeoPoint(b.lat, b.lon)
        m.icon = ContextCompat.getDrawable(ctx, R.drawable.beacon)
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        m.title = b.title
        m.setOnMarkerClickListener { _, _ ->
            onBeacon(b)
            true
        }
        mv.overlays.add(m)
    }

    if (la != null && lo != null) {
        val me = Marker(mv)
        me.position = GeoPoint(la, lo)
        me.icon = ContextCompat.getDrawable(ctx, R.drawable.me)
        me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        me.setOnMarkerClickListener { _, _ -> true }
        mv.overlays.add(me)
    }

    mv.invalidate()
}

private fun solidLine(points: List<GeoPt>, color: String, width: Float): Polyline {
    val line = Polyline()
    line.setPoints(points.map { GeoPoint(it.lat, it.lon) })
    line.outlinePaint.color = android.graphics.Color.parseColor(color)
    line.outlinePaint.strokeWidth = width
    line.outlinePaint.strokeCap = Paint.Cap.ROUND
    line.outlinePaint.strokeJoin = Paint.Join.ROUND
    return line
}

private fun dashedLine(a: GeoPt, b: GeoPt): Polyline {
    val line = Polyline()
    line.setPoints(listOf(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon)))
    line.outlinePaint.color = android.graphics.Color.parseColor("#F77F00")
    line.outlinePaint.strokeWidth = 7f
    line.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f)
    return line
}
