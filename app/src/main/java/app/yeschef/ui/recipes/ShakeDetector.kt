package app.yeschef.ui.recipes

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

/**
 * Detects a deliberate back-and-forth phone shake while ignoring single bumps or setting the
 * device down. Requires [requiredPeaks] acceleration spikes above [thresholdG] spaced at least
 * [minPeakGapMs] apart within [windowMs], followed by a [cooldownMs] lockout.
 */
class ShakeDetector(
    private val thresholdG: Float = 2.1f,
    private val requiredPeaks: Int = 2,
    private val minPeakGapMs: Long = 110L,
    private val windowMs: Long = 600L,
    private val cooldownMs: Long = 1200L,
) {
    private var peakCount = 0
    private var firstPeakAt = 0L
    private var lastPeakAt = 0L
    private var lastTriggerAt = Long.MIN_VALUE

    /** Feeds one accelerometer magnitude sample in multiples of Earth gravity; returns true when a shake fires. */
    fun onSample(gForce: Float, nowMs: Long): Boolean {
        if (lastTriggerAt != Long.MIN_VALUE && nowMs - lastTriggerAt < cooldownMs) return false
        if (gForce < thresholdG) return false
        if (peakCount > 0 && nowMs - lastPeakAt < minPeakGapMs) return false
        if (peakCount == 0 || nowMs - firstPeakAt > windowMs) {
            peakCount = 1
            firstPeakAt = nowMs
            lastPeakAt = nowMs
        } else {
            peakCount++
            lastPeakAt = nowMs
        }
        if (peakCount >= requiredPeaks) {
            peakCount = 0
            lastTriggerAt = nowMs
            return true
        }
        return false
    }

    fun reset() {
        peakCount = 0
        firstPeakAt = 0L
        lastPeakAt = 0L
    }
}

/**
 * Listens to the accelerometer only while [enabled] is true and the host lifecycle is RESUMED,
 * so there is zero sensor use when on another tab, in a recipe, or backgrounded.
 */
@Composable
fun ShakeListener(enabled: Boolean, onShake: () -> Unit) {
    if (!enabled) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnShake by rememberUpdatedState(onShake)

    DisposableEffect(context, lifecycleOwner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || accel == null) return@DisposableEffect onDispose {}

        val detector = ShakeDetector()
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (detector.onSample(g, System.currentTimeMillis())) {
                    latestOnShake()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        var registered = false
        fun register() {
            if (!registered) {
                detector.reset()
                registered = manager.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun unregister() {
            if (registered) {
                manager.unregisterListener(sensorListener)
                registered = false
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
}
