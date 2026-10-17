package org.orientationjp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** Fiche d'une balise : description, motif de poinçon à reproduire sur la carte, bouton « Y aller ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconSheet(
    beacon: Beacon,
    state: UiState,
    onGo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dist = state.distanceTo(beacon)

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
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(beacon.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.fillMaxWidth())
            Text(
                "n° " + beacon.number + " · " + (if (dist != null) "à " + Geo.format(dist) else "position inconnue"),
                fontSize = 14.sp,
                color = Muted,
                modifier = Modifier.fillMaxWidth(),
            )
            if (beacon.description.isNotBlank()) {
                Text(beacon.description, fontSize = 14.sp, color = Muted, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Motif de poinçon à reproduire sur ta carte :",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.fillMaxWidth(),
            )
            PatternView(pattern = beacon.pattern, cell = 64.dp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Y aller", Modifier.weight(1f), onGo)
                GhostButton("Fermer", Modifier.weight(1f), onDismiss)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconListSheet(state: UiState, onPick: (Beacon) -> Unit, onDismiss: () -> Unit) {
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
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Balises", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
            if (state.beacons.isEmpty()) {
                Text("Aucune balise pour l'instant.", color = Muted)
                Spacer(Modifier.height(8.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.beacons) { b ->
                        val d = state.distanceTo(b)
                        var label = "n°" + b.number + "  " + b.title
                        if (d != null) label += "   " + Geo.format(d)
                        GhostButton(label, Modifier.fillMaxWidth()) { onPick(b) }
                    }
                }
            }
            GhostButton("Fermer", Modifier.fillMaxWidth(), onDismiss)
        }
    }
}
