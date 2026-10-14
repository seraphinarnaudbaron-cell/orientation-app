package org.orientationjp.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(initialName: String, initialPrivate: Boolean, onStart: (String, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var priv by rememberSaveable { mutableStateOf(initialPrivate) }

    Box(Modifier.fillMaxSize().background(Green)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-80).dp)
                .size(280.dp)
                .clip(CircleShape)
                .background(Green2)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-70).dp, y = 60.dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(Orange)
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(92.dp))
            Spacer(Modifier.height(14.dp))
            Text("Orientation J.P.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Collège Jean Prévost · Villard-de-Lans", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            AppCard {
                CardTitle("Ton nom de coureur")
                RoundField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    hint = "ex. Léa",
                    modifier = Modifier.fillMaxWidth(),
                    onAction = { onStart(name, priv) },
                )
                ToggleChip(
                    checked = priv,
                    textOn = "Mode privé : position cachée",
                    textOff = "Je partage ma position",
                    onToggle = { priv = it },
                )
                PrimaryButton("Commencer") { onStart(name, priv) }
            }
        }
    }
}

