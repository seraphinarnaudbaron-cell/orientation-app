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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconSheet(
    beacon: Beacon,
    state: UiState,
    vm: AppViewModel,
    onGo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cells = remember(beacon.id) { newCells() }
    val alreadyDone = beacon.id in state.validated
    var feedback by remember { mutableStateOf(if (alreadyDone) "Déjà validée" else "") }
    var ok by remember { mutableStateOf(alreadyDone) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(closing) {
        if (closing) {
            delay(1200L)
            onDismiss()
        }
    }

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
                if (dist != null) "À " + Geo.format(dist) else "Position inconnue",
                fontSize = 14.sp,
                color = Muted,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (beacon.description.isBlank()) "Pas de description." else beacon.description,
                fontSize = 14.sp,
                color = Muted,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Reproduis les poinçons vus sur la balise :",
                fontSize = 14.sp,
                color = TextDark,
                modifier = Modifier.fillMaxWidth(),
            )
            PunchGrid(cells = cells, cell = 58.dp, enabled = !alreadyDone)
            Text(
                feedback,
                color = if (ok) Green2 else ErrorRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
            PrimaryButton("Valider", enabled = !alreadyDone) {
                val r = vm.validate(beacon, patternOf(cells))
                feedback = r.message
                ok = r.ok
                if (r.ok) closing = true
            }
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
                        var label = b.title
                        if (b.id in state.validated) label += "  (validée)"
                        if (d != null) label += "   " + Geo.format(d)
                        GhostButton(label, Modifier.fillMaxWidth()) { onPick(b) }
                    }
                }
            }
            GhostButton("Fermer", Modifier.fillMaxWidth(), onDismiss)
        }
    }
}
