package app.yeschef.ui.cook

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.yeschef.ui.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** How long the little scene plays before cook mode shows. */
private const val PLAY_MS = 1200

/** The fade out at the end. */
private const val FADE_MS = 200

/** The title of cook mode, on the chip and under the intro. */
const val LETS_COOK = "Let's cook!"

/** Colours an intro draws with: the cookbook's white, gold and blue, plus a few vegetables. */
class IntroPalette(
    val body: Color,
    val edge: Color,
    val accent: Color,
    val steam: Color,
    val flame: Color,
    val carrot: Color = Color(0xFFE8893A),
    val green: Color = Color(0xFF6FA35B),
    val red: Color = Color(0xFFD5574B),
)

/**
 * One scene in the catalog. [draw] paints it on a square canvas at [progress] 0..1, in a
 * 100 by 100 grid (multiply by `u`), so a scene is just a drawing function of time.
 */
class CookIntro(val name: String, val draw: DrawScope.(progress: Float, palette: IntroPalette, u: Float) -> Unit)

/**
 * The intros cook mode picks from at random. To add one, write a draw function like
 * [wokToss] and add it to [all].
 */
object CookIntros {
    val all: List<CookIntro> = listOf(
        CookIntro("wok") { t, p, u -> wokToss(t, p, u) },
        CookIntro("chop") { t, p, u -> chopping(t, p, u) },
        CookIntro("steam") { t, p, u -> steamingPot(t, p, u) },
    )

    fun named(name: String): CookIntro = all.first { it.name == name }

    fun random(random: Random = Random.Default): CookIntro = all.random(random)
}

/**
 * A short scene picked at random when cook mode starts: about a second, then it fades and
 * calls [onDone]. Tapping anywhere skips it. With animations turned off on the phone it
 * shows nothing and calls [onDone] straight away.
 */
@Composable
fun CookIntroOverlay(onDone: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val done by rememberUpdatedState(onDone)
    val intro = remember { CookIntros.random() }
    val progress = remember { Animatable(0f) }
    val pop = remember { Animatable(0.5f) }
    val veil = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            done()
            return@LaunchedEffect
        }
        launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        launch { veil.animateTo(1f, tween(150)) }
        progress.animateTo(1f, tween(PLAY_MS, easing = LinearEasing))
        veil.animateTo(0f, tween(FADE_MS))
        done()
    }
    if (reduceMotion) return

    Dialog(onDismissRequest = { done() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Our own backdrop: a plain screen in the app's background colour, so the recipe
        // doesn't show through. It fades with the card to reveal cook mode at the end.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = veil.value }
                .background(MaterialTheme.colorScheme.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
            contentAlignment = Alignment.Center,
        ) {
            CookIntroCard(
                intro,
                progress.value,
                Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
        }
    }
}

/** The card with the scene and "Let's cook!"; public for screenshots. */
@Composable
fun CookIntroCard(intro: CookIntro, progress: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val palette = IntroPalette(
        body = Color.White,
        edge = colors.tertiary,
        accent = colors.primary,
        steam = colors.primary.copy(alpha = 0.6f),
        flame = colors.tertiary,
    )
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        modifier = modifier.width(260.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(170.dp)) {
                val u = size.minDimension / 100f
                intro.draw(this, progress, palette, u)
            }
            Spacer(Modifier.height(12.dp))
            Text(LETS_COOK, style = MaterialTheme.typography.headlineMedium, color = colors.primary)
        }
    }
}

// --- The scenes. Coordinates are in a 100 x 100 grid; `u` turns them into pixels. ---

private fun o(x: Float, y: Float, u: Float) = Offset(x * u, y * u)

/** Small flickering flames under a pan or pot. */
private fun DrawScope.flames(t: Float, p: IntroPalette, u: Float, y: Float) {
    listOf(38f, 50f, 62f).forEachIndexed { i, x ->
        val flicker = 0.75f + 0.25f * sin(t * PI.toFloat() * 10f + i * 1.7f)
        val h = 9f * flicker
        drawOval(p.flame, topLeft = o(x - 3f, y - h, u), size = Size(6f * u, h * u), alpha = 0.85f)
    }
}

/** A wok tossing stir-fry twice: the pan flicks and the pieces fly up and land. */
private fun DrawScope.wokToss(t: Float, p: IntroPalette, u: Float) {
    val phase = (t * 2f) % 1f
    val air = sin(phase * PI.toFloat()) // 0, up to 1, back to 0
    val tilt = -9f * sin(phase * 2f * PI.toFloat())
    flames(t, p, u, 84f)
    rotate(tilt, pivot = o(50f, 60f, u)) {
        translate(top = -4f * air * u) {
            // Handle, behind the bowl.
            drawLine(p.accent, o(76f, 57f, u), o(95f, 50f, u), strokeWidth = 5f * u, cap = StrokeCap.Round)
            val bowl = Path().apply {
                moveTo(22f * u, 56f * u)
                quadraticTo(50f * u, 90f * u, 78f * u, 56f * u)
                close()
            }
            drawPath(bowl, p.body)
            drawPath(bowl, p.edge, style = Stroke(width = 2.5f * u))
        }
    }
    // The stir-fry, flying higher on the first toss.
    val pieces = listOf(36f, 44f, 52f, 60f, 40f, 56f, 64f)
    val foods = listOf(p.green, p.red, p.carrot, p.green, p.carrot, p.red, p.flame)
    val lift = if (t < 0.5f) 1f else 0.75f
    pieces.forEachIndexed { i, x ->
        val height = air * lift * (20f + (i % 3) * 8f)
        val drift = air * (if (i % 2 == 0) 4f else -4f)
        val cx = x + drift
        val cy = 58f - height
        val spin = phase * 360f * (if (i % 2 == 0) 1f else -1f)
        rotate(spin, pivot = o(cx, cy, u)) {
            drawRoundRect(
                foods[i],
                topLeft = o(cx - 3.5f, cy - 2.5f, u),
                size = Size(7f * u, 5f * u),
                cornerRadius = CornerRadius(1.5f * u),
            )
        }
    }
}

/** A cleaver chopping a carrot three times; the slices slide off to the left. */
private fun DrawScope.chopping(t: Float, p: IntroPalette, u: Float) {
    val chops = 3
    val phase = (t * chops) % 1f
    val done = floor(t * chops).toInt()
    val hit = 0.35f
    val drop = if (phase < hit) (phase / hit).let { it * it } else 1f - ((phase - hit) / (1f - hit)).let { it * (2f - it) }
    val slices = (done + if (phase >= hit) 1 else 0).coerceAtMost(chops)
    val cut = 56f

    // Board.
    drawRoundRect(p.body, topLeft = o(8f, 66f, u), size = Size(84f * u, 13f * u), cornerRadius = CornerRadius(4f * u))
    drawRoundRect(p.edge, topLeft = o(8f, 66f, u), size = Size(84f * u, 13f * u), cornerRadius = CornerRadius(4f * u), style = Stroke(2.5f * u))
    // The rest of the carrot, with its leaves.
    drawLine(p.green, o(87f, 61f, u), o(95f, 54f, u), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
    drawLine(p.green, o(87f, 61f, u), o(97f, 61f, u), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
    drawRoundRect(p.carrot, topLeft = o(cut, 56f, u), size = Size(32f * u, 10f * u), cornerRadius = CornerRadius(5f * u))
    // Slices, the newest sliding out from the blade.
    val slide = if (phase >= hit && done < chops) ((phase - hit) / 0.3f).coerceIn(0f, 1f) else 1f
    for (j in 0 until slices) {
        val back = slices - 1 - j
        val x = if (back == 0) cut - 4f - 6f * slide else cut - 10f - back * 9f
        rotate(-10f, pivot = o(x, 61f, u)) {
            drawOval(p.carrot, topLeft = o(x - 3f, 56f, u), size = Size(6f * u, 10f * u))
            drawOval(p.flame, topLeft = o(x - 1.2f, 59f, u), size = Size(2.4f * u, 4f * u), alpha = 0.6f)
        }
    }
    // Cleaver: blade edge comes down to the board, handle on top.
    val bottom = 36f + 30f * drop
    val top = bottom - 22f
    val blade = Path().apply {
        moveTo((cut - 8f) * u, top * u)
        lineTo((cut + 3f) * u, top * u)
        lineTo((cut + 3f) * u, bottom * u)
        lineTo((cut - 8f) * u, (bottom - 3f) * u)
        close()
    }
    drawRoundRect(p.accent, topLeft = o(cut - 5.5f, top - 13f, u), size = Size(6f * u, 14f * u), cornerRadius = CornerRadius(2.5f * u))
    drawPath(blade, p.body)
    drawPath(blade, p.edge, style = Stroke(width = 2.5f * u))
    drawCircle(p.edge, radius = 1.4f * u, center = o(cut - 2.5f, top + 4f, u))
    // A little "chop!" at each hit.
    if (phase in hit..(hit + 0.2f)) {
        val a = 1f - (phase - hit) / 0.2f
        drawLine(p.edge, o(cut - 14f, 58f, u), o(cut - 19f, 53f, u), strokeWidth = 2f * u, cap = StrokeCap.Round, alpha = a)
        drawLine(p.edge, o(cut + 8f, 50f, u), o(cut + 12f, 45f, u), strokeWidth = 2f * u, cap = StrokeCap.Round, alpha = a)
    }
}

/** A pot on the boil: the lid rattles and steam curls up. */
private fun DrawScope.steamingPot(t: Float, p: IntroPalette, u: Float) {
    val twoPi = 2f * PI.toFloat()
    flames(t, p, u, 95f)
    // Pot.
    drawLine(p.accent, o(13f, 60f, u), o(22f, 60f, u), strokeWidth = 4f * u, cap = StrokeCap.Round)
    drawLine(p.accent, o(78f, 60f, u), o(87f, 60f, u), strokeWidth = 4f * u, cap = StrokeCap.Round)
    drawRoundRect(p.body, topLeft = o(22f, 50f, u), size = Size(56f * u, 36f * u), cornerRadius = CornerRadius(8f * u))
    drawRoundRect(p.edge, topLeft = o(22f, 50f, u), size = Size(56f * u, 36f * u), cornerRadius = CornerRadius(8f * u), style = Stroke(2.5f * u))
    drawLine(p.edge, o(28f, 62f, u), o(72f, 62f, u), strokeWidth = 1.5f * u, alpha = 0.5f)
    // Lid, rattling.
    val wobble = sin(t * twoPi * 6f)
    val lift = abs(wobble) * 2.5f
    rotate(wobble * 4f, pivot = o(50f, 50f, u)) {
        translate(top = -lift * u) {
            val lid = Path().apply {
                moveTo(19f * u, 50f * u)
                quadraticTo(50f * u, 34f * u, 81f * u, 50f * u)
                close()
            }
            drawPath(lid, p.body)
            drawPath(lid, p.edge, style = Stroke(width = 2.5f * u))
            drawCircle(p.accent, radius = 3.5f * u, center = o(50f, 40f, u))
        }
    }
    // Steam: three wisps that rise, wiggle and fade.
    listOf(36f, 50f, 64f).forEachIndexed { i, x ->
        val rise = (t * 1.6f + i * 0.33f) % 1f
        val alpha = (1f - rise) * (rise * 4f).coerceAtMost(1f)
        val base = 36f - rise * 24f
        val wisp = Path()
        for (k in 0..10) {
            val yy = base - k * 1.6f
            val xx = x + 3f * sin(k * 0.55f + t * twoPi * 2f + i)
            if (k == 0) wisp.moveTo(xx * u, yy * u) else wisp.lineTo(xx * u, yy * u)
        }
        drawPath(wisp, p.steam, alpha = alpha, style = Stroke(width = 3f * u, cap = StrokeCap.Round))
    }
}
