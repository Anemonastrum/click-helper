package com.example.flashsale

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/** Pure clock calculation, independently unit-testable. */
object Scheduler {
    fun nextOccurrenceEpochMs(nowEpochMs: Long, time: LocalTime, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        var scheduled: ZonedDateTime = now.toLocalDate().atTime(time).atZone(zone)
        if (!scheduled.toInstant().isAfter(now.toInstant())) scheduled = scheduled.plusDays(1)
        return scheduled.toInstant().toEpochMilli()
    }

    fun leadMs(earlyMs: Long, rttMs: Long, compensation: Compensation): Long =
        earlyMs + (rttMs * compensation.factor).roundToLong()

    fun deadlineElapsedNanos(
        nowEpochMs: Long, nowElapsedNanos: Long, targetEpochMs: Long,
        offsetMs: Long, leadMs: Long
    ): Long {
        val remainingMs = targetEpochMs - (nowEpochMs + offsetMs) - leadMs
        require(remainingMs > 0) { "Target time already passed" }
        return nowElapsedNanos + remainingMs * 1_000_000L
    }
}
