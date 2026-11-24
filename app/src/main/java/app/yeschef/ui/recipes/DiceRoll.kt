package app.yeschef.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yeschef.data.Cookbook
import app.yeschef.data.local.RecipeEntity
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.rememberReduceMotion
import java.time.LocalDate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How long the die tumbles before the recipe pops up. */
private const val TUMBLE_MS = 1000

/**
 * The dice roll: a die tumbles and bounces for about a second, lands, and the picked recipe
 * pops up under it. With animations turned off on the phone, the recipe shows straight away.
 * [roll] changes on every roll, so "Roll again" plays it again.
 */
@Composable
fun DiceRollDialog(
    recipe: RecipeEntity,
    roll: Int,
    serverUrl: String?,
    today: LocalDate,
    onOpen: () -> Unit,
    onRollAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    var face by remember { mutableIntStateOf(Random.nextInt(1, 7)) }
    var landed by remember(roll) { mutableStateOf(reduceMotion) }
    val spin = remember(roll) { Animatable(0f) }
    val hop = remember(roll) { Animatable(0f) }
    val squash = remember(roll) { Animatable(1f) }

    LaunchedEffect(roll) {
        if (reduceMotion) return@LaunchedEffect
        coroutineScope {
            // Two and a bit turns, slowing down as it settles.
            launch { spin.animateTo(720f + Random.nextInt(-25, 26), tween(TUMBLE_MS, easing = FastOutSlowInEasing)) }
            // Three bounces, each lower than the last.
            launch {
                hop.animateTo(0f, keyframes {
                    durationMillis = TUMBLE_MS
                    -90f at 180
                    0f at 380
                    -45f at 520
                    0f at 680
                    -16f at 790
                    0f at 900
                })
            }
            // The face keeps changing while it tumbles, quicker at first.
            launch {
                var t = 0
                var step = 55
                while (t < TUMBLE_MS - 120) {
                    face = (face + Random.nextInt(1, 6) - 1) % 6 + 1
                    delay(step.toLong())
                    t += step
                    step += 8
                }
            }
        }
        // A little squash as it lands, then the recipe pops up.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { squash.animateTo(1f, keyframes { durationMillis = 220; 0.82f at 70; 1.06f at 150 }) }
        landed = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Die(
                face,
                Modifier.size(88.dp).graphicsLayer {
                    rotationZ = spin.value
                    translationY = hop.value * density
                    scaleY = squash.value
                    scaleX = 2f - squash.value
                },
            )
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(
                visible = landed,
                enter = if (reduceMotion) fadeIn(tween(0)) else
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.4f) +
                        fadeIn(tween(150)),
            ) {
                RolledCard(recipe, serverUrl, today, onOpen, onRollAgain)
            }
            // Keep the space while the die is tumbling so it doesn't jump when the card arrives.
            if (!landed) Spacer(Modifier.height(320.dp))
        }
    }
}

/** The recipe card that pops up once the die lands; public for screenshots. */
@Composable
fun RolledCard(recipe: RecipeEntity, serverUrl: String?, today: LocalDate, onOpen: () -> Unit, onRollAgain: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        RecipeImage(serverUrl, recipe.id, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "The dice says",
                style = MaterialTheme.typography.labelLarge,
                color = colors.tertiary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                recipe.name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(Cookbook.chapterOf(recipe), Cookbook.lastCookedText(recipe.lastMade, today) ?: "Not cooked yet")
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRollAgain) { Text("Roll again") }
                Button(onClick = onOpen) { Text("Let's cook") }
            }
        }
    }
}

/** A die showing [face] pips, in the cookbook's white, gold and blue. */
@Composable
private fun Die(face: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val body = Color.White
    val edge = colors.tertiary
    val pip = colors.primary
    Canvas(modifier) {
        val s = size.minDimension
        val corner = CornerRadius(s * 0.2f)
        drawRoundRect(body, cornerRadius = corner)
        drawRoundRect(edge, cornerRadius = corner, style = Stroke(width = s * 0.05f))
        val r = s * 0.085f
        val lo = s * 0.27f
        val mid = s * 0.5f
        val hi = s * 0.73f
        val spots = when (face) {
            1 -> listOf(mid to mid)
            2 -> listOf(lo to lo, hi to hi)
            3 -> listOf(lo to lo, mid to mid, hi to hi)
            4 -> listOf(lo to lo, hi to lo, lo to hi, hi to hi)
            5 -> listOf(lo to lo, hi to lo, mid to mid, lo to hi, hi to hi)
            else -> listOf(lo to lo, hi to lo, lo to mid, hi to mid, lo to hi, hi to hi)
        }
        spots.forEach { (x, y) -> drawCircle(pip, r, Offset(x, y)) }
    }
}
