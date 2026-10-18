package org.orientationjp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GhostButton("Retour", onClick = onBack)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
    }
}

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Menu") { vm.back() }
        AppCard {
            CardTitle("Coureur : " + state.username)
            BodyText("En mode privé, ta position n'est plus envoyée et tu disparais de la carte des autres.")
            ToggleChip(
                checked = state.privateMode,
                textOn = "Mode privé : position cachée",
                textOff = "Je partage ma position",
                onToggle = { vm.setPrivate(it) },
            )
        }
        AppCard {
            CardTitle("Fond de carte")
            ChoiceRow(TileSources.LABELS, TileSources.KEYS.indexOf(state.basemap).coerceAtLeast(0)) { vm.setBasemap(TileSources.KEYS[it]) }
            BodyText("La photo aérienne (IGN) est idéale pour viser une balise précisément.")
        }
        OfflineMapCard(state, vm.mapLat, vm.mapLon)
        AppCard {
            SecondaryButton("Panel admin", Modifier.fillMaxWidth()) { vm.go(Screen.Admin) }
            GhostButton("Changer de nom", Modifier.fillMaxWidth()) { vm.logout() }
        }
        Text(
            "Version 2.0.0",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun AdminScreen(state: UiState, vm: AppViewModel) {
    var pw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val cells = remember { newCells() }
    var numText by remember { mutableStateOf("") }
    var posChoice by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Beacon?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Panel admin") { vm.back() }

        if (!state.adminUnlocked) {
            AppCard {
                CardTitle("Accès réservé")
                RoundField(
                    value = pw,
                    onValueChange = { pw = it },
                    hint = "Mot de passe admin",
                    modifier = Modifier.fillMaxWidth(),
                    password = true,
                    onAction = { vm.unlockAdmin(pw) },
                )
                PrimaryButton("Se connecter") { vm.unlockAdmin(pw) }
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppCard {
                    CardTitle("Statistiques")
                    val s = state.adminStats
                    if (s == null) {
                        BodyText("Chargement…")
                    } else {
                        BodyText("Balises : " + s.beacons, TextDark)
                        BodyText("Chemins : " + s.trails, TextDark)
                        BodyText("Coureurs en ligne (2 min) : " + s.activeNow, TextDark)
                        BodyText("Lancements de l'app : " + s.launches, TextDark)
                        BodyText("Coureurs différents : " + s.distinctUsers, TextDark)
                    }
                    GhostButton("Actualiser", Modifier.fillMaxWidth()) { vm.refreshAdmin() }
                }

                AppCard {
                    CardTitle("Nouvelle balise")
                    BodyText("Où placer la balise ?")
                    ChoiceRow(listOf("Centre de la carte", "Ma position GPS"), posChoice) { posChoice = it }
                    BodyText(
                        if (posChoice == 0) {
                            "Centre de la carte tel que tu l'as laissé : " +
                                String.format(java.util.Locale.US, "%.5f, %.5f", vm.mapLat, vm.mapLon)
                        } else {
                            "Là où tu te trouves maintenant (mets-toi sur la balise)."
                        }
                    )
                    RoundField(numText, { numText = it.filter { ch -> ch.isDigit() }.take(4) }, "Numéro (vide = automatique)", Modifier.fillMaxWidth(), keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    RoundField(name, { name = it }, "Nom (ex. Balise 4)", Modifier.fillMaxWidth())
                    RoundField(desc, { desc = it }, "Description", Modifier.fillMaxWidth())
                    BodyText("Motif de poinçons :")
                    PunchGrid(cells = cells, cell = 48.dp)
                    PrimaryButton("Ajouter ici") {
                        vm.addBeacon(name, desc, patternOf(cells), numText, posChoice)
                        name = ""
                        desc = ""
                        numText = ""
                        for (i in 0 until 9) cells[i] = false
                    }
                }

                AppCard {
                    CardTitle("Chemins et obstacles")
                    BodyText(
                        "Mode créateur : marche le long d'un chemin, l'app enregistre ton parcours. " +
                            "Quand deux chemins passent à moins de 5 m l'un de l'autre, ils sont reliés " +
                            "(nœud) : le coureur est guidé le long de tes chemins. " +
                            "Garde l'écran allumé et l'app au premier plan."
                    )
                    PrimaryButton("Créer un chemin") { vm.startRecording("path") }
                    BodyText(
                        "Obstacles : rivière, clôture, falaise... Marche le long de l'obstacle pour le tracer. " +
                            "Aucun chemin ne sera relié d'un côté à l'autre (sauf là où tu as enregistré un chemin qui le traverse, " +
                            "par exemple un pont)."
                    )
                    SecondaryButton("Tracer un obstacle", Modifier.fillMaxWidth()) { vm.startRecording("barrier") }
                    if (state.trails.isEmpty()) BodyText("Aucun chemin enregistré.")
                    for (t in state.trails) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                (if (t.isBarrier) "Obstacle · " else "Chemin · ") + (if (t.name.isBlank()) "#" + t.id else t.name) + " · " + Geo.format(t.length),
                                color = TextDark,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            GhostButton("Suppr.") { vm.deleteTrail(t) }
                        }
                    }
                }

                AppCard {
                    CardTitle("Balises existantes")
                    if (state.beacons.isEmpty()) BodyText("Aucune balise.")
                    for (b in state.beacons) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "n°" + b.number + "  " + b.title,
                                color = TextDark,
                                fontSize = 14.sp,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            GhostButton("Modifier") { editing = b }
                            GhostButton("Suppr.") { vm.deleteBeacon(b) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    editing?.let { b ->
        BeaconEditSheet(
            beacon = b,
            onSave = { n, d, p, num, mv -> vm.updateBeacon(b, n, d, p, num, mv) },
            onDismiss = { editing = null },
        )
    }
}
