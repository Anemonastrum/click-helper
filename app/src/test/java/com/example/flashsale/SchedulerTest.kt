package com.example.flashsale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulerTest {
    private val zone = ZoneId.of("Asia/Jakarta")

    @Test fun nextOccurrenceSameDay() {
        val now = ZonedDateTime.of(2026, 10, 10, 19, 0, 0, 0, zone)
        val next = Scheduler.nextOccurrenceEpochMs(now.toInstant().toEpochMilli(), LocalTime.of(20, 0), zone)
        assertEquals(now.toLocalDate().atTime(20, 0).atZone(zone).toInstant().toEpochMilli(), next)
    }

    @Test fun nextOccurrenceTomorrowWhenPast() {
        val now = ZonedDateTime.of(2026, 10, 10, 21, 0, 0, 0, zone)
        val next = Scheduler.nextOccurrenceEpochMs(now.toInstant().toEpochMilli(), LocalTime.of(20, 0), zone)
        assertEquals(now.plusDays(1).toLocalDate().atTime(20, 0).atZone(zone).toInstant().toEpochMilli(), next)
    }

    @Test fun rttCompensationIsExplicit() {
        assertEquals(12L, Scheduler.leadMs(12L, 120L, Compensation.NONE))
        assertEquals(72L, Scheduler.leadMs(12L, 120L, Compensation.HALF_RTT))
        assertEquals(132L, Scheduler.leadMs(12L, 120L, Compensation.FULL_RTT))
    }

    @Test fun nextOccurrenceEqualToNowIsNextDay() {
        val now = ZonedDateTime.of(2026, 10, 10, 20, 0, 0, 0, zone)
        val next = Scheduler.nextOccurrenceEpochMs(now.toInstant().toEpochMilli(), LocalTime.of(20, 0), zone)
        assertEquals(now.plusDays(1).toInstant().toEpochMilli(), next)
    }

    @Test(expected = IllegalArgumentException::class)
    fun monotonicDeadlineRejectsExpiredTarget() {
        Scheduler.deadlineElapsedNanos(2_000L, 10_000_000L, 1_000L, 0L, 0L)
    }

    @Test fun monotonicDeadlineAdjustsForOffsetAndLead() {
        val deadline = Scheduler.deadlineElapsedNanos(1_000L, 5_000_000_000L, 3_000L, 40L, 15L)
        assertEquals(6_945_000_000L, deadline)
        assertTrue(deadline > 5_000_000_000L)
    }
}
