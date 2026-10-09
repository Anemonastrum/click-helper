package com.example.flashsale

import java.time.LocalTime

/** Coordinates are normalized to physical-display width and height, excluding no insets. */
data class TapPoint(
    val x: Float,
    val y: Float,
    val delayAfterMs: Long = 150,
    val rotation: Int,
    val targetPackage: String
) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "Coordinates out of bounds" }
        require(delayAfterMs in 0..10_000) { "Step delay out of bounds" }
    }
}

enum class Compensation(val factor: Double) {
    NONE(0.0), HALF_RTT(0.5), FULL_RTT(1.0)
}

enum class RunState { IDLE, ARMED, COUNTDOWN, RUNNING, COMPLETED, STOPPED, ERROR }

data class TimingSettings(
    val targetTime: LocalTime,
    val earlyMs: Long,
    val compensation: Compensation,
    val dryRun: Boolean,
    val tapDurationMs: Long,
    val points: List<TapPoint>
)

data class NtpSample(
    val offsetMs: Long,
    val roundTripMs: Long
)

data class SyncReading(
    val offsetMs: Long,
    val roundTripMs: Long,
    val jitterMs: Long,
    val recordedElapsedNanos: Long
)
