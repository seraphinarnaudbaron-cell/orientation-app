package org.orientationjp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val buttonShape = RoundedCornerShape(14.dp)

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Orange,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFE0C9A8),
            disabledContentColor = Color.White,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Green2, contentColor = Color.White),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Field, contentColor = Green2),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun CardTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
}

@Composable
fun BodyText(text: String, color: Color = Muted) {
    Text(text, fontSize = 14.sp, color = color)
}

@Composable
fun RoundField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = Muted) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onAction() }, onSearch = { onAction() }, onGo = { onAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Field,
            unfocusedContainerColor = Field,
            focusedBorderColor = Orange,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = Orange,
            focusedTextColor = TextDark,
            unfocusedTextColor = TextDark,
        ),
        modifier = modifier,
    )
}

@Composable
fun ToggleChip(checked: Boolean, textOn: String, textOff: String, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(buttonShape)
            .background(if (checked) Green2 else Field)
            .clickable { onToggle(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (checked) textOn else textOff,
            color = if (checked) Color.White else Muted,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun RoundFab(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Green, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Grille 3x3 de poinçons à cocher. */
@Composable
fun PunchGrid(cells: SnapshotStateList<Boolean>, cell: androidx.compose.ui.unit.Dp = 56.dp, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    val on = cells[i]
                    Box(
                        modifier = Modifier
                            .size(cell)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) Green else Field)
                            .clickable(enabled = enabled) { cells[i] = !on },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) {
                            Box(Modifier.size(cell / 2.5f).clip(CircleShape).background(Orange))
                        }
                    }
                }
            }
        }
    }
}

fun patternOf(cells: List<Boolean>): String = cells.joinToString("") { if (it) "1" else "0" }

fun newCells(): SnapshotStateList<Boolean> {
    val l = androidx.compose.runtime.mutableStateListOf<Boolean>()
    repeat(9) { l.add(false) }
    return l
}
