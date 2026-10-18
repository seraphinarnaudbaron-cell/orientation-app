package org.orientationjp.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Modifier une balise : numéro, nom, description, motif de poinçon, position. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconEditSheet(
    beacon: Beacon,
    onSave: (name: String, description: String, pattern: String, number: String, move: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var number by remember(beacon.id) { mutableStateOf(beacon.number.toString()) }
    var name by remember(beacon.id) { mutableStateOf(beacon.name) }
    var desc by remember(beacon.id) { mutableStateOf(beacon.description) }
    var move by remember(beacon.id) { mutableStateOf(0) }
    val cells = remember(beacon.id) { cellsFrom(beacon.pattern) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Modifier la balise", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
            BodyText("Numéro (affiché aux coureurs, unique) :")
            RoundField(number, { number = it.filter { ch -> ch.isDigit() }.take(4) }, "Numéro", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number)
            RoundField(name, { name = it }, "Nom", Modifier.fillMaxWidth())
            RoundField(desc, { desc = it }, "Description", Modifier.fillMaxWidth())
            BodyText("Motif de poinçon :")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PunchGrid(cells = cells, cell = 52.dp)
            }
            BodyText("Position :")
            ChoiceRow(listOf("Inchangée", "Centre de la carte", "Ma position GPS"), move) { move = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Enregistrer", Modifier.weight(1f)) {
                    onSave(name, desc, patternOf(cells), number, move)
                    onDismiss()
                }
                GhostButton("Annuler", Modifier.weight(1f), onDismiss)
            }
        }
    }
}
