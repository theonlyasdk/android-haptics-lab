package com.asdk.tools.vibrationlab

enum class WaveformMode {
    DISCRETE, SINE, SQUARE, SAWTOOTH, TRIANGLE, CHIRP
}

enum class LfoShape {
    RAMP_UP, SINE
}

data class EditablePoint(
    var durationMs: Long,
    var amplitude: Int
)

data class GeneratedWaveform(
    val graphPoints: List<VibrationGraphView.VibrationPoint>,
    val isContinuous: Boolean,
    val timings: LongArray,
    val amplitudes: IntArray,
    val totalTimeMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GeneratedWaveform
        if (graphPoints != other.graphPoints) return false
        if (isContinuous != other.isContinuous) return false
        if (!timings.contentEquals(other.timings)) return false
        if (!amplitudes.contentEquals(other.amplitudes)) return false
        if (totalTimeMs != other.totalTimeMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = graphPoints.hashCode()
        result = 31 * result + isContinuous.hashCode()
        result = 31 * result + timings.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        result = 31 * result + totalTimeMs.hashCode()
        return result
    }
}
