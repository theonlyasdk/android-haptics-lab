package com.asdk.tools.vibrationlab

import android.os.VibrationEffect
import android.os.Vibrator

interface PresetHost {
    val vibrator: Vibrator?
    val hasAmplitude: Boolean
    val hasPrimitives: Boolean

    fun stopAll()
    fun playWaveformDirect(waveform: GeneratedWaveform, looping: Boolean, presetTitleRes: Int)
    fun playOneShotPreset(
        points: List<VibrationGraphView.VibrationPoint>,
        durationMs: Long,
        presetTitleRes: Int,
        action: () -> Unit
    )
    fun postDelayed(delayMs: Long, action: () -> Unit)
}

data class PresetItem(
    val titleRes: Int,
    val descRes: Int,
    val isLooping: Boolean,
    val action: (PresetHost) -> Unit
)

object PresetCatalog {

    val ONE_SHOTS: List<PresetItem> = listOf(
        PresetItem(R.string.vibration_oneshot_1_title, R.string.vibration_oneshot_1_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 255f, 18f),
                VibrationGraphView.VibrationPoint(1, 90f, 255f, 18f)
            )
            host.playOneShotPreset(points, 120L, R.string.vibration_oneshot_1_title) {
                if (host.hasPrimitives) {
                    Haptics.playPrimitive(host.vibrator, VibrationEffect.Composition.PRIMITIVE_CLICK, 0L)
                    host.postDelayed(90L) {
                        Haptics.playPrimitive(host.vibrator, VibrationEffect.Composition.PRIMITIVE_CLICK, 0L)
                    }
                } else {
                    Haptics.playWaveform(host.vibrator, longArrayOf(0, 18, 72, 18), intArrayOf(0, 255, 0, 255), -1)
                }
            }
        },
        PresetItem(R.string.vibration_oneshot_2_title, R.string.vibration_oneshot_2_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 255f, 12f)
            )
            host.playOneShotPreset(points, 30L, R.string.vibration_oneshot_2_title) {
                if (host.hasPrimitives) {
                    Haptics.playPrimitive(host.vibrator, VibrationEffect.Composition.PRIMITIVE_CLICK, 0L)
                } else if (host.hasAmplitude) {
                    Haptics.playOneShot(host.vibrator, 12L, 255)
                } else {
                    Haptics.playOneShot(host.vibrator, 20L, 0)
                }
            }
        },
        PresetItem(R.string.vibration_oneshot_3_title, R.string.vibration_oneshot_3_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 255f, 45f),
                VibrationGraphView.VibrationPoint(1, 140f, 170f, 22f)
            )
            host.playOneShotPreset(points, 180L, R.string.vibration_oneshot_3_title) {
                if (host.hasPrimitives) {
                    Haptics.playPrimitive(host.vibrator, VibrationEffect.Composition.PRIMITIVE_THUD, 0L)
                    host.postDelayed(140L) {
                        Haptics.playPrimitive(host.vibrator, VibrationEffect.Composition.PRIMITIVE_CLICK, 0L)
                    }
                } else {
                    Haptics.playWaveform(host.vibrator, longArrayOf(0, 45, 95, 22), intArrayOf(0, 255, 0, 170), -1)
                }
            }
        },
        PresetItem(R.string.vibration_oneshot_4_title, R.string.vibration_oneshot_4_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 255f, 65f)
            )
            host.playOneShotPreset(points, 85L, R.string.vibration_oneshot_4_title) {
                if (host.hasPrimitives) {
                    Haptics.playPrimitive(host.vibrator, VibrationEffect.Composition.PRIMITIVE_THUD, 0L)
                } else if (host.hasAmplitude) {
                    Haptics.playOneShot(host.vibrator, 65L, 255)
                } else {
                    Haptics.playOneShot(host.vibrator, 65L, 0)
                }
            }
        },
        PresetItem(R.string.vibration_oneshot_5_title, R.string.vibration_oneshot_5_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 130f, 8f)
            )
            host.playOneShotPreset(points, 25L, R.string.vibration_oneshot_5_title) {
                if (host.hasPrimitives) {
                    Haptics.playPrimitive(host.vibrator, VibrationEffect.Composition.PRIMITIVE_TICK, 0L)
                } else if (host.hasAmplitude) {
                    Haptics.playOneShot(host.vibrator, 8L, 130)
                } else {
                    Haptics.playOneShot(host.vibrator, 15L, 0)
                }
            }
        },
        PresetItem(R.string.vibration_oneshot_6_title, R.string.vibration_oneshot_6_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 200f, 15f),
                VibrationGraphView.VibrationPoint(1, 45f, 230f, 15f),
                VibrationGraphView.VibrationPoint(2, 90f, 255f, 15f)
            )
            host.playOneShotPreset(points, 130L, R.string.vibration_oneshot_6_title) {
                Haptics.playWaveform(host.vibrator, longArrayOf(0, 15, 30, 15, 30, 15), intArrayOf(0, 200, 0, 230, 0, 255), -1)
            }
        },
        PresetItem(R.string.vibration_oneshot_7_title, R.string.vibration_oneshot_7_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 160f, 20f),
                VibrationGraphView.VibrationPoint(1, 60f, 255f, 35f)
            )
            host.playOneShotPreset(points, 120L, R.string.vibration_oneshot_7_title) {
                Haptics.playWaveform(host.vibrator, longArrayOf(0, 20, 40, 35), intArrayOf(0, 160, 0, 255), -1)
            }
        },
        PresetItem(R.string.vibration_oneshot_8_title, R.string.vibration_oneshot_8_desc, false) { host ->
            host.stopAll()
            val points = listOf(
                VibrationGraphView.VibrationPoint(0, 0f, 180f, 30f),
                VibrationGraphView.VibrationPoint(1, 70f, 255f, 60f)
            )
            host.playOneShotPreset(points, 150L, R.string.vibration_oneshot_8_title) {
                Haptics.playWaveform(host.vibrator, longArrayOf(0, 30, 40, 60), intArrayOf(0, 180, 0, 255), -1)
            }
        }
    )

    val LOOPING_RHYTHMS: List<PresetItem> = listOf(
        PresetItem(R.string.vibration_loop_1_title, R.string.vibration_loop_1_desc, true) { host ->
            val wave = WaveformEngine.buildDiscreteWaveform(
                listOf(
                    EditablePoint(90L, 180),
                    EditablePoint(90L, 200),
                    EditablePoint(120L, 230),
                    EditablePoint(220L, 255)
                )
            )
            host.playWaveformDirect(wave, looping = true, presetTitleRes = R.string.vibration_loop_1_title)
        },
        PresetItem(R.string.vibration_loop_2_title, R.string.vibration_loop_2_desc, true) { host ->
            val wave = WaveformEngine.buildDiscreteWaveform(
                listOf(
                    EditablePoint(60L, 220),
                    EditablePoint(60L, 190),
                    EditablePoint(60L, 240),
                    EditablePoint(140L, 255)
                )
            )
            host.playWaveformDirect(wave, looping = true, presetTitleRes = R.string.vibration_loop_2_title)
        },
        PresetItem(R.string.vibration_loop_3_title, R.string.vibration_loop_3_desc, true) { host ->
            val wave = WaveformEngine.buildContinuousWaveform(WaveformMode.SINE, 25f, 220, 3000L)
            host.playWaveformDirect(wave, looping = true, presetTitleRes = R.string.vibration_loop_3_title)
        },
        PresetItem(R.string.vibration_loop_4_title, R.string.vibration_loop_4_desc, true) { host ->
            val wave = WaveformEngine.buildContinuousWaveform(WaveformMode.CHIRP, 10f, 240, 2000L, startFreq = 10f, endFreq = 50f)
            host.playWaveformDirect(wave, looping = true, presetTitleRes = R.string.vibration_loop_4_title)
        },
        PresetItem(R.string.vibration_loop_5_title, R.string.vibration_loop_5_desc, true) { host ->
            val wave = WaveformEngine.buildContinuousWaveform(WaveformMode.SQUARE, 4f, 230, 2000L, dutyFraction = 0.2)
            host.playWaveformDirect(wave, looping = true, presetTitleRes = R.string.vibration_loop_5_title)
        },
        PresetItem(R.string.vibration_loop_6_title, R.string.vibration_loop_6_desc, true) { host ->
            val wave = WaveformEngine.buildDiscreteWaveform(
                listOf(
                    EditablePoint(60L, 220), EditablePoint(60L, 220), EditablePoint(60L, 220),
                    EditablePoint(160L, 255), EditablePoint(160L, 255), EditablePoint(160L, 255),
                    EditablePoint(60L, 220), EditablePoint(60L, 220), EditablePoint(60L, 220)
                )
            )
            host.playWaveformDirect(wave, looping = true, presetTitleRes = R.string.vibration_loop_6_title)
        },
        PresetItem(R.string.vibration_loop_7_title, R.string.vibration_loop_7_desc, true) { host ->
            val wave = WaveformEngine.buildContinuousWaveform(WaveformMode.TRIANGLE, 1f, 200, 4000L)
            host.playWaveformDirect(wave, looping = true, presetTitleRes = R.string.vibration_loop_7_title)
        }
    )
}
