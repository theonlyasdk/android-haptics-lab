package com.asdk.tools.vibrationlab

import kotlin.math.abs
import kotlin.math.sin

object WaveformEngine {

    fun buildDiscreteWaveform(points: List<EditablePoint>): GeneratedWaveform {
        val timings = mutableListOf<Long>()
        val amps = mutableListOf<Int>()
        val graphPoints = mutableListOf<VibrationGraphView.VibrationPoint>()
        var currentTime = 0f
        val gap = 60f
        points.forEachIndexed { index, pt ->
            val dur = pt.durationMs.toFloat()
            val amp = if (dur > 0) pt.amplitude.toFloat() else 0f
            timings += pt.durationMs
            amps += pt.amplitude

            if (dur > 0) {
                graphPoints += VibrationGraphView.VibrationPoint(
                    stepIndex = index,
                    timeMs = currentTime,
                    amplitude = amp,
                    durationMs = dur
                )
            }
            currentTime += dur + gap
            if (index < points.size - 1) {
                timings += 60L
                amps += 0
            }
        }
        val totalTime = timings.sum()
        return GeneratedWaveform(graphPoints, false, timings.toLongArray(), amps.toIntArray(), totalTime)
    }

    fun buildContinuousWaveform(
        mode: WaveformMode,
        freq: Float,
        amp: Int,
        durationMs: Long,
        dutyFraction: Double = 0.5,
        startFreq: Float = freq,
        endFreq: Float = freq,
        isLfoEnabled: Boolean = false,
        lfoShape: LfoShape = LfoShape.RAMP_UP,
        lfoDepth: Float = 0f,
        lfoRate: Float = 1f
    ): GeneratedWaveform {
        val totalDuration = durationMs.coerceIn(500L, 30000L)
        val totalSec = totalDuration / 1000.0
        val visualPointsCount = (totalDuration / 50L).coerceIn(300L, 600L).toInt()
        val visualStepSec = totalSec / visualPointsCount

        val graphPoints = mutableListOf<VibrationGraphView.VibrationPoint>()
        var visualPhase = 0.0
        for (i in 0..visualPointsCount) {
            val tSec = i * visualStepSec
            val instFreq = if (mode == WaveformMode.CHIRP) {
                val prog = (i.toFloat() / visualPointsCount).coerceIn(0f, 1f)
                startFreq + (endFreq - startFreq) * prog
            } else if (isLfoEnabled) {
                when (lfoShape) {
                    LfoShape.RAMP_UP -> freq + lfoDepth * (tSec / totalSec).coerceIn(0.0, 1.0).toFloat()
                    LfoShape.SINE -> freq + lfoDepth * (0.5 * (1.0 + sin(2.0 * Math.PI * lfoRate * tSec))).toFloat()
                }
            } else {
                freq
            }

            visualPhase += 2.0 * Math.PI * instFreq * visualStepSec
            val normPhase = ((visualPhase / (2.0 * Math.PI)) % 1.0 + 1.0) % 1.0

            val factor: Float = when (mode) {
                WaveformMode.SINE -> (0.5 * (1.0 + sin(visualPhase))).toFloat()
                WaveformMode.SQUARE -> if (normPhase < dutyFraction) 1.0f else 0.0f
                WaveformMode.SAWTOOTH -> normPhase.toFloat()
                WaveformMode.TRIANGLE -> (1.0 - 2.0 * abs(normPhase - 0.5)).toFloat().coerceIn(0f, 1f)
                WaveformMode.CHIRP -> (0.5 * (1.0 + sin(2.0 * Math.PI * instFreq * tSec))).toFloat()
                else -> 1.0f
            }

            val a = (amp * factor).toInt().coerceIn(0, 255)
            graphPoints += VibrationGraphView.VibrationPoint(
                stepIndex = i,
                timeMs = (tSec * 1000.0).toFloat(),
                amplitude = a.toFloat(),
                durationMs = (visualStepSec * 1000.0).toFloat()
            )
        }

        val timings = mutableListOf<Long>()
        val amps = mutableListOf<Int>()
        val hapticStepMs = (totalDuration / 180L).coerceIn(20L, 50L)
        val hapticSteps = (totalDuration / hapticStepMs).toInt().coerceIn(10, 250)
        var hapticPhase = 0.0
        for (i in 0 until hapticSteps) {
            val tSec = i * (hapticStepMs / 1000.0)
            val instFreq = if (mode == WaveformMode.CHIRP) {
                val prog = (i.toFloat() / hapticSteps).coerceIn(0f, 1f)
                startFreq + (endFreq - startFreq) * prog
            } else if (isLfoEnabled) {
                when (lfoShape) {
                    LfoShape.RAMP_UP -> freq + lfoDepth * (tSec / totalSec).coerceIn(0.0, 1.0).toFloat()
                    LfoShape.SINE -> freq + lfoDepth * (0.5 * (1.0 + sin(2.0 * Math.PI * lfoRate * tSec))).toFloat()
                }
            } else {
                freq
            }

            hapticPhase += 2.0 * Math.PI * instFreq * (hapticStepMs / 1000.0)
            val normPhase = ((hapticPhase / (2.0 * Math.PI)) % 1.0 + 1.0) % 1.0

            val factor: Float = when (mode) {
                WaveformMode.SINE -> (0.5 * (1.0 + sin(hapticPhase))).toFloat()
                WaveformMode.SQUARE -> if (normPhase < dutyFraction) 1.0f else 0.0f
                WaveformMode.SAWTOOTH -> normPhase.toFloat()
                WaveformMode.TRIANGLE -> (1.0 - 2.0 * abs(normPhase - 0.5)).toFloat().coerceIn(0f, 1f)
                WaveformMode.CHIRP -> (0.5 * (1.0 + sin(2.0 * Math.PI * instFreq * tSec))).toFloat()
                else -> 1.0f
            }

            val a = (amp * factor).toInt().coerceIn(0, 255)
            timings += hapticStepMs
            amps += a
        }

        val isContinuous = mode != WaveformMode.SQUARE
        return GeneratedWaveform(graphPoints, isContinuous, timings.toLongArray(), amps.toIntArray(), totalDuration)
    }
}
