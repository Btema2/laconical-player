package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch

@Composable
fun CreatePlaylistDialog(
    originOffset: Offset?,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }
    val nameIsValid = text.text.trim().isNotEmpty()
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current

    fun animatedDismiss(callback: () -> Unit) {
        scope.launch {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            callback()
        }
    }

    BackHandler { animatedDismiss(onBack) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        focusRequester.requestFocus()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (progress.value * 1.6f).coerceIn(0f, 1f) }
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { animatedDismiss(onDismiss) },
                ),
        )

        // Card — upper-center
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = maxHeight * 0.15f, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            var cardCenterYPx by remember { mutableStateOf(0f) }
            val prog = progress.value

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        cardCenterYPx = coords.positionInRoot().y + coords.size.height / 2f
                    }
                    .graphicsLayer {
                        alpha = prog
                        val (scaleVal, transYVal) = if (originOffset != null) {
                            // Morph from origin row: scale 0.82→1, translateY from row→card center
                            val targetY = originOffset.y - cardCenterYPx
                            Pair(lerp(0.82f, 1f, prog), lerp(targetY, 0f, prog))
                        } else {
                            // Simple drop-in: scale 0.88→1, translateY -16dp→0
                            val startY = with(density) { -16.dp.toPx() }
                            Pair(lerp(0.88f, 1f, prog), lerp(startY, 0f, prog))
                        }
                        scaleX = scaleVal
                        scaleY = scaleVal
                        translationY = transYVal
                    }
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A24))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        tint = Color(0xFF7C6FE0),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Create new playlist",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12121A))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Playlist name", color = Color(0xFF555555)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (nameIsValid) onConfirm(text.text.trim())
                        }),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { animatedDismiss(onDismiss) }) {
                            Text("Cancel", color = Color(0xFF888888))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { if (nameIsValid) onConfirm(text.text.trim()) },
                            enabled = nameIsValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C6FE0),
                                disabledContainerColor = Color(0xFF7C6FE0).copy(alpha = 0.4f),
                            ),
                        ) {
                            Text("Create", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
