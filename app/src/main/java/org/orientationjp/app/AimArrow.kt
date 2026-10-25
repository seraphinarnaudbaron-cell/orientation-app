package org.orientationjp.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Petite flèche (en haut à gauche) qui apparaît à moins de 60 m de la balise VISÉE
 * (« Y aller » ou prochaine balise du parcours) et pointe vers elle.
 */
@Composable
fun AimArrow(state: UiState, vm: AppViewModel) {
    val heading by vm.heading.collectAsStateWithLifecycle()
    val beacon = state.aimed ?: return
    val dist = state.distanceTo(beacon) ?: return
    if (dist > Config.ARROW_RADIUS_M) return
    val la = state.myLat ?: return
    val lo = state.myLon ?: return

    val bearing = Geo.bearing(la, lo, beacon.lat, beacon.lon)
    // sans boussole : la flèche suit la carte (haut = nord)
    val angle = (bearing - (heading ?: 0f).toDouble()).toFloat()

    Column(
        Modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h)
                lineTo(w / 2f, h * 0.72f)
                lineTo(0f, h)
                close()
            }
            rotate(degrees = angle, pivot = center) {
                drawPath(path, Orange)
            }
        }
        Text(
            "n°" + beacon.number + " · " + Geo.format(dist),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
        )
    }
}
