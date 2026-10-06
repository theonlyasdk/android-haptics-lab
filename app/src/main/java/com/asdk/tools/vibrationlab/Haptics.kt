package com.asdk.tools.vibrationlab

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Thin wrapper over the vibration APIs, covering the three generations of the API:
 * the pre-Oreo waveform arrays, Oreo one-shot/waveform effects, and the Android 11
 * composition primitives.
 */
object Haptics {

    fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun hasVibrator(vibrator: Vibrator?): Boolean = vibrator?.hasVibrator() == true

    fun supportsAmplitude(vibrator: Vibrator?): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            vibrator?.hasAmplitudeControl() == true
    }

    /** True when the platform can play factory-tuned composition primitives (Android 11+). */
    fun supportsPrimitives(vibrator: Vibrator?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || vibrator == null) return false
        return vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_THUD
        )
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    @SuppressLint("MissingPermission")
    private fun vibrateWithAttributes(vibrator: Vibrator, effect: VibrationEffect) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_MEDIA)
                        .build()
                    vibrator.vibrate(effect, attrs)
                } else {
                    vibrator.vibrate(effect)
                }
            } catch (e: Exception) {
                // Ignore binder exceptions
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun playOneShot(vibrator: Vibrator?, durationMs: Long, amplitude: Int) {
        val target = vibrator ?: return
        if (durationMs <= 0L) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = if (target.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
            vibrateWithAttributes(target, VibrationEffect.createOneShot(durationMs, amp))
        } else {
            scope.launch {
                try {
                    @Suppress("DEPRECATION")
                    target.vibrate(durationMs)
                } catch (e: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun playWaveform(
        vibrator: Vibrator?,
        timings: LongArray,
        amplitudes: IntArray,
        repeat: Int
    ) {
        val target = vibrator ?: return
        if (timings.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val safeAmplitudes = if (target.hasAmplitudeControl()) {
                amplitudes
            } else {
                IntArray(timings.size) { VibrationEffect.DEFAULT_AMPLITUDE }
            }
            vibrateWithAttributes(target, VibrationEffect.createWaveform(timings, safeAmplitudes, repeat))
        } else {
            scope.launch {
                try {
                    @Suppress("DEPRECATION")
                    target.vibrate(timings, repeat)
                } catch (e: Exception) {}
            }
        }
    }

    /**
     * Plays a composition primitive, which is a factory-tuned impulse. On an X-axis
     * linear actuator this is what makes a tap read as crisp instead of smearing.
     */
    @SuppressLint("MissingPermission")
    fun playPrimitive(vibrator: Vibrator?, primitive: Int, delayMs: Long = 0L, scale: Float = 1.0f) {
        val target = vibrator ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!target.areAllPrimitivesSupported(primitive)) return
        val effect = VibrationEffect.startComposition()
            .addPrimitive(primitive, scale.coerceIn(0f, 1f), delayMs.toInt().coerceAtLeast(0))
            .compose()
        vibrateWithAttributes(target, effect)
    }

    /** Predefined effects (API 29+) act as safe fallbacks. */
    @SuppressLint("MissingPermission")
    fun playPredefined(vibrator: Vibrator?, effectId: Int) {
        val target = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateWithAttributes(target, VibrationEffect.createPredefined(effectId))
        }
    }

    @SuppressLint("MissingPermission")
    fun cancel(vibrator: Vibrator?) {
        scope.launch {
            try {
                vibrator?.cancel()
            } catch (e: Exception) {}
        }
    }

    /** Probes the device for advanced capabilities based on recent APIs */
    fun getHardwareDiagnostics(context: Context): String {
        val vibrator = vibrator(context) ?: return "No vibrator found on this device."
        val sb = java.lang.StringBuilder()
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})\n\n")

        val hasVib = hasVibrator(vibrator)
        sb.append("Vibrator Present: $hasVib\n")
        if (!hasVib) return sb.toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sb.append("Amplitude Control: ${vibrator.hasAmplitudeControl()}\n")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effs = listOf(
                VibrationEffect.EFFECT_CLICK to "CLICK",
                VibrationEffect.EFFECT_TICK to "TICK",
                VibrationEffect.EFFECT_DOUBLE_CLICK to "DOUBLE_CLICK",
                VibrationEffect.EFFECT_HEAVY_CLICK to "HEAVY_CLICK"
            )
            val supportedList = mutableListOf<String>()
            val fallbackList = mutableListOf<String>()
            val unknownList = mutableListOf<String>()

            for ((eff, name) in effs) {
                when (vibrator.areEffectsSupported(eff).firstOrNull()) {
                    Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> supportedList.add(name)
                    Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> fallbackList.add(name)
                    else -> unknownList.add(name)
                }
            }
            sb.append("\nPredefined Effects (API 29+):\n")
            if (supportedList.isNotEmpty()) sb.append(" Supported: ${supportedList.joinToString()}\n")
            if (fallbackList.isNotEmpty()) sb.append(" Simulated Fallback: ${fallbackList.joinToString()}\n")
            if (unknownList.isNotEmpty()) sb.append(" Unknown: ${unknownList.joinToString()}\n")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prims = listOf(
                VibrationEffect.Composition.PRIMITIVE_CLICK to "CLICK",
                VibrationEffect.Composition.PRIMITIVE_THUD to "THUD",
                VibrationEffect.Composition.PRIMITIVE_SPIN to "SPIN",
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to "QUICK_RISE",
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE to "SLOW_RISE",
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL to "QUICK_FALL",
                VibrationEffect.Composition.PRIMITIVE_TICK to "TICK"
            )
            val supportedPrims = mutableListOf<String>()
            for ((prim, name) in prims) {
                if (vibrator.areAllPrimitivesSupported(prim)) supportedPrims.add(name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                    supportedPrims.add("LOW_TICK")
                }
            }
            sb.append("\nPrimitives (API 30+):\n")
            sb.append(" Supported: ${if (supportedPrims.isEmpty()) "None" else supportedPrims.joinToString()}\n")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sb.append("\nResonant Frequency: ")
            val res = vibrator.resonantFrequency
            sb.append(if (res.isNaN()) "Unknown" else "${res}Hz")
            
            sb.append("\nQ Factor: ")
            val q = vibrator.qFactor
            sb.append(if (q.isNaN()) "Unknown" else "$q")
            sb.append("\n")
        }
        
        return sb.toString()
    }
}
