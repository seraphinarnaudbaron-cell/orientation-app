package org.orientationjp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun courseSummary(c: Course): String {
    val ids = c.order.joinToString(" > ") { it.number.toString() }
    var s = "Ordre : " + ids + " > arrivée\n" + Geo.format(c.lengthM) + " · " + Geo.formatTime(c.timeS)
    if (c.ascent >= 1.0 || c.descent >= 1.0) {
        s += " · D+ " + Math.round(c.ascent) + " m · D- " + Math.round(c.descent) + " m"
    }
    return s
}

/** Bandeau de suivi : prochaine balise, distance restante, temps estimé, balises faites. */
fun courseBannerText(state: UiState, c: Course): String {
    val visited = state.courseVisited
    val doneN = visited.size
    if (state.courseDone) return "Parcours terminé !\n" + doneN + " balise" + (if (doneN > 1) "s" else "") + " · bravo"
    val g = c.geometry
    val remaining = Math.max(0.0, g.total - state.courseProgress)
    val timeLeft = if (g.total > 0.0) c.timeS * remaining / g.total else 0.0
    val nextIdx = c.order.indexOfFirst { it.id !in visited }
    val first = if (nextIdx >= 0 && nextIdx < g.stopCum.size) {
        "Prochaine : n°" + c.order[nextIdx].number + " · " + Geo.format(Math.max(0.0, g.stopCum[nextIdx] - state.courseProgress))
    } else {
        "Va à l'arrivée · " + Geo.format(remaining)
    }
    val totalN = doneN + c.order.count { it.id !in visited }
    return first + "\nReste " + Geo.format(remaining) + " · " + Geo.formatTime(timeLeft) + " · " + doneN + "/" + totalN + " balises"
}

/** Composer un parcours : numéros des balises + arrivée sur la carte, l'app trouve l'ordre le plus rapide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSheet(
    state: UiState,
    text: String,
    onText: (String) -> Unit,
    onPickFinish: () -> Unit,
    onPlan: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Parcours", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
            BodyText(
                "Entre les numéros des balises à faire (dans n'importe quel ordre) et pose l'arrivée sur la carte. " +
                    "L'app calcule l'ordre le plus rapide en suivant les chemins, dénivelé compris. Le départ est ta position."
            )
            if (state.beacons.isNotEmpty()) {
                BodyText("Balises disponibles : " + state.beacons.joinToString(", ") { it.number.toString() }, TextDark)
            }
            RoundField(
                value = text,
                onValueChange = onText,
                hint = "Numéros (ex. 3, 5, 8)",
                modifier = Modifier.fillMaxWidth(),
            )
            BodyText(
                if (state.finish != null) "Arrivée : placée (point rouge sur la carte)" else "Arrivée : pas encore placée",
                if (state.finish != null) Green2 else Muted,
            )
            GhostButton(
                if (state.finish != null) "Déplacer l'arrivée" else "Placer l'arrivée sur la carte",
                Modifier.fillMaxWidth(),
                onPickFinish,
            )
            PrimaryButton("Calculer le parcours", onClick = onPlan)

            val course = state.course
            if (course != null) {
                AppCard {
                    CardTitle("Parcours optimal")
                    BodyText(courseSummary(course), TextDark)
                    for (leg in course.legs) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                leg.from + " > " + leg.to,
                                fontSize = 13.sp,
                                color = TextDark,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                Geo.format(leg.lengthM) + (if (leg.onTrails) "" else " (hors chemin)"),
                                fontSize = 13.sp,
                                color = if (leg.onTrails) Muted else ErrorRed,
                            )
                        }
                    }
                }
                GhostButton("Effacer le parcours", Modifier.fillMaxWidth(), onClear)
            }
            GhostButton("Fermer", Modifier.fillMaxWidth(), onDismiss)
        }
    }
}
